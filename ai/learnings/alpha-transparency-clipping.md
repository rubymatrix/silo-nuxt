# Alpha, Transparency, and Clipping

How FFXI handles alpha testing, alpha blending, and the checkerboard artifact fix.

## The Two Alpha Systems

FFXI uses **two completely different systems** for transparency:

### 1. Alpha Test (Discard) — Binary cutout
Used for foliage, hair, fences — anything with hard-edged transparency.
- The shader computes `finalAlpha = 4.0 * litFragColor.a * basePixel.a`
- If `finalAlpha < discardThreshold`, the fragment is **discarded entirely**
- The pixel is either fully rendered or fully invisible — no partial transparency
- `discardThreshold` is `0.375` for `_`-prefixed zone meshes (trees/foliage), `0.0` for everything else
- For character meshes (ximSkinnedShader), `discardThreshold` is `69/255 ≈ 0.27`

### 2. Alpha Blend — Smooth transparency
Used for glass, water, translucent effects.
- Controlled by `renderState.blendEnabled` flag (bit `0x8000` in zone mesh flags)
- When enabled: `GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA` blending, depth write disabled
- When disabled: no blending, depth write enabled
- Three.js equivalent: `transparent: true`, `depthWrite: false`

These two systems are **independent**. A mesh can have discard only, blend only, both, or neither.

## Alpha Math in the Shaders

### Zone Shader (`zoneShader.ts` / `XimShader.kt`)

```glsl
// Kotlin (XimShader.kt:187)
vec4 coloredPixel = vec4(2.0 * litFragColor.rgb * basePixel.rgb, 4.0 * litFragColor.a * basePixel.a);
if (coloredPixel.a < discardThreshold) { discard; }
outColor = colorMask * fogCalc(frag_cameraPos.xyz, coloredPixel);

// TypeScript (zoneShader.ts:93-102) — note the forced alpha
float finalAlpha = clamp(4.0 * litFragColor.a * basePixel.a, 0.0, 1.0);
vec4 coloredPixel = vec4(2.0 * litFragColor.rgb * basePixel.rgb, finalAlpha);
if (coloredPixel.a < discardThreshold) { discard; }
vec4 fogged = colorMask * fogCalc(frag_cameraPos.xyz, coloredPixel);
gl_FragColor = vec4(fogged.rgb, 1.0);  // <-- FORCED to 1.0
```

Key differences:
- **Kotlin does NOT clamp alpha** — `4.0 * litFragColor.a * basePixel.a` can exceed 1.0
- **TS clamps alpha** to [0, 1] AND forces final output alpha to 1.0
- The `2.0 *` on RGB is the zone vertex color 2x scale (zone colors are half-intensity)
- The `4.0 *` on alpha compensates for FFXI storing vertex alpha at ~0.5 (ByteColor.half = 0x80)

### Character Shader (`ximSkinnedShader.ts` / `XimSkinnedShader.kt`)

```glsl
// Kotlin (XimSkinnedShader.kt):
vec4 coloredPixel = vec4(2.0 * frag_color.rgb * diffusePixel.rgb, 4.0 * frag_color.a * diffusePixel.a);
if (coloredPixel.a < discardThreshold) { discard; }
outColor = uEffectColor * colorMask * fogCalc(frag_cameraSpacePos.xyz, coloredPixel) * 4.0;

// TypeScript (ximSkinnedShader.ts) — forces alpha to 1.0, same as zone shader:
vec4 coloredPixel = vec4(2.0 * litFragColor.rgb * diffusePixel.rgb, 4.0 * litFragColor.a * diffusePixel.a);
coloredPixel.a *= uAlpha;  // per-actor fade
if (coloredPixel.a < uDiscardThreshold) { discard; }
vec4 clamped = clamp(coloredPixel, 0.0, 1.0);
vec4 fogged = uColorMask * fogCalc(vCameraPos, clamped);
gl_FragColor = vec4(fogged.rgb, 1.0);  // <-- FORCED to 1.0
```

The character shader forces alpha to 1.0 (same pattern as zone shader).
Actor fade still works because `uAlpha` modulates `coloredPixel.a` before the discard
test — as an actor fades, fragments are progressively discarded rather than blended.

## The Checkerboard Artifact

### Symptom
A uniform dot-grid / checkerboard pattern across ALL zone surfaces (ground, walls,
buildings). Character models are unaffected. The pattern looks like the HTML page
background bleeding through at regular intervals.

