package xim.poc.game.configuration.v0

import xim.math.Vector3f
import xim.poc.*
import xim.poc.audio.BackgroundMusicResponse
import xim.poc.browser.LocalStorage
import xim.poc.game.*
import xim.poc.game.EffectDisplayer.preloadSkillResources
import xim.poc.game.SkillChainAttribute.*
import xim.poc.game.SynthesisResultType.HighQuality
import xim.poc.game.SynthesisResultType.NormalQuality
import xim.poc.game.actor.components.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.DamageCalculator.getAutoAttackTypeResult
import xim.poc.game.configuration.v0.DamageCalculator.getH2HAutoAttackResult
import xim.poc.game.configuration.v0.DamageCalculator.getWeaponPowerAndDelay
import xim.poc.game.configuration.v0.DamageCalculator.processWeaponSwing
import xim.poc.game.configuration.v0.DamageCalculator.rollAgainst
import xim.poc.game.configuration.v0.GameV0Helpers.createPlayer
import xim.poc.game.configuration.v0.GameV0Helpers.generateMonsterInventory
import xim.poc.game.configuration.v0.GameV0Helpers.getAbilityRange
import xim.poc.game.configuration.v0.GameV0Helpers.getAbilityRecast
import xim.poc.game.configuration.v0.GameV0Helpers.getBaseAugmentPointRankGain
import xim.poc.game.configuration.v0.GameV0Helpers.getCastingRangeInfo
import xim.poc.game.configuration.v0.GameV0Helpers.getItemCastTime
import xim.poc.game.configuration.v0.GameV0Helpers.getItemDescriptionInternal
import xim.poc.game.configuration.v0.GameV0Helpers.getItemLevel
import xim.poc.game.configuration.v0.GameV0Helpers.getItemRange
import xim.poc.game.configuration.v0.GameV0Helpers.getMobSkillCastTime
import xim.poc.game.configuration.v0.GameV0Helpers.getMobSkillRange
import xim.poc.game.configuration.v0.GameV0Helpers.getPlayerLevelStatMultiplier
import xim.poc.game.configuration.v0.GameV0Helpers.getRangedAttackCastTime
import xim.poc.game.configuration.v0.GameV0Helpers.getSkillCost
import xim.poc.game.configuration.v0.GameV0Helpers.getSpellCastTime
import xim.poc.game.configuration.v0.GameV0Helpers.getSpellLockTime
import xim.poc.game.configuration.v0.GameV0Helpers.getSpellRecast
import xim.poc.game.configuration.v0.GameV0Helpers.getSynthesisFailureChance
import xim.poc.game.configuration.v0.GameV0Helpers.getUsedSkillCost
import xim.poc.game.configuration.v0.GameV0Helpers.hasAnyEnmity
import xim.poc.game.configuration.v0.GameV0Helpers.isNpcInteractionValid
import xim.poc.game.configuration.v0.ItemAugmentDefinitions.getInternalQuality
import xim.poc.game.configuration.v0.ItemAugmentDefinitions.maxPossibleRankLevel
import xim.poc.game.configuration.v0.abyssea.AbysseaConfigurations
import xim.poc.game.configuration.v0.abyssea.AbysseaZoneInstance
import xim.poc.game.configuration.v0.behaviors.PlayerBehavior
import xim.poc.game.configuration.v0.behaviors.PlayerBehaviorId
import xim.poc.game.configuration.v0.behaviors.V0DefaultMonsterBehavior
import xim.poc.game.configuration.v0.behaviors.V0MonsterController
import xim.poc.game.configuration.v0.escha.EschaConfigurations
import xim.poc.game.configuration.v0.escha.EschaZitahZoneDefinition
import xim.poc.game.configuration.v0.escha.EschaZoneInstance
import xim.poc.game.configuration.v0.events.ActorFallsToGroundEvent
import xim.poc.game.configuration.v0.events.ActorLearnSpellEvent
import xim.poc.game.configuration.v0.interactions.DynamicNpcInteractionManager
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.configuration.v0.mining.MiningZoneConfigurations
import xim.poc.game.configuration.v0.mining.MiningZoneInstance
import xim.poc.game.configuration.v0.mining.isGatheringNode
import xim.poc.game.configuration.v0.navigation.BattleLocationNavigator
import xim.poc.game.configuration.v0.paradox.CloisterZoneInstance
import xim.poc.game.configuration.v0.paradox.ParadoxZoneInstance
import xim.poc.game.configuration.v0.synthesis.*
import xim.poc.game.configuration.v0.tower.TowerConfiguration
import xim.poc.game.configuration.v0.zones.BaseCamp
import xim.poc.game.configuration.v0.zones.BaseCampLogic
import xim.poc.game.configuration.v0.zones.SimpleZoneOverrides
import xim.poc.game.event.*
import xim.poc.game.event.AutoAttackSubType.None
import xim.poc.tools.DebugToolsManager
import xim.poc.tools.ZoneConfig
import xim.poc.ui.*
import xim.resource.*
import xim.resource.table.AbilityInfoTable
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import xim.resource.table.AugmentTable
import xim.resource.table.MusicSettings
import xim.resource.table.SpellInfoTable.toSpellInfo
import xim.resource.table.ZoneSettingsTable
import xim.util.Fps
import xim.util.addInPlace
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val gameConfiguration = GameConfiguration(
    gameModeId = "GameV0",
    startingZoneConfig = ZoneConfig(zoneId = BaseCamp.definition.zoneId, startPosition = Vector3f(x=-40.01f,y=1.34f,z=33.87f)),
    debugControlsEnabled = false
)

object GameV0: GameLogic {

    override val configuration = gameConfiguration

    val interactionManager = DynamicNpcInteractionManager()

    private var firstTimeZoning = true
    private var zoneLogic: ZoneLogic? = null

    override fun setup(): List<Event> {
        SimpleZoneOverrides.definitions.forEach { ZoneDefinitionManager += it }
        ZoneDefinitionManager += BaseCamp.definition
        ZoneDefinitionManager += EschaZitahZoneDefinition.definition

        MonsterDefinitions.registerAll(V0MonsterDefinitions.definitions)
        ActorBehaviors.register(V0DefaultMonsterBehavior) { V0MonsterController(it) }

        V0MobSkillDefinitions.register()
        V0CustomSpellSetup.setup()
        V0AbilityDefinitions.register()
        V0SpellDefinitions.register()
        V0ItemDefinitions.register()

        return listOf(createPlayer())
    }

    override fun update(elapsedFrames: Float) {
        zoneLogic?.update(elapsedFrames)
        interactionManager.update(elapsedFrames)
        GameV0SaveStateHelper.autoSave()
        CombatBonusAggregator.clear()
        HelpNotifier.update(elapsedFrames)
        EnmityLookupTable.flip()
        HotbarUi.handleInput()
    }

    override fun drawUi() {
        HotbarUi.draw()
    }

    override fun onUnloadZone(unloadingZone: ZoneConfig) {
        zoneLogic?.cleanUp()
    }

    override fun onChangedZones(zoneConfig: ZoneConfig) {
        if (zoneConfig.zoneId == BaseCamp.definition.zoneId) {
            ActorStateManager.player().getInventory().discardTemporaryItems()
            setZoneLogic { BaseCampLogic() }
        } else if (firstTimeZoning) {
            inferZoneLogicForDebugging(zoneConfig)
        }

        firstTimeZoning = false
    }

    override fun onReturnToHomePoint(actorState: ActorState) {
        executeRepeatCurrentFloor(fullRestore = true)
    }

