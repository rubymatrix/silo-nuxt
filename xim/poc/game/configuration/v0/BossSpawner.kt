package xim.poc.game.configuration.v0

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.NoOpActorController
import xim.poc.audio.SystemSound
import xim.poc.game.*
import xim.poc.game.actor.components.discardNotEquippedItems
import xim.poc.game.configuration.MonsterDefinitions
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.NoActionBehaviorId
import xim.poc.game.configuration.v0.BossSpawnerConsumptionMode.OnDefeat
import xim.poc.game.configuration.v0.BossSpawnerConsumptionMode.OnSpawn
import xim.poc.game.configuration.v0.escha.EschaDifficulty
import xim.poc.game.configuration.v0.escha.EschaVorsealBonusApplier.getClearedDifficulties
import xim.poc.game.configuration.v0.escha.plus
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.poc.game.configuration.v0.interactions.NpcInteraction
import xim.poc.game.configuration.v0.interactions.Quantity
import xim.poc.game.event.InitialActorState
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.poc.ui.ShiftJis
import xim.resource.InventoryItems
import xim.resource.KeyItemId
import xim.resource.KeyItemTable
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.seconds

enum class BossSpawnerConsumptionMode {
    OnSpawn,
    OnDefeat,
}

private class SpawnedBoss(val promise: ActorPromise, val definition: BossDefinition)

private class SpawnBossCountdown(val frameTimer: FrameTimer, val definition: BossDefinition)

class BossDefinition(
    val bossMonsterId: MonsterId,
    val requiredItemIds: Map<ItemId, Quantity> = emptyMap(),
    val requiredKeyItemIds: Map<KeyItemId, Quantity> = emptyMap(),
)

class BossSpawnerDefinition(
    val position: Vector3f,
    val bossDefinitions: List<BossDefinition>,
    val model: BossSpawnerModel,
    val consumptionMode: BossSpawnerConsumptionMode = OnSpawn,
    val difficultyModes: List<EschaDifficulty> = emptyList(),
    val difficultyTextProvider: ((MonsterId, EschaDifficulty) -> String)? = null,
)

class BossSpawner(val definition: BossSpawnerDefinition): FloorEntity {

    private val spawnerPromise = createSpawner()

    private var spawnedBossCountdown: SpawnBossCountdown? = null
    private var spawnedBoss: SpawnedBoss? = null

    override fun update(elapsedFrames: Float) {
        val spawnerActor = ActorManager[spawnerPromise.getIfReady()]
        spawnerActor?.renderState?.forceHideShadow = true

        val currentBoss = spawnedBoss
        if (currentBoss != null && currentBoss.promise.isObsolete()) { onBossDefeated(currentBoss.definition) }

        val currentCountdown = spawnedBossCountdown
        if (currentCountdown != null) { updateCountdown(currentCountdown, elapsedFrames) }
    }

    override fun cleanup() {
        spawnerPromise.cleanup()
        spawnedBoss?.promise.cleanup()
    }

    fun hasActiveBoss(): Boolean {
        return spawnedBoss != null
    }

    fun initiateSpawnBoss(bossDefinition: BossDefinition) {
        spawnerPromise.onReady(definition.model::hide)
        spawnedBossCountdown = SpawnBossCountdown(FrameTimer(3.seconds), bossDefinition)
    }

    private fun updateCountdown(currentCountdown: SpawnBossCountdown, elapsedFrames: Float) {
        currentCountdown.frameTimer.update(elapsedFrames)
        if (!currentCountdown.frameTimer.isReady()) { return }

        createBossState(currentCountdown.definition)
        spawnedBossCountdown = null
    }

    private fun createSpawner(): ActorPromise {
        return GameEngine.submitCreateActorState(
            InitialActorState(
                name = "???",
                type = ActorType.StaticNpc,
                position = definition.position,
                modelLook = definition.model.look,
                movementController = NoOpActorController(),
                behaviorController = NoActionBehaviorId,
                appearanceState = 1,
            )
        ).onReady {
            GameV0.interactionManager.registerInteraction(it.id, BossSpawnerInteraction(this))
        }
    }

    private fun createBossState(bossDefinition: BossDefinition) {
        val canSpawnBoss = when (definition.consumptionMode) {
            OnSpawn -> consumeRequiredItems(bossDefinition)
            OnDefeat -> hasRequiredItems(bossDefinition)
        }

        if (!canSpawnBoss) { return }

        val bossPromise = V0MonsterHelper.spawnMonster(
            monsterDefinition = MonsterDefinitions[bossDefinition.bossMonsterId],
            position = definition.position,
        ).onReady {
            it.faceToward(ActorStateManager.player())
            it.getEnmityTable().add(ActorStateManager.playerId, ActorEnmity(totalEnmity = 1))
        }

        spawnedBoss = SpawnedBoss(bossPromise, bossDefinition)
    }

    private fun onBossDefeated(bossDefinition: BossDefinition) {
        if (definition.consumptionMode == OnDefeat) { consumeRequiredItems(bossDefinition) }

        spawnerPromise.onReady(definition.model::show)
        spawnedBoss = null
    }

    fun hasRequiredItems(bossDefinition: BossDefinition): Boolean {
        val player = ActorStateManager.player()
        val hasItems = bossDefinition.requiredItemIds.all { player.discardNotEquippedItems(it.key, it.value, validateOnly = true) }

        val keyItems = GameV0SaveStateHelper.getState().keyItems
        val hasKeyItems = bossDefinition.requiredKeyItemIds.all { keyItems.getOrElse(it.key) { 0 } >= it.value }

        return hasItems && hasKeyItems
    }

