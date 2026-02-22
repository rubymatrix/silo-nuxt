package xim.poc.game

import xim.poc.ActionTargetFilter
import xim.poc.Actor
import xim.poc.ActorId
import xim.poc.ModelLook
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.constants.AbilitySkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.event.*
import xim.poc.tools.DestinationZoneConfig
import xim.resource.DatId
import xim.resource.EquipSlot

object GameClient {

    fun submitUpdateBaseLook(actor: ActorId, modelLook: ModelLook) {
        GameEngine.submitEvent(ActorUpdateBaseLookEvent(actor, modelLook))
    }

    fun submitPlayerEngage(targetId: ActorId? = null) {
        val player = ActorStateManager.player()
        val target = targetId ?: player.targetState.targetId
        GameEngine.submitEvent(BattleEngageEvent(player.id, target))
    }

    fun submitPlayerDisengage() {
        GameEngine.submitEvent(BattleDisengageEvent(ActorStateManager.playerId))
    }

    fun submitStartCasting(sourceId: ActorId, targetId: ActorId, skill: SpellSkillId) {
        submitSkill(sourceId = sourceId, targetId = targetId, skillEvent = CastSpellStart(sourceId, targetId, skill))
    }

    fun submitStartRangedAttack(sourceId: ActorId, targetId: ActorId) {
        submitSkill(sourceId = sourceId, targetId = targetId, skillEvent = CastRangedAttackStart(sourceId, targetId))
    }

    fun submitStartUsingItem(sourceId: ActorId, target: ActorId, inventoryItem: InventoryItem) {
        GameEngine.submitEvent(CastItemStart(sourceId, target, inventoryItem.internalId))
    }

    fun submitUseAbility(skill: AbilitySkillId, sourceId: ActorId, targetId: ActorId) {
        submitSkill(sourceId = sourceId, targetId = targetId, skillEvent = CastAbilityStart(sourceId, targetId, skill))
    }

    fun submitMountEvent(source: Actor, index: Int) {
        if (source.getMount() != null) {
            GameEngine.submitEvents(listOf(ActorDismountEvent(source.id), ActorMountEvent(source.id, index)))
        } else {
            GameEngine.submitEvent(ActorMountEvent(source.id, index))
        }
    }

    fun submitDismountEvent(source: Actor) {
        GameEngine.submitEvent(ActorDismountEvent(source.id))
    }

    fun submitFacingUpdate(source: ActorState, target: ActorState?) {
        target ?: return
        source.faceToward(target)
    }

    fun submitTargetUpdate(sourceId: ActorId, targetId: ActorId?) {
        GameEngine.submitEvent(ActorTargetEvent(sourceId, targetId))
    }

    fun submitClearTarget(sourceId: ActorId) {
        submitTargetUpdate(sourceId, null)
    }
    
    fun submitReleaseTrust(id: ActorId, target: ActorId?) {
        target ?: return
        GameEngine.submitEvent(TrustReleaseEvent(id, target))
    }

    fun toggleResting(actor: ActorId) {
        val state = ActorStateManager[actor] ?: return
        if (state.isResting()) { stopResting(actor) } else { startResting(actor) }
    }

    private fun startResting(actor: ActorId) {
        GameEngine.submitEvent(RestingStartEvent(actor))
    }

    private fun stopResting(actor: ActorId) {
        GameEngine.submitEvent(RestingEndEvent(actor))
    }

    fun submitEquipItem(actor: ActorId, equipSlot: EquipSlot, internalItemId: InternalItemId?) {
        GameEngine.submitEvent(ActorEquipEvent(actor, mapOf(equipSlot to internalItemId)))
    }

    fun submitRequestZoneChange(destinationZoneConfig: DestinationZoneConfig) {
        GameEngine.submitEvent(ChangeZoneEvent(ActorStateManager.playerId, destinationZoneConfig))
    }

    fun toggleTargetLock(sourceId: ActorId) {
        val state = ActorStateManager[sourceId] ?: return
        GameEngine.submitEvent(ActorTargetEvent(sourceId, state.targetState.targetId, !state.targetState.locked))
    }

    fun submitChangeJob(sourceId: ActorId, mainJob: Job? = null, subJob: Job? = null) {
        GameEngine.submitEvent(ChangeJobEvent(sourceId, mainJob?.index, subJob?.index))
    }

    fun submitDiscardItem(actor: ActorId, item: InventoryItem) {
        GameEngine.submitEvent(InventoryDiscardEvent(actor, item.internalId))
    }

    fun submitInventorySort(actor: ActorId) {
        GameEngine.submitEvent(InventorySortEvent(actor))
    }

    fun submitOpenDoor(actor: ActorId, doorId: DatId) {
        GameEngine.submitEvent(DoorOpenEvent(actor, doorId))
    }

    fun submitItemTransferEvent(sourceId: ActorId, destinationId: ActorId, item: InventoryItem) {
        GameEngine.submitEvent(InventoryItemTransferEvent(sourceId, destinationId, item.internalId))
    }

    fun submitExpireBuff(sourceId: ActorId, statusEffect: StatusEffect) {
        GameEngine.submitEvent(ActorExpireBuffEvent(sourceId, statusEffect))
    }

    fun submitInteractWithNpc(sourceId: ActorId, npcId: ActorId) {
        GameState.getGameMode().invokeNpcInteraction(sourceId, npcId)
    }

    fun submitFishingRequest(sourceId: ActorId) {
        GameEngine.submitEvent(FishingStartEvent(sourceId))
    }

    private fun submitSkill(sourceId: ActorId, targetId: ActorId, skillEvent: Event) {
        val source = ActorStateManager[sourceId] ?: return
        val target = ActorStateManager[targetId] ?: return

        val events = ArrayList<Event>()

        if (!source.isEngaged() && ActionTargetFilter.areEnemies(source, target)) {
            events += BattleEngageEvent(sourceId, targetId)
        }

        events += skillEvent
        GameEngine.submitEvents(events)
    }

    fun submitSitChairRequest(playerId: ActorId, index: Int) {
        GameEngine.submitEvent(SitChairStartEvent(playerId, index))
    }

}