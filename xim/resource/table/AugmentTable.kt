package xim.resource.table

import xim.poc.game.AugmentId

object AugmentTable: LoadableResource {

    private val nameTable = StringTable("ROM/220/58.DAT", bitMask = 0xFF.toByte())

    override fun preload() {
        nameTable.preload()
    }

    override fun isFullyLoaded(): Boolean {
        return nameTable.isFullyLoaded()
    }

    fun getAugmentName(augmentId: AugmentId, arg: Int? = null): String {
        return getAugmentName(augmentId.id, arg)
    }

    fun getAugmentName(index: Int, arg: Int? = null): String {
        var augmentName = nameTable.first(index)
        if (arg == null) { return augmentName }

        augmentName = augmentName.replace("%d", arg.toString())
        augmentName = augmentName.replace("%+d", if (arg >= 0) "+$arg" else "$arg")
        augmentName = augmentName.replace("%%", "%")
        return augmentName
    }

    fun getAugmentName(index: Int, arg: String? = null): String {
        var augmentName = nameTable.first(index)
        if (arg == null) { return augmentName }

        augmentName = augmentName.replace("%d", arg)
        augmentName = augmentName.replace("%+d", "+$arg")
        augmentName = augmentName.replace("%%", "%")
        return augmentName
    }

}