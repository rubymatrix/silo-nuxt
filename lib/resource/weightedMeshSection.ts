import { type Vector2, type Vector3 } from './byteReader'
import { ParserResult, type ResourceParser, type SectionHeader, oops } from './datParser'
import {
  type WeightedChunk,
  type WeightedMeshTriangle,
  WeightedMesh,
  WeightedMeshResource,
  type WeightedMeshSectionParserContext,
} from './datResource'

export class WeightedMeshSection implements ResourceParser {
  readonly sectionHeader: SectionHeader
  readonly parserContext: WeightedMeshSectionParserContext

  constructor(sectionHeader: SectionHeader, parserContext: WeightedMeshSectionParserContext) {
    this.sectionHeader = sectionHeader
    this.parserContext = parserContext
  }

  getResource(byteReader: import('./byteReader').ByteReader): ParserResult {
    const weightedMesh = this.read(byteReader)
    return ParserResult.from(new WeightedMeshResource(this.sectionHeader.sectionId, weightedMesh))
  }

  private read(byteReader: import('./byteReader').ByteReader): WeightedMesh {
    byteReader.offsetFromDataStart(this.sectionHeader, 0)
    const unk1 = byteReader.next16()
    if (unk1 !== 1) {
      oops(byteReader, `Unexpected weighted-mesh sentinel: ${unk1}`)
    }

    const meshConfig = byteReader.next8()
    const enableAlphaDiscard = (meshConfig & 0x80) !== 0
    const numWeightedSections = meshConfig & 0x0f
    if (numWeightedSections > 5) {
      console.warn(`[${this.sectionHeader.sectionId}] More weights than expected? ${numWeightedSections} @ ${byteReader}`)
    }
    const offsetExtension = byteReader.next8() * 0x10000

    const numPositions = byteReader.next16()
    const numNormals = byteReader.next16()

    const indexOffset = byteReader.next16() + offsetExtension + this.sectionHeader.dataStartPosition
    const numPrimitives = byteReader.next16()
    const numVertices = numPrimitives * 3

    const colorOffset = byteReader.next16() + this.sectionHeader.dataStartPosition
    const uvOffset = byteReader.next16() + offsetExtension + this.sectionHeader.dataStartPosition
    const textureName = byteReader.nextString(0x10).replace(/\u0000+$/g, '')

    const chunks: WeightedChunk[] = []
    for (let i = 0; i < numWeightedSections; i += 1) {
      const positions = Array.from({ length: numPositions }, () => byteReader.nextVector3f())
      const normals = Array.from({ length: numNormals }, () => this.unpackNormal(byteReader.next32()))
      chunks.push({ positions, normals })
    }

    byteReader.position = colorOffset
    const colorScale = this.parserContext.zoneResource ? 2 : 1
    const colors = Array.from({ length: numVertices }, () => this.scaleColor(byteReader.nextBGRA(), colorScale))

    byteReader.position = uvOffset
    const texCoords = Array.from({ length: numVertices }, () => byteReader.nextVector2f())

    byteReader.position = indexOffset
    const positionIndices = Array.from({ length: numVertices }, () => {
      const positionIndex = byteReader.next16()
      if (positionIndex > numPositions) {
        oops(byteReader, `Position index too large ${positionIndex}`)
      }
      return positionIndex
    })
    const normalIndices = Array.from({ length: numVertices }, () => {
      const normalIndex = byteReader.next16()
      if (normalIndex > numNormals) {
        oops(byteReader, `Normal index too large ${normalIndex}`)
      }
      return normalIndex
    })

    return new WeightedMesh(
      this.sectionHeader.sectionId,
      textureName,
      this.sectionHeader.localDir,
      numPrimitives,
      chunks,
      colors,
      texCoords,
      positionIndices,
      normalIndices,
      enableAlphaDiscard,
    )
  }

  private unpackNormal(packed: number): Vector3 {
    return {
      x: ((packed << 0x16) >> 0x16) / 512,
      y: ((packed << 0x0c) >> 0x16) / 512,
      z: ((packed << 0x02) >> 0x16) / 512,
    }
  }

  private scaleColor(
    color: import('./byteReader').BgraColor,
    scale: number,
  ): import('./byteReader').BgraColor {
    return {
      b: Math.min(0xff, color.b * scale),
      g: Math.min(0xff, color.g * scale),
      r: Math.min(0xff, color.r * scale),
      a: color.a,
    }
  }
}

export function buildWeightedTriangles(
  chunks: readonly WeightedChunk[],
  texCoords: readonly Vector2[],
  colors: readonly import('./byteReader').BgraColor[],
  positionIndices: readonly number[],
  normalIndices: readonly number[],
  numPrimitives: number,
  weights: readonly number[],
): WeightedMeshTriangle[] {
  const normalized = normalizeWeights(weights, chunks.length)
  const triangles: WeightedMeshTriangle[] = []

  for (let i = 0; i < numPrimitives * 3; i += 1) {
    let px = 0
    let py = 0
    let pz = 0
    let nx = 0
    let ny = 0
    let nz = 0

    for (let j = 0; j < chunks.length; j += 1) {
      const weight = normalized[j] ?? 0
      const position = chunks[j]?.positions[positionIndices[i] ?? 0]
      const normal = chunks[j]?.normals[normalIndices[i] ?? 0]
      if (!position || !normal) {
        continue
      }

      px += position.x * weight
      py += position.y * weight
      pz += position.z * weight

      nx += normal.x * weight
      ny += normal.y * weight
      nz += normal.z * weight
    }

    const normalLength = Math.hypot(nx, ny, nz)
    const normalizedNormal = normalLength > 1e-7
      ? { x: nx / normalLength, y: ny / normalLength, z: nz / normalLength }
      : { x: 0, y: 1, z: 0 }

    triangles.push({
      position: { x: px, y: py, z: pz },
      normal: normalizedNormal,
      color: colors[i] ?? { b: 0xff, g: 0xff, r: 0xff, a: 0xff },
      texCoord: texCoords[i] ?? { x: 0, y: 0 },
    })
  }

  return triangles
}

function normalizeWeights(weights: readonly number[], maxCount: number): number[] {
  const sliced = weights.slice(0, maxCount)
  if (sliced.length === 0) {
    return [1]
  }

  const sum = sliced.reduce((acc, value) => acc + value, 0)
  if (sum <= 1e-7) {
    return sliced.map((_, index) => (index === 0 ? 1 : 0))
  }

  return sliced.map((value) => value / sum)
}
