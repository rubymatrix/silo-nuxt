package xim.poc.game.configuration.v0

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xim.poc.browser.LocalStorage
import xim.poc.game.configuration.MonsterId
import xim.poc.game.configuration.v0.interactions.Quantity
import xim.resource.KeyItemId
import xim.util.Periodically
import kotlin.time.Duration.Companion.seconds

@Serializable
data class V0MiningExp(
    var level: Int = 1,
    var currentExp: Int = 0,
)

@Serializable
data class EschaZiTahState(
    var visitedPortals: MutableSet<Int> = HashSet(),
)


@Serializable
data class GameV0SaveState(
    var defeatedMonsterCounter: MutableMap<MonsterId, Int> = HashMap(),
    var highestClearedFloor: Int = 0,
    val mining: V0MiningExp = V0MiningExp(),
    val playerSpells: ActorSpells = ActorSpells(),
    var baseCampMusic: Int = 63,
    var keyItems: MutableMap<Int, Int> = HashMap(),
    val eschaZiTahState: EschaZiTahState = EschaZiTahState(),
) {

    fun consumeKeyItem(keyItemId: KeyItemId, quantity: Quantity): Boolean {
        val currentQuantity = keyItems[keyItemId] ?: 0
        return if (currentQuantity < quantity) {
            false
        } else if (currentQuantity == quantity) {
            keyItems.remove(keyItemId)
            true
        } else {
            keyItems[keyItemId] = currentQuantity - quantity
            true
        }
    }

}

object GameV0SaveStateHelper {

    private const val configurationKey = "GameV0"

    private val saveState: GameV0SaveState by lazy { loadState() }
    private val saveRateLimiter = Periodically(5.seconds)
    private val json = Json { ignoreUnknownKeys = true }

    fun getState(): GameV0SaveState {
        return saveState
    }

    fun autoSave() {
        if (saveRateLimiter.ready()) { writeState() }
    }

    private fun writeState() {
        LocalStorage.writeCustomConfiguration(configurationKey, json.encodeToString(saveState))
    }

    private fun loadState(): GameV0SaveState {
        val savedState = LocalStorage.readCustomConfiguration(configurationKey)
        return if (savedState == null) { GameV0SaveState() } else { json.decodeFromString(savedState) }
    }

}