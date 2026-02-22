package xim.poc.game

import xim.resource.Particle

sealed interface ActorDependentType

object ActorMount: ActorDependentType
object ActorBubble: ActorDependentType
object ActorFishingRod: ActorDependentType
object ActorSitChair: ActorDependentType
object ActorPet: ActorDependentType
object ActorTrust: ActorDependentType
class ActorParticle(val particle: Particle): ActorDependentType

fun ActorState.isMount() = dependentType == ActorMount
fun ActorState.isBubble() = dependentType == ActorBubble
fun ActorState.isFishingRod() = dependentType == ActorFishingRod
fun ActorState.isSitChair() = dependentType == ActorSitChair
fun ActorState.isPet() = dependentType == ActorPet
fun ActorState.isTrust() = dependentType == ActorTrust
fun ActorState.isParticle() = dependentType is ActorParticle

fun ActorState.isDummyActor(): Boolean {
    return isMount() || isBubble() || isFishingRod() || isSitChair() || isParticle()
}