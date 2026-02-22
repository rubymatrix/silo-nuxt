package xim.poc.ui

import xim.poc.game.actor.components.AugmentHelper
import xim.poc.game.actor.components.InventoryItem
import xim.poc.game.configuration.v0.ItemDefinitions

data class InventoryItemDescription(
    val name: String,
    val pages: List<String> = emptyList(),
    val itemType: String? = null,
    val quantity: String? = null,
    val jobLevels: String? = null,
    val augmentPath: String? = null,
    val itemCast: String? = null,
    val itemLevel: String? = null,
    val rare: Boolean = false,
    val exclusive: Boolean = false,
) {
    companion object {
        fun toDescription(
            inventoryItem: InventoryItem,
            capacityConsumption: Int? = null,
        ): InventoryItemDescription {
            val itemInfo = inventoryItem.info()

            val name = itemInfo.logName.replaceFirstChar { it.uppercaseChar() }
            val quantity = if (inventoryItem.isStackable()) { "Quantity: ${inventoryItem.quantity}" } else { null }

            val descriptionLines = itemInfo.description.split("\n")
            val pages = if (descriptionLines.size <= 7) {
                listOf(itemInfo.description)
            } else {
                listOf(
                    descriptionLines.subList(0, 7).joinToString(separator = "\n"),
                    descriptionLines.subList(7, descriptionLines.size).joinToString(separator = "\n"),
                )
            }

            var augmentCurrent = ""
            var augmentNext = ""
            var augmentCapacity = ""

            val augment = inventoryItem.augments
            if (augment != null) {
                val rpNeeded = AugmentHelper.getRpToNextLevel(augment) - augment.rankPoints
                augmentCurrent = "Rank:${augment.rankLevel}"
                augmentNext = if (AugmentHelper.isMaxRank(augment)) { "" } else { "Next Rank:$rpNeeded" }
            }

            val fixedAugments = inventoryItem.fixedAugments
            if (fixedAugments != null && ItemDefinitions[inventoryItem].meldable) {
                val capacity = fixedAugments.capacityRemaining - (capacityConsumption ?: 0)
                augmentCapacity = "Capacity:$capacity"
            }

            val augmentStrings = listOf(augmentCurrent, augmentNext, augmentCapacity)
                .filter { it.isNotBlank() }

            val augmentPath = if (augmentStrings.isNotEmpty()) {
                val augmentString = augmentStrings.joinToString(" / " )
                "${ShiftJis.colorAug}< $augmentString >${ShiftJis.colorClear}"
            } else {
                null
            }

            return InventoryItemDescription(
                name = name,
                pages = pages,
                quantity = quantity,
                augmentPath = augmentPath,
                rare = itemInfo.isRare(),
                exclusive = itemInfo.isExclusive(),
            )
        }
    }
}