    override fun invokeNpcInteraction(sourceId: ActorId, npcId: ActorId) {
        val sourceState = ActorStateManager[sourceId] ?: return
        val npcState = ActorStateManager[npcId] ?: return

        val interaction = interactionManager.getInteraction(npcId)

        if (interaction != null && isNpcInteractionValid(interaction, sourceState, npcState)) {
            GameClient.submitPlayerDisengage()
            interaction.onInteraction(npcId)
        }
    }

    override fun getActorSpellList(actorId: ActorId): List<SpellSkillId> {
        val actor = ActorStateManager[actorId] ?: return emptyList()

        return if (actor.monsterId != null) {
            MonsterDefinitions[actor.monsterId].mobSpells
        } else {
            actor.getLearnedSpells().equippedSpells.filter { it != spellNull_0 }
        }
    }

    override fun getActorAbilityList(actorId: ActorId, abilityType: AbilityType): List<AbilitySkillId> {
        if (abilityType == AbilityType.JobTrait) {
            return if (GameTower.hasClearedFloor(10)) { listOf(skillDualWield_1554) } else { emptyList() }
        }

        val actorState = ActorStateManager[actorId] ?: return emptyList()
        val mainWeapon = actorState.getEquipment(EquipSlot.Main) ?: return emptyList()
        return ItemDefinitions[mainWeapon].abilities
    }

    override fun getActorCombatStats(actorId: ActorId): CombatStats {
        val actor = ActorStateManager[actorId] ?: return CombatStats.defaultBaseStats
        val bonuses = CombatBonusAggregator[actor]

        var stats = if (actor.type == ActorType.Pc) {
            val scaledStats = CombatStats.defaultBaseStats * getPlayerLevelStatMultiplier(actor.getMainJobLevel().level)
            scaledStats.copy(maxMp = scaledStats.maxMp.coerceAtMost(100))
        } else if (actor.isGatheringNode()) {
            val maxHp = getCurrentMiningZoneInstance()?.getAttemptCount() ?: 3
            CombatStats.defaultBaseStats.copy(maxHp = maxHp)
        } else if (actor.behaviorController is V0MonsterController) {
            actor.behaviorController.getCombatStats()
        } else {
            CombatStats.defaultBaseStats
        }

        stats += bonuses.additiveStats.build()
        for ((stat, value) in bonuses.multiplicativeStats) { stats = stats.multiply(stat, value) }

        return stats
    }

    override fun getItemDescription(actorId: ActorId, inventoryItem: InventoryItem): InventoryItemDescription {
        return getItemDescriptionInternal(inventoryItem = inventoryItem)
    }

    override fun getAutoAttackResult(attacker: ActorState, defender: ActorState): List<AutoAttackResult> {
        if (attacker.behaviorController is V0MonsterController) {
            return attacker.behaviorController.getAutoAttackTypes().flatMap { getAutoAttackTypeResult(attacker, defender, it) }
        }

        val attacks = if (attacker.isHandToHand()) {
            getH2HAutoAttackResult(attacker, defender)
        } else if (attacker.isDualWield()) {
            getAutoAttackTypeResult(attacker, defender, MainHandAutoAttack()) + getAutoAttackTypeResult(attacker, defender, OffHandAutoAttack)
        } else {
            getAutoAttackTypeResult(attacker, defender, MainHandAutoAttack())
        }

        return attacks.take(8)
    }

    override fun getAutoAttackInterval(attacker: ActorState): Float {
        var totalDelay = 0f

        val (_, mainDelay) = getWeaponPowerAndDelay(attacker, type = MainHandAutoAttack(subType = None)) ?: (0 to 180)
        totalDelay += mainDelay

        val bonuses = CombatBonusAggregator[attacker]
        var attackSpeed = bonuses.haste

        if (attacker.isDualWield()) {
            val (_, subDelay) = getWeaponPowerAndDelay(attacker, type = OffHandAutoAttack) ?: (0 to 0)
            totalDelay += subDelay
            attackSpeed += bonuses.dualWield
        }

        val reduction = speedBaseTerm.pow(attackSpeed.coerceAtMost(80))
        return totalDelay * reduction
    }

    override fun getRangedAttackResult(attacker: ActorState, defender: ActorState): List<AutoAttackResult> {
        return getAutoAttackTypeResult(attacker, defender, RangedAutoAttack).take(1)
    }

    override fun getRangedAttackInterval(attacker: ActorState): Float {
        val (_, delay) = getWeaponPowerAndDelay(attacker, RangedAutoAttack) ?: (0 to 180)
        return delay.toFloat()
    }

    override fun getAugmentRankPointGain(attacker: ActorState, defender: ActorState, inventoryItem: InventoryItem): Int {
        val itemLevel = getItemLevel(inventoryItem)
        val defenderLevel = defender.getMainJobLevel().level

        val rewardBonus = if (defender.monsterId != null) {
            val monsterDefinition = MonsterDefinitions[defender.monsterId]
            monsterDefinition.rpRewardScale ?: monsterDefinition.expRewardScale
        } else {
            1f
        }

        return (rewardBonus * getBaseAugmentPointRankGain(itemLevel = itemLevel, rpSourceLevel = defenderLevel)).roundToInt()
    }

    override fun getAugmentRankPointsNeeded(augment: ItemAugment): Int {
        val needed = AbilityInfoTable.getReinforcementPointsTable().table.entries.getOrNull(augment.rankLevel) ?: return 0
        return needed.coerceAtMost(1000)
    }

    override fun onAugmentRankUp(actorState: ActorState, inventoryItem: InventoryItem) {
        val definition = ItemDefinitions[inventoryItem]
        val augments = inventoryItem.augments ?: return

        if (!definition.dynamicQuality) { return }

        inventoryItem.internalQuality = getInternalQuality(augments.rankLevel)

        val slottedAugments = inventoryItem.slottedAugments
        if (slottedAugments != null) {
            slottedAugments.slots = maxOf(slottedAugments.slots, getNumAugmentSlots(inventoryItem))
        }
    }

    override fun onActorDefeated(source: ActorState, target: ActorState, context: AttackContext?): List<Event> {
        if (!source.isPlayer()) { return emptyList() }

        val monsterId = target.monsterId ?: return emptyList()

        val saveState = GameV0SaveStateHelper.getState()
        saveState.defeatedMonsterCounter.addInPlace(monsterId, 1)

        val zoneEvents = zoneLogic?.onActorDefeated(source, target, context) ?: emptyList()
        val blueMagicReward = V0BlueMagicMonsterRewards[monsterId]?.let { ActorLearnSpellEvent(source.id, it, context) }

        return zoneEvents + listOfNotNull(blueMagicReward)
    }

    override fun getExperiencePointGain(attacker: ActorState, defender: ActorState): Int {
        if (!attacker.isPlayer()) { return 0 }

        val monsterId = defender.monsterId ?: return 0

        val levelDelta = defender.getMainJobLevel().level - attacker.getMainJobLevel().level
        val levelDeltaMultiplier = ((2f + levelDelta) / 2f).coerceAtLeast(0f)

        val monsterDefinition = MonsterDefinitions[monsterId]
        return (30 * levelDeltaMultiplier * monsterDefinition.expRewardScale).roundToInt()
    }

    override fun getExperiencePointsNeeded(currentLevel: Int): Int {
        val baseValue = 10 * AbilityInfoTable.getLevelTable().table.entries[currentLevel]
        return baseValue.coerceAtMost(1000)
    }

    override fun getMaximumLevel(actorState: ActorState): Int {
        return 30
    }

    override fun getActorEffectTickResult(actorState: ActorState): ActorEffectTickResult {
        val bonuses = CombatBonusAggregator[actorState]
        return ActorEffectTickResult(hpDelta = bonuses.regen, mpDelta = bonuses.refresh, tpDelta = bonuses.regain)
    }

