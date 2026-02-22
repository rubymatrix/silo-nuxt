import { type Vector3 } from './byteReader'
import { ParserResult, type ResourceParser, type SectionHeader } from './datParser'
import { SkeletonAnimation, SkeletonAnimationResource, type SkeletonAnimationKeyFrameTransform } from './datResource'

class FrameSequence {
  readonly frameValues: readonly number[]

  constructor(frameValues: readonly number[]) {
    this.frameValues = frameValues
  }

  getValue(frame: number): number {
    if (this.frameValues.length === 1) {
      return this.frameValues[0] ?? 0
    }
    return this.frameValues[frame] ?? 0
  }
}

export class SkeletonAnimationSection implements ResourceParser {
  private readonly sectionHeader: SectionHeader

  constructor(sectionHeader: SectionHeader) {
    this.sectionHeader = sectionHeader
  }

  getResource(byteReader: import('./byteReader').ByteReader): ParserResult {
    const animation = this.read(byteReader)
    return ParserResult.from(new SkeletonAnimationResource(this.sectionHeader.sectionId, animation))
  }

  private read(byteReader: import('./byteReader').ByteReader): SkeletonAnimation {
    byteReader.offsetFromDataStart(this.sectionHeader, 0)
    byteReader.next16()

    const animation = new SkeletonAnimation(
      this.sectionHeader.sectionId,
      byteReader.next16(),
      byteReader.next16(),
      byteReader.nextFloat(),
    )

    const keyFrameDataOffset = byteReader.position

    for (let i = 0; i < animation.numJoints; i += 1) {
      const jointIndex = byteReader.next32()
      const rotations = this.readKeyFrameSequences(4, animation.numFrames, byteReader, keyFrameDataOffset)
      const translations = this.readKeyFrameSequences(3, animation.numFrames, byteReader, keyFrameDataOffset)
      const scales = this.readKeyFrameSequences(3, animation.numFrames, byteReader, keyFrameDataOffset)

      if (rotations.length === 0 || translations.length === 0 || scales.length === 0) {
        continue
      }

      animation.keyFrameSets.set(jointIndex, this.resolveSequences(animation.numFrames, rotations, translations, scales))
    }

    return animation
  }

  private readKeyFrameSequences(
    amount: number,
    numFrames: number,
    byteReader: import('./byteReader').ByteReader,
    sequenceDataOffset: number,
  ): FrameSequence[] {
    const offsets = byteReader.next32SignedArray(amount)
    const constValues = byteReader.nextFloatArray(amount).map((value) => value % 10_000)
    if (offsets.some((offset) => offset < 0)) {
      return []
    }

    return offsets.map((offset, index) => {
      if (offset === 0) {
        return new FrameSequence([constValues[index] ?? 0])
      }
      return this.fetchSequence(offset, numFrames, byteReader, sequenceDataOffset)
    })
  }

  private fetchSequence(
    index: number,
    numFrames: number,
    byteReader: import('./byteReader').ByteReader,
    sequenceDataOffset: number,
  ): FrameSequence {
    const originalPos = byteReader.position
    byteReader.position = sequenceDataOffset + index * 4
    const sequence = byteReader.nextFloatArray(numFrames)
    byteReader.position = originalPos
    return new FrameSequence(sequence)
  }

  private resolveSequences(
    numFrames: number,
    rotationSequences: readonly FrameSequence[],
    translationSequences: readonly FrameSequence[],
    scaleSequences: readonly FrameSequence[],
  ): readonly SkeletonAnimationKeyFrameTransform[] {
    const keyFrames: SkeletonAnimationKeyFrameTransform[] = []

    for (let frame = 0; frame < numFrames; frame += 1) {
      keyFrames.push({
        rotation: {
          x: rotationSequences[0]?.getValue(frame) ?? 0,
          y: rotationSequences[1]?.getValue(frame) ?? 0,
          z: rotationSequences[2]?.getValue(frame) ?? 0,
          w: rotationSequences[3]?.getValue(frame) ?? 1,
        },
        translation: {
          x: translationSequences[0]?.getValue(frame) ?? 0,
          y: translationSequences[1]?.getValue(frame) ?? 0,
          z: translationSequences[2]?.getValue(frame) ?? 0,
        },
        scale: {
          x: scaleSequences[0]?.getValue(frame) ?? 1,
          y: scaleSequences[1]?.getValue(frame) ?? 1,
          z: scaleSequences[2]?.getValue(frame) ?? 1,
        },
      })
    }

    return keyFrames
  }
}

export function lerpVector3(a: Vector3, b: Vector3, delta: number): Vector3 {
  return {
    x: a.x + (b.x - a.x) * delta,
    y: a.y + (b.y - a.y) * delta,
    z: a.z + (b.z - a.z) * delta,
  }
}
