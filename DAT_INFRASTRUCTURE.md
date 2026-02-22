# DAT File Infrastructure Plan

How silo-nuxt serves FFXI game assets (DAT files) to users who don't have the game installed.

## Problem

The model viewer renders a player character in Three.js by loading binary DAT files from the FFXI game client. Each character render requires ~14 file fetches (4 bootstrap + 4 base model/animation + 6 equipment). Today this relies on manually running `python -m http.server 3005` inside a local FFXI install directory. For a public-facing site, the DATs must be served from infrastructure we control.

## Architecture Decision: Cloudflare R2 + CDN

### Why R2

| Factor | R2 | VPS + nginx | Docker container |
|--------|----|-----------|--------------------|
| Hosting cost | Free tier: 10 GB / 10M reads. Then $0.015/GB/mo | VPS disk space + bandwidth | Same as VPS |
| Egress fees | Zero | VPS bandwidth limits | Same as VPS |
| CDN caching | Built-in (Cloudflare edge) | Would need separate CDN | Same as VPS |
| Maintenance | None | nginx config, disk monitoring | Container + volume management |
| Global latency | Edge-cached worldwide | Single VPS region | Single VPS region |
| Fits existing stack | Yes (Cloudflare Pages, Tunnel already in use) | Partially (VPS exists) | Extra infra |
| CORS | Native bucket config | nginx config | nginx config |

DAT files are static, immutable binary blobs. Object storage behind a CDN is the textbook solution. R2 is the natural choice because the rest of the Phoenix stack already lives on Cloudflare.

### What Gets Uploaded

The app needs these files from the FFXI game directory:

```
FFXiMain.dll                          # Game client binary (parsed for lookup tables)
VTABLE.DAT / FTABLE.DAT              # ROM1 file index tables
landsandboat/ItemModelTable.DAT       # Server-specific item-to-model mapping
ROM/    {folders}/{files}.DAT         # Version 1 game resources
ROM2/   VTABLE2.DAT, FTABLE2.DAT, {folders}/{files}.DAT
ROM3/   VTABLE3.DAT, FTABLE3.DAT, {folders}/{files}.DAT
...through ROM9/
```

Full FFXI install is ~15-20 GB. We can start with a full upload and optimize later (selective upload of only referenced model/texture/animation DATs would reduce this to ~2-5 GB).

### How Paths Resolve

The `FTable` parser reads a 16-bit value per file ID and constructs paths:

```
ROM{version}/{folderName}/{fileName}.DAT

version    = VTable byte (1-9; version 1 omits the number: "ROM/")
folderName = bits 7-15 of FTable entry (0-511)
fileName   = bits 0-6 of FTable entry (0-127)
```

Examples: `ROM/10/20.DAT`, `ROM2/5/1.DAT`, `ROM3/12/45.DAT`

### Per-Character File Breakdown

| Category | Files | Cached? |
|----------|-------|---------|
| Bootstrap: FFXiMain.dll, VTABLE, FTABLE, ItemModelTable | 4 | Once per session |
| Base model (race/gender mesh + skeleton) | 1 | Per race/gender |
| Animation (battle, upper body, skirt) | 3 | Per race/gender |
| Equipment (face, head, body, hands, legs, feet) | 6 | Per equipment set |
| **Total per character** | **14** | |

Subsequent loads with different equipment only re-fetch the changed slots. The DatLoader has an in-memory cache.

## Target Architecture

```
Browser
  |
  |  fetch('https://dats.phoenix-xi.com/ROM/10/20.DAT')
  |
  v
Cloudflare CDN (edge cache, Cache-Control: immutable)
  |
  v
Cloudflare R2 Bucket (phoenix-dat-files)
  |
  (objects stored at: ROM/10/20.DAT, FFXiMain.dll, etc.)


Browser
  |
  |  SPA loaded from Cloudflare Pages
  v
https://phoenix-xi.com   (Nuxt SPA, no server proxy needed)
```

No proxy route. No intermediate server. The Nuxt app fetches DATs directly from R2 via the CDN. CORS is configured on the R2 bucket to allow `phoenix-xi.com` origins.

## App Changes Required

### 1. Add runtime config for DAT base URL

```typescript
// nuxt.config.ts
export default defineNuxtConfig({
  compatibilityDate: "2025-11-01",
  devtools: { enabled: false },
  runtimeConfig: {
    public: {
      datBaseUrl: 'https://dats.phoenix-xi.com',
    },
  },
})
```

