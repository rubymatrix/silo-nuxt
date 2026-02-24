# Zone Rendering System

## Pipeline Overview

```
Zone DAT file
  → HTTP fetch (DatLoader with { zoneResource: true })
  → DatParser.parse() reads sections sequentially:
      0x01 Directory → push/pop DirectoryResource tree
      0x20 Texture   → TextureResource (not encrypted)
      0x1C ZoneDef   → ZoneResource (encrypted, decryptZoneObjects)
      0x2E ZoneMesh  → ZoneMeshResource (encrypted, decryptZoneMesh)
      0x2F Environment → EnvironmentResource
  → collectByTypeRecursive() extracts ZoneResource + EnvironmentResource
  → ZoneRenderer.buildFromZoneData() creates Three.js objects
  → ZoneRenderer.applyLighting() sets shader uniforms
  → zoneCollider.findGroundY() places character
```

## Key Files

| File | Purpose |
|------|---------|
| `lib/resource/zoneMeshSection.ts` | Section 0x2E parser → ZoneMeshResource |
| `lib/resource/zoneDefSection.ts` | Section 0x1C parser → ZoneResource (objects + collision) |
| `lib/resource/environmentSection.ts` | Section 0x2F parser → EnvironmentResource |
| `lib/resource/zoneResource.ts` | All zone data types |
| `lib/resource/zoneCollider.ts` | Ground-finding raycast |
| `lib/resource/table/zoneDecrypt.ts` | Decryption for 0x1C and 0x2E sections |
| `lib/resource/table/zoneTables.ts` | Zone ID → DAT path, starting positions |
| `lib/resource/textureLink.ts` | Lazy texture name → TextureResource resolution |
| `lib/renderer/zoneRenderer.ts` | Builds Three.js objects, applies per-object lighting/fog |
| `lib/renderer/zoneMeshGeometry.ts` | Builds BufferGeometry from ZoneMeshBuffer |
| `lib/renderer/shaders/zoneShader.ts` | GLSL ES 1.00 zone vertex/fragment shaders |
| `lib/renderer/textureFactory.ts` | TextureResource → Three.js DataTexture |
| `lib/renderer/textureDecoder.ts` | DXT1/DXT3/BGRA32/Indexed8 → RGBA decode |
| `lib/renderer/environmentManager.ts` | Time-of-day interpolation, sun direction, per-object env |
| `lib/renderer/skyboxRenderer.ts` | Procedural colored hemisphere from SkyBox config |
| `pages/index.vue` | Application wiring: loadZone(), two DatLoaders, time-of-day UI |

### Kotlin Reference Files

| File | Relevance |
|------|-----------|
| `xim/resource/ZoneMeshSection.kt` | Zone mesh parser (201 lines) |
| `xim/resource/ZoneDefParser.kt` | Zone definition parser (654 lines) + CollisionMap |
| `xim/resource/ZoneObjectUtils.kt` | LOD `_h/_m/_l` suffix resolution (37 lines) |
| `xim/resource/EnvironmentSection.kt` | Environment parser |
| `xim/resource/table/ZoneDecrypt.kt` | Zone decryption |
| `xim/poc/ZoneDrawer.kt` | Zone object rendering + transform pipeline |
| `xim/poc/gl/GLDrawer.kt` | Low-level render states, polygon offset, culling |
| `xim/poc/gl/MeshBuffers.kt` | RenderState, ZBiasLevel, GlBufferBuilder |
| `xim/poc/EnvironmentManager.kt` | Skybox mesh, weather interpolation, wind factor |
| `xim/poc/tools/ZoneChanger.kt` | Zone configs and starting positions |

## Zone Mesh Parsing (0x2E)

Decrypted first (two-pass: XOR cipher + conditional byte-swap).

### Header
- `configData & 0x1`: mesh type (0=TriMesh, 1=TriStrip)
- `configData & 0x2`: vertexBlendEnabled (for foliage sway)
- 16-byte name string (the ZoneMeshResource name)

### Per-buffer
- 16-byte texture name (raw, including null padding)
- numVerts (u16), flags (u16)
  - `0x8000`: blendEnabled
  - `0x2000`: if SET, back-face culling DISABLED (inverted logic)