    fun getItemReinforcementValues(targetItem: InventoryItem): Map<Int, Int> {
        val targetItemDefinition = ItemDefinitions[targetItem]

        return ItemDefinitions.reinforcePointItems.associate {
            it.id to getBaseAugmentPointRankGain(itemLevel = targetItemDefinition.internalLevel, rpSourceLevel = it.internalLevel)
        }
    }

    override fun getSkillRangeInfo(actorState: ActorState, skill: SkillId): SkillRangeInfo {
        val override = (actorState.behaviorController as? V0MonsterController)?.getSkillRangeOverride(skill)
        if (override != null) { return override }

        return when (skill) {
            is SpellSkillId -> getCastingRangeInfo(skill)
            is MobSkillId -> getMobSkillRange(skill)
            is AbilitySkillId -> getAbilityRange(skill)
            is ItemSkillId -> getItemRange(skill)
            is RangedAttackSkillId -> SkillRangeInfo(maxTargetDistance = 24f, effectRadius = 0f, type = AoeType.None)
        }
    }

    override fun getSkillEffectedTargetFlags(actorState: ActorState, skill: SkillId): Int? {
        if (actorState.behaviorController !is V0MonsterController) { return null }
        return actorState.behaviorController.getSkillEffectedTargetType(skill)
    }

    override fun getSkillBaseCost(actorState: ActorState, skill: SkillId): AbilityCost {
        return getSkillCost(actorState, skill).baseCost
    }

    override fun getSkillUsedCost(actorState: ActorState, skill: SkillId): AbilityCost {
        return getUsedSkillCost(actorState, skill)
    }

    override fun getSkillCastTime(caster: ActorState, skill: SkillId): Float {
        return when (skill) {
            is SpellSkillId -> getSpellCastTime(caster, skill)
            is MobSkillId -> getMobSkillCastTime(caster, skill)
            is AbilitySkillId -> 0f
            is ItemSkillId -> getItemCastTime(caster, skill)
            is RangedAttackSkillId -> getRangedAttackCastTime(caster)
        }
    }

    override fun getSkillActionLockTime(caster: ActorState, skill: SkillId): Float {
        val lockTime = (when (skill) {
            is MobSkillId -> MobSkills[skill].lockTime
            is SpellSkillId -> getSpellLockTime(caster, skill)
            is AbilitySkillId -> V0AbilityDefinitions.getActionLock(skill)
            else -> null
        }) ?: 1.seconds

        return Fps.toFrames(lockTime)
    }

    override fun getSkillRecastTime(caster: ActorState, skill: SkillId): Float {
        return when (skill) {
            is AbilitySkillId -> getAbilityRecast(caster, skill)
            is SpellSkillId -> getSpellRecast(caster, skill)
            is MobSkillId -> 0f
            is ItemSkillId -> 0f
            is RangedAttackSkillId -> 0f
        }
    }

    fun getSkillChainAttributes(attacker: ActorState, defender: ActorState, skill: SkillId): List<SkillChainAttribute> {
        return when (skill) {
            is SpellSkillId -> V0SpellDefinitions.getSkillChainAttributes(attacker, skill)
            is MobSkillId -> MobSkills[skill].skillChainAttributes
            is AbilitySkillId -> V0AbilityDefinitions.getSkillChainAttributes(skill)
            is ItemSkillId -> emptyList()
            is RangedAttackSkillId -> emptyList()
        }
    }

    override fun getSkillChainDamage(attacker: ActorState, defender: ActorState, skillChainStep: SkillChainStep, closingWeaponSkillDamage: Int): Int {
        val baseDamage = when (skillChainStep.attribute.level) {
            1 -> 0.5f
            2 -> 0.75f
            3 -> 1f
            4 -> 1.25f
            else -> return 0
        }

        val damageMultiplier = baseDamage * (1f + 0.2f * (skillChainStep.step - 1)).coerceAtMost(2f)
        val bonuses = CombatBonusAggregator[attacker]
        return (closingWeaponSkillDamage * damageMultiplier * bonuses.skillChainDamage.toMultiplier()).roundToInt()
    }

    override fun getSkillChainResult(skillChainRequest: SkillChainRequest): SkillChainState? {
        return getSkillChainResult(skillChainRequest = skillChainRequest, ignoreWindow = false)
    }

    fun getSkillChainResult(skillChainRequest: SkillChainRequest, ignoreWindow: Boolean): SkillChainState? {
        val skillAttributes = getSkillChainAttributes(skillChainRequest.attacker, skillChainRequest.defender, skillChainRequest.skill)
        if (skillAttributes.isEmpty()) { return null }

        val windowBonus = CombatBonusAggregator[skillChainRequest.attacker].skillChainWindowBonus

        if (skillChainRequest.currentState == null) {
            return SkillChainOpen(attributes = skillAttributes, window = 10.seconds + windowBonus)
        }

        if (!ignoreWindow && !skillChainRequest.currentState.isSkillChainWindowOpen()) {
            return SkillChainOpen(attributes = skillAttributes, window = 10.seconds + windowBonus)
        }

        val currentAttributes = when (skillChainRequest.currentState) {
            is SkillChainOpen -> skillChainRequest.currentState.attributes
            is SkillChainStep -> listOf(skillChainRequest.currentState.attribute)
        }

        val nextStepAttribute = findMatchingAttribute(currentAttributes, skillAttributes)
            ?: return SkillChainOpen(attributes = skillAttributes, window = 10.seconds + windowBonus)

        val step = when (skillChainRequest.currentState) {
            is SkillChainOpen -> 1
            is SkillChainStep -> skillChainRequest.currentState.step + 1
        }

        val windowSize = (10.seconds - (step.seconds)/2).coerceAtLeast(6.seconds) + windowBonus

        return SkillChainStep(step = step, attribute = nextStepAttribute, window = windowSize)
    }

    private fun findMatchingAttribute(targetAttributes: List<SkillChainAttribute>, skillAttributes: List<SkillChainAttribute>): SkillChainAttribute? {
        for (targetAttribute in targetAttributes) {
            for (skillAttribute in skillAttributes) {
                return skillChainTransitions[targetAttribute]?.get(skillAttribute) ?: continue
            }
        }
        return null
    }

    override fun getSkillMovementLock(actorState: ActorState, skill: SkillId): Int? {
        return when (skill) {
            is AbilitySkillId -> V0AbilityDefinitions.getMovementLock(skill)
            is SpellSkillId -> if (actorState.hasStatusEffect(StatusEffect.Spontaneity)) { 0 } else { V0SpellDefinitions.getMovementLock(skill) }
            else -> null
        }
    }

    override fun getActorCombatBonuses(actorState: ActorState): CombatBonusAggregate {
        return CombatBonusAggregator[actorState]
    }

    override fun getCurrentBackgroundMusic(): BackgroundMusicResponse {
        val currentLogic = zoneLogic

        val zoneLogicMusic = currentLogic?.getMusic()
        if (zoneLogicMusic != null) { return BackgroundMusicResponse(musicId = zoneLogicMusic) }

        val battleLocation = if (currentLogic is FloorInstance) { currentLogic.definition.location } else { null }
        val zoneSettings = ZoneSettingsTable[SceneManager.getCurrentScene().config]

        val musicSettings = if (isInBaseCamp()) {
            MusicSettings(musicId = GameV0SaveStateHelper.getState().baseCampMusic)
        } else {
            battleLocation?.musicSettings ?: zoneSettings.musicSettings
        }

        val actor = ActorManager.player()

        return if (actor.isDisplayedDead()) {
            BackgroundMusicResponse(musicId = 111, resume = false)
        } else if (actor.isDead()) {
            BackgroundMusicResponse(musicId = null)
        } else if (hasAnyEnmity(actor.id)) {
            val monsterMusicOverride = EnmityLookupTable[actor.id]
                .mapNotNull { ActorStateManager[it] }
                .firstNotNullOfOrNull { MonsterDefinitions[it.monsterId]?.engageMusicId }

            if (monsterMusicOverride != null) {
                return BackgroundMusicResponse(monsterMusicOverride)
            }

            val partySize = PartyManager[actor].getAllState().count { it.owner == null }
            val musicId = if (partySize > 1) { musicSettings.battlePartyMusicId } else { musicSettings.battleSoloMusicId }
            BackgroundMusicResponse(musicId)
        } else {
            BackgroundMusicResponse(musicSettings.musicId)
        }
    }

