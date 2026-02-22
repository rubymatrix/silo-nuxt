package xim.poc.game.configuration.assetviewer

import xim.math.Vector3f
import xim.poc.*
import xim.poc.audio.AudioManager
import xim.poc.audio.BackgroundMusicResponse
import xim.poc.audio.SystemSound
import xim.poc.browser.ExecutionEnvironment
import xim.poc.browser.LocalStorage
import xim.poc.game.*
import xim.poc.game.GameEngine.rollAgainst
import xim.poc.game.SkillChainAttribute.*
import xim.poc.game.actor.components.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.assetviewer.DebugHelpers.createMogHouseActors
import xim.poc.game.configuration.assetviewer.DebugHelpers.createPlayer
import xim.poc.game.configuration.assetviewer.DebugHelpers.getAbilityCost
import xim.poc.game.configuration.assetviewer.DebugHelpers.getAbilityRange
import xim.poc.game.configuration.assetviewer.DebugHelpers.getAbilityRecast
import xim.poc.game.configuration.assetviewer.DebugHelpers.getApproximateSkillChainAttributes
import xim.poc.game.configuration.assetviewer.DebugHelpers.getCastingRangeInfo
import xim.poc.game.configuration.assetviewer.DebugHelpers.getItemRange
import xim.poc.game.configuration.assetviewer.DebugHelpers.registerElevator
import xim.poc.game.configuration.assetviewer.DebugHelpers.triggerDoorOrElevator
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.*
import xim.poc.game.event.ZoneSettings
import xim.poc.tools.*
import xim.poc.ui.ChatLog
import xim.poc.ui.InventoryItemDescription
import xim.poc.ui.TargetAnimationUi
import xim.resource.*
import xim.resource.table.*
import xim.resource.table.AbilityInfoTable.toAbilityInfo
import xim.resource.table.SpellInfoTable.toSpellInfo
import xim.util.Fps
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.safeCast
import kotlin.time.Duration.Companion.seconds

private val assetViewerConfiguration = GameConfiguration(
    gameModeId = "AssetViewer",
    startingZoneConfig = CustomZoneConfig.Sarutabartua_East.config,
    debugControlsEnabled = true
)

object AssetViewer: GameLogic {

    override val configuration = assetViewerConfiguration

    private var zoneLogic: ZoneLogic? = null

    private val npcTasks = HashMap<ActorId, NpcTask>()

    override fun setup(): List<Event> {
        MonsterDefinitions.registerAll(AssetViewerMonsterDefinitions.definitions)

        AssetViewerAbilitySkills.register()
        AssetViewerSpellAppliers.register()
        AssetViewerItemDefinitions.register()
        AssetViewerTrustBehaviors.register()
        ActorBehaviors.register(AssetViewerMonsterBehaviorId) { AssetViewerMonsterBehavior(it) }

        return listOf(createPlayer())
    }

    override fun update(elapsedFrames: Float) {
        zoneLogic?.update(elapsedFrames)

        npcTasks.values.forEach { it.update(elapsedFrames) }
        npcTasks.entries.removeAll { it.value.isComplete() }
    }

    override fun onUnloadZone(unloadingZone: ZoneConfig) {
        zoneLogic?.cleanUp()
        zoneLogic = null

        npcTasks.clear()
    }

    override fun onChangedZones(zoneConfig: ZoneConfig) {
        zoneLogic = when (zoneConfig.zoneId) {
            EastSaru.zoneId -> EastSaru.zoneLogic
            else -> null
        }

        if (zoneConfig.mogHouseSetting != null) {
            createMogHouseActors(zoneConfig)
        }

        ElevatorConfigurations.get(zoneConfig.zoneId)
            .forEach { registerElevator(it) }

        val zoneName = ZoneNameTable.first(zoneConfig)
        ChatLog.addLine("=== Area: $zoneName ===")
    }

    override fun onReturnToHomePoint(actorState: ActorState) {
        ZoneChanger.beginChangeZone(configuration.startingZoneConfig, options = ZoneChangeOptions(fullyRevive = true))
    }