- Vertex data: p0(3f), [p1(3f) if blend], normal(3f), color(BGRA bytes), uv(2f)
- Index data: numIndices(u16), unk(u16), then indices(u16 each)
- Tangents computed per-triangle

### Render state
- `discardThreshold`: 0.375 if mesh name starts with `_` (trees/foliage)
- `zBias`: High(5) if blendEnabled, Normal(0) otherwise
- `useBackFaceCulling`: from flags
- `depthMask`: !blendEnabled

## Zone Object Placement

ZoneObjects from section 0x1C define where meshes appear:
- `objectId`: string that maps to a ZoneMeshResource by name
- `position`, `rotation` (ZYX Euler), `scale`: transform
- `highDefThreshold`, `midDefThreshold`, `lowDefThreshold`: LOD distances
- `pointLightIndices[]`: references into point light array
- `effectLink`: doors/elevators/particles
- `environmentLink`: per-object lighting override
- `fileIdLink`: sub-area deferral

### LOD System (_h/_m/_l)
Objects with IDs ending in `_h`, `_m`, or `_l` use distance-based LOD:
- Strip 2-char suffix → base ID
- Look up `base_h`, `base_m`, `base_l` in the directory
- Select based on distance vs. thresholds with fallback chains

## Texture Resolution Chain

1. `TextureLink.of(textureName, sectionHeader.localDir)` created at parse time
2. `getOrPut()` resolves lazily at render time:
   - `localDir.searchLocalAndParentsByName(name)` walks up directory tree
   - Falls back to `DirectoryResource.getGlobalTexture(name)` (static singleton)
3. `searchLocalAndParentsByName` checks exact match first, then `localTextureName()` (chars 8-16)
4. ALL textures from ALL loaded DATs are registered in the global directory

### Texture name format
Both texture resources and zone mesh buffers store raw 16-char strings (including null padding).
Kotlin uses a `TextureName` class that splits at position 8 into namespace + localName.
TS uses `localTextureName()` which extracts chars 8-16 as fallback.

## Environment System

### Directory Structure

Zone DAT environment data is organized as:
```
zone_root → [weat] → [suny] → [0600], [1200], [1800], ...
          → [ev01] → [suny] → [0600], [1200], ...   (sub-environment override)
          → [ev02] → [suny] → [0600], [1200], ...
```

- `[weat]` is the main weather environment directory (DatId = "weat")
- Weather type subdirs: `[suny]`, `[fine]`, `[clod]`, `[rain]`, etc.
- Hour-keyed EnvironmentResources: DatId = "0600" → 6am, "1200" → noon, etc.
- `DatId.toHourOfDay()` = `parseInt(id, 10) / 100`

### EnvironmentManager (`lib/renderer/environmentManager.ts`)

Port of `xim/poc/EnvironmentManager.kt`. Core responsibilities:
- Groups EnvironmentResource entries by environment ID + weather type
- Picks preferred weather type (sunny/fine first, fallback to first available)
- Interpolates between adjacent hour entries based on current time-of-day
- Per-object environment override: zone objects have `environmentLink` (e.g. "ev01")
  that resolves to a different lighting/fog config (indoor areas within outdoor zones)
- Caches resolved environments per (envId, minute) to avoid redundant computation

### Time-of-Day Interpolation

Given target time, finds the two bracketing hour entries and linearly interpolates
all properties: ambient, diffuse, fog, skybox colors/elevations, draw distance, clear color.

```typescript
const t = (currentMinuteValue - floorHour * 60) / ((ceilHour - floorHour) * 60)
```

Wraps around midnight: if ceil entry is hour 0, treat as hour 24 for interpolation.

### Sun / Moon Direction

```typescript
const anglePerSecond = (0.5 * Math.PI) / (6 * 60 * 60) // PI/2 over 12 game-hours
const angle = timeOfDaySeconds * anglePerSecond
sunDir = { x: sin(angle), y: cos(angle), z: 0 }
moonDir = -sunDir
```

### Model Light Sun/Moon Blending

For character/actor lighting (not terrain), sun and moon are blended:
- Before 05:55 (minute 355): 100% moon
- 05:55-06:05: linear blend moon→sun
- 06:05-17:55: 100% sun
- 17:55-18:05: linear blend sun→moon
- After 18:05: 100% moon

