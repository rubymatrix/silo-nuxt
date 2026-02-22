package xim.poc.game.configuration.v0.behaviors

import xim.math.Vector3f
import xim.poc.game.*
import xim.poc.game.configuration.*
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.SkillId
import xim.poc.game.configuration.constants.mskillSeedAutoAttack_2159
import xim.poc.game.configuration.constants.mskillSeedofDeception_2160
import xim.poc.game.configuration.v0.V0MonsterHelper
import xim.poc.game.configuration.v0.constants.mobSeedThrall_85
import xim.poc.game.configuration.v0.syncEnmity
import xim.poc.game.event.Event

class MobSeedBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun update(elapsedFrames: Float): List<Event> {
        actorState.appearanceState = if (actorState.isEngaged()) { 1 } else { 0 }
        return super.update(elapsedFrames)
    }

}

class MobSeedCrystalBehavior(actorState: ActorState): V0MonsterController(actorState) {

    private var child: ActorPromise? = null
    private var wantsToCastSpell = false

    override fun applyMonsterBehaviorBonuses(aggregate: CombatBonusAggregate) {
        aggregate.fullResist(StatusEffect.Sleep, StatusEffect.Petrify)
        aggregate.knockBackResistance += 100
        aggregate.refresh += 10
        aggregate.fastCast += 10
        aggregate.spellInterruptDown += 100

        if (child.isAlive()) {
            aggregate.physicalDamageTaken -= 50
            aggregate.magicalDamageTaken -= 50
        }
    }

    override fun getSkills(): List<SkillId> {
        val base = super.getSkills()
        return if (child.isAlive()) { base - mskillSeedofDeception_2160 } else { base }
    }

    override fun wantsToCastSpell(): Boolean {
        return wantsToCastSpell
    }

    override fun selectSpell(): SkillSelection? {
        wantsToCastSpell = false
        return super.selectSpell()
    }

    override fun onSkillExecuted(primaryTargetContext: SkillApplierHelper.TargetEvaluatorContext): List<Event> {
        if (primaryTargetContext.skill is MobSkillId && primaryTargetContext.skill != mskillSeedAutoAttack_2159) {
            wantsToCastSpell = true
        }

        if (primaryTargetContext.skill != mskillSeedofDeception_2160) { return emptyList() }

        child = V0MonsterHelper.spawnMonster(
            monsterDefinition = MonsterDefinitions[mobSeedThrall_85],
            position = Vector3f(actorState.position),
        ).onReady { it.syncEnmity(actorState) }

        return emptyList()
    }

    override fun getActorCollisionType(): ActorCollisionType {
        return ActorCollisionType.Object
    }

}

class MobSeedThrallBehavior(actorState: ActorState): V0MonsterController(actorState) {

    override fun onInitialized(): List<Event> {
        actorState.setBaseLook(ActorStateManager.player().getBaseLook())
        return super.onInitialized()
    }

}