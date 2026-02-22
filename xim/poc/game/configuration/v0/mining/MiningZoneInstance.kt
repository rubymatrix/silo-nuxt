package xim.poc.game.configuration.v0.mining

import xim.poc.ModelLook
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.MonsterSpawnerDefinition
import xim.poc.game.configuration.MonsterSpawnerInstance
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.navigation.BattleLocationNavigator
import xim.poc.game.configuration.v0.navigation.BattleLocationPather
import xim.poc.game.configuration.v0.tower.FloorEntrance
import xim.poc.game.event.InitialActorState
import xim.poc.tools.ZoneConfig
import xim.util.Fps
import xim.util.PI_f
import kotlin.math.pow
import kotlin.math.roundToInt

data class MiningProwessBonus(
    val miningPowerBonus: Int = 1,
    val expBonus: Int = 1,
    val attemptBonus: Int = 0,
    val yieldBonus: Int = 0,
)

class MiningZoneInstance(val definition: MiningZoneConfiguration): ZoneLogic {

    private val navigator: BattleLocationNavigator
    private val floorEntities = ArrayList<FloorEntity>()

    init {
        val entrance = FloorEntrance(definition.entrancePosition, definition.entranceLook)
        entrance.promise.onReady { it.faceToward(definition.startingPosition.startPosition!!) }
        floorEntities += entrance

        floorEntities += MonsterSpawnerInstance(
            MonsterSpawnerDefinition(
                spawnArea = definition.spawnerArea,
                maxMonsters = definition.maxActive,
                maxSpawnCount = definition.maxSpawnCount,
                spawnDelay = Fps.secondsToFrames(15),
                providerFactory = definition.monsterProviderFactory,
            )
        )

        floorEntities += GatheringNodeSpawner(definition.gatheringConfiguration)

        navigator = BattleLocationPather.generateNavigator(definition.pathingSettings)

        GameEngine.submitCreateActorState(InitialActorState(
            name = "Mining Manual",
            modelLook = ModelLook.npc(0x928),
            type = ActorType.StaticNpc,
            position = definition.helpBookPosition,
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, MiningHelpInteraction)
            it.faceToward(ActorStateManager.player())
            it.rotation += PI_f
        }
    }

    override fun update(elapsedFrames: Float) {
        val playerPosition = ActorStateManager.player().position
        definition.boundaries.forEach { it.apply(playerPosition) }
        floorEntities.forEach { it.update(elapsedFrames) }
        navigator.update(elapsedFrames)

        val monsterSpawner = floorEntities.firstOrNull { it is MonsterSpawnerInstance }
        if (monsterSpawner is MonsterSpawnerInstance && monsterSpawner.getTotalDefeated() > 0) {
            val status = ActorStateManager.player().getOrGainStatusEffect(StatusEffect.Prowess)
            status.counter = monsterSpawner.getTotalDefeated()
        }

        MiningExpUi.draw()
    }

    override fun cleanUp() {
        floorEntities.forEach { it.cleanup() }
        ActorStateManager.player().expireStatusEffect(StatusEffect.Prowess)
    }

    override fun getCurrentNavigator(): BattleLocationNavigator? {
        return navigator
    }

    override fun getEntryPosition(): ZoneConfig {
        return definition.startingPosition
    }

    override fun toNew(): ZoneLogic {
        return MiningZoneInstance(definition)
    }

    fun getPlayerMiningPower(): Int {
        val gameState = GameV0SaveStateHelper.getState()

        val prowessBonus = getProwessBonus()
        return prowessBonus.miningPowerBonus * miningPowerScalingFn(gameState.mining.level)
    }

    fun getAttemptCount(): Int {
        return 3 + getProwessBonus().attemptBonus
    }

    fun getItemHitRate(itemId: Int): Int {
        val itemDefinition = ItemDefinitions.definitionsById[itemId] ?: return 0

        val itemRequirement = miningPowerScalingFn(itemDefinition.internalLevel)
        val playerPower = getPlayerMiningPower()

        val rawHitRate = 100f * playerPower / itemRequirement
        return if (rawHitRate < 10f) { 0 } else { rawHitRate.roundToInt().coerceAtMost(100) }
    }

    private fun miningPowerScalingFn(level: Int): Int {
        val power = 12.5 * (4.0.pow(1.0/5.0)).pow(level)
        return power.roundToInt()
    }

    fun getProwessBonus(): MiningProwessBonus {
        val prowessLevel = ActorStateManager.player().getStatusEffect(StatusEffect.Prowess)?.counter ?: 0

        var bonus = MiningProwessBonus()

        if (prowessLevel >= 5) {
            bonus = bonus.copy(miningPowerBonus = 4)
        } else if (prowessLevel >= 1) {
            bonus = bonus.copy(miningPowerBonus = 2)
        }

        if (prowessLevel >= 6) {
            bonus = bonus.copy(attemptBonus = 2)
        } else if (prowessLevel >= 2) {
            bonus = bonus.copy(attemptBonus = 1)
        }

        if (prowessLevel >= 7) {
            bonus = bonus.copy(expBonus = 4)
        } else if (prowessLevel >= 3) {
            bonus = bonus.copy(expBonus = 2)
        }

        if (prowessLevel >= 8) {
            bonus = bonus.copy(yieldBonus = 2)
        } else if (prowessLevel >= 4) {
            bonus = bonus.copy(yieldBonus = 1)
        }

        return bonus
    }

}