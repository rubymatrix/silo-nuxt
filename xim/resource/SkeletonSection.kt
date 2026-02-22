package xim.resource

import xim.math.Quaternion
import xim.math.Vector3f
import xim.poc.BoundingBox
import xim.util.OnceLogger.warn

enum class StandardPosition(val referenceIndex: Int) {
    AboveHead(2),
    RightFoot(8),
    LeftFoot(9),
    LeftHand(126),
    RightHand(127),
}

data class Joint(val rotation: Quaternion, val translation: Vector3f, val parentIndex: Int)

data class JointReference(val index: Int, val unkV0: Vector3f, val positionOffset: Vector3f, val fileOffset: Int)

class SkeletonSection(private val sectionHeader: SectionHeader) : ResourceParser {

    private val joints = ArrayList<Joint>()
    private val jointReferences = ArrayList<JointReference>()
    private val boundingBoxes = ArrayList<BoundingBox>()

    override fun getResource(byteReader: ByteReader): ParserResult {
        read(byteReader)
        val skeletonResource = SkeletonResource(sectionHeader.sectionId, joints, jointReferences, boundingBoxes)
        computeSize(skeletonResource)
        return ParserResult.from(skeletonResource)
    }

    private fun read(byteReader: ByteReader) {
        byteReader.offsetFromDataStart(sectionHeader, 0x02)
        val numJoints = byteReader.next8()

        byteReader.offsetFromDataStart(sectionHeader, 0x04)
        for (i in 0 until numJoints) {
            val maybeParentIndex = byteReader.next8()
            val parentIndex = if (maybeParentIndex == i) { -1 } else { maybeParentIndex }

            byteReader.position += 1

            val rotation = Quaternion(byteReader.nextFloat(), byteReader.nextFloat(), byteReader.nextFloat(), byteReader.nextFloat())
            val translation = Vector3f(byteReader.nextFloat(), byteReader.nextFloat(), byteReader.nextFloat())

            joints.add(Joint(rotation, translation, parentIndex))
        }

        val numReferences = byteReader.next16()
        val unk1 = byteReader.next16() // Usually -1?

        for (i in 0 until numReferences) {
            val start = byteReader.position

            val jointIndex = byteReader.next16()
            if (jointIndex > numJoints) { warn("[${sectionHeader.sectionId}] Not enough joints @ $byteReader!") }

            val unkV0 = byteReader.nextVector3f()
            val offset = byteReader.nextVector3f()

            jointReferences.add(JointReference(jointIndex, unkV0, offset, fileOffset = start))
        }

        while (byteReader.position < sectionHeader.sectionEndPosition) {
            val yMax = getNextFloat(byteReader) ?: break
            val yMin = getNextFloat(byteReader) ?: break

            val xMax = getNextFloat(byteReader) ?: break
            val xMin = getNextFloat(byteReader) ?: break

            val zMax = getNextFloat(byteReader) ?: break
            val zMin = getNextFloat(byteReader) ?: break

            boundingBoxes += BoundingBox.extents(bottomLeft = Vector3f(xMin,yMin,zMin), topRight = Vector3f(xMax,yMax,zMax))
        }
    }

    private fun computeSize(skeletonResource: SkeletonResource) {
        // TODO - probably use one of the bounding-boxes for this?
        val instance = SkeletonInstance(skeletonResource)
        instance.tPose()
        skeletonResource.size.y = instance.getStandardJointPosition(StandardPosition.AboveHead).y
    }

    private fun getNextFloat(byteReader: ByteReader): Float? {
        // 0xCDCDCDCD is "invalid"
        val a = byteReader.nextFloat()
        return if (a == Float.fromBits(-842150451)) { null } else { a }
    }

}
