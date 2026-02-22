package xim.resource

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.*
import xim.poc.camera.CameraReference
import xim.util.OnceLogger
import xim.util.PI_f
import kotlin.math.atan2

data class ParticleGenerationAttachmentConfig(
    val association: EffectAssociation,
    val attachType: AttachType,
    val sourceJoint: Int,
    val targetJoint: Int,
    val actorScaleParams: ActorScaleParams,
    val attachSourceOriented: Boolean,
) {

    companion object {
        fun fromDef(particleGenerator: ParticleGenerator, particleGeneratorDefinition: ParticleGeneratorDefinition): ParticleGenerationAttachmentConfig {
            return ParticleGenerationAttachmentConfig(
                association = particleGenerator.association,
                attachType = particleGeneratorDefinition.attachType,
                sourceJoint = particleGeneratorDefinition.attachedJoint0,
                targetJoint = particleGeneratorDefinition.attachedJoint1,
                actorScaleParams = particleGeneratorDefinition.actorScaleParams,
                attachSourceOriented = particleGeneratorDefinition.attachSourceOriented
            )
        }
    }
}

class ParticleGeneratorAttachment(val datId: DatId, private val config: ParticleGenerationAttachmentConfig) {

    private var jointReference0 = 0
    private var jointReference1 = 0

    init {
        resolveExtendedJoints()
    }

    fun updateAssociatedPosition(genAssociatedPosition: Vector3f, elapsedFrames: Float) {
        val association = config.association

        when (config.attachType) {
            AttachType.None -> {
            }
            AttachType.Sun -> {
                genAssociatedPosition.copyFrom(EnvironmentManager.getSunPosition())
                genAssociatedPosition += CameraReference.getInstance().getPosition()

                val areaTransform = SceneManager.getCurrentScene().getAreaTransform()
                if (areaTransform != null) { areaTransform.inverseTransform.transformInPlace(genAssociatedPosition, w = 0f) }
            }
            AttachType.Moon -> {
                genAssociatedPosition.copyFrom(EnvironmentManager.getMoonPosition())
                genAssociatedPosition += CameraReference.getInstance().getPosition()

                val areaTransform = SceneManager.getCurrentScene().getAreaTransform()
                if (areaTransform != null) { areaTransform.inverseTransform.transformInPlace(genAssociatedPosition, w = 0f) }
            }
            AttachType.SourceActor, AttachType.TargetActor, AttachType.TargetActorSourceFacing, AttachType.SourceActorWeapon, AttachType.SourceActorTargetFacing -> {
                if (isWorldSpaceParticleInShipScene()) {
                    // To simplify the world-space -> ship-space transform, just use the facing matrix
                    return
                }

                if (association !is ActorAssociation) {
                    OnceLogger.error("[$datId] ${config.attachType} has non-actor association: ${association::class.simpleName}")
                    return
                }

                // TODO it's not supposed to be an instant update, but most effects are so fast that it doesn't really matter
                val sourceActor = association.actor
                val targetActor = ActorManager[association.context.primaryTargetId] ?: return

                val (actor, isTarget) = if (config.attachType == AttachType.SourceActor || config.attachType == AttachType.SourceActorWeapon || config.attachType == AttachType.SourceActorTargetFacing) {
                    (sourceActor to false)
                } else {
                    (targetActor to true)
                }

                val actorPosition = association.context.getActorPosition(actor.id, isTarget = isTarget) ?: return
                genAssociatedPosition.copyFrom(actorPosition)

                val jointRefIdx = when (config.attachType) {
                    AttachType.SourceActor -> jointReference0
                    AttachType.TargetActor -> jointReference1
                    AttachType.TargetActorSourceFacing -> jointReference1
                    AttachType.SourceActorWeapon -> jointReference0
                    AttachType.SourceActorTargetFacing -> jointReference0
                    else -> { throw IllegalStateException("What") }
                }

                genAssociatedPosition += getJointPosition(actor, jointRefIdx)
            }
            AttachType.SourceToTargetBasis -> {
                association as ActorAssociation
                val actorPosition = association.context.getActorPosition(association.actor.id) ?: return
                genAssociatedPosition.copyFrom(actorPosition)

                genAssociatedPosition += getJointPosition(association.actor, jointReference0)
            }
            AttachType.TargetToSourceBasis -> {
                association as ActorAssociation
                val actorPosition = association.context.getActorPosition(association.context.primaryTargetId, isTarget = true) ?: return
                genAssociatedPosition.copyFrom(actorPosition)

                val target = ActorManager[association.context.primaryTargetId] ?: return
                genAssociatedPosition += getJointPosition(target, jointReference1)
            }
            AttachType.ZoneActor0xA, AttachType.ZoneActor0xB, AttachType.ZoneActor0xC -> {
                val actor = if (association is ActorAssociation) {
                    association.actor
                } else {
                    OnceLogger.warn("[$datId] ${config.attachType} has non-actor association: ${association::class.simpleName}")
                    return
                }

                val actorEffectId = actor.getNpcInfo()?.datId

                if (actorEffectId == null) {
                    genAssociatedPosition.copyFrom(actor.displayPosition)
                    genAssociatedPosition += getJointPosition(actor, jointReference0)
                    return
                }

                val zone = SceneManager.getCurrentScene().getMainArea().getZoneResource()
                val obj = zone.meshesByEffectLink[actorEffectId] ?: return
                genAssociatedPosition.copyFrom(obj.position)
            }
        }
    }