### Root Cause
DXT-compressed zone textures contain pixels with alpha < 1.0 in their raw data. When
these sub-1.0 alpha values reach the WebGL framebuffer, the browser's canvas compositor
blends the WebGL content with the HTML page background. Since DXT block compression
produces a regular pattern of alpha values, the artifact appears as a regular grid.

### Debugging Steps That Confirmed This

1. **Raw texture only (no lighting):**
   ```glsl
   gl_FragColor = basePixel; return;
   ```
   Checkerboard STILL present. Rules out shader lighting math.

2. **Force alpha to 1.0:**
   ```glsl
   gl_FragColor = vec4(basePixel.rgb, 1.0); return;
   ```
   Checkerboard **GONE**. Confirms alpha is the problem.

3. **`WebGLRenderer({ alpha: false })` alone:**
   Checkerboard came BACK. `alpha: false` is necessary but not sufficient in Three.js —
   the ShaderMaterial's `gl_FragColor` alpha still matters for internal blending passes.

4. **Final fix — force output alpha in the shader:**
   ```glsl
   gl_FragColor = vec4(fogged.rgb, 1.0);
   ```
   Checkerboard gone permanently. Foliage cutouts still work (handled by `discard`).

### Why Kotlin Doesn't Have This Issue

Kotlin renders to a raw WebGL2 canvas (not through Three.js). Its unclamped alpha
(`4.0 * litFragColor.a * basePixel.a`) frequently exceeds 1.0 in practice (e.g.,
`4.0 * 0.5 * 0.8 = 1.6`), which the GPU clamps to 1.0 at framebuffer write. So most
pixels naturally end up with alpha = 1.0. The TS port initially clamped alpha to [0,1]
before the final multiply, keeping sub-1.0 values alive longer in the pipeline.

Additionally, Kotlin creates its WebGL context with default parameters (no explicit
`alpha: true`), and writes to the framebuffer with `outColor` directly. The combination
of unclamped-then-hardware-clamped alpha + default canvas alpha handling means the
artifact never manifests.

## The Hair / Gear White Fringing Artifact

### Symptom
White fuzzy fringe around the edges of character hair, transparent gear pieces, and
other character meshes that use alpha cutout textures. The artifact appears as bright
white wisps extending beyond the visible hair boundary.

### Root Cause
Same mechanism as the checkerboard artifact, but on character meshes. Hair textures
have texels at the edges with partial alpha — values between ~0.135 (texture alpha
that yields `4.0 * 0.5 * 0.135 ≈ 0.27`, right at the discard threshold) and ~0.5
(yielding `4.0 * 0.5 * 0.5 = 1.0`). These edge texels survive the discard test at
`69/255 ≈ 0.27` but write sub-1.0 alpha to the framebuffer. The browser compositor
blends those pixels with the white HTML page background, producing white fringe.

The character shader (`ximSkinnedShader.ts`) originally output computed alpha directly:
```glsl
gl_FragColor = uColorMask * fogCalc(vCameraPos, clamp(coloredPixel, 0.0, 1.0));
```

Even though `transparent: false` is set on the Three.js material (since
`blendEnabled = false` for all skeleton meshes), the alpha channel still reaches
the canvas compositor.

### Fix
Force output alpha to 1.0 in the character fragment shader, same as the zone shader:
```glsl
vec4 clamped = clamp(coloredPixel, 0.0, 1.0);
vec4 fogged = uColorMask * fogCalc(vCameraPos, clamped);
gl_FragColor = vec4(fogged.rgb, 1.0);
```

### Why Actor Fade Still Works
The `uAlpha` uniform multiplies into `coloredPixel.a` **before** the discard test.
As an actor fades (uAlpha decreasing from 1.0 toward 0.0), more and more fragments
fall below the discard threshold and get discarded entirely. This produces a dissolve
effect rather than a smooth fade, which matches the original FFXI behavior. The forced
alpha=1.0 only affects fragments that survive the discard.

## Both Fixes (Applied)

Both shaders (zone and character) now use the same pattern: compute alpha for the
discard test, then force the output alpha to 1.0. This is the universal fix for
canvas compositor bleed-through in a Three.js `alpha: false` renderer.

## The Fix (Applied) — Details

### Zone Fragment Shader (`lib/renderer/shaders/zoneShader.ts`)
```glsl
vec4 fogged = colorMask * fogCalc(frag_cameraPos.xyz, coloredPixel);
gl_FragColor = vec4(fogged.rgb, 1.0);  // Force opaque output
```

