package xim.resource

import xim.math.Quaternion
import xim.math.Vector3f
import xim.resource.SkeletonAnimationKeyFrameTransform.Companion.interpolate
import kotlin.math.floor
import kotlin.math.roundToInt

class SkeletonAnimation(
    val id: DatId,
    val numJoints: Int,
    val numFrames: Int,
    val keyFrameDuration: Float,
) {
    val keyFrameSets: HashMap<Int, SkeletonAnimationKeyFrameTransforms> = HashMap()

    fun getJointTransform(jointIndex: Int, frame: Float) : SkeletonAnimationKeyFrameTransform? {
        val keyFrameSet = keyFrameSets[jointIndex] ?: return null

        val scaledFrame = frame * keyFrameDuration
        if (scaledFrame >= numFrames - 1) {
            return keyFrameSet.getTransform(numFrames - 1)
        }

        val lower = floor(scaledFrame).roundToInt()
        val kfLower = keyFrameSet.getTransform(lower)

        val upper = lower + 1
        val kfUpper = keyFrameSet.getTransform(upper)

        val delta = scaledFrame - lower
        return interpolate(kfLower, kfUpper, delta)
    }

    fun getLengthInFrames(): Float {
        return (numFrames-1).coerceAtLeast(1) / keyFrameDuration
    }

}

class SkeletonAnimationKeyFrameTransform(
    val rotation: Quaternion = Quaternion(0f, 0f, 0f, 1f),
    val translation: Vector3f = Vector3f(0f, 0f, 0f),
    val scale: Vector3f = Vector3f(1f,1f,1f),
) {

    companion object {

        fun interpolate(a: SkeletonAnimationKeyFrameTransform, b: SkeletonAnimationKeyFrameTransform, delta: Float) : SkeletonAnimationKeyFrameTransform {
            return SkeletonAnimationKeyFrameTransform(
                rotation = Quaternion.nlerp(a.rotation, b.rotation, delta),
                translation = Vector3f.lerp(a.translation, b.translation, delta),
                scale = Vector3f.lerp(a.scale, b.scale, delta),
            )
        }
    }
}

class SkeletonAnimationKeyFrameTransforms(val transforms: List<SkeletonAnimationKeyFrameTransform>) {
    fun getTransform(frame: Int) = transforms[frame]
}

class FrameSequence(private val frameValues: FloatArray) {
    fun getValue(frame: Int) : Float {
        return if (frameValues.size == 1) { frameValues[0] } else { frameValues[frame] }
    }
}

class SkeletonAnimationParser(val sectionHeader: SectionHeader) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        val animation = read(byteReader)
        val resource = SkeletonAnimationResource(sectionHeader.sectionId, animation)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader) : SkeletonAnimation {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)

        val unk0 = byteReader.next16()

        val animation = SkeletonAnimation(
            id = sectionHeader.sectionId,
            numJoints = byteReader.next16(),
            numFrames = byteReader.next16(),
            keyFrameDuration = byteReader.nextFloat() // time-per-animation-frame
        )

        val keyFrameDataOffset = byteReader.position

        for (i in 0 until animation.numJoints) {
            val jointIndex = byteReader.next32()

            val rotationSequences = readKeyFrameSequences(4, animation.numFrames, byteReader, keyFrameDataOffset)
            val translationSequences = readKeyFrameSequences(3, animation.numFrames, byteReader, keyFrameDataOffset)
            val scaleSequences = readKeyFrameSequences(3, animation.numFrames, byteReader, keyFrameDataOffset)

            if (rotationSequences.isEmpty() || translationSequences.isEmpty() || scaleSequences.isEmpty()) { continue }

            animation.keyFrameSets[jointIndex] = resolveSequences(animation.numFrames, rotationSequences, translationSequences, scaleSequences)
        }

        return animation
    }

    private fun readKeyFrameSequences(amount: Int, numFrames: Int, byteReader: ByteReader, sequenceDataOffset: Int) : List<FrameSequence> {
        val offsets = byteReader.next32(amount)
        val constValues = byteReader.nextFloat(amount).map { it.rem(10_000f) }
        val sequences = ArrayList<FrameSequence>(amount)

        if (offsets.any { it < 0 }) { return emptyList() }

        for (i in 0 until amount) {
            if (offsets[i] == 0) {
                sequences.add(i, FrameSequence(floatArrayOf(constValues[i])))
            } else {
                sequences.add(i, fetchSequence(offsets[i], numFrames, byteReader, sequenceDataOffset))
            }
        }

        return sequences
    }

    private fun fetchSequence(index: Int, numFrames: Int, byteReader: ByteReader, sequenceDataOffset: Int) : FrameSequence {
        val originalPos = byteReader.position
        byteReader.position = sequenceDataOffset + index * 4

        val sequence = byteReader.nextFloat(numFrames)
        byteReader.position = originalPos

        return FrameSequence(sequence)
    }

    private fun resolveSequences(numFrames: Int, rotationSequences: List<FrameSequence>, translationSequences: List<FrameSequence>, scaleSequences: List<FrameSequence>): SkeletonAnimationKeyFrameTransforms {
        val keyFrames = ArrayList<SkeletonAnimationKeyFrameTransform>(numFrames)

        for (frame in 0 until numFrames) {
            val rotation = Quaternion(
                rotationSequences[0].getValue(frame),
                rotationSequences[1].getValue(frame),
                rotationSequences[2].getValue(frame),
                rotationSequences[3].getValue(frame),
            )

            val translation = Vector3f(
                translationSequences[0].getValue(frame),
                translationSequences[1].getValue(frame),
                translationSequences[2].getValue(frame),
            )

            val scale = Vector3f(
                scaleSequences[0].getValue(frame),
                scaleSequences[1].getValue(frame),
                scaleSequences[2].getValue(frame),
            )

            keyFrames += SkeletonAnimationKeyFrameTransform(rotation, translation, scale)
        }

        return SkeletonAnimationKeyFrameTransforms(keyFrames)
    }

}