    override fun invokeNpcInteraction(sourceId: ActorId, npcId: ActorId) {
        val npc = ActorStateManager[npcId] ?: return

        if (NpcInteractions.interact(npcId)) {
            return
        }

        if (npc.name.startsWith("Home Point")) {
            HomePointInteraction.onInteraction(npcId)
            return
        } else if (npc.name.contains("Moogle")) {
            ChangeJobInteraction.onInteraction(npcId)
            return
        } else if (npc.name.contains("Door: To Town")) {
            MogHouseDoorInteraction.onInteraction(npcId)
            return
        }

        if (npc.isDoor() || npc.isElevator()) {
            triggerDoorOrElevator(npc)
            return
        }

        UiStateHelper.pushState(UiStateHelper.actionContext, SystemSound.TargetConfirm)
    }

    override fun getActorSpellList(actorId: ActorId): List<SpellSkillId> {
        val actor = ActorStateManager[actorId] ?: return emptyList()

        return if (actor.monsterId != null) {
            MonsterDefinitions[actor.monsterId].mobSpells
        } else if (actor.isPlayer()) {
            DebugHelpers.allValidSpellIds
        } else {
            emptyList()
        }
    }

    override fun getActorAbilityList(actorId: ActorId, abilityType: AbilityType): List<AbilitySkillId> {
        return (DebugHelpers.abilitiesByType[abilityType] ?: emptyList())
    }

    override fun getActorCombatStats(actorId: ActorId): CombatStats {
        val actor = ActorStateManager[actorId] ?: return CombatStats.defaultBaseStats

        val monsterDefinition = MonsterDefinitions[actor.monsterId]
        if (monsterDefinition?.baseCombatStats != null) { return monsterDefinition.baseCombatStats }

        return CombatStats.defaultBaseStats.copy(maxMp = 999)
    }

    override fun getItemDescription(actorId: ActorId, inventoryItem: InventoryItem): InventoryItemDescription {
        return InventoryItemDescription.toDescription(inventoryItem)
    }

    override fun getAutoAttackResult(attacker: ActorState, defender: ActorState): List<AutoAttackResult> {
        val attacks = ArrayList<AutoAttackResult>()

        attacks += DebugHelpers.getAutoAttackResult(attacker, defender, MainHandAutoAttack(subType = AutoAttackSubType.None))
        if (attacker.isDualWield()) {
            attacks += DebugHelpers.getAutoAttackResult(attacker, defender, OffHandAutoAttack)
        } else if (attacker.isHandToHand()) {
            attacks += DebugHelpers.getAutoAttackResult(attacker, defender, MainHandAutoAttack(subType = AutoAttackSubType.H2HOffHand))
            attacks += DebugHelpers.getAutoAttackResult(attacker, defender, MainHandAutoAttack(subType = AutoAttackSubType.H2HKick))
        }

        return attacks
    }

    override fun getAutoAttackInterval(attacker: ActorState): Float {
        return DebugHelpers.getMainDelayInFrames(attacker) + DebugHelpers.getSubDelayInFrames(attacker)
    }

    override fun getRangedAttackResult(attacker: ActorState, defender: ActorState): List<AutoAttackResult> {
        return listOf(DebugHelpers.getAutoAttackResult(attacker, defender, RangedAutoAttack))
    }

    override fun getRangedAttackInterval(attacker: ActorState): Float {
        val range = attacker.getEquipment(EquipSlot.Range)?.info()?.equipmentItemInfo?.weaponInfo?.delay?.toFloat()
        if (range != null) { return range }

        val ammo = attacker.getEquipment(EquipSlot.Ammo)?.info()?.equipmentItemInfo?.weaponInfo?.delay?.toFloat()
        if (ammo != null) { return ammo }

        return Fps.secondsToFrames(3f)
    }

    override fun getAugmentRankPointGain(attacker: ActorState, defender: ActorState, inventoryItem: InventoryItem): Int {
        return 0
    }

    override fun getAugmentRankPointsNeeded(augment: ItemAugment): Int {
        return AbilityInfoTable.getReinforcementPointsTable().table.entries.getOrNull(augment.rankLevel) ?: 0
    }

