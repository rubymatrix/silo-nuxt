package xim.resource

import xim.math.Matrix4f
import xim.math.Quaternion
import xim.math.Vector3f
import xim.poc.*
import xim.poc.camera.CameraReference
import xim.util.PI_f

class JointInstance(
    val index: Int,
    val parent: JointInstance?,
    val definition: Joint,
    val currentTransform: Matrix4f,
)

private class JointTransform(
    var r: Matrix4f = Matrix4f(),
    var t: Vector3f = Vector3f(),
    var s: Vector3f = Vector3f(1f, 1f, 1f),
) {
    fun toMat4(): Matrix4f {
        return Matrix4f().copyFrom(r).translateDirect(t).scaleInPlace(s)
    }
}

private class JointTransformBuilder {

    private val joints = HashMap<Int, JointTransform>()

    operator fun get(index: Int): JointTransform {
        return joints.getOrPut(index) { JointTransform() }
    }

    fun isComputed(index: Int): Boolean {
        return joints.containsKey(index)
    }

    fun copy(from: Int, to: Int): JointTransform {
        val fromJoint = get(from)
        val toJoint = get(to)

        toJoint.t.copyFrom(fromJoint.t)
        toJoint.s.copyFrom(fromJoint.s)
        toJoint.r.copyFrom(fromJoint.r)

        return toJoint
    }

}

class SkeletonInstance(val resource: SkeletonResource) {

    val joints = ArrayList<JointInstance>()

    init {
        for (i in resource.joints.indices) {
            val jointDef = resource.joints[i]
            val parent = if (jointDef.parentIndex == -1) { null } else { joints[jointDef.parentIndex] }
            joints.add(JointInstance(i, parent, resource.joints[i], Matrix4f()))
        }
    }

    fun getStandardJoint(standardPosition: StandardPosition) : JointReference {
        return getStandardJoint(standardPosition.referenceIndex)
    }

    fun getStandardJoint(referenceIndex: Int) : JointReference {
        return resource.jointReference[referenceIndex]
    }

    fun getStandardJointExtended(referenceIndex: Int, fromActor: Actor, toActor: Actor): Int {
        if (referenceIndex < 49 || referenceIndex > 51) { return referenceIndex }
        // joint 49 refer to a set of 8 joints ([13,20]) that form a circle around the actor.
        // The nearest of these should be chosen.
        // TODO - how do 50 and 51 differ from 49?

        var reference = referenceIndex
        var distance = Float.MAX_VALUE

        for (i in 13 .. 20) {
            val jointPosition = toActor.getWorldSpaceJointPosition(i)
            val jointDistance = Vector3f.distanceSquared(fromActor.displayPosition, jointPosition)

            if (jointDistance < distance) {
                reference = i
                distance = jointDistance
            }
        }

        return reference
    }

    fun getJoint(jointReference: JointReference) : JointInstance {
        return joints[jointReference.index]
    }

    fun getJoint(standardPosition: StandardPosition) : JointInstance {
        return joints[getStandardJoint(standardPosition).index]
    }

    fun isLeftFootTouchingGround(): Boolean {
        return isJointTouchingGround(getStandardJoint(StandardPosition.LeftFoot))
    }

    fun isRightFootTouchingGround(): Boolean {
        return isJointTouchingGround(getStandardJoint(StandardPosition.RightFoot))
    }

    private fun isJointTouchingGround(jointReference: JointReference): Boolean {
        val jointInstance = getJoint(jointReference)
        val jointPosition = jointInstance.currentTransform.transform(jointReference.positionOffset)
        return jointPosition.y > -0.02f
    }

    private fun identity() {
        joints.forEach { it.currentTransform.identity() }
    }

    fun tPose() {
        identity()

        for (i in joints.indices) {
            val joint = joints[i]
            joint.currentTransform.translateInPlace(joint.definition.translation)
            joint.currentTransform.multiplyInPlace(joint.definition.rotation.toMat4())
            if (joint.parent != null) {
                joint.parent.currentTransform.multiply(joint.currentTransform, joint.currentTransform)
            }
        }
    }

    fun getStandardJointPosition(index: Int): Vector3f {
        return getJointPosition(getStandardJoint(index))
    }

