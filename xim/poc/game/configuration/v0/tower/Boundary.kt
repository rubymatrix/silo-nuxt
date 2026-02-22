package xim.poc.game.configuration.v0.tower

import xim.math.Vector3f
import xim.poc.game.ActorStateManager
import xim.poc.game.configuration.v0.GameV0Helpers.hasAnyEnmity
import xim.poc.game.configuration.v0.tower.BoundaryConditions.alwaysOn
import kotlin.math.sqrt

interface Boundary {
    fun apply(actorPosition: Vector3f)
}

fun interface BoundaryCondition {
    fun invoke(): Boolean
}

object BoundaryConditions {

    val alwaysOn = BoundaryCondition { true }

    val playerHasEnmity = BoundaryCondition { hasAnyEnmity(ActorStateManager.playerId) }

    val playerDoesNotHaveEnmity = BoundaryCondition { !hasAnyEnmity(ActorStateManager.playerId) }

}

class EncompassingSphere(val center: Vector3f, val radius: Float, val condition: BoundaryCondition = alwaysOn): Boundary {

    private val marker = BoundaryMarker()

    override fun apply(actorPosition: Vector3f) {
        if (!condition.invoke()) {
            marker.hide()
            return
        }

        val distance = Vector3f.distance(center, actorPosition)

        if (distance > radius) {
            val fix = distance - (radius - 0.01f)
            val delta = (center - actorPosition).normalizeInPlace() * fix
            actorPosition.addInPlace(delta)
        }

        if (distance + 5f > radius) {
            displayMarker(actorPosition)
            marker.update()
            marker.show()
        } else {
            marker.hide()
        }

    }

    private fun displayMarker(actorPosition: Vector3f) {
        val yDelta = actorPosition.y - center.y
        val horizontalMagnitude = sqrt(radius*radius - yDelta*yDelta )

        val horizontal = (actorPosition - center).withY(0f).normalize() * (horizontalMagnitude + 1.01f)
        val markerPosition = center + horizontal.withY(yDelta)

        marker.configure(markerPosition, center)
    }

}

class PushWall(
    val center: Vector3f,
    val direction: Vector3f,
    val tracking: Boolean = false,
    val condition: BoundaryCondition = alwaysOn,
    val effectRange: Float = Float.MAX_VALUE,
): Boundary {

    private val marker = BoundaryMarker()

    override fun apply(actorPosition: Vector3f) {
        if (!condition.invoke()) {
            marker.hide()
            return
        }

        val actorDirection = actorPosition - center
        val projectedMagnitude = actorDirection.dot(direction)

        val markerEffectDistance = Vector3f.distance(actorPosition, center)
        if (markerEffectDistance <= effectRange && projectedMagnitude < 0f) {
            actorPosition += direction * (-1f * projectedMagnitude)
        }

        val markerActorPosition = if (tracking) { nearestPoint(actorPosition) } else { center }
        val markerActorDistance = Vector3f.distance(actorPosition, markerActorPosition)

        if (markerActorDistance < 4f && markerEffectDistance <= effectRange) {
            marker.configure(markerActorPosition, markerActorPosition + direction)
            marker.update()
            marker.show()
        } else {
            marker.hide()
        }
    }

    private fun nearestPoint(actorPosition: Vector3f): Vector3f {
        val d = direction.dot(direction * -1f)

        val planeConstant = direction.dot(center)
        val rayDistance = (planeConstant - direction.dot(actorPosition)) / d

        return actorPosition - direction * rayDistance
    }

}