    override fun onActorDefeated(source: ActorState, target: ActorState, context: AttackContext?): List<Event> {
        return emptyList()
    }

    override fun getExperiencePointGain(attacker: ActorState, defender: ActorState): Int {
        return 500
    }

    override fun getExperiencePointsNeeded(currentLevel: Int): Int {
        return 10 * AbilityInfoTable.getLevelTable().table.entries[currentLevel]
    }

    override fun getMaximumLevel(actorState: ActorState): Int {
        return 50
    }

    override fun getActorEffectTickResult(actorState: ActorState): ActorEffectTickResult {
        return ActorEffectTickResult(hpDelta = 0, mpDelta = 0, tpDelta = 0)
    }

    override fun getSkillRangeInfo(actorState: ActorState, skill: SkillId): SkillRangeInfo {
        return when (skill) {
            is SpellSkillId -> getCastingRangeInfo(skill.id)
            is MobSkillId -> SkillRangeInfo(maxTargetDistance = 10f, effectRadius = 0f, type = AoeType.None)
            is AbilitySkillId -> getAbilityRange(skill)
            is ItemSkillId -> getItemRange(skill.id)
            is RangedAttackSkillId -> SkillRangeInfo(maxTargetDistance = 24f, effectRadius = 0f, type = AoeType.None)
        }
    }

    override fun getSkillEffectedTargetFlags(actorState: ActorState, skill: SkillId): Int? {
        return null
    }

    override fun getSkillBaseCost(actorState: ActorState, skill: SkillId): AbilityCost {
        return when (skill) {
            is SpellSkillId -> AbilityCost(AbilityCostType.Mp, skill.toSpellInfo().mpCost)
            is MobSkillId -> AbilityCost(AbilityCostType.Tp, 0)
            is AbilitySkillId -> getAbilityCost(skill)
            is ItemSkillId -> AbilityCost(AbilityCostType.Tp, 0)
            is RangedAttackSkillId -> AbilityCost(AbilityCostType.Tp, 0)
        }
    }

    override fun getSkillUsedCost(actorState: ActorState, skill: SkillId): AbilityCost {
        return when (skill) {
            is SpellSkillId -> getSkillBaseCost(actorState, skill)
            is MobSkillId -> AbilityCost(AbilityCostType.Tp, actorState.getTp())
            is ItemSkillId -> getSkillBaseCost(actorState, skill)
            is RangedAttackSkillId -> AbilityCost(AbilityCostType.Tp, 0)
            is AbilitySkillId -> {
                val info = skill.toAbilityInfo()
                if (info.type == AbilityType.WeaponSkill) {
                    AbilityCost(AbilityCostType.Tp, actorState.getTp())
                } else {
                    getSkillBaseCost(actorState, skill)
                }
            }
        }
    }

    override fun getSkillCastTime(caster: ActorState, skill: SkillId): Float {
        return when (skill) {
            is SpellSkillId -> getSpellCastTime(skill.id)
            is MobSkillId -> Fps.secondsToFrames(2)
            is AbilitySkillId -> 0f
            is ItemSkillId -> getItemCastTime(skill.id)
            is RangedAttackSkillId -> getRangedAttackInterval(caster)
        }
    }

    override fun getSkillActionLockTime(caster: ActorState, skill: SkillId): Float {
        return Fps.toFrames(1.seconds)
    }

    override fun getSkillRecastTime(caster: ActorState, skill: SkillId): Float {
        return when (skill) {
            is AbilitySkillId -> getAbilityRecast(skill.toAbilityInfo())
            is SpellSkillId -> skill.toSpellInfo().recastDelayInFrames()
            is MobSkillId -> 0f
            is ItemSkillId -> 0f
            is RangedAttackSkillId -> 0f
        }
    }

    fun getSkillChainAttributes(attacker: ActorState, defender: ActorState, skill: SkillId): List<SkillChainAttribute> {
        return if (skill is AbilitySkillId) {
            getApproximateSkillChainAttributes(skill.id)
        } else {
            emptyList()
        }
    }

