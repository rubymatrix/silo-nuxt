package xim.poc.browser

import js.buffer.ArrayBuffer
import js.promise.Promise
import js.promise.catch
import js.typedarrays.Uint8Array
import web.cache.Cache
import web.cache.caches
import web.http.fetchAsync
import xim.poc.browser.FileStatus.*
import xim.poc.ui.ChatLog
import xim.poc.ui.ChatLogColor
import xim.resource.ByteReader
import xim.resource.DatParser
import xim.resource.DirectoryResource
import xim.util.OnceLogger.info
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

class DownloadFailureException(message: String): RuntimeException(message)

data class ParserContext(
    val zoneResource: Boolean = false,
    val staticResource: Boolean = false,
    val requiredResource: Boolean = true,
) {
    companion object {
        val staticResource = ParserContext(staticResource = true)
        val optionalResource = ParserContext(requiredResource = false)
    }
}

object DatLoader {

    private lateinit var browserFileCache: Cache
    private val inMemoryCache: MutableMap<String, DatWrapper> = HashMap()

    init {
        caches.open("dats").then(
            onFulfilled = { browserFileCache = it },
            onRejected = { throw IllegalStateException("Failed to open browser cache: ${it.cause}") }
        )
    }

    fun isReady(): Boolean {
        return this::browserFileCache.isInitialized
    }

    fun load(resourceName: String, parserContext: ParserContext = ParserContext()): DatWrapper {
        val cachedValue = inMemoryCache[resourceName]
        if (cachedValue != null && cachedValue.isReady()) {
            return cachedValue
        }

        return inMemoryCache.getOrPut(resourceName) {
            val wrapper = DatWrapper(resourceName, parserContext, ExecutionEnvironment.getServerPath())
            wrapper.load()
        }
    }

    fun update(elapsed: Duration) {
        inMemoryCache.filter { it.value.status == REJECTED }.forEach { it.value.retryIfNeeded(elapsed) }
    }

    fun getFileCache(): Cache {
        return browserFileCache
    }

    fun releaseAll() {
        val itr = inMemoryCache.entries.iterator()
        while (itr.hasNext()) {
            val next = itr.next()
            if (next.value.release()) { itr.remove() }
        }
    }

    fun getLoadingCount(): Int {
        return inMemoryCache.values.count { it.status == IN_PROGRESS }
    }

}

enum class FileStatus {
    NOT_STARTED,
    IN_PROGRESS,
    REJECTED,
    READY
}

private class RetryInterval(val retryCount: Int = 0, var remaining: Duration = baseInterval) {
    companion object {
        val baseInterval = 500.milliseconds
    }
}

class DatWrapper(val resourceName: String, val parserContext: ParserContext, val serverPath: String) {

    private val callbacks = ArrayList<(DatWrapper) -> Unit>()

    var status: FileStatus = NOT_STARTED

    private var retryInterval = RetryInterval()
    private var doomed = false

    private lateinit var buffer: ArrayBuffer

    private var rootDirectory: DirectoryResource? = null

    fun retryIfNeeded(elapsed: Duration) {
        if (status != REJECTED || doomed) { return }

        if (retryInterval.retryCount >= 3) {
            doomed = true
            val errorMessage = "Failed to download resource: $resourceName"

            if (parserContext.requiredResource) {
                throw DownloadFailureException(errorMessage)
            } else {
                ChatLog.addLine(errorMessage, ChatLogColor.Error)
            }

            return
        }

        retryInterval.remaining -= elapsed
        if (retryInterval.remaining > ZERO) { return }

        status = NOT_STARTED
        load()
    }

    fun onReady(callback: (DatWrapper) -> Unit): DatWrapper {
        if (status == READY) {
            callback.invoke(this)
        } else {
            callbacks += callback
        }

        return this
    }

    internal fun load(): DatWrapper {
        if (status != NOT_STARTED) { return this }
        status = IN_PROGRESS
        info("Loading $resourceName")

        DatLoader.getFileCache().match(resourceName)
            .flatThen {
                if (it != null) {
                    info("[$resourceName] Found in browser cache")
                    it.arrayBuffer()
                } else {
                    info("[$resourceName] Not found in browser cache")
                    fetchResource(resourceName)
                }
            }.then {
                buffer = it
                status = READY
                completeCallbacks()
            }.catch {
                status = REJECTED
                reconfigureRetryInterval()
                info("[$resourceName] Failure #${retryInterval.retryCount}, retrying in ${retryInterval.remaining}. $it")
            }
        return this
    }

    private fun completeCallbacks() {
        callbacks.forEach { it(this) }
        callbacks.clear()
    }

    private fun fetchResource(resourceName: String): Promise<ArrayBuffer> {
        return fetchAsync("${serverPath}${resourceName}")
            .flatThen {
                if (!it.ok) {
                    throw IllegalStateException()
                }

                if (it.status != 206.toShort()) {
                    info("[$resourceName] Fetch success")
                    DatLoader.getFileCache().put(resourceName, it.clone())
                } else {
                    info("[$resourceName] Partial fetch")
                }

                it.arrayBuffer()
            }
    }

    private fun reconfigureRetryInterval() {
        val newInterval = RetryInterval.baseInterval * 2.0.pow(retryInterval.retryCount)
        retryInterval = RetryInterval(retryCount = retryInterval.retryCount + 1, remaining = newInterval)
    }

    fun isReady(): Boolean {
        return when (status) {
            READY -> true
            NOT_STARTED -> { load(); false }
            else -> false
        }
    }

    fun getAsResourceIfReady(): DirectoryResource? {
        return if (isReady()) { getRootDirectory() } else { null }
    }

    fun getAsResource(): DirectoryResource {
        return getAsResourceIfReady() ?: throw IllegalStateException("Attempting to resolve $resourceName, but it is not ready yet")
    }

    fun getAsBufferIfReady(): ArrayBuffer? {
        return if (isReady()) { buffer } else { null }
    }

    fun getAsBuffer(): ArrayBuffer {
        return getAsBufferIfReady() ?: throw IllegalStateException("Attempting to resolve $resourceName, but it is not ready yet")
    }

    fun getAsBytes(): ByteReader {
        val buffer = getAsBufferIfReady() ?: throw IllegalStateException("Attempting to resolve $resourceName, but it is not ready yet")
        return ByteReader(Uint8Array(buffer))
    }

    fun release(): Boolean {
        return if (isReady() && !parserContext.staticResource) {
            rootDirectory?.release()
            true
        } else {
            false
        }
    }

    private fun getRootDirectory(): DirectoryResource {
        return rootDirectory ?: DatParser.parse(resourceName, Uint8Array(buffer), parserContext).also { rootDirectory = it }
    }

}