    fun getStandardJointPosition(standardPosition: StandardPosition): Vector3f {
        return getJointPosition(getStandardJoint(standardPosition))
    }

    private fun getJointPosition(jointReference: JointReference): Vector3f {
        val jointInstance = getJoint(jointReference)
        return jointInstance.currentTransform.transform(jointReference.positionOffset)
    }

    fun animate(actor: Actor, actorModel: ActorModel, actorMount: Mount?) {
        identity()
        val jointTransformBuilder = JointTransformBuilder()

        // When a PC Model is engaged, the joints that correspond to weapon-handles get re-parented to the right & left hand.
        val jointParentOverrides = computeJointParentOverrides(actor, actorModel)

        // A joint may have a smaller index than its parent (especially when re-parenting is involved).
        // The iteration below is based on the index-order, so children might be queried before their parents.
        // For now, detect this and  do multiple passes. It seems that at most ~3 passes are needed (Mithra w/shield).
        var hasMissingParentComputation: Boolean

        do {
            hasMissingParentComputation = false

            for (joint in joints) {
                if (jointTransformBuilder.isComputed(joint.index)) { continue }

                val jointParentOverride = jointParentOverrides[joint.index]
                val jointParent = jointParentOverride ?: joint.parent

                if (jointParent != null && !jointTransformBuilder.isComputed(jointParent.index)) {
                    hasMissingParentComputation = true
                    continue
                }

                if (jointParentOverride != null) {
                    updateCurrentJointTransformWithParentOverride(actorModel, joint, jointParentOverride, jointTransformBuilder)
                } else {
                    updateCurrentJointTransform(actor, actorModel, actorMount, joint, jointTransformBuilder)
                }
            }
        } while (hasMissingParentComputation)

        updateBoundingBoxes(actor)
    }

    private fun updateCurrentJointTransform(actor: Actor, actorModel: ActorModel, actorMount: Mount?, joint: JointInstance, jointTransformBuilder: JointTransformBuilder) {
        val jointTransform = jointTransformBuilder[joint.index]

        if (joint.index == 0) {
            applyRootActorTransform(actor, actorModel, jointTransform)
        } else if (joint.index == 2 && actorMount != null) {
            applyMountAttachTransform(actor, actorMount, jointTransform)
            joint.currentTransform.copyFrom(jointTransform.toMat4())
            return
        }

        val translation = Vector3f(joint.definition.translation)
        val rotation = Quaternion(joint.definition.rotation)
        val scale = Vector3f(1f, 1f, 1f)

        val animationTransform = actorModel.skeletonAnimationCoordinator.getJointTransform(joint.index)
        if (animationTransform != null) {
            translation += animationTransform.translation
            Quaternion.multiplyAndStore(animationTransform.rotation, rotation, rotation)

            // For the root-joint, the scale values are ignored - the actor's scale is used instead
            if (joint.index != 0) { scale *= animationTransform.scale }
        }

        // For the root-joint, the translation doesn't seem to be in "skeleton-space"
        if (joint.index == 0) { translation.rotate270() }

        if (joint.parent == null) {
            jointTransform.t = jointTransform.r.transform(jointTransform.s * translation)
            jointTransform.s.timesAssign(scale)
            jointTransform.r.multiply(rotation.toMat4(), jointTransform.r)
        } else {
            val parentJointTransform = jointTransformBuilder[joint.parent.index]
            jointTransform.t = parentJointTransform.t + parentJointTransform.r.transform(parentJointTransform.s * translation)
            jointTransform.s = parentJointTransform.s * scale
            parentJointTransform.r.multiply(rotation.toMat4(), jointTransform.r)
        }

        joint.currentTransform.copyFrom(jointTransform.toMat4())
    }

    private fun updateCurrentJointTransformWithParentOverride(actorModel: ActorModel, joint: JointInstance, jointParent: JointInstance, jointTransformBuilder: JointTransformBuilder) {
        // The re-parenting effect has odd scaling properties - it seems that the scale of the new parent should be ignored.
        val scale = Vector3f(1f, 1f, 1f)

        val animationTransform = actorModel.skeletonAnimationCoordinator.getJointTransform(joint.index)
        if (animationTransform != null) { scale *= animationTransform.scale }

        val jointTransform = jointTransformBuilder.copy(from = jointParent.index, to = joint.index)
        jointTransform.s.copyFrom(scale)
        joint.currentTransform.copyFrom(jointTransform.toMat4())
    }

