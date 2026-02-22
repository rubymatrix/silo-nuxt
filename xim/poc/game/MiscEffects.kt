package xim.poc.game

import xim.poc.*
import xim.poc.browser.DatLoader
import xim.resource.DatId
import xim.resource.EffectRoutineResource
import xim.resource.table.FileTableManager
import xim.resource.table.MobSkillInfoTable
import xim.util.OnceLogger

enum class ExclamationProc(val mobSkillAnimationIndex: Int) {
    Red(0x70E),
    Yellow(0x70F),
    Blue(0x710),
    White(0x79A)
}

object MiscEffects {

    enum class Effect(val fileTableIndex: Int) {
        LevelDown(0xCED),
        LevelUp(0xCEE),
        NewChallenge(0xE86),
        JobPoint(0xE90),
        MasterLevelUp(0xEE8),
        MasterLevelDown(0xEE9),
        ChangeJobs(0x13AE),
    }

    private val skillChainAnimations = mapOf(
        SkillChainAttribute.Compression to 0xCC3,
        SkillChainAttribute.Impaction to 0xCC4,
        SkillChainAttribute.Liquefaction to 0xCC5,
        SkillChainAttribute.Detonation to 0xCC6,
        SkillChainAttribute.Reverberation to 0xCC7,
        SkillChainAttribute.Transfixion to 0xCC8,
        SkillChainAttribute.Scission to 0xCC9,
        SkillChainAttribute.Induration to 0xCCA,

        SkillChainAttribute.Gravitation to 0xCCB,
        SkillChainAttribute.Fragmentation to 0xCCC,
        SkillChainAttribute.Distortion to 0xCCD,
        SkillChainAttribute.Fusion to 0xCCE,

        SkillChainAttribute.Light to 0xCCF,
        SkillChainAttribute.Darkness to 0xCD0,

        SkillChainAttribute.Light2 to 0xCCF,
        SkillChainAttribute.Darkness2 to 0xCD0,

        SkillChainAttribute.Radiance to 0xEE5,
        SkillChainAttribute.Umbra to 0xEE6,
    )

    fun playSkillChain(source: ActorId?, target: ActorId? = source, attribute: SkillChainAttribute) {
        val sourceActor = ActorManager[source] ?: return
        val targetActor = ActorManager[target] ?: return
        val animation = skillChainAnimations[attribute] ?: return
        playEffect(sourceActor, targetActor, animation, DatId.main)
    }

    fun playExclamationProc(actorId: ActorId, exclamationProc: ExclamationProc) {
        val actor = ActorManager[actorId] ?: return
        playExclamationProc(actor, exclamationProc)
    }

    fun playExclamationProc(actor: Actor, exclamationProc: ExclamationProc) {
        val path = MobSkillInfoTable.getAnimationPath(exclamationProc.mobSkillAnimationIndex) ?: return
        playEffect(actor, actor, path, DatId.main)
    }

    fun playEffect(actorId: ActorId, effect: Effect) {
        val actor = ActorManager[actorId] ?: return
        playEffect(actor, actor, effect)
    }

    fun playEffect(source: Actor?, target: Actor? = source, effect: Effect, effectId: DatId = DatId.main) {
        if (source == null) { return }
        playEffect(source, target ?: source, effect.fileTableIndex, effectId)
    }

    fun playEffect(actor: Actor, target: Actor, fileTableIndex: Int, effectId: DatId) {
        val path = FileTableManager.getFilePath(fileTableIndex) ?: return
        playEffect(actor, target, path, effectId)
    }

    private fun playEffect(actor: Actor, target: Actor, resourceName: String, effectId: DatId) {
        DatLoader.load(resourceName).onReady {
            val resource = it.getAsResource().getNullableChildRecursivelyAs(effectId, EffectRoutineResource::class)
            if (resource != null) {
                EffectManager.registerActorRoutine(actor, ActorContext(actor.id, primaryTargetId = target.id), resource)
            } else {
                OnceLogger.warn("Couldn't find $effectId in $resourceName")
            }
        }
    }

}