    override fun getSkillChainDamage(attacker: ActorState, defender: ActorState, skillChainStep: SkillChainStep, closingWeaponSkillDamage: Int): Int {
        val damageMultiplier = (0.5f * skillChainStep.attribute.level) * (1.25f.pow(skillChainStep.step)).coerceAtMost(3f)
        return (closingWeaponSkillDamage * damageMultiplier).roundToInt()
    }

    override fun getSkillChainResult(skillChainRequest: SkillChainRequest): SkillChainState? {
        val skillAttributes = getSkillChainAttributes(skillChainRequest.attacker, skillChainRequest.defender, skillChainRequest.skill)
        if (skillAttributes.isEmpty()) { return null }

        if (skillChainRequest.currentState == null || !skillChainRequest.currentState.isSkillChainWindowOpen()) {
            return SkillChainOpen(attributes = skillAttributes, window = 10.seconds)
        }

        val currentAttributes = when (skillChainRequest.currentState) {
            is SkillChainOpen -> skillChainRequest.currentState.attributes
            is SkillChainStep -> listOf(skillChainRequest.currentState.attribute)
        }

        val nextStepAttribute = findMatchingAttribute(currentAttributes, skillAttributes)
            ?: return SkillChainOpen(attributes = skillAttributes, window = 10.seconds)

        val step = when (skillChainRequest.currentState) {
            is SkillChainOpen -> 1
            is SkillChainStep -> skillChainRequest.currentState.step + 1
        }

        val windowSize = (10.seconds - step.seconds).coerceAtLeast(6.seconds)
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

    override fun getActorCombatBonuses(actorState: ActorState): CombatBonusAggregate {
        val aggregate = CombatBonusAggregate(actorState)

        if (actorState.mountedState != null) {
            aggregate.movementSpeed += 100
        }

        val restingTicks = (actorState.type == ActorType.Pc && actorState.isResting()) || (actorState.isEnemy() && actorState.isIdle())
        if (restingTicks) {
            aggregate.regen += (actorState.getMaxHp() * 0.25f).roundToInt()
            aggregate.refresh += (actorState.getMaxHp() * 0.25f).roundToInt()
        }

        return aggregate
    }

    override fun getCurrentBackgroundMusic(): BackgroundMusicResponse {
        val musicSettings = ZoneSettingsTable[SceneManager.getCurrentScene().config].musicSettings

        val actor = ActorManager.player()

        if (actor.isDisplayedDead()) {
            return BackgroundMusicResponse(musicId = 111, resume = false)
        }

        if (actor.isDisplayEngagedOrEngaging()) {
            val partySize = PartyManager[actor].size()
            val musicId = if (partySize > 1) { musicSettings.battlePartyMusicId } else { musicSettings.battleSoloMusicId }
            return BackgroundMusicResponse(musicId)
        }

        val mount = actor.getMount()
        if (mount != null) {
            val musicId = if (mount.index == 0) { 212 } else { 84 }
            return BackgroundMusicResponse(musicId)
        }

        val fishingState = actor.state.getFishingState()
        if (fishingState != null && fishingState.currentState.active) {
            return when (fishingState.fishSize) {
                FishSize.Small -> BackgroundMusicResponse(129)
                FishSize.Large -> BackgroundMusicResponse(136)
            }
        }

        if (musicSettings.musicId == 110 && FestivalHelper.starlightFestivalActive) {
            return BackgroundMusicResponse(239)
        }

        return BackgroundMusicResponse(musicSettings.musicId)
    }

    private fun getSpellCastTime(spellId: Int): Float {
        val spellInfo = SpellInfoTable[spellId]
        return spellInfo.castTimeInFrames()
    }

    private fun getItemCastTime(itemId: Int): Float {
        return InventoryItems[itemId].usableItemInfo?.castTimeInFrames() ?: 0f
    }

    override fun rollParry(attacker: ActorState, defender: ActorState): Boolean {
        return Random.nextFloat() <= 0.05f
    }

    override fun rollCounter(attacker: ActorState, defender: ActorState): AttackCounterEffect? {
        return null
    }

    override fun rollGuard(attacker: ActorState, defender: ActorState): Boolean {
        return false
    }

    override fun rollEvasion(attacker: ActorState, defender: ActorState): Boolean {
        return Random.nextFloat() <= 0.05f
    }

    override fun rollSpellInterrupted(attacker: ActorState, defender: ActorState): Boolean {
        return Random.nextFloat() > 0.5f
    }

    override fun rollParalysis(actorState: ActorState): Boolean {
        val effect = actorState.getStatusEffects().firstOrNull { it.statusEffect == StatusEffect.Paralysis } ?: return false
        return rollAgainst(effect.potency)
    }

    override fun spawnMonster(monsterId: MonsterId, initialActorState: InitialActorState): ActorPromise {
        return GameEngine.submitCreateActorState(initialActorState)
    }

    override fun onSelectedCrystal(actorId: ActorId, inventoryItem: InventoryItem) {
        val actorState = ActorStateManager[actorId] ?: return
        val synthesisType = SynthesisType.fromItemId(inventoryItem.id)

        val attempt = ActorSynthesisAttempt(
            type = synthesisType,
            context = AttackContext(),
        )

        if (!actorState.initiateAction(attempt)) { return }

        val synthesisScript = EventScript(listOf(
            WaitRoutine(10.seconds),
            RunOnceEventItem { attempt.result = SynthesisResultType.values().random() },
            WaitRoutine(5.seconds),
            RunOnceEventItem { attempt.complete = true },
            RunOnceEventItem { actorState.clearActionState(attempt) },
        ))

        EventScriptRunner.runScript(synthesisScript)
    }

    override fun onCheck(id: ActorId, targetId: ActorId?) {
        TargetAnimationUi.push()
    }

    override fun canFish(sourceState: ActorState): Boolean {
        val rangedItem = sourceState.getEquipment(EquipSlot.Range) ?: return false
        return rangedItem.info().skill() == Skill.Fishing
    }

    fun getTask(actorId: ActorId): NpcTask? {
        return npcTasks[actorId]
    }

    fun <T: NpcTask> getTask(actorId: ActorId, type: KClass<T>): T? {
        return type.safeCast(getTask(actorId))
    }

    fun <T: NpcTask> addTask(actorId: ActorId, npcTask: T): T {
        npcTasks[actorId] = npcTask
        return npcTask
    }

}

private object DebugHelpers {