    override fun rollParry(attacker: ActorState, defender: ActorState): Boolean {
        val defenderBonuses = CombatBonusAggregator[defender]
        val parryChance = defenderBonuses.parryRate.coerceAtMost(70) / 100f
        return Random.nextFloat() <= parryChance
    }

    override fun rollCounter(attacker: ActorState, defender: ActorState): AttackCounterEffect? {
        if (defender.hasStatusActionLock()) { return null }

        val defenderBonus = CombatBonusAggregator[defender]
        if (!rollAgainst(defenderBonus.counterRate)) { return null }

        val autoAttackResult = processWeaponSwing(defender, attacker, MainHandAutoAttack(subType = None), autoAttack = true)
        return AttackCounterEffect(
            damageAmount = autoAttackResult.damageDone,
            counterSourceTpGain = autoAttackResult.sourceTpGained,
            counterTargetTpGain = autoAttackResult.targetTpGained,
        )
    }

    override fun rollGuard(attacker: ActorState, defender: ActorState): Boolean {
        val defenderBonuses = CombatBonusAggregator[defender]
        val parryChance = defenderBonuses.guardRate / 100f
        return Random.nextFloat() <= parryChance
    }

    override fun rollEvasion(attacker: ActorState, defender: ActorState): Boolean {
        val blind = attacker.getStatusEffects().firstOrNull { it.statusEffect == StatusEffect.Blind }
        if (blind != null && GameEngine.rollAgainst(blind.potency)) { return true }

        val flash = attacker.getStatusEffects().firstOrNull { it.statusEffect == StatusEffect.Flash }
        if (flash != null && GameEngine.rollAgainst(flash.potency)) { return true }

        val defenderBonus = CombatBonusAggregator[defender]
        return rollAgainst(defenderBonus.evasionRate)
    }

    override fun rollSpellInterrupted(attacker: ActorState, defender: ActorState): Boolean {
        return DamageCalculator.rollSpellInterrupted(attacker, defender)
    }

    override fun rollParalysis(actorState: ActorState): Boolean {
        val castingState = actorState.getCastingState()
        if (castingState != null && castingState.castTime == 0f) { return false }

        val castingSkill = castingState?.skill
        if (castingSkill is MobSkillId) {
            val mobSkillDefinition = MobSkills[castingSkill]
            if (mobSkillDefinition.bypassParalysis) { return false }
        }

        val effect = actorState.getStatusEffects().firstOrNull { it.statusEffect == StatusEffect.Paralysis } ?: return false
        return rollAgainst(effect.potency)
    }

    fun generateItem(itemId: Int, quantity: Int, temporary: Boolean = false): InventoryItem {
        return generateItem(ItemDropDefinition(
            itemId = itemId,
            quantity = quantity,
            temporary = temporary,
        ))
    }

    override fun canObtainItem(sourceState: ActorState?, destinationState: ActorState, inventoryItem: InventoryItem): Boolean {
        val definition = ItemDefinitions.getNullable(inventoryItem) ?: return true
        if (!definition.rare) { return true }
        return destinationState.getInventory().getByItemId(definition.id).isEmpty()
    }

    override fun getMaxTp(actor: ActorState): Int {
        if (!actor.isPlayer()) { return 1000 }

        val levelTpBonus = ((actor.getMainJobLevel().level / 10) * 500).coerceAtMost(2000)
        return 1000 + levelTpBonus
    }

    override fun spawnMonster(monsterId: MonsterId, initialActorState: InitialActorState): ActorPromise {
        val monsterDefinition = MonsterDefinitions[monsterId]

        val inventory = generateMonsterInventory(monsterDefinition)
        val modifiedActorState = initialActorState.copy(
            components = listOf(inventory, ActorEnmityTable())
        )

        val navigator = getNavigator()
        if (navigator != null) {
            modifiedActorState.position.copyFrom(navigator.nearestGridPoint(modifiedActorState.position))
        }

        preloadSkillResources(monsterDefinition.mobSkills, monsterDefinition.mobSpells, monsterDefinition.autoAttackSkills)
        return GameEngine.submitCreateActorState(modifiedActorState)
    }

    override fun onSelectedCrystal(actorId: ActorId, inventoryItem: InventoryItem) {
        SynthesisUi.push(inventoryItem)
    }

    fun getKnownSynthesisRecipes(actorState: ActorState, type: SynthesisType): List<V0SynthesisRecipe> {
        val miningLevel = GameV0SaveStateHelper.getState().mining.level

        return V0SynthesisRecipes.recipes.mapNotNull {
            if (it.recipeLevel - miningLevel < 10) { it } else { null }
        }
    }

    fun getSynthesisSuccessRate(actorState: ActorState, synthesisRecipe: V0SynthesisRecipe): Int {
        val miningLevel = GameV0SaveStateHelper.getState().mining.level
        val failureChance = getSynthesisFailureChance(synthesisRecipe, miningLevel)
        return (100 - failureChance).coerceIn(0, 100)
    }

    fun getSynthesisResult(actorState: ActorState, synthesisState: ActorSynthesisComponent, synthesisRecipe: V0SynthesisRecipe): SynthesisResult {
        if (synthesisState.interrupted) { return SynthesisResult(type = SynthesisResultType.Break) }

        val miningLevel = GameV0SaveStateHelper.getState().mining.level

        val failureChance = getSynthesisFailureChance(synthesisRecipe, miningLevel)
        val failed = rollAgainst(failureChance)
        if (failed) { return SynthesisResult(type = SynthesisResultType.Break) }

        val hqChance = 15 + 10 * (miningLevel - synthesisRecipe.recipeLevel).coerceAtLeast(0)
        val hq = synthesisRecipe.hqOutput != null &&rollAgainst(hqChance)
        val synthesisOutput = if (synthesisRecipe.hqOutput != null && hq) { synthesisRecipe.hqOutput } else { synthesisRecipe.output }

        val resultDefinition = ItemDefinitions.definitionsById[synthesisOutput.itemId]
            ?: return SynthesisResult(type = SynthesisResultType.Break)

        val result = generateItem(itemId = resultDefinition.id, quantity = synthesisOutput.quantity * synthesisState.quantity)

        return SynthesisResult(type = if (hq) { HighQuality } else { NormalQuality }, item = result)
    }

    override fun pathNextPosition(actorState: ActorState, elapsedFrames: Float, desiredEnd: Vector3f): Vector3f? {
        val navigator = getNavigator() ?: return null
        return navigator.getNextPosition(actorState, desiredEnd)
    }

    override fun getWanderPosition(monsterState: ActorState, desiredEnd: Vector3f): Vector3f {
        val navigator = getNavigator() ?: return desiredEnd
        return navigator.nearestGridPoint(desiredEnd)
    }

    override fun onCheck(id: ActorId, targetId: ActorId?) {
        if (DebugToolsManager.debugEnabled) { TargetAnimationUi.push() }

        val source = ActorStateManager[id] ?: return
        val target = ActorStateManager[targetId] ?: return
        if (target.monsterId == null) { return }

        val levelDelta = (target.getMainJobLevel().level - source.getMainJobLevel().level).coerceIn(-4, 3)

        val description = when (levelDelta) {
            -4 -> "too weak to be worthwhile"
            -3 -> "like incredibly easy prey"
            -2 -> "like easy prey"
            -1 -> "like a decent challenge"
             0 -> "like an even match"
             1 -> "tough"
             2 -> "very tough"
             3 -> "incredibly tough"
            else -> return
        }

        ChatLog("The ${target.name} seems $description.")
    }