### Character Fragment Shader (`lib/renderer/shaders/ximSkinnedShader.ts`)
```glsl
vec4 clamped = clamp(coloredPixel, 0.0, 1.0);
vec4 fogged = uColorMask * fogCalc(vCameraPos, clamped);
gl_FragColor = vec4(fogged.rgb, 1.0);  // Force opaque output
```

### WebGLRenderer (`lib/renderer/threeRenderer.ts`)
```typescript
new WebGLRenderer({ canvas, antialias: true, alpha: false })
```

### Why This Doesn't Break Anything

| Feature | Why it still works |
|---------|--------------------|
| **Foliage cutout** (trees, bushes) | Handled by `discard` before the alpha=1.0 line. Pixel is either fully rendered or fully gone. |
| **Blended meshes** (water, glass) | Three.js `transparent: true` uses src/dst alpha blending between objects within the scene. The forced alpha=1.0 only affects the final framebuffer write, not inter-object blending order. |
| **Character hair** | Uses `ximSkinnedShader` which also forces alpha=1.0 now. Transparency handled by discard at threshold `69/255`. |
| **Fog** | Fog is applied before the alpha override. Fog color blends into RGB. |

## Render State Flags Reference

From zone mesh parsing (section 0x2E, flags u16):

| Flag | Meaning | Effect |
|------|---------|--------|
| `0x8000` | `blendEnabled` | Enables alpha blending (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) |
| `0x2000` | Back-face culling **disabled** | Inverted: flag SET = culling OFF |

Derived from `blendEnabled`:
- `depthWrite` = `!blendEnabled`
- `zBias` = `High(5)` if blendEnabled, else `Normal(0)`

From mesh name:
- `discardThreshold` = `0.375` if name starts with `_`, else `0.0`

## Texture Alpha by Format

| Format | Alpha behavior |
|--------|---------------|
| **DXT1** | 1-bit alpha (fully opaque or fully transparent). Transparent pixels have `a=0`. Block compression creates regular patterns. |
| **DXT3** | 4-bit explicit alpha per pixel (16 levels). Often contains sub-1.0 alpha that is NOT meant to be visible — it's a compression artifact. |
| **BGRA32** | Full 8-bit alpha. Usually correct (0xFF for opaque). |
| **Indexed8** | Palette-based. Alpha comes from palette entries, typically 0x00 or 0xFF. |

DXT3 is the primary source of the checkerboard artifact — its 4-bit alpha granularity
means many "opaque" pixels decode to alpha values like 0xEE (≈0.93) instead of 0xFF (1.0).

## Key Files

| File | Role |
|------|------|
| `lib/renderer/shaders/zoneShader.ts` | Zone fragment shader — the `vec4(fogged.rgb, 1.0)` fix |
| `lib/renderer/shaders/ximSkinnedShader.ts` | Character fragment shader — forces alpha to 1.0 (same as zone) |
| `lib/renderer/zoneRenderer.ts` | Sets `transparent`, `depthWrite`, `depthFunc` per mesh |
| `lib/renderer/threeRenderer.ts` | `WebGLRenderer({ alpha: false })` |
| `lib/renderer/textureDecoder.ts` | DXT1/DXT3/BGRA32/Indexed8 → RGBA decode |
| `lib/renderer/textureFactory.ts` | LINEAR filtering, no mipmaps, 0x80 fallback alpha |
| `lib/resource/zoneMeshSection.ts` | Zone mesh parser — sets `blendEnabled`, `discardThreshold` |
| `lib/resource/datResource.ts` | `MeshRenderState` type definition |

### Kotlin Reference

| File | Role |
|------|------|
| `xim/poc/gl/XimShader.kt` | Zone shader — unclamped `4.0 * a * a` alpha |
| `xim/poc/gl/XimSkinnedShader.kt` | Character shader — same alpha pattern |
| `xim/poc/gl/GLDrawer.kt` | GL state: blend enable/disable, depth mask, polygon offset |
| `xim/poc/gl/MeshBuffers.kt` | `RenderState`, `BlendFunc`, `ZBiasLevel` definitions |
| `xim/resource/ZoneMeshSection.kt` | Zone mesh parser — `blendEnabled`, `discardThreshold` |
| `xim/resource/SkeletonMeshSection.kt` | Character mesh parser — `discardThreshold = 69/255` |
