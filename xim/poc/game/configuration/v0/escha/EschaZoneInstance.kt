package xim.poc.game.configuration.v0.escha

import xim.math.Vector3f
import xim.poc.game.*
import xim.poc.game.configuration.MonsterSpawnerInstance
import xim.poc.game.configuration.v0.BossSpawner
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.ZoneLogic
import xim.poc.game.configuration.v0.events.KeyItemGainEvent
import xim.poc.game.configuration.v0.navigation.BattleLocationNavigator
import xim.poc.game.configuration.v0.navigation.BattleLocationPather
import xim.poc.game.configuration.v0.zones.PathingSettings
import xim.poc.game.event.Event
import xim.poc.tools.ZoneConfig
import xim.poc.ui.MapDrawer
import kotlin.math.abs

private val navigators = HashMap<PathingSettings, BattleLocationNavigator>()

private class BindingAreaInstance(val configuration: EschaBindingArea) {

    private val floorEntities = ArrayList<FloorEntity>()
    private val navigator: BattleLocationNavigator

    init {
        floorEntities += when (configuration) {
            is EschaBossAreaConfiguration -> BossSpawner(configuration.bossSpawnerDefinition)
            is EschaMonsterAreaConfiguration -> MonsterSpawnerInstance(configuration.monsterSpawnerDefinition)
        }

        navigator = navigators.getOrPut(configuration.pathingSettings) {
            BattleLocationPather.generateNavigator(configuration.pathingSettings)
        }
    }

    fun update(elapsedFrames: Float) {
        navigator.update(elapsedFrames)
        floorEntities.forEach { it.update(elapsedFrames) }
        configuration.boundaries.forEach { it.apply(ActorStateManager.player().position) }
    }

    fun cleanup() {
        floorEntities.forEach { it.cleanup() }
    }

    fun getNavigator(): BattleLocationNavigator {
        return navigator
    }

}

class EschaZoneInstance(val definition: EschaConfiguration): ZoneLogic {

    private val floorEntities = ArrayList<FloorEntity>()
    private var currentBindingArea: BindingAreaInstance? = null

    init {
        val entrance = EschaExitEntity(definition.entrancePosition)
        entrance.promise.onReady { it.faceToward(definition.startingPosition.startPosition!!) }
        floorEntities += entrance

        definition.portals.forEach { floorEntities += EschaPortalEntity(it, definition) }

        floorEntities += definition.entityFactory.invoke()

        ActorStateManager.player().gainStatusEffect(StatusEffect.Vorseal)
    }

    override fun update(elapsedFrames: Float) {
        definition.staticBoundaries.forEach { it.apply(ActorStateManager.player().position) }
        setCurrentMonsterArea()
        currentBindingArea?.update(elapsedFrames)
        floorEntities.forEach { it.update(elapsedFrames) }
        addMapMarkers()
    }

    override fun cleanUp() {
        currentBindingArea?.cleanup()
        floorEntities.forEach { it.cleanup() }
        ActorStateManager.player().expireStatusEffect(StatusEffect.Vorseal)
    }

    override fun getCurrentNavigator(): BattleLocationNavigator? {
        return currentBindingArea?.getNavigator()
    }

    override fun getEntryPosition(): ZoneConfig {
        return definition.startingPosition
    }

    override fun toNew(): ZoneLogic {
        return EschaZoneInstance(definition)
    }

    override fun onActorDefeated(source: ActorState, target: ActorState, context: AttackContext?): List<Event> {
        val keyItem = definition.keyItemDropTable[target.monsterId] ?: return emptyList()
        return listOf(KeyItemGainEvent(keyItemId = keyItem, quantity = 1, context = context))
    }

    fun applyVorsealBonus(aggregate: CombatBonusAggregate) {
        EschaVorsealBonusApplier.compute(definition, aggregate)
    }

    private fun setCurrentMonsterArea() {
        val playerPosition = ActorStateManager.player().position
        val closestArea = definition.bindingAreas.minByOrNull { Vector3f.distanceSquared(it.center, playerPosition) }

        if (closestArea == null) {
            discardCurrentMonsterArea()
            return
        }

        val currentArea = currentBindingArea ?: return setupNewBindingArea(closestArea)
        if (currentArea.configuration == closestArea) { return }

        // Tolerance check to avoid rapid switching
        val distanceToClosestArea = Vector3f.distance(playerPosition, closestArea.center)
        val distanceToCurrentArea = Vector3f.distance(playerPosition, currentArea.configuration.center)
        if (abs(distanceToClosestArea - distanceToCurrentArea) < 5f) { return }

        discardCurrentMonsterArea()
        setupNewBindingArea(closestArea)
    }

    private fun discardCurrentMonsterArea() {
        currentBindingArea?.cleanup()
        currentBindingArea = null
    }

    private fun setupNewBindingArea(configuration: EschaBindingArea) {
        currentBindingArea = BindingAreaInstance(configuration)
    }

    private fun addMapMarkers() {
        for (configuration in definition.portals) {
            MapDrawer.drawMapMarker(configuration.location, uiElementIndex = 154)
        }

        definition.bindingAreas.filterIsInstance<EschaBossAreaConfiguration>().forEach {
            if (!it.hidden) { MapDrawer.drawMapMarker(it.center, uiElementIndex = 151) }
        }

        definition.extraMapMarkers.forEach(MapDrawer::drawMapMarker)
    }

}