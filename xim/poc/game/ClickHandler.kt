package xim.poc.game

import xim.math.Vector2f
import xim.poc.*
import xim.poc.browser.ClickEvent
import xim.poc.browser.PointerPosition
import xim.poc.browser.WheelData
import xim.poc.camera.CameraReference
import xim.poc.game.actor.components.getDoorState

data class HoverEvent(
    val pointerPosition: PointerPosition,
    val pointerHasMoved: Boolean,
)

private class UiClickListener(val position: Vector2f, val size: Vector2f, val handler: (ClickEvent) -> Boolean)
private class UiHoverListener(val position: Vector2f, val size: Vector2f, val handler: (HoverEvent) -> Boolean)
private class UiWheelListener(val position: Vector2f, val size: Vector2f, val handler: (WheelData) -> Boolean)
private class WorldClickListener(val handler: (Ray) -> Boolean)

object ClickHandler {

    private val listeners = ArrayList<UiClickListener>()
    private val hoverListeners = ArrayList<UiHoverListener>()
    private val wheelListeners = ArrayList<UiWheelListener>()
    private val worldClickListeners = ArrayList<WorldClickListener>()

    private val previousPointerPosition = Vector2f()

    fun registerUiClickHandler(position: Vector2f, size: Vector2f, handler: (ClickEvent) -> Boolean) {
        listeners += UiClickListener(position, size, handler)
    }

    fun registerUiHoverListener(position: Vector2f, size: Vector2f, handler: (HoverEvent) -> Boolean) {
        hoverListeners += UiHoverListener(position, size, handler)
    }

    fun registerScrollListener(position: Vector2f, size: Vector2f, handler: (WheelData) -> Boolean) {
        wheelListeners += UiWheelListener(position, size, handler)
    }

    fun registerWorldClickListener(handler: (Ray) -> Boolean) {
        worldClickListeners += WorldClickListener(handler)
    }

    fun handleClickEvents() {
        handleHoverListeners()
        hoverListeners.clear()

        handleWheelListeners()
        wheelListeners.clear()

        handleInternal()
        listeners.clear()
        worldClickListeners.clear()
    }

    private fun handleHoverListeners() {
        val mousePosition = MainTool.platformDependencies.keyboard.getPointerPosition() ?: return

        val hasMoved = Vector2f.distance(previousPointerPosition, mousePosition.screenPosition) > 1e-5f
        previousPointerPosition.copyFrom(mousePosition.screenPosition)

        val hoverEvent = HoverEvent(mousePosition, hasMoved)

        for (listener in hoverListeners) {
            if (checkListener(listener, mousePosition, hoverEvent)) {
                return
            }
        }
    }

    private fun handleWheelListeners() {
        val mousePosition = MainTool.platformDependencies.keyboard.getPointerPosition() ?: return
        val wheelData = MainTool.platformDependencies.keyboard.getWheelEvents().lastOrNull() ?: return

        for (listener in wheelListeners) {
            if (checkListener(listener, mousePosition, wheelData)) {
                wheelData.consumed = true
                return
            }
        }
    }

    private fun handleInternal() {
        val clickEvent = MainTool.platformDependencies.keyboard.getClickEvents().lastOrNull() ?: return

        for (listener in listeners) {
            if (checkListener(listener, clickEvent)) {
                clickEvent.consumed = true
                return
            }
        }

        if (!clickEvent.rightClick && clickEvent.isLongClick()) {
            PlayerTargetSelector.tryDirectlyTarget(null)
        }

        val ray = CameraReference.getInstance().getWorldSpaceRay(clickEvent.normalizedScreenPosition) ?: return
        for (worldClickListener in worldClickListeners) {
            if (worldClickListener.handler.invoke(ray)) {
                clickEvent.consumed = true
                return
            }
        }

        val maxClickDistance = RayGridCollider.collideWithTerrain(ray, maxSteps = 20)?.distance ?: 50f
        if (checkDoors(ray, maxClickDistance, clickEvent)) { return }
        if (checkActors(ray, maxClickDistance, clickEvent)) { return }
    }

    private fun checkDoors(ray: Ray, maxClickDistance: Float, clickEvent: ClickEvent): Boolean {
        if (clickEvent.rightClick) { return false }

        val doors = SceneManager.getCurrentScene().getZoneInteractions().filter { it.isDoor() }
        var nearestDoor: Pair<Actor, Float>? = null

        for (door in doors) {
            val doorActor = SceneManager.getCurrentScene().getInteractionActor(door) ?: continue
            if (doorActor.getDoorState().open) { continue }

            val doorActorDisplay = ActorManager[doorActor.id] ?: continue

            val intersection = RayBoxCollider.intersect(ray, door.boundingBox) ?: continue
            if (intersection.second > maxClickDistance) { continue }

            if (nearestDoor != null && nearestDoor.second < intersection.second) { continue }
            nearestDoor = (doorActorDisplay to intersection.second)
        }

        if (nearestDoor != null) {
            PlayerTargetSelector.tryDirectlyTarget(nearestDoor.first)
            clickEvent.consumed = true
            return true
        }

        return false
    }

    private fun checkActors(ray: Ray, maxClickDistance: Float, clickEvent: ClickEvent): Boolean {
        val visibleActors = ActorManager.getVisibleActors()
        var nearestActor: Pair<Actor, Float>? = null

        for (actor in visibleActors) {
            if (actor.isPlayer()) { continue }
            val boundingBox = actor.getSkeletonBoundingBox(index = 0) ?: continue

            val intersection = RayBoxCollider.intersect(ray, boundingBox) ?: continue
            if (intersection.second > maxClickDistance) { continue }

            if (nearestActor != null && nearestActor.second < intersection.second) { continue }
            nearestActor = actor to intersection.second
        }

        if (nearestActor != null) {
            val (actor, _) = nearestActor

            if (clickEvent.rightClick) {
                PlayerTargetSelector.tryDirectlyEngage(actor)
            } else {
                PlayerTargetSelector.tryDirectlyTarget(actor)
            }

            clickEvent.consumed = true
            return true
        }

        return false
    }

    private fun checkListener(listener: UiClickListener, clickEvent: ClickEvent): Boolean {
        val eventPosition = Vector2f(clickEvent.screenPosition.x, clickEvent.screenPosition.y)
        val collides = checkBoundingBox(listener.position, listener.size, eventPosition)
        return if (collides) { listener.handler(clickEvent) } else { false }
    }

    private fun checkListener(listener: UiHoverListener, mousePosition: PointerPosition, hoverEvent: HoverEvent): Boolean {
        val collides = checkBoundingBox(listener.position, listener.size, mousePosition.screenPosition)
        return if (collides) { listener.handler(hoverEvent) } else { false }
    }

    private fun checkListener(listener: UiWheelListener, mousePosition: PointerPosition, wheelData: WheelData): Boolean {
        val collides = checkBoundingBox(listener.position, listener.size, mousePosition.screenPosition)
        return if (collides) { listener.handler(wheelData) } else { false }
    }

    private fun checkBoundingBox(boxPosition: Vector2f, boxSize: Vector2f, eventPosition: Vector2f): Boolean {
        val position = boxPosition * UiElementHelper.globalUiScale
        val size = boxSize * UiElementHelper.globalUiScale

        return position.x < eventPosition.x && (position.x + size.x) >= eventPosition.x
                && position.y < eventPosition.y && (position.y + size.y) >= eventPosition.y
    }

}