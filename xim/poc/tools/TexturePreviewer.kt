package xim.poc.tools

import kotlinx.browser.document
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import xim.math.Vector2f
import xim.poc.MainTool
import xim.poc.UiElementHelper
import xim.poc.gl.DrawXimUiCommand
import xim.resource.DirectoryResource
import xim.resource.TextureResource
import xim.resource.UiElement

object TexturePreviewer {

    fun draw() {
        if (!isCheckBox("TextureSelectEnable")) { return }

        val select = document.getElementById("TextureSelect") as HTMLSelectElement
        val texture = DirectoryResource.getGlobalTexture(select.value)

        if (texture == null) {
            web.console.console.warn("[Preview] Failed to load: ${select.value}")
            return
        }

        if (texture.name != select.value) {
            web.console.console.warn("[Preview] Loaded a texture from a different namespace: [${texture.name}] vs [${select.value}]")
            return
        }

        val dummyElement = UiElement.basic32x32(texture.name, uvWidth = texture.textureReference.width, uvHeight = texture.textureReference.height)

        MainTool.drawer.setupXimUi()
        MainTool.drawer.drawXimUi(DrawXimUiCommand(
                uiElement = dummyElement,
                position = Vector2f(32f, 32f),
                scale = UiElementHelper.globalUiScale,
                elementScale = Vector2f(texture.textureReference.width.toFloat() / 32f, texture.textureReference.height.toFloat() / 32f)
            ))
    }

    fun register(textureResource: TextureResource) {
        if (!isCheckBox("TextureSelectRegister")) { return }

        val select = document.getElementById("TextureSelect") as HTMLSelectElement

        val option = document.createElement("option") as HTMLOptionElement
        option.text = textureResource.name
        option.value = textureResource.name

        select.add(option)
    }

}