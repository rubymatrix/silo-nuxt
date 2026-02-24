import {
  DataTexture,
  LinearFilter,
  NearestFilter,
  RGBAFormat,
  RepeatWrapping,
  UnsignedByteType,
  type Texture,
} from 'three'

import type { TextureResource } from '~/lib/resource/datResource'
import { decodeTextureToRgba } from './textureDecoder'

const textureCache = new WeakMap<TextureResource, Texture>()

let _defaultGrayTexture: Texture | null = null

/**
 * Returns a 1x1 gray (0x80) texture used when a zone mesh's texture fails to resolve.
 * Matches Kotlin's `makeSingleColorTexture(0x80)` — all four channels 0x80, with
 * NEAREST filtering (Kotlin default texture uses NEAREST).
 */
export function defaultGrayTexture(): Texture {
  if (_defaultGrayTexture) return _defaultGrayTexture

  const data = new Uint8Array([0x80, 0x80, 0x80, 0x80])
  const tex = new DataTexture(data, 1, 1, RGBAFormat, UnsignedByteType)
  tex.wrapS = RepeatWrapping
  tex.wrapT = RepeatWrapping
  tex.magFilter = NearestFilter
  tex.minFilter = NearestFilter
  tex.needsUpdate = true
  _defaultGrayTexture = tex
  return tex
}

/**
 * Decode and upload a TextureResource as a Three.js DataTexture.
 *
 * Matches Kotlin's texture parameters:
 *   TEXTURE_MAG_FILTER = LINEAR, TEXTURE_MIN_FILTER = LINEAR (no mipmaps).
 * No colorSpace override — Kotlin uses implicit linear.
 */
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
  texture.minFilter = LinearFilter
  texture.generateMipmaps = false
  texture.needsUpdate = true

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
