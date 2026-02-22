package xim.poc.browser

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import web.location.location
import web.storage.localStorage
import xim.math.Vector3f
import xim.poc.EquipmentLook
import xim.poc.ModelLook
import xim.poc.RaceGenderConfig
import xim.poc.SceneManager
import xim.poc.game.*
import xim.poc.game.actor.components.Equipment
import xim.poc.game.actor.components.Inventory
import xim.poc.game.actor.components.getEquipmentComponentOrThrow
import xim.poc.game.actor.components.getInventory
import xim.poc.tools.KeybindTool.defaultGameKeyKeybinds
import xim.poc.tools.KeybindTool.defaultHotbarKeybinds
import xim.poc.tools.MogHouseSetting
import xim.poc.tools.UiPosition
import xim.poc.tools.ZoneConfig
import xim.util.OnceLogger
import xim.util.Periodically
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.seconds

@Serializable
data class CameraSettings(
    var invertX: Boolean = false,
    var invertY: Boolean = false,
    var screenShake: Int = 100,
    var mouseSensitivity: Float = 1f,
    var arrowSensitivity: Float = 1f,
    var collision: Boolean = true,
)

@Serializable
data class ScreenSettings(
    var windowWidth: Int = 1280,
    var windowHeight: Int = 720,
    var resolution: Float = 1f,
    var aspectRatio: Float = 1.6f,
    var aspectRatioEnabled: Boolean = false,

    var windowStyle: Int = 1,
    var uiScale: Float = 1f,
    var bumpMapEnabled: Boolean = false,
)

@Serializable
data class UiElementSettings(
    var offsetX: Int = 0,
    var offsetY: Int = 0,
)

@Serializable
data class VolumeSettings(
    var backgroundMusicVolume: Float = 0.25f,
    var ambientVolume: Float = 0.25f,
    var systemSoundVolume: Float = 0.25f,
    var effectVolume: Float = 0.25f,
)

@Serializable
enum class ModifierKey {
    None,
    Shift,
    Alt,
    Control,
}

@Serializable
data class Keybind(
    val keyCode: String,
    val modifierKey: ModifierKey,
)

@Serializable
data class KeyboardSettings(
    val gameKeys: HashMap<GameKey, Keybind> = defaultGameKeyKeybinds(),
    val hotbar1Bindings: ArrayList<Keybind> = defaultHotbarKeybinds(ModifierKey.None),
    val hotbar2Bindings: ArrayList<Keybind> = defaultHotbarKeybinds(ModifierKey.Alt),
    val hotbar3Bindings: ArrayList<Keybind> = defaultHotbarKeybinds(ModifierKey.Shift),
)

@Serializable
data class PlayerPosition(
    var zoneId: Int,
    var subAreaId: Int? = null,
    var position: Vector3f = Vector3f(),
    var rotation: Float = 0f,
    var mogHouseSetting: MogHouseSetting? = null,
) {
    constructor(zoneConfig: ZoneConfig): this(
        zoneId = zoneConfig.zoneId,
        position = zoneConfig.startPosition ?: Vector3f(),
        mogHouseSetting = zoneConfig.mogHouseSetting,
    )
}

@Serializable
data class PlayerSettings(
    var playerPosition: PlayerPosition,
    var playerLook: ModelLook = ModelLook.pc(RaceGenderConfig.HumeM, EquipmentLook()),
    var playerInventory: Inventory = Inventory(),
    var playerEquipment: Equipment = Equipment(),
    var playerLevels: JobLevels = JobLevels(),
    var playerJob: JobState = JobState(),
    var playerEquipmentSets: Array<Equipment> = Array(20) { Equipment() },
) {
    constructor(zoneConfig: ZoneConfig): this(
        playerPosition = PlayerPosition(zoneConfig)
    )
}

@Serializable
data class LocalConfiguration(
    var volumeSettings: VolumeSettings = VolumeSettings(),
    var screenSettings: ScreenSettings = ScreenSettings(),
    var cameraSettings: CameraSettings = CameraSettings(),
    var keyboardSettings: KeyboardSettings = KeyboardSettings(),
    var playerSettings: MutableMap<String, PlayerSettings> = HashMap(),
    var uiPositionSettings: MutableMap<UiPosition, UiElementSettings> = HashMap(),
)