### Terrain vs Model Lighting

Two separate configs per environment:
- `terrainLighting`: used for zone meshes (ground, buildings). Terrain gets both sun AND
  moon as separate directional lights (2 lights).
- `modelLighting`: used for character actors. Models blend sun+moon into a single light.

Both apply FFXI color bias `[1.4, 1.36, 1.45]` when all RGB channels < 0xCC.

### Indoor Lighting

When `environmentLighting.indoors === true`:
- Diffuse direction is derived from `moonLightColor` bytes reinterpreted as signed:
  ```typescript
  const dx = ((moonLightColor.r << 24) >> 24) / 128
  ```
- Only one light (no separate sun/moon)
- Clear color comes from the `EnvironmentResource.clearColor` field (not skybox)

### Clear Color

- **Outdoor:** First skybox slice color (horizon color at elevation 0)
- **Indoor:** The environment's `clearColor` field directly

### Ambient Color Conversion

Ambient is divided by 510 (not 255), clamped to 0.5 max:
```typescript
r = min(0.5, (bias * c.r) / 510)
```
This matches the Kotlin reference where zone ambient is "half-strength" compared to character ambient.

## Skybox System

### SkyboxRenderer (`lib/renderer/skyboxRenderer.ts`)

Port of `SkyBoxMesh` from `EnvironmentManager.kt`. Procedural hemisphere:
- 8 elevation layers (from SkyBox.slices), each with a color + elevation angle
- N spokes (SkyBox.spokes) around the Y axis
- 7 triangle strips connecting adjacent layers
- Vertex-colored, no texture, no normals
- `depthWrite: false`, `side: BackSide`, `renderOrder: -1000`
- Camera is always inside the hemisphere (centered at origin)

Elevation mapping: 0.0 = horizon, 1.0 = zenith → angle = `-0.5 * PI * elevation`

Rebuilds when time-of-day changes (interpolated slice colors change).

## Coordinate System

- FFXI uses Y-down (negative Y is up)
- Camera has `camera.up.set(0, -1, 0)`
- Collision raycasts fire direction `(0, 1, 0)` (downward) from `y = -200` (high up)
- Euler rotation order: ZYX

## Critical Gotchas

1. **Zone decryption must be initialized first** — `initZoneDecrypt(mainDll)` once
2. **Zone vertex colors are raw bytes normalized to [0,1]** — In Kotlin, vertex colors are
   uploaded as `UNSIGNED_BYTE` with `normalized=true` (`GLDrawer.kt:125`), so the GPU converts
   `0-255 → 0.0-1.0`. The shader's `2.0 *` multiplier on the final RGB output is the zone-specific
   2x scaling. Do NOT pre-multiply vertex colors by 2 in the geometry builder — that would
   result in 4x brightness (2x geometry * 2x shader).
3. **Zone shader must force output alpha to 1.0** — Zone textures (especially
   DXT-compressed) contain pixels with alpha < 1.0. If these sub-1.0 alpha values
   reach the framebuffer, the HTML page background bleeds through every fragment,
   producing a uniform checkerboard/dot-grid artifact across ALL zone surfaces. The
   fix has two parts: (a) `WebGLRenderer({ alpha: false })` and (b) the zone fragment
   shader forces `gl_FragColor = vec4(fogged.rgb, 1.0)`. Both are necessary —
   `alpha: false` alone is NOT sufficient (the checkerboard returns). The shader
   `discard` handles foliage transparency (alpha test), so forcing output alpha to
   1.0 does not break tree/bush cutouts. See `ai/learnings/alpha-transparency-clipping.md`
   for the full investigation and explanation.
4. **Separate DatLoader** — zones use `{ zoneResource: true }` parser option
5. **`SectionOffsetSource` requires `sectionSize`** — for decryption bounds checking
6. **Triangle winding** — collision test: `(t0-t1)`, `(t1-t2)`, `(t2-t0)` — NOT reversed
7. **ZoneSectionPlaceholder** — `DirectoryResource.addChild()` stores under actual constructor
8. **Mirrored winding** — `scale.x * scale.y * scale.z < 0` → flip CW/CCW
9. **Polygon offset** — blended meshes need `polygonOffset(-5, 1)` to avoid z-fighting
10. **Back-face culling flag is inverted** — flag 0x2000 SET = culling DISABLED
11. **Global texture directory is never cleared** — all textures from all DATs accumulate
12. **Decryption key progression must NOT be masked** — Kotlin/JS uses unbounded int addition
    for key counters. The TypeScript port must NOT apply `& 0xff` or `& 0xffff` masks to
    `key`, `key1`, `key2` during decryption loops. Masking causes key divergence on large
    sections, corrupting texture names and vertex data.