Overridden locally via `.env`:
```
NUXT_PUBLIC_DAT_BASE_URL=http://localhost:3005
```

### 2. Update DatLoader to use runtime config

The `DatLoader` default `baseUrl` changes from `/api/dat` to the runtime config value. The fetch goes directly to R2 in production, bypassing the proxy.

### 3. Remove the server proxy route

`server/routes/api/dat/[...path].ts` is no longer needed in production. Can be kept behind a feature flag or removed entirely. For local dev, the `.env` override points to `localhost:3005` and the proxy route can remain as a convenience.

### 4. Add .env patterns to .gitignore

```
.env
.env.*
*.pem
*.key
```

### 5. Add .env.example

```
# DAT file server URL
# Production: https://dats.phoenix-xi.com
# Local dev: http://localhost:3005 (run: python -m http.server 3005 in FFXI dir)
NUXT_PUBLIC_DAT_BASE_URL=http://localhost:3005
```

## Local Dev Options

### Option A: Python HTTP Server (current, simplest)

```bash
cd "C:\Program Files (x86)\PlayOnline\SquareEnix\FINAL FANTASY XI"
python -m http.server 3005
```

Set in `.env`:
```
NUXT_PUBLIC_DAT_BASE_URL=http://localhost:3005
```

### Option B: Docker nginx (optional, more robust)

```yaml
# docker-compose.dev.yml
services:
  dat-server:
    image: nginx:alpine
    ports:
      - "3005:80"
    volumes:
      - "${FFXI_GAME_DIR:?Set FFXI_GAME_DIR}:/usr/share/nginx/html:ro"
    restart: unless-stopped
```

```bash
FFXI_GAME_DIR="C:/Program Files (x86)/PlayOnline/SquareEnix/FINAL FANTASY XI" docker compose -f docker-compose.dev.yml up -d
```

Both options work with the same `.env` setting. The python server is perfectly fine for dev.

---

# Setup Instructions

Step-by-step guide to set up DAT file serving for both local development and production.

## Prerequisites

- Cloudflare account with `phoenix-xi.com` domain
- `wrangler` CLI installed (`pnpm add -g wrangler` or `npm install -g wrangler`)
- Authenticated with Cloudflare (`wrangler login`)
- Access to an FFXI game directory (local install or copy)

## Part 1: Cloudflare R2 Bucket Setup

### Step 1: Create the R2 bucket

```bash
wrangler r2 bucket create phoenix-dat-files
```

### Step 2: Configure CORS on the bucket

Create a file `r2-cors.json`:

```json
{
  "cors": [
    {
      "allowedOrigins": [
        "https://phoenix-xi.com",
        "https://beta.phoenix-xi.com",
        "http://localhost:3000"
      ],
      "allowedMethods": ["GET", "HEAD"],
      "allowedHeaders": ["*"],
      "maxAgeSeconds": 86400
    }
  ]
}
```

Apply it:

```bash
wrangler r2 bucket cors put phoenix-dat-files --file r2-cors.json
```

### Step 3: Connect a custom domain

1. Go to **Cloudflare Dashboard > R2 > phoenix-dat-files > Settings > Public Access**
2. Click **Connect Domain**
3. Enter `dats.phoenix-xi.com`
4. Cloudflare automatically creates the DNS record and configures the bucket for public read access on that domain

After this, any object in the bucket is accessible at `https://dats.phoenix-xi.com/{key}`.

### Step 4: Add a Cache-Control transform rule

DAT files are immutable (they only change if you re-upload from a different game version). Set aggressive caching:

1. Go to **Cloudflare Dashboard > phoenix-xi.com > Rules > Transform Rules > Modify Response Header**
2. Create a rule:
   - **When**: Hostname equals `dats.phoenix-xi.com`
   - **Then**: Set static header `Cache-Control` = `public, max-age=31536000, immutable`

This ensures DATs are cached at the Cloudflare edge and in the browser for 1 year.

## Part 2: Upload DAT Files

### Step 5: Upload files from your FFXI directory

Set your FFXI directory path:

```bash
# Windows (PowerShell)
$FFXI_DIR = "C:\Program Files (x86)\PlayOnline\SquareEnix\FINAL FANTASY XI"

# Linux/macOS
FFXI_DIR="/path/to/ffxi"
```

Upload the bootstrap files first (small, critical):

```bash
# Bootstrap files
wrangler r2 object put phoenix-dat-files/FFXiMain.dll --file "$FFXI_DIR/FFXiMain.dll"
wrangler r2 object put phoenix-dat-files/VTABLE.DAT --file "$FFXI_DIR/VTABLE.DAT"
wrangler r2 object put phoenix-dat-files/FTABLE.DAT --file "$FFXI_DIR/FTABLE.DAT"
```

