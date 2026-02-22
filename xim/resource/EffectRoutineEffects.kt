package xim.resource

import xim.math.Axis
import xim.math.Vector3f
import xim.poc.gl.ByteColor
import xim.poc.gl.PointLight

sealed class Effect {
    abstract val delay: Int
}

data class ParticleGeneratorRoutine(
    override val delay: Int,
    val duration: Int,
    val id: DatId,
): Effect()

data class LinkedEffectRoutine(
    override val delay: Int,
    val duration: Int,
    val id: DatId,
    val useTarget: Boolean = false,
    val blocking: Boolean = false,
): Effect()

enum class SoundEffectTarget {
    Source,
    Target,
    NearestTarget,
    Global,
    PlayerOnly,
}

data class SoundEffectRoutine(
    override val delay: Int,
    val duration: Int,
    val id: DatId,
    val farDistance: Float? = null,
    val nearDistance: Float? = null,
    val target: SoundEffectTarget = SoundEffectTarget.Source,
): Effect()

data class SkeletonAnimationRoutine(
    override val delay: Int,
    val duration: Int,
    val id: DatId,
    val maxLoops: Int,
    val transitionInTime: Int,
    val transitionOutTime: Int,
): Effect()

data class MovementLockEffect(
    override val delay: Int,
    val duration: Int,
): Effect()

data class AnimationLockEffect(
    override val delay: Int,
    val duration: Int,
): Effect()

data class FacingLockEffect(
    override val delay: Int,
    val duration: Int,
): Effect()

data class JointSnapshotEffect(
    override val delay: Int,
    val snapshot: Boolean,
) : Effect()

data class TimeBasedReplayRoutine(
    override val delay: Int,
    val duration: Int,

    val timeOfDayStart: Int,
    val timeOfDayEnd: Int,
    val loopInterval: Int,
) : Effect()

data class ToggleBroadcastEffect(
    override val delay: Int,
    val duration: Int,
    val useBroadcast: Boolean,
): Effect()

data class StartLoopRoutine(
    override val delay: Int,
    val duration: Int,
    val refId: DatId,
): Effect()

data class EndLoopRoutine(
    override val delay: Int,
    val duration: Int,
    val refId: DatId,
): Effect()

data class ControlFlowCondition(
    override val delay: Int,
    val duration: Int,
    val arg0: Int,
    val arg1: Int,
    val input: Int?
): Effect()

data class ControlFlowBlock (
    override val delay: Int,
    val duration: Int,
    val openBlock: Boolean,
): Effect()

data class ControlFlowBranch (
    override val delay: Int,
    val duration: Int,
    val branchType: Boolean,
): Effect()

data class RandomChildRoutine (
    override val delay: Int,
    val duration: Int,
    val children: ArrayList<Effect> = ArrayList()
): Effect()

sealed class ModelTransformEffect: Effect() {
    abstract val finalValue: Vector3f
    abstract val index: Int
    abstract val duration: Int
}

data class ModelTranslationRoutine (
    override val delay: Int,
    override val duration: Int,
    override val finalValue: Vector3f,
    override val index: Int,
): ModelTransformEffect()

data class ModelRotationRoutine (
    override val delay: Int,
    override val duration: Int,
    override val finalValue: Vector3f,
    override val index: Int,
): ModelTransformEffect()

data class ParticleDampenRoutine (
    override val delay: Int,
    val duration: Int,
    val id: DatId,
) : Effect()

data class ActorFadeRoutine (
    override val delay: Int,
    val duration: Int,
    val endColor: ByteColor,
    val useTarget: Boolean,
) : Effect()

data class StartRoutineMarker (
    override val delay: Int,
    val duration: Int,
) : Effect()

data class EndRoutineMarker (
    override val delay: Int,
    val duration: Int,
) : Effect()

data class ActorWrapUvTranslation(
    override val delay: Int,
    val duration: Int,
    val endValue: Float,
    val uv: Axis,
    val useTarget: Boolean = false,
) : Effect()