13. **Empty-name mesh buffers should be skipped** — Some zone mesh resources contain buffers
    with all-null texture names. These are geometry that requires a texture for its visual.
    Without a texture they render as opaque gray, obscuring the real textured ground beneath.
    Skip these buffers during rendering. (e.g., Bastok had 140 such buffers out of 2282 total)
14. **Environment manager needs init before use** — Call `envManager.init(zoneDirectory)` after
    parsing the zone DAT. The directory tree must be fully built first.
15. **Per-object environmentLink** — Zone objects may have `environmentLink: "ev01"` etc.
    These reference sub-environment directories in the zone DAT that override lighting/fog.
    Critical for indoor/outdoor transitions within a single zone.
16. **Reset time-of-day on scene reload** — When `loadScene()` is called, reset
    `timeOfDayMinutes` to noon (720) to avoid stale environment state.
17. **Texture filtering must match Kotlin: LINEAR, no mipmaps** — Kotlin uses
    `TEXTURE_MAG_FILTER = LINEAR`, `TEXTURE_MIN_FILTER = LINEAR` with no mipmap
    generation. The TS port uses `LinearFilter` for both and `generateMipmaps = false`.
    The default fallback texture uses `NearestFilter` (matching Kotlin's `NEAREST`).
18. **Default fallback texture alpha is 0x80, not 0xFF** — Kotlin's
    `makeSingleColorTexture(0x80)` fills ALL four channels (RGBA) with 0x80. The TS
    `defaultGrayTexture()` must match: `[0x80, 0x80, 0x80, 0x80]`.
19. **Zone depth function is LESS, not LEQUAL** — Kotlin explicitly sets
    `depthFunc(LESS)`. Three.js defaults to `LessEqualDepth`. Zone materials use
    `depthFunc: LessDepth` to match.

## What's Implemented vs. Missing

| Feature | Status | Notes |
|---------|--------|-------|
| Zone mesh parsing + decryption | Done | |
| Zone def parsing + collision | Done | |
| Environment parsing | Done | |
| Texture resolution | Done | Gray fallback texture + diagnostic logging on failure |
| Terrain + model lighting | Done | |
| Collision ground raycast | Done | |
| Back-face culling | Done | `useBackFaceCulling` in `MeshRenderState`, applied as Three.js `side` |
| Z-bias / polygon offset | Done | `zBias` in `MeshRenderState`, blended meshes get `polygonOffset(-5, 1)` |
| Mirrored winding detection | Done | `scale.x*y*z < 0` flips FrontSide to BackSide |
| LOD system | Done | `_h/_m/_l` suffix resolution with high>med>low fallback chain |
| Wind factor (foliage sway) | Done | `positionBlendWeight` oscillates 0→1 over 2s via `updateWind()` |
| Skybox | Done | Procedural hemisphere in `skyboxRenderer.ts`, rebuilds on time change |
| Per-object environment | Done | `environmentLink` resolved via `EnvironmentManager` |
| Time-of-day interpolation | Done | Slider UI 0:00-23:59, interpolates all env properties |
| Sun/moon direction | Done | Computed from time-of-day, model light blending at dawn/dusk |
| Draw distance culling | Done | `updateVisibility()` hides objects beyond `drawDistance` |
| Clear color | Done | `setClearColor()` from environment (skybox horizon or indoor color) |
| Point lights | **Not implemented** | Data parsed, not wired to shader uniforms |
| Zone interactions (0x36) | **Not ported** | Doors, zone lines, elevators |
| Weather transitions | **Not implemented** | Env manager accepts weather type but doesn't interpolate between weather changes |
| Tree/foliage textures | Improved | Gray fallback + `discardThreshold=0.375` for `_`-prefixed. Some may still be missing textures |
