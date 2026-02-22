#!/usr/bin/env bash
# Generate ItemModelTable.DAT from the game database and upload to R2.
#
# Binary format: flat array of uint16le values.
# At byte offset (itemId * 2), the 16-bit model ID (MId) is stored.
# File size = (max_item_id + 1) * 2 bytes.
#
# Prerequisites:
#   - SSH access to game server via `ssh phoenix-game-beta`
#   - DB credentials in /srv/phoenix/settings/network.lua on the server
#   - wrangler authenticated for R2 uploads
#
# Usage: ./infra/generate-item-model-table.sh

set -euo pipefail

REMOTE_HOST="phoenix-game-beta"
R2_BUCKET="phoenix-dat-files"
R2_KEY="landsandboat/ItemModelTable.DAT"
LOCAL_TMP="$(mktemp)"

echo "Generating ItemModelTable.DAT on ${REMOTE_HOST}..."

# Extract DB credentials from server config and generate binary on the remote host.
# The python script reads mysql output from stdin and writes raw bytes to stdout.
ssh "${REMOTE_HOST}" '
  DB_PASS=$(grep -oP "SQL_PASSWORD\s*=\s*\"\K[^\"]*" /srv/phoenix/settings/network.lua)
  mysql -u root -p"${DB_PASS}" xidb -N -B -e "SELECT itemId, MId FROM item_equipment" | python3 -c "
import sys, struct

lines = sys.stdin.read().strip().split(chr(10))
entries = []
max_id = 0
for line in lines:
    parts = line.split(chr(9))
    item_id = int(parts[0])
    model_id = int(parts[1])
    entries.append((item_id, model_id))
    if item_id > max_id:
        max_id = item_id

buf = bytearray((max_id + 1) * 2)
for item_id, model_id in entries:
    struct.pack_into(chr(60) + chr(72), buf, item_id * 2, model_id)

sys.stdout.buffer.write(buf)
print(f\"Wrote {len(entries)} entries, max_id={max_id}, file_size={len(buf)} bytes\", file=sys.stderr)
"
' > "${LOCAL_TMP}"

FILE_SIZE=$(wc -c < "${LOCAL_TMP}" | tr -d ' ')
echo "Downloaded ${FILE_SIZE} bytes to ${LOCAL_TMP}"

echo "Uploading to R2: ${R2_BUCKET}/${R2_KEY}..."
wrangler r2 object put "${R2_BUCKET}/${R2_KEY}" \
  --file="${LOCAL_TMP}" \
  --remote \
  --content-type=application/octet-stream

rm -f "${LOCAL_TMP}"
echo "Done. ItemModelTable.DAT uploaded to R2."