Upload ROM table files for ROM2-ROM9:

```bash
# ROM2-9 table files
for N in 2 3 4 5 6 7 8 9; do
  wrangler r2 object put "phoenix-dat-files/ROM${N}/VTABLE${N}.DAT" --file "$FFXI_DIR/ROM${N}/VTABLE${N}.DAT"
  wrangler r2 object put "phoenix-dat-files/ROM${N}/FTABLE${N}.DAT" --file "$FFXI_DIR/ROM${N}/FTABLE${N}.DAT"
done
```

Upload the landsandboat ItemModelTable:

```bash
wrangler r2 object put phoenix-dat-files/landsandboat/ItemModelTable.DAT --file "$FFXI_DIR/landsandboat/ItemModelTable.DAT"
```

### Step 6: Bulk upload ROM directories

The `wrangler r2 object put` command uploads one file at a time. For bulk uploads of the ROM directories (~15-20 GB), use **rclone** which supports S3-compatible backends and parallel uploads.

#### Install rclone

```bash
# Windows (scoop)
scoop install rclone

# macOS
brew install rclone

# Linux
curl https://rclone.org/install.sh | sudo bash
```

#### Configure rclone for R2

Generate an R2 API token:

1. Go to **Cloudflare Dashboard > R2 > Overview > Manage R2 API Tokens**
2. Create a token with **Object Read & Write** permission on the `phoenix-dat-files` bucket
3. Note the **Access Key ID** and **Secret Access Key**
4. Note your **Account ID** (visible in the Cloudflare dashboard URL or R2 overview)

Create the rclone config:

```bash
rclone config
```

Follow the prompts:
- Name: `r2`
- Type: `s3`
- Provider: `Cloudflare`
- Access Key ID: (paste from step above)
- Secret Access Key: (paste from step above)
- Endpoint: `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`
- Leave everything else default

#### Run the sync

```bash
# Sync ROM directories (this will take a while -- 15-20 GB)
rclone sync "$FFXI_DIR/ROM" r2:phoenix-dat-files/ROM --progress --transfers 16
rclone sync "$FFXI_DIR/ROM2" r2:phoenix-dat-files/ROM2 --progress --transfers 16
rclone sync "$FFXI_DIR/ROM3" r2:phoenix-dat-files/ROM3 --progress --transfers 16
rclone sync "$FFXI_DIR/ROM4" r2:phoenix-dat-files/ROM4 --progress --transfers 16
rclone sync "$FFXI_DIR/ROM5" r2:phoenix-dat-files/ROM5 --progress --transfers 16
rclone sync "$FFXI_DIR/ROM6" r2:phoenix-dat-files/ROM6 --progress --transfers 16
rclone sync "$FFXI_DIR/ROM7" r2:phoenix-dat-files/ROM7 --progress --transfers 16
rclone sync "$FFXI_DIR/ROM8" r2:phoenix-dat-files/ROM8 --progress --transfers 16
rclone sync "$FFXI_DIR/ROM9" r2:phoenix-dat-files/ROM9 --progress --transfers 16
```

Use `--dry-run` first to preview what will be uploaded:

```bash
rclone sync "$FFXI_DIR/ROM" r2:phoenix-dat-files/ROM --dry-run
```

### Step 7: Verify the upload

Check a known file is accessible:

```bash
curl -I https://dats.phoenix-xi.com/FFXiMain.dll
# Should return 200 with content-length

curl -I https://dats.phoenix-xi.com/VTABLE.DAT
# Should return 200

curl -I https://dats.phoenix-xi.com/ROM/0/0.DAT
# Should return 200 (if that file exists)
```

Check CORS headers:

```bash
curl -I -H "Origin: https://phoenix-xi.com" https://dats.phoenix-xi.com/FFXiMain.dll
# Should include: access-control-allow-origin: https://phoenix-xi.com
```

## Part 3: Update the Nuxt App

### Step 8: Update nuxt.config.ts

Add runtime config so the DAT base URL is configurable per environment:

```typescript
export default defineNuxtConfig({
  compatibilityDate: "2025-11-01",
  devtools: { enabled: false },
  runtimeConfig: {
    public: {
      datBaseUrl: 'https://dats.phoenix-xi.com',
    },
  },
})
```

### Step 9: Create .env.example and .env

Create `.env.example` (committed to git):

