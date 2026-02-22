# AGENTS.md - Silo Model Viewer (Nuxt 3)

## Project Overview

A Nuxt 3 application that loads, parses, and renders game model assets (FFXI DAT files).
Three core layers: **resource library** (binary parsing), **resource loader** (async HTTP
fetching from `http://localhost:3005/{path}`), and **rendering library** (Three.js/WebGL).

The `xim/` directory contains a working Kotlin/JS reference implementation. Port its
patterns to TypeScript, adapting idioms appropriately (e.g., Kotlin `object` -> TS module
or singleton, Kotlin `sealed class` -> TS discriminated union, Kotlin `data class` -> TS
interface/type).

## Build & Run Commands

```bash
pnpm install          # Install dependencies
pnpm dev              # Start dev server (local DAT files from localhost:3005)
pnpm dev:r2           # Start dev server (DAT files from Cloudflare R2 — no local FFXI needed)
pnpm build            # Production build
pnpm preview          # Preview production build
```

### Testing (not yet configured - use Vitest when adding tests)

```bash
pnpm test             # Run all tests (after vitest is added)
pnpm test -- path/to/file.test.ts          # Run a single test file
pnpm test -- -t "test name pattern"        # Run tests matching a name
pnpm vitest run path/to/file.test.ts       # Alternative: run single file directly
```

When adding Vitest, install with `pnpm add -D @nuxt/test-utils vitest @vue/test-utils`
and create a `vitest.config.ts` that extends the Nuxt config.

### Linting (not yet configured)

No ESLint or Prettier configured. When adding, use `@nuxt/eslint` module.

## Tech Stack

- **Framework:** Nuxt 3.21+ with TypeScript (strict mode)
- **Package manager:** pnpm
- **UI:** shadcn-vue + reka-ui (headless), Tailwind CSS v4, lucide-vue-next icons
- **Rendering:** Three.js / WebGL2 (to be added)
- **State:** @vueuse/core composables (no Pinia currently)
- **Module system:** ESM (`"type": "module"` in package.json)

## Project Architecture

```
silo-nuxt/
  app.vue                 # Root - renders <NuxtPage />
  pages/                  # File-based routing
  components/             # Auto-imported Vue components (to be created)
  composables/            # Auto-imported composables (to be created)
  utils/                  # Auto-imported utility functions (to be created)
  lib/                    # Non-auto-imported libraries (to be created)
    resource/             # Binary DAT parsers (port from xim/resource/)
    loader/               # Async resource fetcher (port from xim/poc/browser/DatLoader.kt)
    renderer/             # Three.js/WebGL rendering (port from xim/poc/gl/)
    math/                 # Math utilities if not using Three.js equivalents
  server/                 # Nitro server routes (to be created)
  xim/                    # Kotlin reference implementation (READ-ONLY reference)
    math/                 # Vector, Matrix, Quaternion types
    resource/             # DAT file parsers (ByteReader, DatParser, sections)
    util/                 # Logging, timing, RNG utilities
    poc/                  # Full app: GL rendering, game logic, browser platform
```

## Code Style Guidelines

### TypeScript

