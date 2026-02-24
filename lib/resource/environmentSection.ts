/**
 * Environment section parser (section type 0x2F).
 *
 * Ported from xim/resource/EnvironmentSection.kt.
 * Parses skybox, lighting, fog, and draw distance.
 */

import type { ByteReader, BgraColor } from './byteReader'
import type { SectionHeader } from './datParser'
import { ParserResult, type ResourceParser } from './datParser'
import {
  EnvironmentResource,
  type LightConfig,
  type SkyBoxSlice,
} from './zoneResource'

export class EnvironmentSection implements ResourceParser {
  private readonly sectionHeader: SectionHeader

  constructor(sectionHeader: SectionHeader) {
    this.sectionHeader = sectionHeader
  }

  getResource(byteReader: ByteReader): ParserResult {
    const resource = this.read(byteReader)
    return ParserResult.from(resource)
  }

  private read(byteReader: ByteReader): EnvironmentResource {
    byteReader.offsetFromDataStart(this.sectionHeader, 0x0)

    const indoorFlag = byteReader.next32()
    const indoors = indoorFlag === 1

    byteReader.next32() // padding
    byteReader.next32() // padding

    const modelLighting = readLightConfig(byteReader)
    byteReader.next32() // padding

    const terrainLighting = readLightConfig(byteReader)
    byteReader.next32() // padding

    const clearColor = byteReader.nextRGBA()
    byteReader.next32() // unknown
    byteReader.next32() // unknown
    const drawDistance = byteReader.nextFloat()

    byteReader.next16() // unknown
    const sphereSpokeCount = byteReader.next16()

    const _unkColor = byteReader.nextRGBA()
    byteReader.next32() // padding
    const skyBoxRadius = byteReader.nextFloat()

    // Skybox slices
    const colors: BgraColor[] = []
    for (let i = 0; i < 8; i++) colors.push(byteReader.nextRGBA())

    const sizes: number[] = []
    for (let i = 0; i < 8; i++) sizes.push(byteReader.nextFloat())

    const slices: SkyBoxSlice[] = colors.map((color, i) => ({
      color,
      elevation: sizes[i]!,
    }))

    byteReader.next32() // trailing zero

    return new EnvironmentResource(
      this.sectionHeader.sectionId,
      { radius: skyBoxRadius, slices, spokes: sphereSpokeCount },
      { modelLighting, terrainLighting, indoors },
      drawDistance,
      { r: clearColor.r / 255, g: clearColor.g / 255, b: clearColor.b / 255, a: clearColor.a / 255 },
    )
  }
}

function readLightConfig(byteReader: ByteReader): LightConfig {
  return {
    sunLightColor: byteReader.nextRGBA(),
    moonLightColor: byteReader.nextRGBA(),
    ambientColor: byteReader.nextRGBA(),
    fogColor: byteReader.nextRGBA(),
    fogEnd: byteReader.nextFloat(),
    fogStart: byteReader.nextFloat(),
    diffuseMultiplier: byteReader.nextFloat(),
  }
}
