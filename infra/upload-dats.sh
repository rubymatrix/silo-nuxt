#!/usr/bin/env bash
# Upload FFXI DAT files to Cloudflare R2.
#
# Prerequisites:
#   - wrangler CLI authenticated (wrangler login)
#   - rclone configured with an "r2" remote (see DAT_INFRASTRUCTURE.md)
#
# Usage:
#   ./infra/upload-dats.sh /path/to/ffxi/install
#
# Example:
#   ./infra/upload-dats.sh "/mnt/c/Program Files (x86)/PlayOnline/SquareEnix/FINAL FANTASY XI"

set -euo pipefail

BUCKET="phoenix-dat-files"
FFXI_DIR="${1:?Usage: $0 <ffxi-game-directory>}"

if [ ! -d "$FFXI_DIR" ]; then
  echo "Error: $FFXI_DIR is not a directory" >&2
  exit 1
fi

echo "=== Uploading bootstrap files with wrangler ==="

for file in FFXiMain.dll VTABLE.DAT FTABLE.DAT; do
  if [ -f "$FFXI_DIR/$file" ]; then
    echo "  $file"
    wrangler r2 object put "$BUCKET/$file" --file "$FFXI_DIR/$file"
  else
    echo "  SKIP: $file not found"
  fi
done

if [ -f "$FFXI_DIR/landsandboat/ItemModelTable.DAT" ]; then
  echo "  landsandboat/ItemModelTable.DAT"
  wrangler r2 object put "$BUCKET/landsandboat/ItemModelTable.DAT" \
    --file "$FFXI_DIR/landsandboat/ItemModelTable.DAT"
else
  echo "  SKIP: landsandboat/ItemModelTable.DAT not found"
fi

echo ""
echo "=== Uploading ROM table files (ROM2-ROM9) ==="

for n in 2 3 4 5 6 7 8 9; do
  for table in VTABLE FTABLE; do
    src="$FFXI_DIR/ROM${n}/${table}${n}.DAT"
    if [ -f "$src" ]; then
      echo "  ROM${n}/${table}${n}.DAT"
      wrangler r2 object put "$BUCKET/ROM${n}/${table}${n}.DAT" --file "$src"
    fi
  done
done

echo ""
echo "=== Bulk uploading ROM directories with rclone ==="
echo "    (this may take a while for ~15-20 GB)"
echo ""

for dir in ROM ROM2 ROM3 ROM4 ROM5 ROM6 ROM7 ROM8 ROM9; do
  src="$FFXI_DIR/$dir"
  if [ -d "$src" ]; then
    echo "  Syncing $dir/ ..."
    rclone sync "$src" "r2:$BUCKET/$dir" --progress --transfers 16
  else
    echo "  SKIP: $dir/ not found"
  fi
done

echo ""
echo "=== Upload complete ==="
echo "Verify: curl -I https://dats.phoenix-xi.com/FFXiMain.dll"
