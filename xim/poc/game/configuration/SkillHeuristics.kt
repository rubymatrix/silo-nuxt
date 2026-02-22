package xim.poc.game.configuration

import xim.poc.game.ActorState
import xim.poc.game.StatusEffect
import xim.poc.game.configuration.constants.SkillId
import xim.util.PI_f
import xim.util.fallOff

data class SkillHeuristicContext(
    val source: ActorState,
    val target: ActorState,
    val skill: SkillId,
)

fun interface SkillHeuristic {
    fun computeScore(context: SkillHeuristicContext): Double
}

object SkillHeuristics {

    private const val defaultScore = 0.5

    private val heuristics = HashMap<SkillId, SkillHeuristic>()

    fun register(skill: SkillId, skillHeuristic: SkillHeuristic) {
        check(!heuristics.containsKey(skill)) { "Skill heuristic was already defined: $skill" }
        heuristics[skill] = skillHeuristic
    }

    operator fun set(skill: SkillId, heuristic: SkillHeuristic) {
        register(skill, heuristic)
    }

    fun getScore(context: SkillHeuristicContext): Double {
        val heuristic = heuristics[context.skill] ?: return defaultScore
        return heuristic.computeScore(context)
    }

    fun chooseSkill(source: ActorState, skills: List<SkillId>, targetProvider: (SkillId) -> ActorState?): SkillHeuristicContext? {
        val skillsWithTargets = skills.mapNotNull {
            val target = targetProvider(it)
            if (target == null) { null } else { it to target }
        }

        return chooseSkill(source, skillsWithTargets)
    }

    fun chooseSkill(source: ActorState, skillsWithTargets: List<Pair<SkillId, ActorState>>): SkillHeuristicContext? {
        val outputs = ArrayList<Pair<SkillHeuristicContext, Double>>()

        for ((skill, target) in skillsWithTargets) {
            val context = SkillHeuristicContext(source, target, skill)

            val score = getScore(context)
            if (score <= 0f) { continue }

            outputs += (context to score)
        }

        if (outputs.isEmpty()) {
            return null
        }

        return WeightedTable(outputs).getRandom()
    }

}

object CommonSkillHeuristics {

    fun avoidOverwritingBuff(statusEffect: StatusEffect, score: Double = 0.5): SkillHeuristic {
        return avoidOverwritingBuff(listOf(statusEffect), score)
    }

    fun avoidOverwritingBuff(statusEffects: List<StatusEffect>, score: Double = 0.5): SkillHeuristic {
        return SkillHeuristic {
            val anyMatches = statusEffects.any { se -> it.target.hasStatusEffect(se) }
            if (anyMatches) { 0.0 } else { score }
        }
    }

    fun avoidOverwritingDebuff(statusEffect: StatusEffect, score: Double = 0.5): SkillHeuristic {
        return SkillHeuristic {
            if (it.target.hasStatusEffect(statusEffect)) { 0.0 } else { score }
        }
    }

    fun canEraseDebuffs(baseScore: Double = 0.5, debuffCountFn: (Int) -> Double = { it * 0.5 }): SkillHeuristic {
        return SkillHeuristic {
            val debuffCount = it.target.getStatusEffects().count { se -> se.canErase }
            baseScore + debuffCountFn.invoke(debuffCount)
        }
    }

    fun canInterruptTarget(interruptScore: Double = 2.0): SkillHeuristic {
        return SkillHeuristic {
            val canInterrupt = it.target.getCastingState()?.isCharging() ?: false
            if (canInterrupt) { interruptScore } else { 0.0 }
        }
    }

    fun canDispelBuffs(baseScore: Double = 0.5, buffCountFn: (Int) -> Double = { it * 0.5 }): SkillHeuristic {
        return SkillHeuristic {
            val buffCount = it.target.getStatusEffects().count { se -> se.canDispel }
            baseScore + buffCountFn.invoke(buffCount)
        }
    }

    fun healingBasedOnHpp(bias: Double = 0.25): SkillHeuristic {
        return SkillHeuristic {
            val hpp = it.source.getHpp()
            if (hpp > 0.75f) { 0.0 } else { bias + (1f - hpp) }
        }
    }

    fun preferFarAway(distanceScore: Double, nearDistance: Float, farDistance: Float): SkillHeuristic {
        return SkillHeuristic {
            val distance = it.source.getTargetingDistance(it.target)
            distanceScore * (1.0 - distance.fallOff(nearDistance, farDistance))
        }
    }

    fun onlyIfFacingTarget(baseScore: Double = 0.5): SkillHeuristic {
        return SkillHeuristic {
            if (it.source.isFacingTowards(it.target)) { baseScore } else { 0.0 }
        }
    }

    fun targetIsBehindSource(baseScore: Double = 0.5): SkillHeuristic {
        return SkillHeuristic {
            val angle = it.source.getFacingAngle(it.target)
            if (angle > PI_f - (PI_f / 4f)) { baseScore } else { 0.0 }
        }
    }

    fun onlyIfBelowHppThreshold(threshold: Double, scalingFactor: Double = 1.0): SkillHeuristic {
        return SkillHeuristic {
            val hpp = it.source.getHpp()
            if (hpp > threshold) { 0.0 } else { (1f - hpp) * scalingFactor }
        }
    }

    fun onlyIfBelowMppThreshold(threshold: Double, scalingFactor: Double = 1.0): SkillHeuristic {
        return SkillHeuristic {
            val mpp = it.source.getMpp()
            if (mpp > threshold) { 0.0 } else { (1f - mpp) * scalingFactor }
        }
    }

    fun requireAppearanceState(appearanceState: Int, baseScore: Double = 0.5): SkillHeuristic {
        return SkillHeuristic {
            if (it.source.appearanceState == appearanceState) { baseScore } else { 0.0 }
        }
    }

    fun minimumOf(vararg skillHeuristics: SkillHeuristic): SkillHeuristic {
        return SkillHeuristic {
            skillHeuristics.minOf { skillHeuristic -> skillHeuristic.computeScore(it) }
        }
    }

}