    private fun consumeRequiredItems(definition: BossDefinition): Boolean {
        var success = true
        for ((requiredItemId, requiredItemQuantity) in definition.requiredItemIds) {
            success = ActorStateManager.player().discardNotEquippedItems(requiredItemId, requiredItemQuantity) && success
        }

        val state = GameV0SaveStateHelper.getState()
        for ((requiredKeyItem, requiredQuantity) in definition.requiredKeyItemIds) {
            success = state.consumeKeyItem(requiredKeyItem, requiredQuantity) && success
        }

        return success
    }

}

class BossSpawnerInteraction(val bossSpawner: BossSpawner): NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        if (bossSpawner.hasActiveBoss()) {
            ChatLog("Now is not the time!", ChatLogColor.Error)
            return
        }

        UiStateHelper.openQueryMode(prompt = "Which opponent?", options = getOptions(), callback = this::handleResponse)
    }

    private fun getOptions(): List<QueryMenuOption> {
        val options = ArrayList<QueryMenuOption>()

        bossSpawner.definition.bossDefinitions.forEachIndexed { index, bossDefinition ->
            val monsterDefinition = MonsterDefinitions[bossDefinition.bossMonsterId]
            val prefix = if (bossSpawner.hasRequiredItems(bossDefinition)) { "${ShiftJis.colorItem}" } else { "" }
            options += QueryMenuOption(text = "$prefix${monsterDefinition.name}${ShiftJis.colorClear}", value = index)
        }

        return options
    }

    private fun handleResponse(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) { return QueryMenuResponse.pop }

        val choice = bossSpawner.definition.bossDefinitions.getOrNull(queryMenuOption.value) ?: return QueryMenuResponse.pop

        if (choice.requiredItemIds.isNotEmpty() || choice.requiredKeyItemIds.isNotEmpty()) {
            val itemPrompts = choice.requiredItemIds.entries.map {
                val itemInfo = InventoryItems[it.key]
                val itemLogName = if (it.value > 1) { itemInfo.logNamePlural } else { itemInfo.logName }
                val itemQuantity = if (it.value > 1) { "${it.value} " } else { "" }
                "$itemQuantity ${ShiftJis.colorItem}${itemLogName}${ShiftJis.colorClear}"
            }

            val keyItemPrompts = choice.requiredKeyItemIds.entries.map {
                val itemLogName = KeyItemTable.getName(it.key, it.value)
                val itemQuantity = if (it.value > 1) { "${it.value} " } else { "" }
                "$itemQuantity ${ShiftJis.colorKey}${itemLogName}${ShiftJis.colorClear}"
            }

            val allItemPrompts = (itemPrompts + keyItemPrompts).joinToString(", ")
            val plural = if (itemPrompts.size + keyItemPrompts.size > 1) { "are" } else { "is" }

            ChatLog("You sense that ${ShiftJis.leftRoundedBracket} $allItemPrompts ${ShiftJis.rightRoundedBracket} $plural needed...")
        }

        if (!bossSpawner.hasRequiredItems(choice)) {
            return QueryMenuResponse.noop(SystemSound.Invalid)
        }

        val monsterDefinition = MonsterDefinitions[choice.bossMonsterId]

        if (bossSpawner.definition.difficultyModes.isNotEmpty()) {
            val options = ArrayList<QueryMenuOption>()
            options += QueryMenuOption("Go back.", -1)

            val clearedDifficulties = getClearedDifficulties(choice.bossMonsterId)
            options += bossSpawner.definition.difficultyModes.map {
                val hasDefeated = clearedDifficulties.contains(it)
                val difficultyText = bossSpawner.definition.difficultyTextProvider?.invoke(choice.bossMonsterId, it) ?: ""

                val color = if (hasDefeated) { "${ShiftJis.colorGold}" } else { "${ShiftJis.colorClear}" }
                QueryMenuOption("${color}${it.displayName}${difficultyText}", it.value)
            }

            UiStateHelper.openQueryMode(prompt = "Spawn ${monsterDefinition.name}? Difficulty:", options = options) { onConfirmationWithDifficulty(choice, it) }
        } else {
            UiStateHelper.openQueryMode(prompt = "Spawn ${monsterDefinition.name}?", options = listOf(
                QueryMenuOption("Yes.", 0),
                QueryMenuOption("No.", -1),
            )) { onConfirmation(choice, it) }
        }

        return QueryMenuResponse.noop
    }

    private fun onConfirmation(choice: BossDefinition, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) {
            return QueryMenuResponse.pop(SystemSound.MenuClose)
        }

        GameClient.submitTargetUpdate(ActorStateManager.playerId, null)
        bossSpawner.initiateSpawnBoss(choice)

        return QueryMenuResponse.popAll
    }

    private fun onConfirmationWithDifficulty(choice: BossDefinition, queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null || queryMenuOption.value < 0) {
            return QueryMenuResponse.pop(SystemSound.MenuClose)
        }

        val difficulty = EschaDifficulty.values().firstOrNull { it.value == queryMenuOption.value } ?: return QueryMenuResponse.pop
        val adjustedChoice = BossDefinition(choice.bossMonsterId + difficulty, choice.requiredItemIds)

        GameClient.submitTargetUpdate(ActorStateManager.playerId, null)
        bossSpawner.initiateSpawnBoss(adjustedChoice)

        return QueryMenuResponse.popAll
    }

}