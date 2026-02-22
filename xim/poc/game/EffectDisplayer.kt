package xim.poc.game

import xim.poc.ActorContext
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.RoutineOptions
import xim.poc.audio.AudioManager
import xim.poc.browser.DatLoader
import xim.poc.browser.ParserContext
import xim.poc.game.GameEngine.displayName
import xim.poc.game.configuration.constants.*
import xim.resource.DatId
import xim.resource.EffectRoutineResource
import xim.resource.InventoryItems.toItemInfo
import xim.resource.SoundPointerResource
import xim.resource.table.*
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import xim.resource.table.MobSkillInfoTable.toMobSkillInfo
import xim.util.OnceLogger

object EffectDisplayer {

    fun displaySkill(skillId: SkillId, castingContext: CastingStateContext?, sourceId: ActorId, primaryTargetId: ActorId, allTargetIds: List<ActorId>, actionContext: AttackContexts) {
        if (skillId is MobSkillId) {
            prepareMobSkillAnimation(sourceId)
        }

        val resourcePath = when (skillId) {
            is SpellSkillId -> getSpellAnimationPath(skillId)
            is MobSkillId -> getMobSkillAnimationPath(skillId)
            is AbilitySkillId -> getAbilityAnimationPath(skillId, sourceId)
            is ItemSkillId -> getItemAnimationPath(skillId)
            is RangedAttackSkillId -> return // Displayed as an auto-attack
        }

        if (resourcePath == null) {
            OnceLogger.warn("No animation for skill: $skillId / ${skillId.displayName()}")
            return
        }

        val context = displayMain(datPath = resourcePath, sourceId = sourceId, primaryTargetId = primaryTargetId, allTargets = allTargetIds, attackContexts = actionContext)

        context?.targetAoeCenter = castingContext?.targetAoeCenter
        context?.movementLockOverride = castingContext?.movementLockOverride
        context?.skillId = skillId
    }

    private fun getSpellAnimationPath(skillId: SpellSkillId): String? {
        val animationId = SpellAnimationTable[skillId.id]
        return FileTableManager.getFilePath(animationId)
    }

    private fun getAbilityAnimationPath(abilitySkillId: AbilitySkillId, sourceId: ActorId): String? {
        val source = ActorStateManager[sourceId] ?: return null
        val race = source.getCurrentLook().race

        val abilityInfo = abilitySkillId.toAbilityInfo()

        val animationId = if (abilitySkillId == skillDoubleUp_635) {
            val linkedAbility = source.getStatusEffect(StatusEffect.DoubleUpChance)?.linkedSkillId ?: return null
            if (linkedAbility !is AbilitySkillId) { return null }
            AbilityTable.getAnimationId(linkedAbility.toAbilityInfo(), race)
        } else if (source.isPet()) {
            PetSkillTable.getAnimationId(abilityInfo)
        } else {
            AbilityTable.getAnimationId(abilityInfo, race)
        }

        return FileTableManager.getFilePath(animationId)
    }

    private fun prepareMobSkillAnimation(sourceId: ActorId) {
        // TODO: Some mob-skills don't invoke [stnm], causing the charging effect to continue indefinitely.
        // Is there another op-code that has a side effect of doing the same thing?
        val source = ActorManager[sourceId] ?: return
        source.playRoutine(DatId("stnm"))
    }

    private fun getMobSkillAnimationPath(skillId: MobSkillId): String? {
        return MobSkillInfoTable.getAnimationPath(skillId.toMobSkillInfo())
    }

    private fun getItemAnimationPath(skillId: ItemSkillId): String? {
        return ItemAnimationTable.getAnimationPath(skillId.toItemInfo())
    }

    fun displayMain(datPath: String, sourceId: ActorId, primaryTargetId: ActorId, attackContext: AttackContext): ActorContext? {
        return displayMain(datPath = datPath, sourceId = sourceId, primaryTargetId = primaryTargetId, attackContexts = AttackContexts.single(primaryTargetId, attackContext))
    }

    private fun displayMain(datPath: String, sourceId: ActorId, primaryTargetId: ActorId, allTargets: List<ActorId> = listOf(primaryTargetId), attackContexts: AttackContexts): ActorContext? {
        val source = ActorManager[sourceId] ?: return null
        val target = ActorManager[primaryTargetId] ?: return null

        val context = ActorContext(originalActor = source.id, primaryTargetId = target.id, allTargetIds = allTargets, attackContexts = attackContexts)
        val datWrapper = DatLoader.load(datPath, parserContext = ParserContext.optionalResource)

        source.enqueueRoutine(context, options = RoutineOptions(highPriority = true)) {
            datWrapper.getAsResourceIfReady()?.getNullableChildRecursivelyAs(DatId.main, EffectRoutineResource::class)
        }

        return context
    }

    fun preloadSkillResources(vararg skills: List<SkillId>) {
        for (skillSet in skills) {
            skillSet.forEach(this::preloadSkillResource)
        }
    }

    fun preloadSkillResource(skill: SkillId) {
        val animationPath = (when(skill) {
            is AbilitySkillId -> null
            is RangedAttackSkillId -> null
            is ItemSkillId -> getItemAnimationPath(skill)
            is MobSkillId -> getMobSkillAnimationPath(skill)
            is SpellSkillId -> getSpellAnimationPath(skill)
        }) ?: return

        DatLoader.load(animationPath).onReady {
            it.getAsResource().collectByTypeRecursive(SoundPointerResource::class).forEach(this::preloadSoundEffects)
        }
    }

    private fun preloadSoundEffects(soundPointerResource: SoundPointerResource) {
        AudioManager.preloadSoundEffect(soundPointerResource)
    }

}