- **Strict mode** is enabled (inherited from Nuxt's generated tsconfig).
- Use `interface` for object shapes that may be extended; use `type` for unions,
  intersections, and aliases.
- Prefer discriminated unions over class hierarchies (TS equivalent of Kotlin sealed classes).
- Never use `any`. Use `unknown` for truly unknown types and narrow with type guards.
- Use `readonly` on properties/arrays that should not be mutated after creation.
- Prefer `const` assertions and literal types where applicable.

### Naming Conventions

| Entity              | Convention       | Example                        |
|---------------------|------------------|--------------------------------|
| Files (Vue)         | PascalCase       | `ModelViewer.vue`              |
| Files (TS modules)  | camelCase         | `byteReader.ts`, `datParser.ts`|
| Files (composables) | camelCase, `use`  | `useModelLoader.ts`           |
| Interfaces/Types    | PascalCase       | `SectionHeader`, `DatEntry`    |
| Enums               | PascalCase       | `SectionType`                  |
| Enum members        | PascalCase       | `Texture`, `Skeleton`          |
| Functions           | camelCase        | `parseSection()`, `nextFloat()`|
| Constants           | camelCase or UPPER_SNAKE | `maxBufferSize`, `GL_REPEAT` |
| Vue components      | PascalCase       | `<ModelViewer />`              |
| CSS classes         | kebab-case (Tailwind utility-first) | `flex items-center`  |

### Imports

- Use path aliases: `~/` or `@/` for project root, `#imports` for Nuxt auto-imports.
- Nuxt auto-imports from `components/`, `composables/`, and `utils/` -- do not manually
  import from those directories unless needed for type-only imports.
- Use explicit named exports/imports. Avoid default exports except for Vue components
  and Nuxt config/pages (which require them).
- Use `import type { ... }` for type-only imports (`verbatimModuleSyntax` is enabled).

```typescript
// Good
import type { SectionHeader, DatEntry } from '~/lib/resource/types'
import { ByteReader } from '~/lib/resource/byteReader'

// Bad - default export for non-component
export default function parseSection() { ... }
```

### Vue Components

- Always use `<script setup lang="ts">` (Composition API with script setup).
- Define props with `defineProps<T>()` using TypeScript generics, not runtime declaration.
- Define emits with `defineEmits<T>()`.
- Keep template logic minimal -- extract complex logic to composables.

```vue
<script setup lang="ts">
interface Props {
  modelId: number
  showWireframe?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showWireframe: false,
})

const emit = defineEmits<{
  loaded: [modelId: number]
}>()
</script>
```

### Error Handling

- Use typed error results or throw descriptive errors with context (match the Kotlin
  reference pattern of including resource name and parser state in error messages).
- For async resource loading, return `Promise<T>` and handle failures at the call site.
- For binary parsing errors, throw with byte position and resource name for debugging:
  ```typescript
  throw new Error(`[${resourceName}] Failed to parse at 0x${position.toString(16)}`)
  ```
- Use `console.warn` for recoverable issues (unknown section types, missing textures).
- Never silently swallow errors.

### Binary Parsing Patterns (from xim/resource/ reference)

The Kotlin `ByteReader` is the core pattern to follow. Port it as a class that wraps
a `DataView` or `Uint8Array`:

- Methods: `next8()`, `next16()`, `next32()`, `nextFloat()`, `nextString(length)`,
  `nextVector3f()`, `align0x10()`, `subBuffer(size)`, `hasMore()`.
- All multi-byte reads are **little-endian** (matching x86/DAT format).
- Section parsing follows: read header -> dispatch by section type -> parse body ->
  return structured resource.

### Resource Loading Pattern (from xim/poc/browser/DatLoader.kt reference)

- Fetch DAT files from a configurable base URL as `ArrayBuffer`.
- In local dev: `http://localhost:3005/{filePath}` (no auth).
- In R2/production: `https://dats.phoenix-xi.com/{filePath}` (Bearer token auth).
- Cache loaded resources to avoid duplicate fetches.
- Return `Promise<DirectoryResource>` or equivalent parsed result.
- Use a queue or concurrency limit to avoid overwhelming the server.
- `DatLoader` accepts an optional `headers` option — when a token is configured,
  an `Authorization: Bearer <token>` header is sent with every fetch.

### Rendering Pattern (from xim/poc/gl/ reference)

- Use Three.js (or raw WebGL2 if preferred) for rendering.
- Mesh buffers contain interleaved vertex data (position, normal, UV, color, joint indices).
- Textures come in palette-indexed, DXT1, and DXT3 compressed formats.
- Skeleton animation uses joint hierarchies with keyframe interpolation.

### Formatting

- **No formatter is configured.** When one is added, use 2-space indentation, no semicolons
  (or with semicolons -- be consistent), single quotes for strings, trailing commas.
- Keep lines under 120 characters where reasonable.
- Use blank lines to separate logical sections within files.

### General Principles

- Prefer pure functions and immutable data where possible.
- Keep files focused -- one major export per file for parsers and resources.
- Co-locate types with the code that uses them; extract to a shared `types.ts` only
  when types are used across multiple modules.
- Write JSDoc comments for public APIs, especially parser functions that handle
  binary formats (document the byte layout).
- When porting from Kotlin, preserve the original structure names (e.g., `ByteReader`,
  `DatParser`, `SectionHeader`, `DirectoryResource`) for easy cross-referencing.