    override fun onGearChange(actorState: ActorState, equipment: Map<EquipSlot, InternalItemId?>): List<Event> {
        return if (actorState.behaviorController is PlayerBehavior) {
            actorState.behaviorController.onGearChanged(equipment)
        } else {
            emptyList()
        }
    }

    override fun getStatusDescription(actorState: ActorState, status: StatusEffectState): String? {
        return V0StatusDescriptionHelper.describeStatus(actorState, status)
    }

    override fun getActorEffectsDescription(player: ActorState): List<BonusDescription> {
        return V0BonusDescriptionHelper.describeBonuses(player)
    }

    override fun onEffectRoutineStarted(effectRoutineInstance: EffectRoutineInstance, initialSequence: EffectRoutineInstance.EffectSequence) {
        val association = effectRoutineInstance.effectAssociation as? ActorAssociation ?: return
        val skillId = association.context.skillId ?: return

        val override = V0CustomSpellSetup.getOverrideInfo(skillId)
        if (override != null) { EffectRoutineMobSkillConverter.apply(effectRoutineInstance, initialSequence, override) }

        if (skillId is MobSkillId) {
            val broadcast = MobSkills[skillId].broadcastIds
            if (broadcast != null) { broadcast.forEach { initialSequence.addCustomBroadcast(it) } }
        }
    }

    fun getItemPrice(vendorId: ActorId, item: InventoryItem): Pair<CurrencyType, Int>? {
        val itemDefinition = ItemDefinitions.getNullable(item) ?: return null
        val basePrice = 100 * (1.225f).pow(itemDefinition.internalLevel - 1)

        val rank = item.augments?.rankLevel ?: 1
        val rankMultiplier = 1f + 9f * rank / maxPossibleRankLevel

        val finalCost = (basePrice * rankMultiplier).roundToInt()

        return (CurrencyType.Gil to finalCost)
    }

    fun setZoneLogic(newLogicProvider: () -> ZoneLogic) {
        ActorStateManager.player().setTp(0)
        zoneLogic?.cleanUp()
        zoneLogic = newLogicProvider.invoke()
    }

    fun setupBattle(floor: Int) {
        val floorDefinition = FloorDefinition.fromFloor(floor)
        setZoneLogic { floorDefinition.newInstance() }
    }

    fun generateItem(dropDefinition: ItemDropDefinition): InventoryItem {
        val item = InventoryItem(id = dropDefinition.itemId, quantity = dropDefinition.quantity, temporary = dropDefinition.temporary)

        val itemDefinition = ItemDefinitions.getNullable(item) ?: return item

        if (itemDefinition.meldable) {
            val fixedAugments = CapacityAugments(capacityRemaining = itemDefinition.initialCapacity ?: 3)
                .also { item.fixedAugments = it }

            for (initialMeld in itemDefinition.initialMelds) {
                fixedAugments.add(initialMeld.augmentId, initialMeld.potency)
            }
        }

        if (itemDefinition.slotted && !itemDefinition.ranked && dropDefinition.slottedAugment != null) {
            val slottedAugments = SlottedAugments(slots = 1, augments = HashMap())
            slottedAugments.set(0, dropDefinition.slottedAugment)
            item.slottedAugments = slottedAugments
        }

        if (!itemDefinition.ranked || dropDefinition.rankSettings == null) { return item }

        val itemAugmentRank = dropDefinition.rankSettings.rankDistribution.getRank()
        val itemAugmentMaxRank = if (dropDefinition.rankSettings.canRankUp) { maxPossibleRankLevel } else { itemAugmentRank }
        item.internalQuality = getInternalQuality(itemAugmentRank)

        val augments = ItemAugment(rankLevel = itemAugmentRank, maxRankLevel = itemAugmentMaxRank)
        item.augments = augments

        val numAugments = getNumAugmentSlots(item)
        populateItemAugments(item, numAugments)

        if (itemDefinition.slotted) {
            item.slottedAugments = SlottedAugments(slots = numAugments, augments = HashMap())
        }

        if (itemDefinition.applyRandomSlots) {
            val randomSlots = MysteryMeldRanges.getRandomSlots(item, numAugments)
            randomSlots.forEachIndexed { i, augment -> item.slottedAugments!!.set(i, augment) }
        }

        return item
    }

    fun populateItemAugments(inventoryItem: InventoryItem, numAugments: Int) {
        val augments = inventoryItem.augments ?: return
        val itemDefinition = ItemDefinitions[inventoryItem]

        augments.augmentIds.clear()

        for (i in 0 until numAugments) {
            val augmentSlot = itemDefinition.augmentSlots.getOrNull(i) ?: continue
            val currentAugmentIds = augments.augmentIds.toSet()

            val remaining = augmentSlot.entries.filter { !currentAugmentIds.contains(it.first) }
            if (remaining.isEmpty()) { continue }

            augments.augmentIds += WeightedTable(remaining).getRandom()
        }
    }

    private fun getNumAugmentSlots(item: InventoryItem): Int {
        val itemDefinition = ItemDefinitions[item]

        return if (item.info().isMainHandWeapon()) {
            item.internalQuality + 1
        } else if (itemDefinition.slotted) {
            item.internalQuality
        } else {
            min(itemDefinition.augmentSlots.size, item.internalQuality)
        }
    }

    private fun inferZoneLogicForDebugging(zoneConfig: ZoneConfig) {
        val floor = TowerConfiguration.getAll()
            .filter { it.value.battleLocation.startingPosition.zoneId == zoneConfig.zoneId }
            .minByOrNull { Vector3f.distance(ActorStateManager.player().position, it.value.battleLocation.startingPosition.startPosition!!) }

        if (floor != null) {
            GameTower.resetTowerState(floor.key)
            ActorStateManager.player().position.copyFrom(floor.value.battleLocation.startingPosition.startPosition!!)
            setupBattle(floor = floor.key)
            return
        }

        val abysseaConfiguration = AbysseaConfigurations.getAll()
            .filter { it.value.startingPosition.zoneId == zoneConfig.zoneId }
            .toList()
            .firstOrNull()
            ?.second

        if (abysseaConfiguration != null) {
            ActorStateManager.player().position.copyFrom(abysseaConfiguration.startingPosition.startPosition!!)
            setZoneLogic { AbysseaZoneInstance(abysseaConfiguration) }
            return
        }

        val miningZoneConfiguration = MiningZoneConfigurations.zones
            .filter { it.startingPosition.zoneId == zoneConfig.zoneId }
            .toList()
            .firstOrNull()

        if (miningZoneConfiguration != null) {
            ActorStateManager.player().position.copyFrom(miningZoneConfiguration.startingPosition.startPosition!!)
            setZoneLogic { MiningZoneInstance(miningZoneConfiguration) }
            return
        }

        val eschaConfiguration = EschaConfigurations.getAll()
            .filter { it.value.startingPosition.zoneId == zoneConfig.zoneId }
            .toList()
            .firstOrNull()
            ?.second

        if (eschaConfiguration != null) {
            ActorStateManager.player().position.copyFrom(eschaConfiguration.startingPosition.startPosition!!)
            setZoneLogic { EschaZoneInstance(eschaConfiguration) }
            return
        }

        if (zoneConfig.zoneId == ParadoxZoneInstance.zoneConfig.zoneId) {
            setZoneLogic { ParadoxZoneInstance() }
            return
        }

        val cloister = CloisterZoneInstance.matchToStartingPosition(zoneConfig)
        if (cloister != null) {
            setZoneLogic { CloisterZoneInstance(cloister) }
            return
        }

    }