    fun updateAssociatedFacing(genAssociatedRotation: Matrix4f, elapsedFrames: Float) {
        val association = config.association

        when (config.attachType) {
            AttachType.None -> {
            }
            AttachType.SourceActor, AttachType.TargetActor, AttachType.TargetActorSourceFacing, AttachType.SourceActorTargetFacing -> {
                if (isWorldSpaceParticleInShipScene()) {
                    association as ZoneAssociation
                    val areaTransform = SceneManager.getCurrentScene().getAreaTransform(association.area)
                    if (areaTransform == null) {
                        OnceLogger.warn("[$datId] It's not a ship scene?")
                        return
                    }

                    genAssociatedRotation.copyFrom(areaTransform.inverseTransform)
                    return
                }

                if (association !is ActorAssociation) {
                    OnceLogger.error("[$datId] ${config.attachType} has non-actor association: ${association::class.simpleName}")
                    return
                }

                // TODO it's not supposed to be an instant update, but most effects are so fast that it doesn't really matter
                val sourceActor = association.actor
                val targetActor = ActorManager[association.context.primaryTargetId] ?: return

                genAssociatedRotation.identity()

                if (config.attachType == AttachType.SourceActor || config.attachType == AttachType.TargetActorSourceFacing) {
                    val facingDir = association.context.getActorFacingDir(sourceActor.id) ?: 0f
                    genAssociatedRotation.rotateYInPlace(facingDir)
                } else if (config.attachType == AttachType.TargetActor && config.attachSourceOriented) {
                    val direction = getActorToActorWorldSpaceDirection().normalizeInPlace()
                    val theta = -atan2(direction.z, direction.x)
                    genAssociatedRotation.rotateYInPlace(theta)
                } else if (config.attachType == AttachType.TargetActor || config.attachType == AttachType.SourceActorTargetFacing) {
                    val facingDir = association.context.getActorFacingDir(targetActor.id) ?: 0f
                    genAssociatedRotation.rotateYInPlace(facingDir)
                }
            }
            AttachType.SourceActorWeapon -> {
                association as ActorAssociation
                val actor = association.actor

                val actorModel = actor.actorModel ?: return
                val skeleton = actorModel.getSkeleton()
                if (skeleton == null) {
                    genAssociatedRotation.identity().rotateYInPlace(actor.displayFacingDir)
                    return
                }

                val jointRefIdx = jointReference0
                val joint = skeleton.getStandardJoint(jointRefIdx)
                genAssociatedRotation.copyUpperLeft(skeleton.joints[joint.index].currentTransform)
            }
            AttachType.Sun, AttachType.Moon -> {
            }
            AttachType.SourceToTargetBasis, AttachType.TargetToSourceBasis -> {
                genAssociatedRotation.copyFrom(getActorToActorRotationTransform())
            }
            AttachType.ZoneActor0xA, AttachType.ZoneActor0xB, AttachType.ZoneActor0xC -> {
                val actor = if (association is ActorAssociation) {
                    association.actor
                } else {
                    OnceLogger.warn("[$datId] ${config.attachType} has non-actor association: ${association::class.simpleName}")
                    return
                }

                val actorEffectLink = actor.getNpcInfo()?.datId

                val rotation = if (actor.isDoor()) {
                    val zone = SceneManager.getCurrentScene().getMainArea().getZoneResource()
                    val obj = zone.meshesByEffectLink[actorEffectLink] ?: return
                    Vector3f().copyFrom(obj.rotation)
                } else {
                    genAssociatedRotation.identity()
                    val facingDir = association.context.getActorFacingDir(actor.id) ?: 0f
                    Vector3f(0f, facingDir, 0f)
                }

                if (config.attachType == AttachType.ZoneActor0xB || config.attachType == AttachType.ZoneActor0xC) {
                    // This is wrong - the x-rotation and z-rotation contribute to the y-rotation using a complex formula.
                    // However, these attach-types are very rare, and this works well enough for the existing cases.
                    rotation.y -= 0.5f * PI_f + rotation.x
                    rotation.x = 0f
                    rotation.z = 0f
                }

                genAssociatedRotation.copyFrom(Matrix4f().rotateZYXInPlace(rotation))
            }
        }
    }

