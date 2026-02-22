package xim.poc

import xim.poc.browser.DatLoader
import xim.poc.browser.DatWrapper
import xim.resource.DatId
import xim.resource.EffectRoutineResource
import xim.resource.table.FileTableManager

enum class FishingArrowAnimation(val animationId: DatId) {
    LeftArrow(DatId("mai0")),
    LeftArrowSuccess(DatId("mai1")),
    LeftArrowFade(DatId("mai2")),
    RightArrow(DatId("mai3")),
    RightArrowSuccess(DatId("mai4")),
    RightArrowFade(DatId("mai5")),
    GoldLeftArrow(DatId("mai6")),
    GoldLeftArrowSuccess(DatId("mai7")),
    GoldLeftArrowFade(DatId("mai8")),
    GoldRightArrow(DatId("mai9")),
    GoldRightArrowSuccess(DatId("maia")),
    GoldRightArrowFade(DatId("maib")),
}

object FishingAnimationHelper {

    private val resourceId = 0xE8F
    private var resource: DatWrapper? = null

    fun playArrowAnimation(actorId: ActorId, arrowAnimation: FishingArrowAnimation) {
        val actor = ActorManager[actorId] ?: return

        loadResource()?.onReady {
            val root = it.getAsResource()
            val routine = root.getChildAs(arrowAnimation.animationId, EffectRoutineResource::class)
            EffectManager.registerRoutine(ActorAssociation(actor), routine)
        }
    }

    private fun loadResource(): DatWrapper? {
        if (resource != null) { return resource }
        val path = FileTableManager.getFilePath(resourceId) ?: return null
        resource = DatLoader.load(path)
        return resource
    }

}