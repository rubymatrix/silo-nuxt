import { describe, expect, it } from 'vitest'

import { DatId, DirectoryResource, TextureResource } from '~/lib/resource/datResource'
import { TextureLink } from '~/lib/resource/textureLink'

describe('TextureLink', () => {
  it('allows empty texture names like Kotlin', () => {
    const localDir = new DirectoryResource(null, new DatId('root'))
    const link = TextureLink.of('', localDir)
    expect(link).not.toBeNull()
  })

  it('falls back to global texture registry in default getOrPut path', () => {
    const globalDir = new DirectoryResource(null, new DatId('glob'))
    const localDir = new DirectoryResource(null, new DatId('root'))

    const texture = new TextureResource(new DatId('tx00'), 'abcdefghlocal000', {
      textureType: 0x91,
      width: 1,
      height: 1,
      bitCount: 32,
      dxtType: null,
      rawData: new Uint8Array([0, 0, 0, 0]),
    })

    const fakeHeader = {
      sectionId: new DatId('tx00'),
      sectionType: { code: 0x20, resourceType: TextureResource },
    }

    globalDir.addChild(fakeHeader as never, texture)

    const link = TextureLink.of('ijklmnoplocal000', localDir)
    const resolved = link?.getOrPut()
    expect(resolved?.name).toBe('abcdefghlocal000')
  })
})