data class ActorWrapColor(
    override val delay: Int,
    val duration: Int,
    val endValue: ByteColor,
    val useTarget: Boolean = false,
) : Effect()

data class ActorWrapTexture(
    override val delay: Int,
    val duration: Int,
    val textureLink: DatLink<TextureResource>,
    val flags: Int,
    val useTarget: Boolean = false,
): Effect()

data class ActorJumpRoutine(
    override val delay: Int,
    val duration: Int,
    val targetJoint: Int,
): Effect()

data class StartRangedAnimationRoutine(
    override val delay: Int,
    val duration: Int,
    val rangeSubtype: Int,
): Effect()

data class FinishRangedAnimationRoutine(
    override val delay: Int,
    val duration: Int,
    val rangeSubtype: Int,
): Effect()

data class StopParticleGeneratorRoutine(
    override val delay: Int,
    val id: DatId,
): Effect()

data class StopRoutineEffect(
    override val delay: Int,
    val id: DatId
) : Effect()

data class DisplayRangedModelRoutine(
    override val delay: Int,
    val duration: Int
): Effect()

data class TransitionParticleEffect(
    override val delay: Int,
    val duration: Int,
    val stopEffect: DatId,
    val startEffect: DatId,
): Effect()

data class TransitionToIdleEffect(
    override val delay: Int,
    val transitionTime: Float,
): Effect()

data class DualWieldEngageRoutine(
    override val delay: Int,
    val inOutFlag: Int,
    val index: Int,
) : Effect()

data class DamageCallbackRoutine(
    override val delay: Int
): Effect()

data class SetModelVisibilityRoutine(
    override val delay: Int,
    val hidden: Boolean,
    val slot: Int,
    val ifEngaged: Boolean,
): Effect()

data class SpellEffect(
    override val delay: Int,
    val spellIndex: Int
): Effect()

data class ForwardDisplacementEffect(
    override val delay: Int,
    val duration: Int,
    val displacement: Float
): Effect()

data class PointLightInterpolationEffect(
    override val delay: Int,
    val duration: Int,
    val particleGenId: DatId,
    val endValue: Float,
    val theta: Boolean,
): Effect()

data class ActorPositionSnapshotEffect(
    override val delay: Int,
): Effect()

data class ToggleModelVisibilityRoutine(
    override val delay: Int,
    val hidden: Boolean,
    val slot: Int,
): Effect()

data class FlinchRoutine(
    override val delay: Int,
    val animationDuration: Float,
    val useTarget: Boolean,
): Effect()

data class AdjustAnimationModeRoutine(
    override val delay: Int,
    val mode: Int,
    val value: Int,
): Effect()

data class DisplayDeadRoutine(
    override val delay: Int
): Effect()

data class KnockBackRoutine(
    override val delay: Int,
    val animationDuration: Float,
): Effect()

data class AttackBlockedRoutine(
    override val delay: Int,
    val blockType: Int,
    val animationDuration: Float,
): Effect()

data class AttackCounteredRoutine(
    override val delay: Int,
): Effect()

data class LoadBaseModelRoutine(
    override val delay: Int,
    val modelId: Int,
): Effect()

data class WeaponTraceRoutine(
    override val delay: Int,
    val duration: Int,
    val resourceId: DatLink<WeaponTraceResource>,
    val color1: ByteColor,
    val color2: ByteColor,
    val squeeze1: Float,
    val squeeze2: Float,
): Effect()

data class FollowPointsRoutine(
    override val delay: Int,
    val duration: Int,
    val resourceId: DatLink<PointListResource>,
    val rotation: Float,
    val flags0: Int,
    val flags1: Int,
): Effect()

data class NotImplementedRoutine(
    override val delay: Int,
    val duration: Int,
) : Effect()

data class DelayRoutine(
    override val delay: Int,
) : Effect()

data class CustomRoutine(
    override val delay: Int,
    val callback: () -> Unit,
): Effect()