    fun getCurrentMiningZoneInstance(): MiningZoneInstance? {
        return zoneLogic as? MiningZoneInstance
    }

    fun isInBaseCamp(): Boolean {
        return zoneLogic is BaseCampLogic
    }

    fun getNavigator(): BattleLocationNavigator? {
        return zoneLogic?.getCurrentNavigator()
    }

    fun applyVorsealBonus(aggregate: CombatBonusAggregate) {
        val currentLogic = zoneLogic
        if (currentLogic is EschaZoneInstance) { currentLogic.applyVorsealBonus(aggregate) }
    }

    fun executeRepeatCurrentFloor(fullRestore: Boolean) {
        val currentZoneLogic = zoneLogic!!

        EventScriptRunner.runScript(EventScript(listOf(
            FadeOutEvent(1.seconds),
            WarpZoneEventItem(currentZoneLogic.getEntryPosition(), fade = false, revive = fullRestore),
            RunOnceEventItem { SceneManager.requestReload() },
            RunOnceEventItem { setZoneLogic { currentZoneLogic.toNew() } },
            FadeInEvent(1.seconds),
        )))
    }

}

object GameV0Helpers {

    fun getPlayerLevelStatMultiplier(level: Int): Float {
        return if (level <= 5) {
            1f + 0.2f * (level - 1)
        } else {
            2f * (1.09298f.pow(level - 6))
        }
    }

    fun getItemDescriptionInternal(
        inventoryItem: InventoryItem,
        meldBonuses: Map<AugmentId, Int> = emptyMap(),
        includeAllMeldCaps: Boolean = false,
        capacityConsumption: Int? = null,
        mysterySlotMax: String? = null,
    ): InventoryItemDescription {
        val baseDescription = InventoryItemDescription.toDescription(inventoryItem, capacityConsumption = capacityConsumption)
        val itemDefinition = ItemDefinitions.getNullable(inventoryItem) ?: return baseDescription.copy(rare = false, exclusive = false)
        val itemInfo = inventoryItem.info()

        var description = baseDescription.pages.toMutableList()
        var augmentPath = baseDescription.augmentPath

        if (itemInfo.type == ItemListType.Weapon || itemInfo.type == ItemListType.Armor) {
            val augmentDescription = getAugmentDescription(inventoryItem, meldBonuses, includeAllMeldCaps) ?: ""
            description = mutableListOf(itemDefinition.toDescription(inventoryItem) + augmentDescription)
        } else if (itemDefinition.capacityAugment != null) {
            augmentPath = getCapacityAugmentDescription(itemDefinition.capacityAugment)
        } else if (inventoryItem.slottedAugments != null) {
            augmentPath = getSlottedMaterialDescription(inventoryItem)
        } else if (itemDefinition.mysterySlot != null) {
            augmentPath = getMysteryMaterialDescription(itemDefinition, mysterySlotMax)
        }

        val itemLevel = getItemLevel(inventoryItem)
        val itemLevelDescription = "< Item Level: $itemLevel >"

        return baseDescription.copy(
            pages = description,
            itemLevel = itemLevelDescription,
            augmentPath = augmentPath,
            rare = itemDefinition.rare,
            exclusive = false,
        )
    }

    private fun getAugmentDescription(
        item: InventoryItem,
        meldBonuses: Map<AugmentId, Int>,
        includeAllMeldCaps: Boolean,
    ): String? {
        val itemDefinition = ItemDefinitions[item]
        if (item.augments == null && item.fixedAugments == null && item.slottedAugments == null && itemDefinition.staticAugments == null) { return null }

        val descriptions = StringBuilder()
        descriptions.append("${ShiftJis.colorInfo}")

        var idx = 1

        for (augmentId in item.augments?.augmentIds ?: emptyList()) {
            if (idx != 1) { descriptions.appendLine() }

            val def = ItemAugmentDefinitions[augmentId]
            val value = def.valueFn.calculate(item)
            descriptions.append("[$idx] ${def.attribute.toDescription(value)}")

            idx += 1
        }

        val allMeldCaps = if (includeAllMeldCaps) { itemDefinition.meldBonusCaps } else { emptyMap() }

        val fixedAugmentIds = (item.fixedAugments?.augments?.keys ?: emptySet()) + meldBonuses.keys + allMeldCaps.keys

        for (augmentId in fixedAugmentIds) {
            val baseValue = item.fixedAugments?.augments?.get(augmentId)?.potency ?: 0
            val bonusValue =  meldBonuses[augmentId]

            if (!includeAllMeldCaps && baseValue == 0 && (bonusValue ?: 0) == 0) { continue }

            val cap = itemDefinition.meldBonusCaps[augmentId]

            if (idx != 1) { descriptions.appendLine() }
            descriptions.append(augmentId.toDescription(baseValue, bonusValue, cap, includeAllMeldCaps))
            idx += 1
        }

        val slottedAugments = item.slottedAugments
        if (slottedAugments != null) {
            for (i in 0 until slottedAugments.slots) {
                if (idx != 1) { descriptions.appendLine() }

                val augmentSlot = slottedAugments.get(i)
                descriptions.append("[$idx] ${augmentSlot.augmentId.toDescription(augmentSlot.potency)}")

                idx += 1
            }
        }

        if (itemDefinition.staticAugments != null) {
            for (i in 0 until itemDefinition.staticAugments.size) {
                if (idx != 1) { descriptions.appendLine() }

                val augmentSlot = itemDefinition.staticAugments[i]
                descriptions.append("[$idx] ${augmentSlot.first.toDescription(augmentSlot.second)}")

                idx += 1
            }
        }

        if (idx == 1) { return null }

        descriptions.append("${ShiftJis.colorClear}")
        return descriptions.toString()
    }

    private fun getCapacityAugmentDescription(capacityAugment: ItemCapacityAugment): String {
        val descriptions = StringBuilder()
        descriptions.appendLine("${ShiftJis.colorAug}< Capacity Cost:${capacityAugment.capacity} >")

        descriptions.append("${ShiftJis.colorAug}< ${capacityAugment.augmentId.toDescription(capacityAugment.potency)}>")
        return descriptions.toString()
    }

    private fun getSlottedMaterialDescription(item: InventoryItem): String {
        val descriptions = StringBuilder()
        val augment = item.slottedAugments?.get(0) ?: return ""

        descriptions.append("${ShiftJis.colorAug}< Slot: ${augment.augmentId.toDescription(augment.potency)}>")
        return descriptions.toString()
    }

    private fun getMysteryMaterialDescription(itemDefinition: ItemDefinition, mysterySlotMax: String?): String {
        val augment = itemDefinition.mysterySlot ?: return ""
        val augmentText = AugmentTable.getAugmentName(augment.augmentId.id, arg = mysterySlotMax ?: "?")
        return "${ShiftJis.colorAug}< Slot: $augmentText>"
    }

    fun getItemLevel(inventoryItem: InventoryItem): Int {
        return ItemDefinitions[inventoryItem].internalLevel
    }

    fun getSkillCost(actorState: ActorState, skill: SkillId): V0AbilityCost {
        return when(skill) {
            is SpellSkillId -> V0SpellDefinitions.getCost(skill)
            is MobSkillId -> (actorState.behaviorController as? V0MonsterController)?.getSkillCostOverride(skill) ?: MobSkills[skill].cost
            is AbilitySkillId -> V0AbilityDefinitions.getCost(skill)
            is ItemSkillId -> V0AbilityCost(AbilityCost(AbilityCostType.Tp, 0))
            is RangedAttackSkillId -> V0AbilityCost(AbilityCost(AbilityCostType.Tp, 0))
        }
    }

