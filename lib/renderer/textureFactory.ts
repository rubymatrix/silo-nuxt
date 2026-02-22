import {
  DataTexture,
  LinearFilter,
  LinearMipmapLinearFilter,
  LinearSRGBColorSpace,
  RGBAFormat,
  RepeatWrapping,
  UnsignedByteType,
  type Texture,
} from 'three'

import type { TextureResource } from '~/lib/resource/datResource'
import { decodeTextureToRgba } from './textureDecoder'

const textureCache = new WeakMap<TextureResource, Texture>()

export function getOrCreateTexture(textureResource: TextureResource): Texture {
  const existing = textureCache.get(textureResource)
  if (existing) {
    return existing
  }

  const texture = new DataTexture(
    decodeTextureToRgba(textureResource),
    textureResource.width,
    textureResource.height,
    RGBAFormat,
    UnsignedByteType,
  )

  texture.wrapS = RepeatWrapping
  texture.wrapT = RepeatWrapping
  texture.magFilter = LinearFilter
  texture.minFilter = LinearMipmapLinearFilter
  texture.colorSpace = LinearSRGBColorSpace
  texture.needsUpdate = true
  texture.generateMipmaps = true

  textureCache.set(textureResource, texture)
  return texture
}

export function releaseTexture(textureResource: TextureResource): void {
  const texture = textureCache.get(textureResource)
  if (!texture) {
    return
  }

  texture.dispose()
  textureCache.delete(textureResource)
}
