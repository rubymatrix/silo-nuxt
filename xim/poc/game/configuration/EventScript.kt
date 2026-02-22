package xim.poc.game.configuration

import xim.math.Vector3f
import xim.poc.*
import xim.poc.browser.DatLoader
import xim.poc.browser.DatWrapper
import xim.poc.game.ActorStateManager
import xim.poc.tools.ZoneChangeOptions
import xim.poc.tools.ZoneChanger
import xim.poc.tools.ZoneConfig
import xim.resource.DatId
import xim.resource.EffectRoutineInstance
import xim.resource.EffectRoutineResource
import xim.resource.table.FileTableManager
import xim.util.Fps
import xim.util.FrameTimer
import kotlin.time.Duration

interface EventItem {
    fun start() { }
    fun update(elapsedFrames: Float) { }
    fun isComplete(): Boolean
}

class RunOnceEventItem(val onStarted: () -> Unit): EventItem {

    override fun start() {
        onStarted.invoke()
    }

    override fun isComplete(): Boolean {
        return true
    }

}

class ActorRoutineEventItem(val fileTableIndex: Int, val actorId: ActorId, val targetId: ActorId = actorId, val eagerlyComplete: Boolean = false): EventItem {

    private var delegate: EffectRoutineEventItem? = null

    override fun start() {
        val actor = ActorManager[actorId] ?: return

        val context = ActorContext(originalActor = actorId, primaryTargetId = targetId)
        val association = ActorAssociation(actor, context)

        delegate = EffectRoutineEventItem(fileTableIndex = fileTableIndex, association, eagerlyComplete = eagerlyComplete)
        delegate?.start()
    }

    override fun isComplete(): Boolean {
        return delegate?.isComplete() ?: true
    }

}

class EffectRoutineEventItem(val fileTableIndex: Int, val effectAssociation: EffectAssociation, val routineId: DatId = DatId.main, val eagerlyComplete: Boolean = false): EventItem {

    private var resourceWrapper: DatWrapper? = null
    private var effectRoutineInstance: EffectRoutineInstance? = null

    override fun start() {
        val resourcePath = FileTableManager.getFilePath(fileTableIndex) ?: return
        resourceWrapper = DatLoader.load(resourcePath).onReady {
            val routine = it.getAsResource().getNullableChildRecursivelyAs(routineId, EffectRoutineResource::class) ?: return@onReady
            effectRoutineInstance = EffectManager.registerRoutine(effectAssociation, routine)
        }
    }

    override fun isComplete(): Boolean {
        if (eagerlyComplete) { return true }

        val wrapper = resourceWrapper ?: return true
        if (!wrapper.isReady()) { return false }
        return effectRoutineInstance?.isComplete() == true
    }

}

class WaitRoutine(duration: Duration): EventItem {

    constructor(waitFrames: Float): this(Fps.framesToSeconds(waitFrames))

    private val timer = FrameTimer(duration)

    override fun update(elapsedFrames: Float) {
        timer.update(elapsedFrames)
    }

    override fun isComplete(): Boolean {
        return timer.isReady()
    }

}

class FadeOutEvent(val duration: Duration): EventItem {

    private var complete = false

    override fun start() {
        ScreenFader.fadeOut(duration) { complete = true }
    }

    override fun isComplete(): Boolean {
        return complete
    }

}

class FadeInEvent(val duration: Duration): EventItem {

    private var complete = false

    override fun start() {
        ScreenFader.fadeIn(duration) { complete = true }
    }

    override fun isComplete(): Boolean {
        return complete
    }

}

class WarpSameZoneEventItem(val destination: Vector3f): EventItem {

    override fun start() {
        ActorStateManager.player().position.copyFrom(destination)
    }

    override fun isComplete(): Boolean {
        return true
    }

}

class WarpZoneEventItem(val destination: ZoneConfig, val fade: Boolean = true, val revive: Boolean = false): EventItem {

    override fun start() {
        ZoneChanger.beginChangeZone(destination, options = ZoneChangeOptions(fade = fade, fullyRevive = revive, pop = false))
    }

    override fun isComplete(): Boolean {
        return !ZoneChanger.isChangingZones()
    }

}

class InterpolateEventItem(val duration: Duration, val interpolation: (Float) -> Unit): EventItem {

    private val fadeParameters = FadeParameters.fadeIn(duration)

    override fun update(elapsedFrames: Float) {
        fadeParameters.update(elapsedFrames)
        interpolation.invoke(fadeParameters.getOpacity())
    }

    override fun isComplete(): Boolean {
        return fadeParameters.isComplete()
    }

}

class EventScript(val items: List<EventItem>)

object EventScriptRunner {

    private val events: ArrayList<EventItem> = ArrayList()
    private var currentItem: EventItem? = null

    fun runScript(script: EventScript) {
        if (isRunningScript()) { return }
        events += ArrayList(script.items)
    }

    fun isRunningScript(): Boolean {
        return events.isNotEmpty() || currentItem != null
    }

    fun update(elapsedFrames: Float) {
        if (!isRunningScript()) { return }

        val current = currentItem
        current?.update(elapsedFrames)

        if (current != null && !current.isComplete()) { return }

        currentItem = events.removeFirstOrNull()
        currentItem?.start()
    }

}