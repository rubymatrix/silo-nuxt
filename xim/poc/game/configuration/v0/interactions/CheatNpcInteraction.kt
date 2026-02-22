package xim.poc.game.configuration.v0.interactions

import xim.poc.ActorId
import xim.poc.ActorManager
import xim.poc.game.*
import xim.poc.game.actor.components.Inventory
import xim.poc.game.actor.components.getInventory
import xim.poc.game.configuration.constants.spellNull_0
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.ItemAugmentDefinitions.maxPossibleRankLevel
import xim.poc.game.configuration.v0.tower.TowerConfiguration
import xim.resource.InventoryItems

object CheatNpcInteraction: NpcInteraction {

    override fun onInteraction(npcId: ActorId) {
        val cheatOptions = listOf(
            QueryMenuOption(text = "Level", value = 0),
            QueryMenuOption(text = "Armor", value = 1),
            QueryMenuOption(text = "Weapon", value = 2),
            QueryMenuOption(text = "Spells", value = 3),
            QueryMenuOption(text = "Forget spells", value = 4),
            QueryMenuOption(text = "Floor", value = 5),
            QueryMenuOption(text = "Forget kills", value = 6),
        )

        UiStateHelper.openQueryMode(prompt = "Which cheat?", options = cheatOptions, callback = this::handleCheatResponse)
    }

    private fun handleCheatResponse(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null) { return QueryMenuResponse.pop }

        if (queryMenuOption.value == 0) {
            val levels = (1 until 51).map { QueryMenuOption("$it", it) }
            UiStateHelper.openQueryMode("What level?", options = levels, callback = this::handleLevelChoice)
        } else if (queryMenuOption.value == 1) {
            listOf(12523,12551,12679,12807,12935,13606,13215).map {
                    GameV0.generateItem(ItemDropDefinition(
                        itemId = it,
                        rankSettings = ItemRankSettings.fixed(maxPossibleRankLevel),
                    ))
            }.forEach { ActorStateManager.player().getInventory().addItem(it) }
            return QueryMenuResponse.pop
        } else if (queryMenuOption.value == 2) {
            val levels = (0 .. 10).map { (it * 5).coerceAtLeast(1) }.map { QueryMenuOption(it.toString(), it) }
            UiStateHelper.openQueryMode("What item level?", options = levels, callback = this::generateWeapons)
        } else if (queryMenuOption.value == 3) {
            learnSpells()
            return QueryMenuResponse.pop
        } else if (queryMenuOption.value == 4) {
            forgetSpells()
            return QueryMenuResponse.pop
        } else if (queryMenuOption.value == 5) {
            floorPrompt()
        } else if (queryMenuOption.value == 6) {
            forgetKills()
        }

        return QueryMenuResponse.noop
    }

    private fun handleLevelChoice(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null) { return QueryMenuResponse.pop }

        val mainJob = ActorStateManager.player().getMainJobLevel()
        val destination = queryMenuOption.value

        if (mainJob.level > destination) {
            MiscEffects.playEffect(ActorManager.player(), effect = MiscEffects.Effect.LevelDown)
        } else {
            MiscEffects.playEffect(ActorManager.player(), effect = MiscEffects.Effect.LevelUp)
        }

        mainJob.level = destination
        mainJob.exp = 0

        return QueryMenuResponse.popAll
    }

    private fun generateWeapons(queryMenuOption: QueryMenuOption?): QueryMenuResponse {
        if (queryMenuOption == null) { return QueryMenuResponse.popAll }

        val swords = ItemDefinitions.definitionsById
            .filter { InventoryItems[it.value.id].isMainHandWeapon() }
            .filter { it.value.internalLevel == queryMenuOption.value }

        for ((id, _) in swords) {
            val inventoryItem = GameV0.generateItem(ItemDropDefinition(
                itemId = id,
                rankSettings = ItemRankSettings.leveling(),
            ))

            Inventory.player().addItem(inventoryItem)
        }

        return QueryMenuResponse.popAll
    }

    private fun learnSpells() {
        val player = ActorStateManager.player()

        val spellIds = V0BlueMagicMonsterRewards.getAll().values +
                TowerConfiguration.getAll().values.map { it.blueMagicReward }.flatten()

        val learnedSpells = player.getLearnedSpells()
        spellIds.forEach { learnedSpells.learnSpell(it) }
    }

    private fun forgetSpells() {
        val learnedSpells = ActorStateManager.player().getLearnedSpells()
        learnedSpells.spellIds.clear()
        for (i in learnedSpells.equippedSpells.indices) { learnedSpells.equippedSpells[i] = spellNull_0 }
    }

    private fun floorPrompt() {
        val floorOptions = (0 .. 20).map { it * 5 }.map { QueryMenuOption("$it", it) }

        UiStateHelper.openQueryMode("Which floor?", options = floorOptions, callback = {
            GameV0SaveStateHelper.getState().highestClearedFloor = it?.value ?: 0
            QueryMenuResponse.popAll
        })
    }

    private fun forgetKills() {
        MiscEffects.playExclamationProc(ActorStateManager.playerId, ExclamationProc.White)
        GameV0SaveStateHelper.getState().defeatedMonsterCounter.clear()
    }

}