package xim.poc.game.configuration.v0.zones

import xim.math.Vector3f
import xim.poc.*
import xim.poc.game.ActorPromise
import xim.poc.game.ActorStateManager
import xim.poc.game.ActorType
import xim.poc.game.GameEngine
import xim.poc.game.actor.components.Inventory
import xim.poc.game.configuration.ActorBehaviors
import xim.poc.game.configuration.CustomZoneDefinition
import xim.poc.game.configuration.constants.itemRemedy_4155
import xim.poc.game.configuration.v0.*
import xim.poc.game.configuration.v0.abyssea.AbysseaEntranceInteraction
import xim.poc.game.configuration.v0.behaviors.NpcVrednevBehavior
import xim.poc.game.configuration.v0.constants.mobCherryTree_8
import xim.poc.game.configuration.v0.constants.mobOvni_135_022
import xim.poc.game.configuration.v0.constants.mobSeedCrystal_84
import xim.poc.game.configuration.v0.constants.npcVrednev
import xim.poc.game.configuration.v0.escha.EschaEntranceInteraction
import xim.poc.game.configuration.v0.interactions.*
import xim.poc.game.configuration.v0.mining.MineEntranceInteraction
import xim.poc.game.configuration.v0.paradox.ParadoxTransfer
import xim.poc.game.configuration.v0.paradox.ParadoxZoneInstance
import xim.poc.game.event.InitialActorState
import xim.poc.tools.DebugToolsManager
import xim.poc.tools.ZoneConfig
import xim.resource.InventoryItems
import xim.resource.table.MusicSettings
import xim.resource.table.ZoneNpcList
import xim.resource.table.ZoneSettings
import xim.util.PI_f

object BaseCamp {

    private const val zoneId = 283

    private val staticNpcList = ZoneNpcList(
        resourceId = "",
        npcs = emptyList(),
        npcsByDatId = emptyMap(),
    )

    val definition = CustomZoneDefinition(
        zoneId = zoneId,
        zoneSettings = ZoneSettings(zoneId, MusicSettings(musicId = 63)),
        staticNpcList = staticNpcList,
    )

}

class BaseCampLogic : ZoneLogic {

    private val dynamicNpcs = ArrayList<ActorPromise>()

    init {
        createStaticNpcs()
        createBooks()
        createCheatNpc()
        createMiscFurniture()
        createParadox()

        if (GameTower.hasClearedFloor(1)) {
            createShop()
            createRerollNpc()
        }

        val player = ActorStateManager.player()
        if (player.getLearnedSpells().spellIds.size >= 2) {
            createBlueMagicMoogle()
        }

        if (GameTower.hasClearedFloor(15)) { createMineEntrance() }
        if (GameTower.hasClearedFloor(30)) { createCavernousMaw() }
        if (GameTower.hasClearedFloor(50)) { createConfluence() }
    }

    override fun update(elapsedFrames: Float) {
    }

    override fun cleanUp() {
        dynamicNpcs.forEach { it.onReady { state -> ActorStateManager.delete(state.id) } }
    }

    override fun getEntryPosition(): ZoneConfig {
        return GameV0.configuration.startingZoneConfig
    }

    override fun toNew(): ZoneLogic {
        return BaseCampLogic()
    }