    fun getUsedSkillCost(actorState: ActorState, skill: SkillId): AbilityCost {
        val customCost = getSkillCost(actorState, skill)
        val bonuses = CombatBonusAggregator[actorState]

        var cost = if (customCost.consumesAll) {
            getConsumeAllCost(actorState, customCost.baseCost.type)
        } else {
            customCost.baseCost.value
        }

        if (customCost.baseCost.type == AbilityCostType.Mp && rollAgainst(bonuses.conserveMp)) {
            cost /= 2
        }

        if (DamageCalculator.rollConserveTp(actorState, skill)) {
            cost = (cost * Random.nextDouble(0.50, 0.75)).roundToInt()
        }

        return AbilityCost(customCost.baseCost.type, cost)
    }

    private fun getConsumeAllCost(actorState: ActorState, type: AbilityCostType): Int {
        return when(type) {
            AbilityCostType.Tp -> actorState.getTp()
            AbilityCostType.Mp -> actorState.getMp()
        }
    }

    fun getSpellLockTime(actorState: ActorState, skill: SpellSkillId): Duration {
        val custom = V0CustomSpellSetup.getCastingLockTime(skill)
        if (custom != null) { return custom }

        val spellInfo = skill.toSpellInfo()

        return if (spellInfo.magicType == MagicType.BlueMagic) {
            1.seconds
        } else {
            2.5.seconds
        }
    }

    fun getRangedAttackCastTime(attacker: ActorState): Float {
        val (_, delay) = getWeaponPowerAndDelay(attacker, RangedAutoAttack) ?: (0 to 180)
        return delay.toFloat()
    }

    fun getSpellBaseCastTime(skill: SpellSkillId): Float {
        val customCastTime = V0SpellDefinitions.getCastTime(skill)
        return if (customCastTime != null) {
            Fps.toFrames(customCastTime)
        } else {
            val spellInfo = skill.toSpellInfo()
            spellInfo.castTimeInFrames()
        }
    }

    fun getSpellCastTime(caster: ActorState, skill: SpellSkillId): Float {
        val spontaneity = caster.getStatusEffect(StatusEffect.Spontaneity)
        if (spontaneity != null && spontaneity.counter > 0) { return 0f }

        val baseCastTime = getSpellBaseCastTime(skill)

        val bonuses = CombatBonusAggregator[caster]
        val fastCastBonus = speedBaseTerm.pow(bonuses.fastCast.coerceAtMost(80))

        return baseCastTime * fastCastBonus
    }

    fun getMobSkillCastTime(caster: ActorState, skill: MobSkillId): Float {
        val castTimeOverride = if (caster.behaviorController is V0MonsterController) {
            caster.behaviorController.getSkillCastTimeOverride(skill)?.let { Fps.toFrames(it) }
        } else {
            null
        }

        val baseCastTime = castTimeOverride ?: Fps.toFrames(MobSkills[skill].castTime)

        val bonuses = CombatBonusAggregator[caster]
        val potency = bonuses.mobSkillFastCast
        return baseCastTime * 100f / (100f + potency)
    }

    fun getItemCastTime(caster: ActorState, skillId: ItemSkillId): Float {
        val customCastTime = V0ItemDefinitions.getCastTime(skillId)
        if (customCastTime != null) { return Fps.toFrames(customCastTime) }

        return InventoryItems[skillId.id].usableItemInfo?.castTimeInFrames() ?: 0f
    }

    fun getAbilityRecast(actorState: ActorState, skill: AbilitySkillId): Float {
        val abilityInfo = skill.toAbilityInfo()
        val delayInSeconds = if (abilityInfo.type == AbilityType.WeaponSkill) { 2.5.seconds } else { V0AbilityDefinitions.getRecast(skill).baseTime }
        return Fps.toFrames(delayInSeconds)
    }

    fun getBaseSpellRecast(skill: SpellSkillId): Pair<Float, Boolean> {
        val customRecast = V0SpellDefinitions.getRecast(skill)

        val customRecastTime = customRecast?.baseTime?.let { Fps.toFrames(it) }
        if (customRecastTime != null && customRecast.fixedTime) { return customRecastTime to true }

        return (customRecastTime ?: skill.toSpellInfo().recastDelayInFrames()) to false
    }

    fun getSpellRecast(actorState: ActorState, skill: SpellSkillId): Float {
        val (baseRecastTime, fixedRecastTime) = getBaseSpellRecast(skill)
        if (fixedRecastTime) { return baseRecastTime }

        val spontaneity = actorState.getStatusEffect(StatusEffect.Spontaneity)
        val spontaneityBonus = if (spontaneity != null && spontaneity.counter > 0) { 0.5f } else { 1f }

        val selfTarget = skill.toSpellInfo().targetFilter.targetFilter(actorState, actorState)
        val outOfCombatBonus = if (selfTarget && actorState.isPlayer() && !hasAnyEnmity(actorState.id)) { 0.1f } else { 1f }

        val bonuses = CombatBonusAggregator[actorState]

        val fastCastBonus = speedBaseTerm.pow(bonuses.fastCast.coerceAtMost(80))
        val hasteBonus = speedBaseTerm.pow(0.5f * bonuses.haste.coerceAtMost(80))
        val totalSpeedBonus = (fastCastBonus * hasteBonus).coerceAtLeast(0.2f)

        return baseRecastTime * spontaneityBonus * totalSpeedBonus * outOfCombatBonus
    }

    fun generateMonsterInventory(monsterDefinition: MonsterDefinition): Inventory {
        val monsterInventory = Inventory()
        val monsterDropTable = V0MonsterDropTables[monsterDefinition.id]

        for (entry in monsterDropTable.dropTable) {
            val dropDefinition = entry.getDropTable().getRandom()
            if (dropDefinition.itemId == null) { continue }

            val item = GameV0.generateItem(ItemDropDefinition(
                itemId = dropDefinition.itemId,
                quantity = dropDefinition.quantity,
                rankSettings = dropDefinition.rankSettings,
                temporary = dropDefinition.temporary,
                slottedAugment = dropDefinition.slottedAugment,
            ))
            monsterInventory.addItem(item, stack = false)
        }

        return monsterInventory
    }

    fun getRemainingMeldPotential(weapon: InventoryItem): Map<AugmentId, Int> {
        val definition = ItemDefinitions[weapon]

        val fixedAugments = weapon.fixedAugments?.augments ?: return definition.meldBonusCaps
        val remainingMeldPotential = HashMap<AugmentId, Int>()

        for ((itemAugmentId, maxAugmentValue) in definition.meldBonusCaps) {
            val currentAugmentValue = fixedAugments[itemAugmentId]?.potency ?: 0
            remainingMeldPotential[itemAugmentId] = (maxAugmentValue - currentAugmentValue).coerceAtLeast(0)
        }

        return remainingMeldPotential
    }

    fun generateUpgradedWeapon(sourceItem: InventoryItem, itemInfo: InventoryItemInfo): InventoryItem {
        val fakeItem = GameV0.generateItem(ItemDropDefinition(
            itemId = itemInfo.itemId,
            rankSettings = ItemRankSettings.leveling(),
        ))

        fakeItem.slottedAugments = sourceItem.slottedAugments?.copy()

        return fakeItem
    }

    fun getNeededItemsForWeaponUpgrade(weapon: InventoryItem, destinationItemInfo: InventoryItemInfo): Map<Int, Int> {
        val upgradeOption = ItemDefinitions[weapon].upgradeOptions
            .firstOrNull { it.destinationId == destinationItemInfo.itemId } ?: return emptyMap()

        val itemRequirement = upgradeOption.itemRequirement ?: return emptyMap()

        val player = ActorStateManager.player()

        val inventoryCount = player.countNotEquippedItems(itemRequirement.itemId)
        if (inventoryCount >= itemRequirement.quantity) { return emptyMap() }

        return mapOf(itemRequirement.itemId to itemRequirement.quantity - inventoryCount)
    }

