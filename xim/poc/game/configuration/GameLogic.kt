package xim.poc.game.configuration

import xim.math.Vector3f
import xim.poc.ActorId
import xim.poc.audio.BackgroundMusicResponse
import xim.poc.game.*
import xim.poc.game.actor.components.InternalItemId
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.actor.components.ItemAugment
import xim.poc.game.configuration.constants.AbilitySkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.SpellSkillId
import xim.poc.game.event.*
import xim.poc.tools.ZoneConfig
import xim.poc.ui.BonusDescription
import xim.poc.ui.InventoryItemDescription
import xim.resource.AbilityCost
import xim.resource.AbilityType
import xim.resource.EffectRoutineInstance
import xim.resource.EquipSlot

typealias GameModeId = String

class GameConfiguration(
    val gameModeId: GameModeId,
    val startingZoneConfig: ZoneConfig,
    val debugControlsEnabled: Boolean,
)

interface GameLogic {

    val configuration: GameConfiguration

    fun setup(): List<Event>

    fun update(elapsedFrames: Float)

    fun drawUi() = Unit

    fun onUnloadZone(unloadingZone: ZoneConfig)

    fun onChangedZones(zoneConfig: ZoneConfig)

    fun onReturnToHomePoint(actorState: ActorState)

    fun invokeNpcInteraction(sourceId: ActorId, npcId: ActorId)

    fun getActorSpellList(actorId: ActorId): List<SpellSkillId>

    fun getActorAbilityList(actorId: ActorId, abilityType: AbilityType): List<AbilitySkillId>

    fun getActorCombatStats(actorId: ActorId): CombatStats

    fun canObtainItem(sourceState: ActorState?, destinationState: ActorState, inventoryItem: InventoryItem) = true

    fun canFish(sourceState: ActorState): Boolean = false

    fun getItemDescription(actorId: ActorId, inventoryItem: InventoryItem): InventoryItemDescription

    fun getAutoAttackResult(attacker: ActorState, defender: ActorState): List<AutoAttackResult>

    fun getAutoAttackInterval(attacker: ActorState): Float

    fun getRangedAttackResult(attacker: ActorState, defender: ActorState): List<AutoAttackResult>

    fun getRangedAttackInterval(attacker: ActorState): Float

    fun getAugmentRankPointGain(attacker: ActorState, defender: ActorState, inventoryItem: InventoryItem): Int

    fun getAugmentRankPointsNeeded(augment: ItemAugment): Int

    fun onAugmentRankUp(actorState: ActorState, inventoryItem: InventoryItem) { }

    fun onActorDefeated(source: ActorState, target: ActorState, context: AttackContext?): List<Event>

    fun getExperiencePointGain(attacker: ActorState, defender: ActorState): Int

    fun getExperiencePointsNeeded(currentLevel: Int): Int

    fun getMaximumLevel(actorState: ActorState): Int

    fun getActorEffectTickResult(actorState: ActorState): ActorEffectTickResult

    fun getSkillRangeInfo(actorState: ActorState, skill: SkillId): SkillRangeInfo

    fun getSkillEffectedTargetFlags(actorState: ActorState, skill: SkillId): Int?

    fun getSkillBaseCost(actorState: ActorState, skill: SkillId): AbilityCost

    fun getSkillUsedCost(actorState: ActorState, skill: SkillId): AbilityCost

    fun getSkillCastTime(caster: ActorState, skill: SkillId): Float

    fun getSkillActionLockTime(caster: ActorState, skill: SkillId): Float

    fun getSkillRecastTime(caster: ActorState, skill: SkillId): Float

    fun getSkillChainDamage(attacker: ActorState, defender: ActorState, skillChainStep: SkillChainStep, closingWeaponSkillDamage: Int): Int

    fun getSkillChainResult(skillChainRequest: SkillChainRequest): SkillChainState?

    fun getSkillMovementLock(actorState: ActorState, skill: SkillId): Int? = null

    fun getActorCombatBonuses(actorState: ActorState): CombatBonusAggregate

    fun getCurrentBackgroundMusic(): BackgroundMusicResponse

    fun rollParry(attacker: ActorState, defender: ActorState): Boolean

    fun rollCounter(attacker: ActorState, defender: ActorState): AttackCounterEffect?

    fun rollGuard(attacker: ActorState, defender: ActorState): Boolean

    fun rollEvasion(attacker: ActorState, defender: ActorState): Boolean

    fun rollSpellInterrupted(attacker: ActorState, defender: ActorState): Boolean

    fun rollParalysis(actorState: ActorState): Boolean

    fun getMaxTp(actor: ActorState): Int {
        return 3000
    }

    fun spawnMonster(monsterId: MonsterId, initialActorState: InitialActorState): ActorPromise

    fun onSelectedCrystal(actorId: ActorId, inventoryItem: InventoryItem)

    fun pathNextPosition(actorState: ActorState, elapsedFrames: Float, desiredEnd: Vector3f): Vector3f? = null

    fun getWanderPosition(monsterState: ActorState, desiredEnd: Vector3f): Vector3f = desiredEnd

    fun onCheck(id: ActorId, targetId: ActorId?)

    fun onGearChange(actorState: ActorState, equipment: Map<EquipSlot, InternalItemId?>): List<Event> = emptyList()

    fun getStatusDescription(actorState: ActorState, status: StatusEffectState): String? = null

    fun getActorEffectsDescription(player: ActorState): List<BonusDescription>? = null

    fun onEffectRoutineStarted(effectRoutineInstance: EffectRoutineInstance, initialSequence: EffectRoutineInstance.EffectSequence) = Unit

}