    private fun createShop() {
        val shopInventory = Inventory()
        shopInventory.inventoryItems.clear()

        val shopItems = ArrayList<Pair<Int, Int>>()

        // == Weapons
        val swordLevel = if (GameTower.hasClearedFloor(50)) {
            25
        } else if (GameTower.hasClearedFloor(40)) {
            20
        } else if (GameTower.canEnterTower2()) {
            15
        } else if (GameTower.hasClearedFloor(25)) {
            10
        } else if (GameTower.hasClearedFloor(15)) {
            5
        } else {
            1
        }

        val (floorLevel, armorLevel) = if (GameTower.hasClearedFloor(45)) {
            45 to 25
        } else if (GameTower.hasClearedFloor(40)) {
            40 to 23
        } else if (GameTower.hasClearedFloor(35)) {
            35 to 20
        } else if (GameTower.canEnterTower2()) {
            30 to 18
        } else if (GameTower.hasClearedFloor(25)) {
            25 to 12
        } else if (GameTower.hasClearedFloor(20)) {
            20 to 10
        } else if (GameTower.hasClearedFloor(15)) {
            15 to 7
        } else if (GameTower.hasClearedFloor(10)) {
            10 to 5
        } else if (GameTower.hasClearedFloor(5)) {
            5 to 3
        } else {
            0 to 1
        }

        shopItems += ItemDefinitions.potions
            .mapNotNull { ItemDefinitions.definitionsById[it] }
            .filter { it.internalLevel <= armorLevel }
            .maxBy { it.internalLevel }.id to 999

        shopItems += ItemDefinitions.ethers
            .mapNotNull { ItemDefinitions.definitionsById[it] }
            .filter { it.internalLevel <= armorLevel }
            .maxBy { it.internalLevel }.id to 999

        shopItems += itemRemedy_4155.id to 999

        shopItems += ItemDefinitions.definitionsById.filter { InventoryItems[it.key].isMainHandWeapon() }
            .filter { it.value.shopBuyable }.values
            .filter { it.internalLevel == swordLevel }
            .map { it.id to 1 }

        shopItems += ItemDefinitions.definitionsById.filter { InventoryItems[it.key].equipmentItemInfo != null }
            .filter { !InventoryItems[it.key].isMainHandWeapon() }
            .filter { !InventoryItems[it.key].isRingOrEarring() }
            .filter { it.value.shopBuyable }.values
            .filter { it.internalLevel == armorLevel }.map { it.id to 1 }

        // == Upgrade Items
        // === Aug. items
        val augItems = ArrayList<Pair<Int, Int>>()

        if (GameTower.hasClearedFloor(5)) {
            augItems += listOf(8933 to 999, 8942 to 999, 8951 to 999, 8960 to 999)
        }

        if (GameTower.hasClearedFloor(30)) {
            augItems += listOf(8934 to 999, 8943 to 999, 8952 to 999, 8961 to 999)
        }

        shopItems += augItems.sortedBy { it.first }

        // Populate the shop
        shopItems.forEach {
            val (id, quantity) = it
            val itemDefinition = ItemDefinitions.definitionsById[id]

            val rankDistribution = if (InventoryItems[id].isMainHandWeapon()) {
                ItemRankFixed(1)
            } else {
                val deltaLevel = (GameTower.getHighestClearedFloor() - floorLevel).coerceAtMost(4)
                ItemRankFixed(3 * (deltaLevel + 1))
            }

            val rankSettings = if (itemDefinition != null) {
                ItemRankSettings(canRankUp = itemDefinition.canRankUp, rankDistribution = rankDistribution)
            } else {
                null
            }

            val dropDefinition = ItemDropDefinition(itemId = id, rankSettings = rankSettings, quantity = quantity)
            shopInventory.addItem(GameV0.generateItem(dropDefinition))
        }

        val look = generateShopKeeperLook()
        val position = Vector3f(x=-40.00f,y=1.34f,z=26.00f)

        if (look.race == RaceGenderConfig.TaruF || look.race == RaceGenderConfig.TaruM) {
            spawnTaruStool(position)
            position.y -= 0.575f
        }

        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Shopkeep",
            type = ActorType.StaticNpc,
            position = position,
            staticPosition = true,
            rotation = -PI_f/2f,
            modelLook = look,
            components = listOf(shopInventory),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, ShopInteraction)
        }
    }

    private fun generateShopKeeperLook(): ModelLook {
        val raceGender = RaceGenderConfig.values().filter { it.genderType != GenderType.None }.random()

        val equipmentLook = EquipmentLook()
            .set(ItemModelSlot.Face, (0 .. 31).random())
            .set(ItemModelSlot.Head, 480)
            .set(ItemModelSlot.Body, 480)
            .set(ItemModelSlot.Hands, 480)
            .set(ItemModelSlot.Legs, 480)
            .set(ItemModelSlot.Feet, 480)

        return ModelLook.pc(raceGender, equipmentLook = equipmentLook)
    }

    private fun spawnTaruStool(position: Vector3f) {
        dynamicNpcs +=  GameEngine.submitCreateActorState(InitialActorState(
            name = "",
            type = ActorType.Effect,
            position = Vector3f(position),
            modelLook = ModelLook.furniture(343), // Harp Stool
            targetable = false,
        ))
    }

    private fun createStaticNpcs() {
        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Entrance",
            type = ActorType.StaticNpc,
            position = Vector3f(x=-46.03f,y=-0.65f,z=46.81f),
            modelLook = ModelLook.npc(0x975),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, EntranceInteraction)
        }

        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Stylist",
            type = ActorType.StaticNpc,
            position = Vector3f(x=-41.27f,y=-0.64f,z=46.57f),
            rotation = PI_f/2f,
            modelLook = ModelLook.pc(RaceGenderConfig.HumeChildF, EquipmentLook()),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, StylistInteraction)
        }
    }

    private fun createBlueMagicMoogle() {
        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Blue Magic",
            type = ActorType.StaticNpc,
            position = Vector3f(x=-44.595f,y=1.35f,z=42.44f),
            modelLook = ModelLook.furniture(3676),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, BlueMagicInteraction)
        }
    }

    private fun createCavernousMaw() {
        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Cavernous Maw",
            type = ActorType.StaticNpc,
            position = Vector3f(x=-35.28f,y=1.35f,z=43.14f),
            rotation = PI_f/2f,
            scale = 0.25f,
            modelLook = ModelLook.npc(0x915),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, AbysseaEntranceInteraction)
        }
    }

    private fun createMineEntrance() {
        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Vrednev",
            behaviorController = ActorBehaviors.getOrRegister(npcVrednev) { NpcVrednevBehavior(it) } ,
            type = ActorType.StaticNpc,
            position = Vector3f(x=-33.28f,y=1.35f,z=41.14f),
            rotation = 3*PI_f/4f,
            modelLook = ModelLook.npc(0x7B4),
            appearanceState = 1,
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, MineEntranceInteraction)
        }
    }

    private fun createConfluence() {
        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Confluence",
            type = ActorType.Effect,
            position = Vector3f(x=-34.06f,y=-0.66f,z=46.78f),
            modelLook = ModelLook.npc(0x9BA),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, EschaEntranceInteraction)
        }
    }

    private fun createBooks() {
        if (GameTower.hasClearedFloor(1)) {
            dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
                name = "",
                type = ActorType.Effect,
                position =  Vector3f(x=-46.15f,y=1.34f,z=29.87f),
                rotation = 3*PI_f/2f,
                modelLook = ModelLook.furniture(3625),
                targetable = false
            ))

            dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
                name = "Reinforce Moogle",
                type = ActorType.StaticNpc,
                position =  Vector3f(x=-46.15f,y=0.84f,z=29.87f),
                staticPosition = true,
                modelLook = ModelLook.npc(0x961),
            )).onReady {
                GameV0.interactionManager.registerInteraction(it.id, ItemReinforcementInteraction)
            }
        }

        if (GameTower.hasClearedFloor(5)) {
            dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
                name = "Meld Moogle",
                type = ActorType.StaticNpc,
                position =  Vector3f(x=-46.15f,y=0.84f,z=28.87f),
                staticPosition = true,
                modelLook = ModelLook.npc(0x965),
            )).onReady {
                GameV0.interactionManager.registerInteraction(it.id, MysterySlotInteraction)
            }
        }

        if (GameTower.hasClearedFloor(9)) {
            dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
                name = "Upgrade Moogle",
                type = ActorType.StaticNpc,
                position =  Vector3f(x=-46.15f,y=0.84f,z=30.87f),
                staticPosition = true,
                modelLook = ModelLook.npc(0x966),
            )).onReady {
                GameV0.interactionManager.registerInteraction(it.id, UpgradeWeaponInteraction)
            }
        }
    }

    private fun createMiscFurniture() {
        if (GameV0Helpers.hasDefeated(mobCherryTree_8)) {
            dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
                name = "",
                type = ActorType.Effect,
                position = Vector3f(x=-43.19f,y=0.34f,z=24.95f),
                modelLook = ModelLook.furniture(180),
                targetable = false,
            ))
        }

        if (GameV0Helpers.hasDefeated(mobOvni_135_022)) {
            dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
                name = "",
                type = ActorType.Effect,
                position = Vector3f(x=-33.28f,y=1.35f,z=41.14f),
                modelLook = ModelLook.furniture(282),
                targetable = false,
            ))
        }

        if (GameV0Helpers.hasDefeated(mobCherryTree_8)) {
            dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
                name = "Spinet",
                type = ActorType.StaticNpc,
                position = Vector3f(x=-32.77f,y=1.35f,z=29.82f),
                modelLook = ModelLook.furniture(3677),
                rotation = PI_f/2f
            )).onReady {
                GameV0.interactionManager.registerInteraction(it.id, MusicInteraction)
            }
        }

    }

    private fun createCheatNpc() {
        if (!DebugToolsManager.debugEnabled) { return }

        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Cheat",
            type = ActorType.StaticNpc,
            position = Vector3f(x=-42.99f,y=0.34f,z=25.66f),
            modelLook = ModelLook.npc(0x9E2),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, CheatNpcInteraction)
        }
    }

    private fun createRerollNpc() {
        dynamicNpcs += GameEngine.submitCreateActorState(InitialActorState(
            name = "Leafkin Roller",
            type = ActorType.StaticNpc,
            position = Vector3f(x=-36.99f,y=0.34f,z=25.66f),
            modelLook = ModelLook.npc(0x9E2),
        )).onReady {
            GameV0.interactionManager.registerInteraction(it.id, BaseCampRerollInteraction)
        }
    }

    private fun createParadox() {
        if (!GameV0Helpers.hasDefeated(mobSeedCrystal_84)) { return }

        dynamicNpcs += ParadoxTransfer(
            location = Vector3f(x=-46.72f,y=1.35f,z=41.14f),
            destination = ParadoxZoneInstance.zoneConfig,
        ).promise
    }

}