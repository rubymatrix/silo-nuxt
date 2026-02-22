package xim.poc.game.configuration.v0.behaviors

import xim.poc.game.ActorState
import xim.poc.game.configuration.ActorDamagedContext
import xim.poc.game.configuration.constants.*
import xim.poc.game.configuration.v0.V0MonsterFamilies
import xim.poc.game.event.Event
import xim.util.FrameTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class FamilyTrollLightBehavior(actorState: ActorState): V0MonsterController(actorState) {

    init {
        spellTimer = FrameTimer(20.seconds)
    }

    override fun onDamaged(context: ActorDamagedContext): List<Event> {
        if (context.actionContext?.criticalHit() == true && Random.nextDouble() < 0.10) {
            breakWeapon()
        }

        return emptyList()
    }

    override fun getWeapon(): Pair<Int, Int> {
        if (!isWeaponBroken()) { return super.getWeapon() }
        val damage = V0MonsterFamilies.computeDamage(actorState.getMainJobLevel().level, 0.8f)
        return (damage to 240)
    }

    override fun getSkills(): List<SkillId> {
        return super.getSkills() + if (isWeaponBroken()) {
            listOf(mskillRockSmash_1487, mskillOverthrow_1486)
        } else {
            listOf(mskillPotentLunge_1485)
        }
    }

    private fun breakWeapon() {
        actorState.appearanceState = 1
    }

    private fun isWeaponBroken(): Boolean {
        return actorState.appearanceState == 1
    }

}