    private fun computeJointParentOverrides(actor: Actor, actorModel: ActorModel): Map<Int, JointInstance> {
        if (!actor.isDisplayEngaged()) { return emptyMap() }
        if (actorModel.model !is PcModel) { return emptyMap() }

        val overrideMap = HashMap<Int, JointInstance>()

        val mainInfo = actorModel.model.getMainWeaponInfo() ?: return overrideMap
        if (mainInfo.standardJointIndex != null) {
            val mainJointReference = getStandardJoint(mainInfo.standardJointIndex)
            val rightHandJoint = getJoint(StandardPosition.RightHand)
            overrideMap[mainJointReference.index] = rightHandJoint
        }

        val subInfo = actorModel.model.getSubWeaponInfo() ?: return overrideMap
        if (subInfo.standardJointIndex != null) {
            val subJointReference = getStandardJoint(subInfo.standardJointIndex)
            val leftHandJoint = getJoint(StandardPosition.LeftHand)
            overrideMap[subJointReference.index] = leftHandJoint
        }

        return overrideMap
    }

    private fun applyRootActorTransform(actor: Actor, actorModel: ActorModel, jointTransform: JointTransform) {
        jointTransform.r.rotateYInPlace(actor.displayFacingDir)

        val slopeOriented = actorModel.getFootInfoDefinition()?.movementType?.slopeOriented ?: false
        if (slopeOriented) { jointTransform.r.rotateZInPlace(actor.displayFacingSkew) }

        jointTransform.s.copyFrom(actor.getScale())
    }

    private fun applyMountAttachTransform(actor: Actor, actorMount: Mount, jointTransform: JointTransform) {
        val riderModel = actor.actorModel?.model ?: return
        val riderTypeIndex = if (riderModel is PcModel) { riderModel.raceGenderConfig.index - 1 } else { 0 }

        val mount = ActorManager[actorMount.id] ?: return
        val mountSkeleton = mount.actorModel?.getSkeleton() ?: return

        val jointRef = mountSkeleton.getStandardJoint(48 + riderTypeIndex)
        val jointInstance = mountSkeleton.getJoint(jointRef)

        val actorJointPosition = jointInstance.currentTransform.transform(jointRef.positionOffset)
        jointTransform.t.addInPlace(actorJointPosition)
        jointTransform.t.addInPlace(Vector3f(0f, -0.1f, 0f)) // TODO is there a real offset to use here?

        jointTransform.r.rotateYInPlace(actor.displayFacingDir - PI_f /2f + actorMount.getRiderRotation())
    }


    private fun updateBoundingBoxes(actor: Actor) {
        val boxes = ArrayList<BoundingBox>(resource.boundingBoxes.size)

        val stdRef = getStandardJoint(0)
        val joint = getJoint(stdRef)
        val jointPos = actor.getWorldSpaceJointPosition(0)

        val transform = Matrix4f().translateDirect(jointPos).copyUpperLeft(joint.currentTransform)

        for (baseBox in resource.boundingBoxes) {
            boxes += baseBox.transform(transform)
        }

        actor.updateSkeletonBoundingBoxes(boxes)
    }

}

class SkeletonAnimationThrottler(val actor: Actor) {

    private var storedFrames = 0f

    fun updateAndCheck(elapsedFrames: Float): Boolean {
        val cameraPosition = CameraReference.getInstance().getPosition()
        val adjustedDistance = Vector3f.distance(cameraPosition, actor.displayPosition) - 20f

        if (adjustedDistance <= 0f) {
            storedFrames = 0f
            return true
        }

        val ratio = (1f - adjustedDistance/30f).coerceIn(0.25f, 1f)
        storedFrames += elapsedFrames * ratio

        return if (storedFrames >= 0f) {
            storedFrames -= 1f
            true
        } else {
            false
        }
    }

}