    private fun getActorToActorRotationTransform(): Matrix4f {
        val direction = getActorToActorWorldSpaceDirection()
        if (direction.magnitudeSquare() == 0f) { return Matrix4f() }

        direction.normalizeInPlace()
        val left = (direction.cross(Vector3f.UP)).normalize()

        val t = Matrix4f()
        t.changeOfBasisWithoutTranslate(direction, Vector3f.Y, left)
        return t
    }

    private fun getJointPosition(actor: Actor, jointIndex: Int): Vector3f {
        val association = config.association as ActorAssociation
        return association.context.getJointPosition(actor, jointIndex)
    }

    fun getActorToActorScaleTransform(): Matrix4f {
        val direction = getActorToActorWorldSpaceDirection()
        return Matrix4f().scaleInPlace(direction.magnitude(), 1f, 1f)
    }

    private fun getActorToActorWorldSpaceDirection(): Vector3f {
        val association = config.association as ActorAssociation

        val sourcePosition = association.context.getActorPosition(association.actor.id) ?: return Vector3f.ZERO
        val sourceJointPosition = sourcePosition + getJointPosition(association.actor, jointReference0)

        val targetPosition = association.context.getActorPosition(association.context.primaryTargetId, isTarget = true) ?: return Vector3f.ZERO
        val target = ActorManager[association.context.primaryTargetId] ?: return Vector3f.ZERO
        val targetJointPosition = targetPosition + getJointPosition(target, jointReference1)

        return if (config.attachType == AttachType.SourceToTargetBasis) {
            targetJointPosition - sourceJointPosition
        } else if (config.attachType == AttachType.TargetToSourceBasis) {
            sourceJointPosition - targetJointPosition
        } else if (config.attachSourceOriented) {
            sourceJointPosition - targetJointPosition
        } else {
            throw IllegalStateException("Can't determine direction")
        }
    }

    private fun resolveExtendedJoints() {
        jointReference0 = config.sourceJoint
        jointReference1 = config.targetJoint

        if (config.association is ActorAssociation) {
            val jointRemapping = config.association.context.jointOverride
            jointReference0 = jointRemapping[jointReference0] ?: jointReference0
            jointReference1 = jointRemapping[jointReference1] ?: jointReference1
        }

        if (config.attachType == AttachType.SourceActorWeapon) {
            if (config.association is ActorAssociation && config.association.actor.actorModel?.model !is PcModel) {
                // The remapping function only seems to apply to PC-type models?
                // Needed for NPC 0x9A4 [Altana]
                return
            }

            jointReference0 = when (config.sourceJoint) {
                32,34,54 -> 126       // Sub-hand Naegling, Tauret, Hedron Dagger, etc
                31,33,35,55 -> 127    // Main-hand + Ohakari ^
                36 -> 100   // Aphelion (left)
                37 -> 101   // Aphelion (right)
                56 -> 102   // Nandaka
                57 -> 103   // Kaja Chopper
                58 -> 104   // Wroth Scythe, Escritorio
                59 -> 105   // Sub-hand Ikarigiri
                60 -> 106   // Main-hand Ikarigiri
                else -> config.sourceJoint
            }
            return
        }

        // This is needed for mounts' footstep effects; maybe it should snap to the "impact height" instead?
        if (config.sourceJoint in 52..53) { jointReference0 = 0 }

        if (isNearestJointSnapshot(config.sourceJoint) || isNearestJointSnapshot(config.targetJoint)) {
            resolveNearestJointSnapshot()
        }
    }

    private fun resolveNearestJointSnapshot() {
        if (config.association !is ActorAssociation) {
            OnceLogger.warn("[$datId] Uses extended joints, but isn't an actor?")
            return
        }

        val sourceActor = config.association.actor
        val targetActor = ActorManager[config.association.context.primaryTargetId] ?: return

        if (isNearestJointSnapshot(config.sourceJoint)) {
            jointReference0 = config.association.context.getStandardJointExtended(config.sourceJoint, targetActor, sourceActor)
        }

        if (isNearestJointSnapshot(config.targetJoint)) {
            jointReference1 = config.association.context.getStandardJointExtended(config.targetJoint, sourceActor, targetActor)
        }
    }

    private fun isNearestJointSnapshot(jointIndex: Int): Boolean {
        return jointIndex in 49 .. 51
    }

    // For ship-scenes, some particles are specified in ship-space, and some in world-space. This seems to be how they're differentiated?
    // However, this setup is also sometimes used for zone particles that are not related to ships at all (ex: the volcano in [Wajaom Woodlands] and [Bhaflau Thickets]).
    fun isWorldSpaceParticleInShipScene(): Boolean {
        return config.association is ZoneAssociation && config.attachType == AttachType.TargetActorSourceFacing
    }

}