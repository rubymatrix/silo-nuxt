package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.ActorController
import xim.poc.ActorId
import xim.poc.DefaultEnemyController
import xim.poc.game.ActorState
import xim.poc.game.ActorStateManager
import xim.poc.game.CombatBonusAggregate
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillSelection
import xim.poc.game.configuration.SkillSelector
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.behaviors.MobBeastmanParty.getMemberStates
import xim.poc.game.configuration.v0.getEnmityTable
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

private object MobBeastmanParty {
    val members = ArrayList<ActorId>()

    fun getMemberStates() = members.mapNotNull { ActorStateManager[it] }

}

open class MobBeastmanPartyMember(actorState: ActorState): V0MonsterController(actorState) {

    override fun update(elapsedFrames: Float): List<Event> {
        val engaged = getMemberStates().firstOrNull { it.isEngaged() }
        if (engaged != null && engaged.id != actorState.id) {
            actorState.getEnmityTable().syncFrom(engaged.getEnmityTable())
        }

        return super.update(elapsedFrames)
    }

    override fun onInitialized(): List<Event> {
        MobBeastmanParty.members.removeAll { ActorStateManager[it] == null }
        MobBeastmanParty.members += actorState.id
        return super.onInitialized()
    }

    override fun onDefeated(): List<Event> {
        MobBeastmanParty.members -= actorState.id
        return super.onDefeated()
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.autoAttackScale = 0.5f
        super.applyMonsterBehaviorBonuses(aggregate)
    }

}

class MobQuadavIronBehavior(actorState: ActorState): MobBeastmanPartyMember(actorState) {

    private val provokeTimer = FrameTimer(30.seconds).reset(ZERO)

    override fun update(elapsedFrames: Float): List<Event> {
        provokeTimer.update(elapsedFrames)
        return super.update(elapsedFrames)
    }

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(20.seconds)
        actorState.position.copyFrom(Vector3f(x=90.00f,y=-24.02f,z=20.00f))
        actorState.rotation = 0f
        return super.onInitialized()
    }

    override fun wantsToUseSkill(): Boolean {
        return wantsToUseProvoke() || super.wantsToUseSkill()
    }

    override fun onDefeated(): List<Event> {
        ActorStateManager.player().expireStatusEffect(StatusEffect.Provoke)
        return super.onDefeated()
    }

    override fun selectSkill(): SkillSelection? {
        return if (wantsToUseProvoke()) {
            SkillSelector.selectSkill(actorState, listOf(mskillProvoke_1635), ActorStateManager.player())
        } else {
            super.selectSkill()
        }
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill == mskillProvoke_1635) { provokeTimer.reset() }
        return super.onSkillExecuted(primaryTargetContext)
    }

    private fun wantsToUseProvoke(): Boolean {
        return provokeTimer.isReady() && actorState.isEngaged() && !ActorStateManager.player().hasStatusEffect(StatusEffect.Provoke)
    }

}

class MobQuadavVajraBehavior(actorState: ActorState): MobBeastmanPartyMember(actorState) {

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(15.seconds)

        actorState.position.copyFrom(Vector3f(x=82.00f,y=-24.02f,z=20.00f))
        actorState.rotation = 0f
        return super.onInitialized()
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

    override fun wantsToCastSpell(): Boolean {
        if (getSleepingMember() != null) { return true }

        val member = getLowestHppMember() ?: return false
        return member.getHpp() < 0.5f && super.wantsToCastSpell()
    }

    override fun selectSpell(): SkillSelection? {
        val sleepingMember = getSleepingMember()
        if (sleepingMember != null) { return SkillSelection(spellCuragaIV_10, sleepingMember) }

        val member = getLowestHppMember() ?: return null
        return SkillSelector.selectSkill(actorState, listOf(spellCureV_5, spellCureIV_4, spellCureIII_3), member)
    }

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep)
        super.applyMonsterBehaviorBonuses(aggregate)
    }

    private fun getLowestHppMember(): ActorState? {
        return getMemberStates().filter { !it.isDead() }.minByOrNull { it.getHpp() }
    }

    private fun getSleepingMember(): ActorState? {
        return getMemberStates().firstOrNull { it.hasStatusEffect(StatusEffect.Sleep) }
    }

}

class MobYagudoTemplarBehavior(actorState: ActorState): MobBeastmanPartyMember(actorState) {

    override fun onInitialized(): List<Event> {
        actorState.position.copyFrom(Vector3f(x=88.00f,y=-24.02f,z=23.00f))
        actorState.rotation = 0f
        return super.onInitialized()
    }

}

class MobYagudoChanterBehavior(actorState: ActorState): MobBeastmanPartyMember(actorState) {

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(5.seconds).reset(ZERO)

        actorState.position.copyFrom(Vector3f(x=80.00f,y=-24.02f,z=23.00f))
        actorState.rotation = 0f
        return super.onInitialized()
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

}

class MobOrcishVeteranBehavior(actorState: ActorState): MobBeastmanPartyMember(actorState) {

    override fun onInitialized(): List<Event> {
        actorState.position.copyFrom(Vector3f(x=88.00f,y=-24.02f,z=17.00f))
        actorState.rotation = 0f
        return super.onInitialized()
    }

}

class MobOrcishProphetessBehavior(actorState: ActorState): MobBeastmanPartyMember(actorState) {

    override fun onInitialized(): List<Event> {
        spellTimer = FrameTimer(15.seconds)

        actorState.position.copyFrom(Vector3f(x=80.00f,y=-24.02f,z=17.00f))
        actorState.rotation = 0f
        return super.onInitialized()
    }

    override fun onReadyToAutoAttack(): List<Event> {
        return emptyList()
    }

}

class MobBeastmanPartyController(val near: Float = 2f, val far: Float = 4f): ActorController {

    private val delegate = DefaultEnemyController().also {
        it.followDistanceNear = near
        it.followDistanceFar = far
    }

    override fun getVelocity(actorState: ActorState, elapsedFrames: Float): Vector3f {
        return delegate.getVelocity(actorState, elapsedFrames)
    }

}