```
# DAT file server URL
# Production: https://dats.phoenix-xi.com (Cloudflare R2 + CDN)
# Local dev: http://localhost:3005 (python -m http.server 3005 in FFXI dir)
NUXT_PUBLIC_DAT_BASE_URL=http://localhost:3005
```

Create `.env` (gitignored) for local dev:

```
NUXT_PUBLIC_DAT_BASE_URL=http://localhost:3005
```

### Step 10: Update .gitignore

Add environment and secret file patterns:

```
.env
.env.*
!.env.example
*.pem
*.key
```

### Step 11: Update DatLoader to use runtime config

In `pages/index.vue` (or wherever DatLoader is instantiated), replace the hardcoded `/api/dat` base URL with the runtime config value:

```typescript
const config = useRuntimeConfig()

const datLoader = new DatLoader<DirectoryResource>({
  baseUrl: config.public.datBaseUrl,
  parseDat: (resourceName, bytes) => DatParser.parse(resourceName, bytes),
})
```

Similarly update `createResourceTableRuntime()`:

```typescript
resourceTableRuntime = createResourceTableRuntime({
  baseUrl: config.public.datBaseUrl,
  fileTableCount: 1,
})
```

### Step 12: Decide on the proxy route

The server proxy route at `server/routes/api/dat/[...path].ts` is no longer needed in production (the browser fetches directly from R2). You have two options:

**Option A: Remove it entirely.** The simplest approach. Local dev uses `NUXT_PUBLIC_DAT_BASE_URL=http://localhost:3005` which bypasses the proxy anyway. CORS is not an issue locally since the python server doesn't enforce it.

**Option B: Keep it as a local dev fallback.** If you want to proxy through Nitro locally to avoid any CORS edge cases, keep the route but have production bypass it entirely via the runtime config URL.

Recommendation: **Option A** -- remove it. The python server doesn't set CORS headers but browsers don't enforce CORS for `localhost` origins in dev mode, and Nuxt dev server can be configured with a proxy in `vite.server.proxy` if needed.

## Part 4: Verify End-to-End

### Step 13: Test locally

```bash
# Terminal 1: Start the DAT server
cd "C:\Program Files (x86)\PlayOnline\SquareEnix\FINAL FANTASY XI"
python -m http.server 3005

# Terminal 2: Start the Nuxt app
pnpm dev
```

Open `http://localhost:3000`. The model viewer should load the character just as before, but now using the configurable base URL.

### Step 14: Test against production R2

Temporarily change `.env`:

```
NUXT_PUBLIC_DAT_BASE_URL=https://dats.phoenix-xi.com
```

Restart the dev server and verify the viewer loads DATs from R2.

### Step 15: Deploy

Deploy the Nuxt app to Cloudflare Pages. The default `runtimeConfig.public.datBaseUrl` is `https://dats.phoenix-xi.com`, so no environment variable override is needed in production -- it uses the default from `nuxt.config.ts`.

If you do want to set it explicitly in Cloudflare Pages:
1. Go to **Cloudflare Dashboard > Pages > silo-nuxt > Settings > Environment Variables**
2. Add `NUXT_PUBLIC_DAT_BASE_URL` = `https://dats.phoenix-xi.com`

## Future Optimizations

### Selective Upload

Instead of uploading the entire 15-20 GB FFXI directory, write a script that:
1. Parses `FTABLE.DAT` to enumerate all valid file IDs
2. Reads the equipment model tables from `FFXiMain.dll` to identify which file IDs are model/texture/animation DATs
3. Uploads only those files (~2-5 GB estimated)

### Content-Addressed Storage

If DAT files are ever updated (game patches), use content hashes in the URL path to enable infinite caching without cache invalidation concerns:
```
https://dats.phoenix-xi.com/v1/ROM/10/20.DAT
```
Bump the version prefix when you re-upload after a game update.

### Compression

R2 doesn't auto-compress binary files. If bandwidth becomes a concern:
- Pre-compress DATs with gzip/brotli and upload as `.DAT.br`
- Use a Cloudflare Worker in front of R2 to serve with `Content-Encoding: br`
- Or rely on Cloudflare's automatic compression (works for responses > 256 bytes, but binary formats may not compress well)

### Multiple Game Versions

If you ever need to support multiple FFXI versions (different private servers):
```
https://dats.phoenix-xi.com/phoenix/ROM/10/20.DAT
https://dats.phoenix-xi.com/retail/ROM/10/20.DAT
```
Namespace by server/version prefix in the R2 bucket.
