package xim.resource.table

import xim.poc.browser.DatLoader
import xim.poc.game.StatusEffect
import xim.resource.*

val StatusEffectNameTable = StringTable("ROM/180/102.DAT", bitMask = 0xFF.toByte())

data class StatusEffectIcon(val index: Int, val textureName: String, private val textureReader: ByteReader) {
    val texture: TextureResource? by lazy {
        TextureSection.read(textureReader).also {
            if (it != null) { DirectoryResource.setGlobalTexture(it) }
        }
    }
}

data class StatusEffectInfo(val id: Int, val name: String, val description: String, val icon: StatusEffectIcon)

object StatusEffectHelper {

    operator fun get(statusEffect: StatusEffect): StatusEffectInfo {
        val strings = StatusEffectNameTable[statusEffect.id]
        val icon = StatusEffectIcons[statusEffect.id]
        return StatusEffectInfo(statusEffect.id, strings[0], strings[1], icon)
    }

}

object StatusEffectIcons: LoadableResource {
    private var prefetchInitiated = false

    private const val iconDat = "ROM/119/57.DAT"
    private lateinit var icons: List<StatusEffectIcon>

    override fun preload() {
        if (prefetchInitiated) { return }
        prefetchInitiated = true

        DatLoader.load(iconDat).onReady { wrapper ->
            val bytes = wrapper.getAsBytes()
            icons = StatusEffectIconParser.parse(bytes)
        }
    }

    operator fun get(iconId: Int): StatusEffectIcon {
        return icons[iconId]
    }

    override fun isFullyLoaded(): Boolean {
        return this::icons.isInitialized
    }

}

private object StatusEffectIconParser {

    fun parse(byteReader: ByteReader): List<StatusEffectIcon> {
        val icons = ArrayList<StatusEffectIcon>()
        var index = 0
        while (byteReader.hasMore()) { icons += parseSingle(index, byteReader); index += 1 }
        return icons
    }

    private fun parseSingle(index: Int, byteReader: ByteReader): StatusEffectIcon {
        val start = byteReader.position

        // Not sure what any of the leading data is
        byteReader.position = start + 0x280

        // Texture data
        val dataSize = byteReader.next32()

        val textureReader = ByteReader(byteReader.bytes)
        textureReader.position = byteReader.position

        val textureType = byteReader.next8()
        val textureName = byteReader.nextString(0x10)

        byteReader.position = start + 0x17FF
        if (byteReader.next8() != 0xFF) { oops(byteReader, "Missed the end delimiter?") }

        return StatusEffectIcon(index, textureName, textureReader)
    }

}