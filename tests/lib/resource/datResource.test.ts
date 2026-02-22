import { describe, expect, it } from 'vitest'

import { DatId, DirectoryResource, SectionTypes, SkeletonMeshResource } from '~/lib/resource/datResource'
import { SectionHeader } from '~/lib/resource/datParser'

describe('DatResource.path', () => {
  it('falls back to section id when localDir is missing', () => {
    const mesh = new SkeletonMeshResource(new DatId('face'), [], 0)

    expect(mesh.path()).toBe('face')
  })
})

describe('DirectoryResource type matching', () => {
  it('collects entries when constructor identity differs but type name matches', () => {
    const root = new DirectoryResource(null, new DatId('root'))
    const header = new SectionHeader()
    header.sectionId = new DatId('face')
    header.sectionType = SectionTypes.S2A_SkeletonMesh

    const altMesh = new (class SkeletonMeshResource {
      readonly id = new DatId('face')
    })()

    const childrenByType = (root as unknown as { childrenByType: Map<Function, Map<string, unknown>> }).childrenByType
    childrenByType.set(altMesh.constructor as Function, new Map([[header.sectionId.id, altMesh]]))

    const entries = root.collectByType(SkeletonMeshResource)
    expect(entries).toHaveLength(1)
  })
})
