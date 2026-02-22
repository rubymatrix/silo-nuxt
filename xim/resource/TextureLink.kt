package xim.resource

import xim.poc.SceneManager
import xim.poc.browser.LocalStorage
import xim.poc.gl.TextureReference
import xim.util.OnceLogger

class TextureLink(val name: String, val localDir: DirectoryResource) {

    companion object {
        fun of(name: String?, localDir: DirectoryResource): TextureLink? {
            if (name == null) { return null }
            return TextureLink(name, localDir)
        }
    }

    private var linkedResource: TextureResource? = null
    private var notFound: Boolean = false

    fun get(): TextureResource? {
        return linkedResource
    }

    fun getOrPut(provider: (String) -> TextureResource?): TextureResource? {
        if (linkedResource != null) { return linkedResource!! }
        if (notFound) { return null }

        linkedResource = provider.invoke(name)
        notFound = (linkedResource == null)

        if (notFound) { OnceLogger.warn("Texture not found: [${name}]") }

        return linkedResource
    }

    fun getOrPut(): TextureResource? {
        return getOrPut { localDir.searchLocalAndParentsByName(it) ?: DirectoryResource.getGlobalTexture(it) }
    }

    override fun equals(other: Any?): Boolean {
        return other is TextureLink && other.name == name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

}

class DatLink<T: DatEntry>(val id: DatId) {

    companion object {
        fun <T: DatEntry> of(id: DatId?): DatLink<T>? {
            if (id == null) { return null }
            return DatLink(id)
        }
    }

    private var linkedResource: T? = null
    private var notFound: Boolean = false

    fun getIfPresent(): T? {
        return linkedResource
    }

    fun getOrPut(provider: (DatId) -> T?): T? {
        if (linkedResource != null) { return linkedResource!! }
        if (notFound) { return null }

        linkedResource = provider.invoke(id)
        notFound = (linkedResource == null)
        return linkedResource
    }

}

private class BumpMapLink(val bumpMapResource: BumpMapResource?)

object BumpMapLinks {

    private val links = HashMap<String, BumpMapLink>()
    private var enabled = false

    fun update() {
        enabled = LocalStorage.getConfiguration().screenSettings.bumpMapEnabled
        if (enabled) { SceneManager.getNullableCurrentScene()?.getAreas()?.forEach { it.loadBumpMapResource() } }
    }

    fun getBumpMap(textureName: String?): TextureReference? {
        if (!enabled || textureName == null) { return null }
        return links.getOrPut(textureName) { findBumpMap(textureName) }.bumpMapResource?.textureReference
    }

    fun clear() {
        links.clear()
    }

    private fun findBumpMap(textureName: String): BumpMapLink {
        val directories = SceneManager.getCurrentScene().getAreas().mapNotNull { it.getBumpMapDirectory() }
        val resource = directories.firstNotNullOfOrNull { it.getCorrespondingBumpMapResource(textureName) }
        return BumpMapLink(resource)
    }

}