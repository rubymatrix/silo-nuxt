import { type Vector3 } from './byteReader'
import { ParserResult, type ResourceParser, type SectionHeader } from './datParser'
import { SkeletonResource } from './datResource'
import { SkeletonInstance, StandardPosition } from './skeletonInstance'

const INVALID_FLOAT = -842150451
const INVALID_FLOAT_VALUE = (() => {
  const buffer = new ArrayBuffer(4)
  const view = new DataView(buffer)
  view.setInt32(0, INVALID_FLOAT, true)
  return view.getFloat32(0, true)
})()

export interface Quaternion {
  readonly x: number
  readonly y: number
  readonly z: number
  readonly w: number
}

export interface Joint {
  readonly rotation: Quaternion
  readonly translation: Vector3
  readonly parentIndex: number
}

export interface JointReference {
  readonly index: number
  readonly unkV0: Vector3
  readonly positionOffset: Vector3
  readonly fileOffset: number
}

export interface BoundingBox {
  readonly min: Vector3
  readonly max: Vector3
}

export class SkeletonSection implements ResourceParser {
  private readonly sectionHeader: SectionHeader

  constructor(sectionHeader: SectionHeader) {
    this.sectionHeader = sectionHeader
  }

  getResource(byteReader: import('./byteReader').ByteReader): ParserResult {
    const joints: Joint[] = []
    const jointReferences: JointReference[] = []
    const boundingBoxes: BoundingBox[] = []

    byteReader.offsetFromDataStart(this.sectionHeader, 0x02)
    const numJoints = byteReader.next8()

    byteReader.offsetFromDataStart(this.sectionHeader, 0x04)
    for (let i = 0; i < numJoints; i += 1) {
      const maybeParentIndex = byteReader.next8()
      const parentIndex = maybeParentIndex === i ? -1 : maybeParentIndex
      byteReader.position += 1

      const rotation = {
        x: byteReader.nextFloat(),
        y: byteReader.nextFloat(),
        z: byteReader.nextFloat(),
        w: byteReader.nextFloat(),
      }
      const translation = byteReader.nextVector3f()
      joints.push({ rotation, translation, parentIndex })
    }

    const numReferences = byteReader.next16()
    byteReader.next16()

    for (let i = 0; i < numReferences; i += 1) {
      const start = byteReader.position
      const jointIndex = byteReader.next16()
      const unkV0 = byteReader.nextVector3f()
      const positionOffset = byteReader.nextVector3f()
      jointReferences.push({ index: jointIndex, unkV0, positionOffset, fileOffset: start })
    }

    while (byteReader.position < this.sectionHeader.sectionEndPosition) {
      const yMax = this.nextFloatOrInvalid(byteReader)
      const yMin = this.nextFloatOrInvalid(byteReader)
      const xMax = this.nextFloatOrInvalid(byteReader)
      const xMin = this.nextFloatOrInvalid(byteReader)
      const zMax = this.nextFloatOrInvalid(byteReader)
      const zMin = this.nextFloatOrInvalid(byteReader)

      if ([yMax, yMin, xMax, xMin, zMax, zMin].some((value) => value === null)) {
        break
      }

      boundingBoxes.push({
        min: { x: xMin ?? 0, y: yMin ?? 0, z: zMin ?? 0 },
        max: { x: xMax ?? 0, y: yMax ?? 0, z: zMax ?? 0 },
      })
    }

    const skeletonResource = new SkeletonResource(this.sectionHeader.sectionId, joints, jointReferences, boundingBoxes)
    this.computeSize(skeletonResource)
    return ParserResult.from(skeletonResource)
  }

  private computeSize(skeletonResource: SkeletonResource): void {
    if (skeletonResource.jointReferences.length <= StandardPosition.AboveHead) {
      return
    }

    const instance = new SkeletonInstance(skeletonResource)
    instance.tPose()
    skeletonResource.size.y = instance.getStandardJointPosition(StandardPosition.AboveHead).y
  }

  private nextFloatOrInvalid(byteReader: import('./byteReader').ByteReader): number | null {
    const value = byteReader.nextFloat()
    return value === INVALID_FLOAT_VALUE ? null : value
  }
}