    private val estimatedSkillChainAttributes = HashMap<Int, List<SkillChainAttribute>>()

    val allValidSpellIds: List<SpellSkillId> by lazy {
        (1 until 1024).asSequence()
            .filter { SpellInfoTable.hasInfo(it) }
            .filter { SpellNameTable.first(it) != "." }
            .filter { SpellNameTable.first(it) != "jet-stream-attack" }
            .filter { SpellNameTable.first(it) != "dummy" }
            .map { SpellSkillId(it) }
            .toList()
    }

    val abilitiesByType: Map<AbilityType, List<AbilitySkillId>> by lazy {
        (1 until 0x1000)
            .filter { AbilityInfoTable.hasInfo(it) }
            .filter { AbilityNameTable.first(it) != "." }
            .map { AbilitySkillId(it) }
            .groupBy { it.toAbilityInfo().type }
    }

    fun getMainHandDamage(actorState: ActorState): Int {
        return delayToDamage(getMainDelayInFrames(actorState))
    }

    fun getSubHandDamage(actorState: ActorState): Int {
        return delayToDamage(getSubDelayInFrames(actorState))
    }

    fun getMainDelayInFrames(actorState: ActorState): Float {
        return actorState.getEquipment(EquipSlot.Main)?.info()?.equipmentItemInfo?.weaponInfo?.delay?.toFloat()
            ?: Fps.secondsToFrames(3)
    }

    fun getSubDelayInFrames(actorState: ActorState): Float {
        return actorState.getEquipment(EquipSlot.Sub)?.info()?.equipmentItemInfo?.weaponInfo?.delay?.toFloat()
            ?: 0f
    }

    fun delayToDamage(delay: Float): Int {
        val base = delay / 60f
        val remainder = (base % 1f)
        val roundUp = if (Random.nextFloat() < remainder) { 1 } else { 0 }
        return floor(base).toInt() + roundUp
    }

