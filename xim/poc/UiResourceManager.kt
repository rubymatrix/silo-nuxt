package xim.poc

import xim.poc.browser.DatLoader
import xim.poc.browser.LocalStorage
import xim.poc.browser.ParserContext.Companion.staticResource
import xim.poc.tools.UiElementTool
import xim.resource.DirectoryResource
import xim.resource.TextureResource
import xim.resource.UiElementResource
import xim.resource.UiMenuResource
import xim.resource.table.FileTableManager
import xim.util.OnceLogger

object UiResourceManager {

    private var prefetchInitiated = false

    private val uiElementGroupLookup = HashMap<String, UiElementResource>()
    private val uiMenuLookup = HashMap<String, UiMenuResource>()

    private val uiDats = listOf(
        "ROM/0/13.DAT",
        "ROM/119/51.DAT",
        "ROM/280/15.DAT",
        "ROM/324/95.DAT",
    )

    fun prefetch() {
        if (prefetchInitiated) { return }
        prefetchInitiated = true

        setWindowStyle(LocalStorage.getConfiguration().screenSettings.windowStyle)

        uiDats.forEach {
            DatLoader.load(it, parserContext = staticResource).onReady { wrapper ->
                wrapper.getAsResource() // Force it to eagerly parse, which will resolve the UI elements
            }
        }

    }

    fun setWindowStyle(type: Int) {
        val sanitizedType = type.coerceIn(1, 8)
        val resourceName = FileTableManager.getFilePath(13 + sanitizedType) ?: return

        DatLoader.load(resourceName, parserContext = staticResource).onReady {
            val resources = it.getAsResource().collectByTypeRecursive(UiElementResource::class)
            resources.forEach { resource -> register(resource) }

            val textures = it.getAsResource().collectByTypeRecursive(TextureResource::class)
            textures.forEach { texture -> DirectoryResource.setGlobalTexture(texture) }
        }
    }

    fun register(uiElementResource: UiElementResource) {
        if (uiElementGroupLookup[uiElementResource.uiElementGroup.name] == uiElementResource) { return }
        uiElementGroupLookup[uiElementResource.uiElementGroup.name] = uiElementResource

        // Not sure how the US is supposed to map to base...?
        if (uiElementResource.uiElementGroup.name == "menu    framesus") {
            uiElementGroupLookup["menu    frames  "] = uiElementResource
        }

        OnceLogger.info("[UI] Registered [${uiElementResource.uiElementGroup.name}] - ${uiElementResource.uiElementGroup.uiElements.size}")
        UiElementTool.addUiOption(uiElementResource)
    }

    fun register(resource: UiMenuResource) {
        uiMenuLookup[resource.uiMenu.name] = resource
        OnceLogger.info("[UI] Registered Menu [${resource.uiMenu.name}] - ${resource.uiMenu.elements.size}")
        UiElementTool.addUiOption(resource.uiMenu)
    }

    fun getElement(lookup: String) : UiElementResource? {
        return uiElementGroupLookup[lookup]
    }

    fun getMenu(lookup: String) : UiMenuResource? {
        return uiMenuLookup[lookup]
    }

}