@OptIn(ExperimentalEncodingApi::class)
object LocalStorage {

    private val storage by lazy { localStorage }
    private val syncRateLimiter = Periodically(5.seconds)

    private lateinit var localConfiguration: LocalConfiguration

    fun changeConfiguration(fn: (LocalConfiguration) -> Unit) {
        val config = getConfiguration()
        fn.invoke(config)
        writeConfig()
    }

    fun getConfiguration(): LocalConfiguration {
        if (this::localConfiguration.isInitialized) { return localConfiguration }

        localConfiguration = readConfig()
        return localConfiguration
    }

    fun getPlayerConfiguration(): PlayerSettings {
        return getCurrentPlayerSettings(getConfiguration())
    }

    fun getPlayerEquipmentSet(index: Int): Equipment? {
        return getPlayerConfiguration().playerEquipmentSets.getOrNull(index)
    }

    fun resetConfiguration() {
        storage.clear()
        location.reload()
    }

    fun exportConfig(): String {
        val configs = HashMap<String, String>()

        for (i in 0 until storage.length) {
            val key = storage.key(i) ?: continue
            val value = storage.getItem(key) ?: continue
            configs[key] = value
        }

        val bytes = Json.encodeToString(configs).encodeToByteArray()
        return Base64.encode(bytes)
    }

    fun importConfig(raw: String) {
        val rawMap = Base64.decode(raw).decodeToString()
        val parsed = Json.decodeFromString<Map<String, String>>(rawMap)

        storage.clear()

        for ((key, value) in parsed) {
            storage.setItem(key, value)
        }

        location.reload()
    }

    fun persistPlayerState() {
        if (!syncRateLimiter.ready()) { return }
        forcePersistPlayerState()
    }

    fun writeCustomConfiguration(key: String, value: String) {
        storage.setItem(key, value)
    }

    fun readCustomConfiguration(key: String): String? {
        return storage.getItem(key)
    }

    private fun forcePersistPlayerState() {
        changeConfiguration {
            val playerSettings = getCurrentPlayerSettings(it)

            val player = ActorStateManager.player()
            playerSettings.playerLook = player.getBaseLook().copy()
            playerSettings.playerPosition = getCurrentPlayerPosition(player, playerSettings.playerPosition)
            playerSettings.playerInventory.copyFrom(player.getInventory())
            playerSettings.playerEquipment.copyFrom(player.getEquipmentComponentOrThrow())
            playerSettings.playerLevels.copyFrom(player.jobLevels)
            playerSettings.playerJob.copyFrom(player.jobState)
        }
    }

    private fun getCurrentPlayerPosition(player: ActorState, current: PlayerPosition): PlayerPosition {
        if (!SceneManager.isFullyLoaded()) { return current }
        val scene = SceneManager.getCurrentScene()

        return PlayerPosition(
            zoneId = scene.config.zoneId,
            subAreaId = scene.getSubArea()?.id,
            position = Vector3f(player.position),
            rotation = player.rotation,
            mogHouseSetting = scene.config.mogHouseSetting,
        )
    }

    private fun readConfig(): LocalConfiguration {
        val raw = storage.getItem("configuration") ?: return LocalConfiguration()

        return try {
            Json.decodeFromString(raw)
        } catch (e: Exception) {
            OnceLogger.error("Failed to parse config! Clearing storage... ${e.message}")
            LocalConfiguration()
        }
    }

    private fun writeConfig() {
        storage.setItem("configuration", Json.encodeToString(localConfiguration))
    }

    private fun getCurrentPlayerSettings(config: LocalConfiguration): PlayerSettings {
        val configuration = GameState.getGameMode().configuration

        return config.playerSettings.getOrPut(configuration.gameModeId) {
            val zoneConfig = configuration.startingZoneConfig
            PlayerSettings(zoneConfig)
        }
    }

}