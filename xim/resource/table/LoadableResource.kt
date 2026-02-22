package xim.resource.table

interface LoadableResource {

    fun preload()

    fun isFullyLoaded(): Boolean

}