    fun getAutoAttackResult(attacker: ActorState, defender: ActorState, type: AutoAttackType): AutoAttackResult {
        val damage = when (type) {
            is MainHandAutoAttack -> getMainHandDamage(attacker)
            is OffHandAutoAttack -> getSubHandDamage(attacker)
            is RangedAutoAttack -> 1
        }

        val criticalHit = Random.nextDouble() < 0.15

        return AutoAttackResult(sourceTpGained = 500, damageDone = damage, criticalHit = criticalHit, targetId = defender.id, targetTpGained = 0, type = type)
    }

    fun getCastingRangeInfo(spellId: Int): SkillRangeInfo {
        val spellInfo = SpellInfoTable[spellId]
        return SkillRangeInfo(maxTargetDistance = 24f, effectRadius = spellInfo.aoeSize.toFloat(), type = spellInfo.aoeType)
    }

    fun getItemRange(itemId: Int): SkillRangeInfo {
        return SkillRangeInfo(maxTargetDistance = 10f, effectRadius = 0f, type = AoeType.None)
    }

    fun getAbilityRange(abilitySkillId: AbilitySkillId): SkillRangeInfo {
        val abilityInfo = abilitySkillId.toAbilityInfo()
        return SkillRangeInfo(maxTargetDistance = 10f, effectRadius = abilityInfo.aoeSize.toFloat(), type = abilityInfo.aoeType)
    }

    fun getAbilityRecast(abilityInfo: AbilityInfo): Float {
        val delayInSeconds = if (abilityInfo.type == AbilityType.WeaponSkill) {
            2.5f
        } else if (abilityInfo.type == AbilityType.PhantomRoll) {
            2.5f
        } else {
            5f
        }

        return Fps.secondsToFrames(delayInSeconds)
    }

    fun getApproximateSkillChainAttributes(abilityId: Int): List<SkillChainAttribute> {
        return estimatedSkillChainAttributes.getOrPut(abilityId) {
            val description = AbilityDescriptionTable.first(abilityId)
            val parts = description.split(":").lastOrNull() ?: return@getOrPut emptyList()

            val words = parts.split(",").map { it.trim() }

            words.mapNotNull { when(it) {
                "Dark" -> Darkness
                "Grav." -> Gravitation
                else -> SkillChainAttribute.values().firstOrNull { sc -> sc.name == it }
            } }.distinct().sortedByDescending { it.level }
        }
    }

    fun getAbilityCost(abilityId: AbilitySkillId): AbilityCost {
        val info = abilityId.toAbilityInfo()

        return if (info.type == AbilityType.PetAbility || info.type == AbilityType.PetWard) {
            AbilityCost(type = AbilityCostType.Mp, value = info.cost)
        } else {
            AbilityCost(type = AbilityCostType.Tp, value = info.cost)
        }
    }

    fun createPlayer(): Event {
        val config = LocalStorage.getPlayerConfiguration()
        val modelLook = config.playerLook.copy()

        val paramZoneId = ExecutionEnvironment.getExecutionParameter("areaId")?.toIntOrNull()
        val zoneSettings = if (paramZoneId != null) {
            ZoneSettings(
                zoneId = paramZoneId,
                subAreaId = ExecutionEnvironment.getExecutionParameter("subAreaId")?.toIntOrNull(),
            )
        } else {
            ZoneSettings(
                zoneId = config.playerPosition.zoneId,
                subAreaId = config.playerPosition.subAreaId,
                mogHouseSetting = config.playerPosition.mogHouseSetting,
            )
        }

        val paramPosX = ExecutionEnvironment.getExecutionParameter("x")?.toDoubleOrNull()
        val paramPosY = ExecutionEnvironment.getExecutionParameter("y")?.toDoubleOrNull()
        val paramPosZ = ExecutionEnvironment.getExecutionParameter("z")?.toDoubleOrNull()
        val position = if (paramPosX != null && paramPosY != null && paramPosZ != null) {
            Vector3f(paramPosX.toFloat(), paramPosY.toFloat(), paramPosZ.toFloat())
        } else {
            Vector3f(config.playerPosition.position)
        }

        val components = listOf(
            Inventory().copyFrom(config.playerInventory),
            Equipment().copyFrom(config.playerEquipment),
        )

        return ActorCreateEvent(InitialActorState(
            name = "Player",
            type = ActorType.Pc,
            position = position,
            rotation = config.playerPosition.rotation,
            zoneSettings = zoneSettings,
            modelLook = modelLook,
            movementController = KeyboardActorController(),
            presetId = ActorStateManager.playerId,
            jobSettings = JobSettings(config.playerJob.mainJob, config.playerJob.subJob),
            jobLevels = config.playerLevels,
            components = components,
            behaviorController = ActorBehaviors.register(PlayerBehaviorId) { AssetViewerPlayerBehavior(it) },
        ))
    }

