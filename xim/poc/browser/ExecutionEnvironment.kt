package xim.poc.browser

import kotlinx.browser.window
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import web.url.URLSearchParams

private const val configFileName = "env.json"

@Serializable
private data class EnvironmentVariables(
    val serverPath: String,
)

object ExecutionEnvironment {

    private var loading = false
    private lateinit var envVariables: EnvironmentVariables
    private val executionParams by lazy { loadExecutionParameters() }

    fun isReady(): Boolean {
        if (!loading) {
            loading = true
            load()
        }

        return this::envVariables.isInitialized
    }

    fun load() {
        window.fetch(configFileName)
            .then {
                it.text()
            }.then {
                println("Execution Environment: $it")
                envVariables = Json.decodeFromString(it)
            }.catch {
                throw RuntimeException("Failed to load execution environment!", it)
            }
    }

    fun getServerPath(): String {
        return envVariables.serverPath
    }

    fun getExecutionParameter(key: String): String? {
        return executionParams[key]
    }

    private fun loadExecutionParameters(): URLSearchParams {
        return URLSearchParams(window.location.search)
    }

}