    fun weaponRankIsSufficient(weapon: InventoryItem, destinationItemInfo: InventoryItemInfo): Pair<Boolean, Int>? {
        val upgradeOption = ItemDefinitions[weapon].upgradeOptions
            .firstOrNull { it.destinationId == destinationItemInfo.itemId } ?: return null

        val weaponRank = weapon.augments?.rankLevel ?: return null
        return (weaponRank >= upgradeOption.rankRequirement) to upgradeOption.rankRequirement
    }

    fun getBaseAugmentPointRankGain(itemLevel: Int, rpSourceLevel: Int): Int {
        val levelDifference = rpSourceLevel - itemLevel

        return if (levelDifference >= 0) {
            100 * (levelDifference+1)
        } else {
            100 / (abs(levelDifference)+1)
        }
    }

    fun getCastingRangeInfo(skill: SpellSkillId): SkillRangeInfo {
        val custom = V0SpellDefinitions.getRange(skill)
        if (custom != null) { return custom }

        val spellInfo = skill.toSpellInfo()
        val spellInfoRange = spellInfo.aoeSize.toFloat() + if (spellInfo.magicType == MagicType.Songs) { 4f } else { 0f }

        return if (spellInfo.aoeType == AoeType.Cone || spellInfo.aoeType == AoeType.Source) {
            SkillRangeInfo(maxTargetDistance = spellInfoRange, effectRadius = spellInfoRange, type = spellInfo.aoeType)
        } else {
            SkillRangeInfo(maxTargetDistance = 24f, effectRadius = spellInfoRange, type = spellInfo.aoeType)
        }
    }

    fun getItemRange(itemSkillId: ItemSkillId): SkillRangeInfo {
        return SkillRangeInfo(maxTargetDistance = 10f, effectRadius = 0f, type = AoeType.None)
    }

    fun getAbilityRange(abilitySkillId: AbilitySkillId): SkillRangeInfo {
        val customRange = V0AbilityDefinitions.getRange(abilitySkillId)
        if (customRange != null) { return customRange }

        val abilityInfo = abilitySkillId.toAbilityInfo()
        return SkillRangeInfo(maxTargetDistance = 8f, effectRadius = abilityInfo.aoeSize.toFloat(), type = abilityInfo.aoeType)
    }

    fun getMobSkillRange(skill: MobSkillId): SkillRangeInfo {
        return MobSkills[skill].rangeInfo
    }

    fun learnSpells(actor: ActorState, spells: List<SpellSkillId>) {
        spells.map { ActorLearnSpellEvent(actor.id, it) }.forEach { GameEngine.submitEvent(it) }
    }

    fun hasDefeated(monsterId: MonsterId): Boolean {
        val defeatCount = GameV0SaveStateHelper.getState().defeatedMonsterCounter[monsterId] ?: 0
        return defeatCount > 0
    }

    fun getSynthesisFailureChance(internalRecipe: V0SynthesisRecipe, miningLevel: Int): Int {
        val levelDelta = internalRecipe.recipeLevel - miningLevel
        if (levelDelta <= 0) { return 0 }
        return (((100 * 1.15.pow(levelDelta)) - 100).roundToInt()).coerceIn(0, 100)
    }

    fun createPlayer(): Event {
        val config = LocalStorage.getPlayerConfiguration()
        val modelLook = config.playerLook.copy()

        val v0SaveState = GameV0SaveStateHelper.getState()

        val components = listOf(
            v0SaveState.playerSpells,
            Inventory().copyFrom(config.playerInventory),
            Equipment().copyFrom(config.playerEquipment),
        )

        val zoneSettings = ZoneSettings(
            zoneId = config.playerPosition.zoneId,
            subAreaId = config.playerPosition.subAreaId,
            mogHouseSetting = config.playerPosition.mogHouseSetting,
        )

        val playerBehaviorId = ActorBehaviors.register(PlayerBehaviorId) { PlayerBehavior(it) }

        return ActorCreateEvent(InitialActorState(
            name = "Player",
            type = ActorType.Pc,
            position = Vector3f(config.playerPosition.position),
            rotation = config.playerPosition.rotation,
            zoneSettings = zoneSettings,
            modelLook = modelLook,
            movementController = KeyboardActorController(),
            behaviorController = playerBehaviorId,
            presetId = ActorStateManager.playerId,
            jobSettings = JobSettings(config.playerJob.mainJob, config.playerJob.subJob),
            jobLevels = config.playerLevels,
            components = components,
        ))
    }

    fun isNpcInteractionValid(npcInteraction: NpcInteraction, source: ActorState, target: ActorState): Boolean {
        if (EventScriptRunner.isRunningScript()) {
            return false
        }

        val distance = source.getTargetingDistance(target)
        if (distance > npcInteraction.maxInteractionDistance(target.id)) {
            ChatLog("Target out of range.", ChatLogColor.Info)
            return false
        }

        return true
    }

    fun hasAnyEnmity(actorId: ActorId): Boolean {
        return EnmityLookupTable[actorId].isNotEmpty()
    }

    fun defeatActor(actorState: ActorState) {
        GameEngine.submitEvent(ActorFallsToGroundEvent(actorState.id))
    }

}

// This term is used to "linearize" the benefit from haste/fast-cast, such that each point gives a ~2% multiplicative bonus
// At 80 points: speedBaseTerm^80 = 0.2
const val speedBaseTerm = 0.98008305f

private fun makeSkillChainTransitions(): Map<SkillChainAttribute, Map<SkillChainAttribute, SkillChainAttribute>> {
    // T1
    val compression = mapOf(Transfixion to Transfixion, Detonation to Detonation)
    val scission = mapOf(Liquefaction to Liquefaction, Detonation to Detonation, Reverberation to Reverberation)

    val reverberation = mapOf(Induration to Induration, Impaction to Impaction)
    val induration = mapOf(Compression to Compression, Reverberation to Fragmentation, Impaction to Impaction)

    val transfixion = mapOf(Compression to Compression, Scission to Distortion, Reverberation to Reverberation)
    val liquefaction = mapOf(Scission to Scission, Impaction to Fusion)

    val detonation = mapOf(Compression to Gravitation, Scission to Scission)
    val impaction = mapOf(Liquefaction to Liquefaction, Detonation to Detonation)

    // T2 - allowing for easy loops & backward-transitions
    val gravitation = mapOf(Distortion to Darkness, Fragmentation to Fragmentation, Fusion to Fusion) + compression + scission
    val distortion = mapOf(Gravitation to Darkness, Fusion to Fusion, Fragmentation to Fragmentation) + reverberation + induration
    val fusion = mapOf(Fragmentation to Light, Gravitation to Gravitation, Distortion to Distortion) + liquefaction + transfixion
    val fragmentation = mapOf(Fusion to Light, Distortion to Distortion, Gravitation to Gravitation) + impaction + detonation

    // T3
    val light = mapOf(Light to Light2)
    val darkness = mapOf(Darkness to Darkness2)

    return mapOf(
        Transfixion to transfixion,
        Compression to compression,
        Liquefaction to liquefaction,
        Scission to scission,
        Reverberation to reverberation,
        Detonation to detonation,
        Induration to induration,
        Impaction to impaction,
        Gravitation to gravitation,
        Distortion to distortion,
        Fusion to fusion,
        Fragmentation to fragmentation,
        Light to light,
        Darkness to darkness,
    )
}

private val skillChainTransitions = makeSkillChainTransitions()