    fun triggerDoorOrElevator(targetState: ActorState) {
        val interactionId = targetState.getNpcInfo()?.datId ?: return

        if (TargetAnimationUi.isBasicDoor()) {
            GameClient.submitOpenDoor(ActorStateManager.playerId, interactionId)
            PlayerTargetSelector.clearTarget()
            AudioManager.playSystemSoundEffect(SystemSound.MenuClose)
        } else if (TargetAnimationUi.getItems().isNotEmpty()) {
            TargetAnimationUi.push()
        } else {
            PlayerTargetSelector.clearTarget()
            AudioManager.playSystemSoundEffect(SystemSound.MenuClose)
        }
    }

    fun createMogHouseActors(zoneConfig: ZoneConfig) {
        val mogHouseSetting = zoneConfig.mogHouseSetting ?: return

        val doorOffset = if (zoneConfig.mogHouseSetting.secondFloorModel != null) {
            Vector3f(0f, 0f, -3.15f)
        } else {
            when (mogHouseSetting.baseModel) {
                MogHouseConfig.SandoriaS -> Vector3f(-0.5f, 0f, 0f)
                MogHouseConfig.WindurstS, MogHouseConfig.Adoulin -> Vector3f(-1f, 0f, 0f)
                MogHouseConfig.BastokS -> Vector3f(-1.15f, 0f, 0f)
                else -> Vector3f.ZERO
            }
        }

        GameEngine.submitCreateActorState(InitialActorState(
            name = "Door: To Town",
            type = ActorType.StaticNpc,
            position = Vector3f(0f, -1f, -8f) + doorOffset,
            staticPosition = true,
            modelLook = ModelLook.npc(0),
        ))
    }

    fun registerElevator(elevatorConfiguration: ElevatorConfiguration) {
        val scene = SceneManager.getCurrentScene()
        val elevatorPromise = scene.getNpc(elevatorConfiguration.elevatorId)
        val elevatorInteraction = scene.getFirstZoneInteractionBySourceId(elevatorConfiguration.elevatorId) ?: return

        elevatorPromise?.onReady {
            it.components[ElevatorState::class] = ElevatorState(elevatorConfiguration, elevatorInteraction.elevatorSettings)
        }
    }

}

private val skillChainTransitions = mapOf(
    Transfixion to mapOf(Compression to Compression, Scission to Distortion, Reverberation to Reverberation),
    Compression to mapOf(Transfixion to Transfixion, Detonation to Detonation),
    Liquefaction to mapOf(Scission to Scission, Impaction to Fusion),
    Scission to mapOf(Liquefaction to Liquefaction, Detonation to Detonation, Reverberation to Reverberation),
    Reverberation to mapOf(Induration to Induration, Impaction to Impaction),
    Detonation to mapOf(Compression to Gravitation, Scission to Scission),
    Induration to mapOf(Compression to Compression, Reverberation to Fragmentation, Impaction to Impaction),
    Impaction to mapOf(Liquefaction to Liquefaction, Detonation to Detonation),

    Gravitation to mapOf(Distortion to Darkness, Fragmentation to Fragmentation),
    Distortion to mapOf(Gravitation to Darkness, Fusion to Fusion),
    Fusion to mapOf(Gravitation to Gravitation, Fragmentation to Light),
    Fragmentation to mapOf(Distortion to Distortion, Fusion to Light),

    Light to mapOf(Light to Radiance),
    Darkness to mapOf(Darkness to Umbra),
)
