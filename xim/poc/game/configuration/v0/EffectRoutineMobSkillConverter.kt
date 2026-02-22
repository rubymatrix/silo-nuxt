package xim.poc.game.configuration.v0

import xim.poc.ActorAssociation
import xim.poc.MobSkillToBlueMagicOverride
import xim.resource.*

object EffectRoutineMobSkillConverter {

    fun apply(
        effectRoutineInstance: EffectRoutineInstance,
        initialSequence: EffectRoutineInstance.EffectSequence,
        mobSkillToBlueMagicOverride: MobSkillToBlueMagicOverride,
    ) {
        val association = effectRoutineInstance.effectAssociation as? ActorAssociation ?: return

        association.context.jointOverride.putAll(mobSkillToBlueMagicOverride.standardJointRemap)
        val movementLockOverride = mobSkillToBlueMagicOverride.movementLockDuration

        initialSequence.prependCustomEffect(CustomRoutine(delay = 0) {
            effectRoutineInstance.skipTypes += setOf(SkeletonAnimationRoutine::class, ActorJumpRoutine::class, ActorFadeRoutine::class)
            if (movementLockOverride != null) {
                effectRoutineInstance.skipTypes += setOf(MovementLockEffect::class, AnimationLockEffect::class, FacingLockEffect::class)
            }
        })

        initialSequence.prependCustomEffect(SkeletonAnimationRoutine(delay = 0, duration = 64, id = DatId("ma2?"), maxLoops = 0, transitionInTime = 20, transitionOutTime = 20))
        initialSequence.prependCustomEffect(LinkedEffectRoutine(delay = 0, duration = 0, id = DatId.invokeBlueMagic, useTarget = false, blocking = true))

        if (movementLockOverride != null) {
            initialSequence.prependCustomEffect(AnimationLockEffect(delay = 0, duration = movementLockOverride))
            initialSequence.prependCustomEffect(FacingLockEffect(delay = 0, duration = movementLockOverride))
            initialSequence.prependCustomEffect(MovementLockEffect(delay = 0, duration = movementLockOverride))
        }

        initialSequence.appendCustomEffect(TransitionToIdleEffect(delay = 0, transitionTime = 30f))
    }

}