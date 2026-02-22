package xim.resource.table

import xim.poc.ActionTargetFilter
import xim.poc.browser.DatLoader
import xim.poc.game.configuration.constants.MobSkillId
import xim.poc.game.configuration.constants.mskillHolyMist_3297
import xim.poc.game.configuration.constants.mskillImpact_3296
import xim.poc.game.configuration.constants.mskillLunarBay_3295
import xim.resource.ByteReader
import xim.resource.TargetFlag
import xim.resource.table.AdditionalMobAnimations.additionalAnimations

val MobSkillNameTable = BasicStringTable("ROM/27/80.DAT", 0x80.toByte())

data class MobSkillInfo(val id: Int, val animationId: Int, val type: Int, val logged: Boolean = true) {

    val targetFlag: Int = validTargetsToTargetFlag(type)
    val targetFilter = ActionTargetFilter(targetFlag)

    private fun validTargetsToTargetFlag(validTargets: Int): Int {
        return when (validTargets) {
            1 -> TargetFlag.Self.flag
            2 -> TargetFlag.Party.flag
            3 -> TargetFlag.Self.flag or TargetFlag.Party.flag
            4 -> TargetFlag.Enemy.flag
            6 -> TargetFlag.Ally.flag
            7 -> TargetFlag.Self.flag or TargetFlag.Enemy.flag
            // 16 -> pup?
            else -> 0
        }
    }

}

// https://raw.githubusercontent.com/LandSandBoat/server/base/sql/mob_skills.sql
// with some overrides & additions listed below
object MobSkillInfoTable: LoadableResource {

    private lateinit var mobSkillInfo: MutableMap<Int, MobSkillInfo>
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::mobSkillInfo.isInitialized
    }

    operator fun get(skillId: Int): MobSkillInfo? {
        return mobSkillInfo[skillId]
    }

    fun getAnimationPath(info: MobSkillInfo): String? {
        return getAnimationPath(info.animationId)
    }

    fun getAnimationPath(animationId: Int): String? {
        val fileTableIndex = getFileTableOffset(animationId)
        return FileTableManager.getFilePath(fileTableIndex)
    }

    fun getFileTableOffset(animationId: Int): Int {
        return animationId + if (animationId < 0x200) {
            0x0F3C
        } else if (animationId < 0x600) {
            0xC1EF
        } else if (animationId < 0x800) {
            0xE739
        } else {
            0x14B07
        }
    }

    fun mutate(mobSkillId: MobSkillId, fn: (MobSkillInfo) -> MobSkillInfo) {
        val mobSkill = mobSkillId.toMobSkillInfo()
        mobSkillInfo[mobSkillId.id] = fn.invoke(mobSkill)
    }

    fun create(mobSkillId: MobSkillId, new: MobSkillInfo) {
        if (mobSkillInfo.containsKey(mobSkillId.id)) { throw IllegalStateException("Cannot overwrite mob skill $mobSkillId") }
        mobSkillInfo[mobSkillId.id] = new
    }

    private fun loadTable() {
        DatLoader.load("landsandboat/MobSkillsTable.DAT").onReady { parseTable(it.getAsBytes()) }
    }

    private fun parseTable(byteReader: ByteReader) {
        val skills = HashMap<Int, MobSkillInfo>()
        while (byteReader.hasMore()) {
            val skill = MobSkillInfo(id = byteReader.next16(), animationId = byteReader.next16(), type = byteReader.next16())
            skills[skill.id] = skill
        }

        mobSkillInfo = HashMap(skills + additionalAnimations())
    }

    fun MobSkillId.toMobSkillInfo(): MobSkillInfo {
        return MobSkillInfoTable[id] ?: throw IllegalStateException("No such mob skill: $this")
    }

    fun MobSkillId.toMobSkillInfoOrNull(): MobSkillInfo? {
        return get(id)
    }

}

private object AdditionalMobAnimations {

    fun additionalAnimations(): Map<Int, MobSkillInfo> {
        return listOf(
            MobSkillInfo(id = 86, animationId = 0x100, type = 4), // Bomb: Vulcanion Impact

            MobSkillInfo(id = 225, animationId = 0x121, type = 1), // Coeurl: Frenzied Rage

            MobSkillInfo(id = 256, animationId = 0x0FF, type = 4), // Bomb: Heat Wave

            MobSkillInfo(id = 270, animationId = 0x36B, type = 1), // Snoll:Berserk
            MobSkillInfo(id = 271, animationId = 0x36C, type = 4), // Snoll:Freeze Rush
            MobSkillInfo(id = 272, animationId = 0x36D, type = 4), // Snoll:Cold Wave
            MobSkillInfo(id = 273, animationId = 0x36E, type = 4), // Snoll:Hypothermal Combustion

            MobSkillInfo(id = 315, animationId = 0x366, type = 4), // Cluster:Self-Destruct (3->2)
            MobSkillInfo(id = 316, animationId = 0x367, type = 4), // Cluster:Self-Destruct (3->0)
            MobSkillInfo(id = 317, animationId = 0x368, type = 4), // Cluster:Self-Destruct (2->1)
            MobSkillInfo(id = 318, animationId = 0x369, type = 4), // Cluster:Self-Destruct (2->0)
            MobSkillInfo(id = 319, animationId = 0x36A, type = 4), // Cluster:Self-Destruct (1->0)

            MobSkillInfo(id = 342, animationId = 0x374, type = 1), // Snoll Tzar:Berserk
            MobSkillInfo(id = 343, animationId = 0x375, type = 4), // Snoll Tzar:Arctic Impact
            MobSkillInfo(id = 344, animationId = 0x376, type = 4), // Snoll Tzar:Cold Wave
            MobSkillInfo(id = 345, animationId = 0x377, type = 4), // Snoll Tzar:Hiemal Storm
            MobSkillInfo(id = 346, animationId = 0x378, type = 4), // Snoll Tzar:Hypothermal Combustion

            MobSkillInfo(id = 732, animationId = 0x2B1, type = 1, logged = true), // Eald: Initiate Warp
            MobSkillInfo(id = 733, animationId = 0x2B2, type = 1, logged = true), // Eald: Complete Warp

            MobSkillInfo(id = 927, animationId = 0x47A, type = 4), // Dragon: 2HR Chaos Blade (unconfirmed)

            MobSkillInfo(id = 965, animationId = 0x346, type = 4), // Weeper:Memory of Fire
            MobSkillInfo(id = 966, animationId = 0x347, type = 4), // Weeper:Memory of Ice
            MobSkillInfo(id = 967, animationId = 0x348, type = 4), // Weeper:Memory of Wind
            MobSkillInfo(id = 968, animationId = 0x349, type = 4), // Weeper:Memory of Light
            MobSkillInfo(id = 969, animationId = 0x34A, type = 4), // Weeper:Memory of Earth
            MobSkillInfo(id = 970, animationId = 0x34B, type = 4), // Weeper:Memory of Lightning
            MobSkillInfo(id = 971, animationId = 0x34C, type = 4), // Weeper:Memory of Water
            MobSkillInfo(id = 972, animationId = 0x34D, type = 4), // Weeper:Memory of Darkness

            MobSkillInfo(id = 1103, animationId = 0x357, type = 4), // Taurus: Chthonian Ray

            MobSkillInfo(id = 1118, animationId = 0x402, type = 4), // Yovra: Torrential Torment
            MobSkillInfo(id = 1121, animationId = 0x405, type = 1), // Yovra: Fluorescence

            MobSkillInfo(id = 1354, animationId = 0x9D0, type = 4), // Morbol:Extremely Bad Breath (unconfirmed)

            MobSkillInfo(id = 1424, animationId = 0x3D8, type = 4), // Tiger: Predatory Glare
            MobSkillInfo(id = 1425, animationId = 0x11, type = 4), // Tiger: Crossthrash

            MobSkillInfo(id = 1582, animationId = 0x4C5, type = 4), // Mine:Mine Blast

            MobSkillInfo(id = 1635, animationId = 0x4C2, type = 4), // ???:Provoke
            MobSkillInfo(id = 1636, animationId = 0x4CD, type = 4, logged = false), // Cerberus:Howl (unnamed)

            MobSkillInfo(id = 1844, animationId = 0x59E, type = 4), // Wivre: Clobber

            MobSkillInfo(id = 1909, animationId = 0x60F, type = 4), // Rafflesia: Rotten Stench
            MobSkillInfo(id = 1910, animationId = 0x610, type = 4), // Rafflesia: Floral Bouquet

            MobSkillInfo(id = 1915, animationId = 0x636, type = 1), // Gnole: Call of the Moon (Up)
            MobSkillInfo(id = 1916, animationId = 0x637, type = 1), // Gnole: Call of the Moon (Down)
            MobSkillInfo(id = 1921, animationId = 0x63C, type = 1), // Gnole: Cacophony

            MobSkillInfo(id = 1940, animationId = 0x630, type = 1), // Pixie: Summer Breeze

            MobSkillInfo(id = 1945, animationId = 0x613, type = 1), // Orc: Orcish Counterstance
            MobSkillInfo(id = 1947, animationId = 0x61B, type = 1), // Quadav: Diamondshell

            MobSkillInfo(id = 1953, animationId = 0x647, type = 4), // Coeurl: Blink of Peril

            MobSkillInfo(id = 1966, animationId = 0x642, type = 4), // Wyvern: Hurricane Breath

            MobSkillInfo(id = 2111, animationId = 0x683, type = 4), // Gigas:Moribund Hack

            MobSkillInfo(id = 2116, animationId = 0x65C, type = 4), // Demon:Hellborn Yawp

            MobSkillInfo(id = 2133, animationId = 0x67E, type = 4), // Taurus: Lethal Triclip

            MobSkillInfo(id = 2134, animationId = 0x67F, type = 1), // Behemoth: Accursed Armor
            MobSkillInfo(id = 2135, animationId = 0x680, type = 4), // Behemoth: Amnesiac Blast

            MobSkillInfo(id = 2154, animationId = 0x5D1, type = 4), // Mandragora: Demonic Flower (Crystalline)
            MobSkillInfo(id = 2155, animationId = 0x5D2, type = 4), // Orc: Phantasmal Dance (Crystalline)
            MobSkillInfo(id = 2156, animationId = 0x5D3, type = 4), // Quadav: Thunderous Yowl (Crystalline)
            MobSkillInfo(id = 2157, animationId = 0x5D4, type = 4), // Yagudo: Feather Maelstrom (Crystalline)
            MobSkillInfo(id = 2158, animationId = 0x5D5, type = 4), // Goblin: Saucepan (Crystalline)

            MobSkillInfo(id = 2159, animationId = 0x5D6, type = 4, logged = false), // Living Crystal: Auto-Attack
            MobSkillInfo(id = 2160, animationId = 0x5D8, type = 4), // Living Crystal: Seed of Deception
            MobSkillInfo(id = 2161, animationId = 0x5D7, type = 4), // Living Crystal: Seed of Deference
            MobSkillInfo(id = 2162, animationId = 0x5D9, type = 4), // Living Crystal: Seed of Nihility
            MobSkillInfo(id = 2163, animationId = 0x5DA, type = 4), // Living Crystal: Seed of Judgement

            MobSkillInfo(id = 2170, animationId = 0x693, type = 4), // Gargouille: Shadow Burst

            MobSkillInfo(id = 2178, animationId = 0x6A8, type = 4, logged = false), // Amphiptere: Aura knock-back

            MobSkillInfo(id = 2255, animationId = 0x6EF, type = 4), // Corpselight: Corpse Breath

            MobSkillInfo(id = 2262, animationId = 0x6F1, type = 4), // Pixie: Norn Arrows

            MobSkillInfo(id = 2277, animationId = 0x6F0, type = 4), // Taurus: Lithic Ray

            MobSkillInfo(id = 2279, animationId = 0x6D1, type = 4), // Shadow Lord (S): Vicious Kick
            MobSkillInfo(id = 2287, animationId = 0x6D9, type = 1), // Shadow Lord (S): Spawn Shadow

            MobSkillInfo(id = 2330, animationId = 0x694, type = 4), // Behemoth: Ecliptic Meteor

            MobSkillInfo(id = 2346, animationId = 0x647, type = 4), // Coeurl: Mortal Blast

            MobSkillInfo(id = 2383, animationId = 0x72B, type = 4), // Orobon: Mayhem Lantern

            MobSkillInfo(id = 2438, animationId = 0x634, type = 4), // Pixie: Cyclonic Blight

            MobSkillInfo(id = 2466, animationId = 0x775, type = 4, logged = false), // Harpeia:Auto Attack
            MobSkillInfo(id = 2467, animationId = 0x776, type = 4, logged = false), // Harpeia:Auto Attack
            MobSkillInfo(id = 2468, animationId = 0x777, type = 4, logged = false), // Harpeia:Auto Attack

            MobSkillInfo(id = 2479, animationId = 0x75D, type = 4), // Elemental:Searing Tempest
            MobSkillInfo(id = 2480, animationId = 0x75E, type = 4), // Elemental:Blinding Fulgor
            MobSkillInfo(id = 2481, animationId = 0x75F, type = 4), // Elemental:Spectral Floe
            MobSkillInfo(id = 2482, animationId = 0x760, type = 4), // Elemental:Scouring Spate
            MobSkillInfo(id = 2483, animationId = 0x761, type = 4), // Elemental:Anvil Lightning
            MobSkillInfo(id = 2484, animationId = 0x762, type = 4), // Elemental:Silent Storm
            MobSkillInfo(id = 2485, animationId = 0x763, type = 4), // Elemental:Entomb
            MobSkillInfo(id = 2486, animationId = 0x764, type = 4), // Elemental:Tenebral Crush

            MobSkillInfo(id = 2492, animationId = 0x782, type = 4, logged = false), // Mantid:Auto Attack (stab)
            MobSkillInfo(id = 2493, animationId = 0x783, type = 4, logged = false), // Mantid:Auto Attack (slash)
            MobSkillInfo(id = 2494, animationId = 0x784, type = 4, logged = false), // Mantid:Auto Attack (jump)

            MobSkillInfo(id = 2502, animationId = 0x78C, type = 4), // Harpeia: Kaleidoscopic Fury

            MobSkillInfo(id = 2504, animationId = 0x78E, type = 4), // Coeurl: Preternatural Gleam

            MobSkillInfo(id = 2512, animationId = 0x796, type = 4), // Ahriman: Deathly Glare

            MobSkillInfo(id = 2525, animationId = 0x7A1, type = 4, logged = false), // Gallu:Auto-Attack (punches)
            MobSkillInfo(id = 2526, animationId = 0x7A2, type = 4, logged = false), // Gallu:Auto-Attack (swipe)
            MobSkillInfo(id = 2527, animationId = 0x7A3, type = 4, logged = false), // Gallu:Auto-Attack (slam)

            MobSkillInfo(id = 2553, animationId = 0x7C5, type = 4), // Mantid: Immolating Claw

            MobSkillInfo(id = 2555, animationId = 0x7C7, type = 4), // Harpeia: Keraunos Quill
            MobSkillInfo(id = 2566, animationId = 0x7C3, type = 4), // Pixie: Eldritch Wind
            MobSkillInfo(id = 2567, animationId = 0x7C2, type = 4), // Beetle: Rhinowrecker

            MobSkillInfo(id = 2569, animationId = 0x7C1, type = 4), // Corpselight: Louring Skies

            MobSkillInfo(id = 2606, animationId = 0x7CE, type = 4, logged = false), // Pteraketos:Auto-Attack (bite)
            MobSkillInfo(id = 2607, animationId = 0x7CF, type = 4, logged = false), // Pteraketos:Auto-Attack (charge)
            MobSkillInfo(id = 2608, animationId = 0x7D0, type = 4, logged = false), // Pteraketos:Auto-Attack (sweep)
            MobSkillInfo(id = 2609, animationId = 0x7D1, type = 4, logged = false), // Pteraketos:Auto-Attack (right-flank)
            MobSkillInfo(id = 2610, animationId = 0x7D2, type = 4, logged = false), // Pteraketos:Auto-Attack (left-flank)
            MobSkillInfo(id = 2611, animationId = 0x7D3, type = 4, logged = false), // Pteraketos:Auto-Attack (tail flick)

            MobSkillInfo(id = 2618, animationId = 0x7DA, type = 4), // Pteraketos:Waterspout
            MobSkillInfo(id = 2619, animationId = 0x7DB, type = 4), // Pteraketos:Angry Seas

            MobSkillInfo(id = 2621, animationId = 0x7DD, type = 4, logged = false), // Belladonna:Auto-Attack (smack)
            MobSkillInfo(id = 2622, animationId = 0x7DE, type = 4, logged = false), // Belladonna:Auto-Attack (swipe)
            MobSkillInfo(id = 2623, animationId = 0x7DF, type = 4, logged = false), // Belladonna:Auto-Attack (spin)
            MobSkillInfo(id = 2627, animationId = 0x7E3, type = 7), // Belladonna: Full Bloom

            MobSkillInfo(id = 2630, animationId = 0x7E8, type = 4), // Mantid: Exorender

            MobSkillInfo(id = 2663, animationId = 0x82B, type = 4, logged = false), // Yztarg:Auto-Attack (Swipe)
            MobSkillInfo(id = 2664, animationId = 0x82C, type = 4, logged = false), // Yztarg:Auto-Attack (Slam)
            MobSkillInfo(id = 2665, animationId = 0x82D, type = 4, logged = false), // Yztarg:Auto-Attack (Pummel)

            MobSkillInfo(id = 2666, animationId = 0x82E, type = 4), // Yztarg:Soulshattering Roar
            MobSkillInfo(id = 2667, animationId = 0x82F, type = 4), // Yztarg:Calcifying Claw
            MobSkillInfo(id = 2668, animationId = 0x830, type = 4), // Yztarg:Divesting Stampede
            MobSkillInfo(id = 2669, animationId = 0x831, type = 4), // Yztarg:Bonebreaking Barrage
            MobSkillInfo(id = 2670, animationId = 0x832, type = 4), // Yztarg:Beastruction

            MobSkillInfo(id = 2671, animationId = 0x826, type = 4), // Marolith:Metamorphic Blast
            MobSkillInfo(id = 2672, animationId = 0x827, type = 4), // Marolith:Enervating Grasp
            MobSkillInfo(id = 2673, animationId = 0x828, type = 4), // Marolith:Orogenic Storm
            MobSkillInfo(id = 2674, animationId = 0x829, type = 4), // Marolith:Subduction

            MobSkillInfo(id = 2676, animationId = 0x7FB, type = 4), // Gallu:Unrelenting Brand

            MobSkillInfo(id = 2678, animationId = 0x7FD, type = 4), // Wyvern:Blazing Shriek
            MobSkillInfo(id = 2679, animationId = 0x81F, type = 4), // Golem:Volcanic Wrath

            MobSkillInfo(id = 2689, animationId = 0x877, type = 1), // Chapuli: Nature's Meditation
            MobSkillInfo(id = 2690, animationId = 0x878, type = 4), // Chapuli: Sensilla Blades
            MobSkillInfo(id = 2691, animationId = 0x879, type = 4), // Chapuli: Tegmina Buffet
            MobSkillInfo(id = 2692, animationId = 0x87A, type = 4), // Chapuli: Sanguinary Slash
            MobSkillInfo(id = 2693, animationId = 0x87B, type = 4), // Chapuli: Orthopterror

            MobSkillInfo(id = 2694, animationId = 0x851, type = 4), // Twitherym:Tempestuous Upheaval
            MobSkillInfo(id = 2695, animationId = 0x852, type = 4), // Twitherym:Slice'n'Dice
            MobSkillInfo(id = 2696, animationId = 0x853, type = 4), // Twitherym:Black Out
            MobSkillInfo(id = 2697, animationId = 0x854, type = 4), // Twitherym:Smouldering Swarm

            MobSkillInfo(id = 2743, animationId = 0x855, type = 4, logged = false), // Bztavian:Auto-Attack 0
            MobSkillInfo(id = 2744, animationId = 0x856, type = 4, logged = false), // Bztavian:Auto-Attack 1
            MobSkillInfo(id = 2745, animationId = 0x857, type = 4, logged = false), // Bztavian:Auto-Attack 2
            MobSkillInfo(id = 2746, animationId = 0x858, type = 4), // Bztavian:Mandibular Lashing
            MobSkillInfo(id = 2747, animationId = 0x859, type = 4), // Bztavian:Vespine Hurricane
            MobSkillInfo(id = 2748, animationId = 0x85A, type = 4), // Bztavian:Stinger Volley
            MobSkillInfo(id = 2749, animationId = 0x85B, type = 4), // Bztavian:Droning Whirlwind
            MobSkillInfo(id = 2750, animationId = 0x85C, type = 4), // Bztavian:Incisive Denouement
            MobSkillInfo(id = 2751, animationId = 0x85D, type = 4), // Bztavian:Incisive Apotheosis

            MobSkillInfo(id = 2698, animationId = 0x881, type = 4, logged = false), // Craklaw:Auto-Attack 0 (Triple)
            MobSkillInfo(id = 2699, animationId = 0x882, type = 4, logged = false), // Craklaw:Auto-Attack 1 (Swipe)
            MobSkillInfo(id = 2700, animationId = 0x883, type = 4, logged = false), // Craklaw:Auto-Attack 2 (Spin)
            MobSkillInfo(id = 2701, animationId = 0x884, type = 1), // Craklaw:Impenetrable Carapace
            MobSkillInfo(id = 2702, animationId = 0x885, type = 4), // Craklaw:Rending Deluge
            MobSkillInfo(id = 2703, animationId = 0x886, type = 4), // Craklaw:Sundering Snip
            MobSkillInfo(id = 2704, animationId = 0x887, type = 4), // Craklaw:Viscid Spindrift
            MobSkillInfo(id = 2705, animationId = 0x888, type = 4), // Craklaw:Riptide Eupnea

            MobSkillInfo(id = 2714, animationId = 0x889, type = 4), // Umbril:Paralyzing Triad
            MobSkillInfo(id = 2715, animationId = 0x88A, type = 4), // Umbril:Crepuscular Grasp
            MobSkillInfo(id = 2716, animationId = 0x88B, type = 4), // Umbril:Necrotic Brume
            MobSkillInfo(id = 2717, animationId = 0x88C, type = 4), // Umbril:Terminal Bloom

            MobSkillInfo(id = 2718, animationId = 0x88D, type = 4), // Acuex:Foul Waters
            MobSkillInfo(id = 2719, animationId = 0x88E, type = 4), // Acuex:Pestilent Plume
            MobSkillInfo(id = 2720, animationId = 0x88F, type = 4), // Acuex:Deadening Haze
            MobSkillInfo(id = 2721, animationId = 0x890, type = 4), // Acuex:Venomous Vapor

            MobSkillInfo(id = 2752, animationId = 0x891, type = 4, logged = false), // Rockfin:Auto-Attack 0 (Bite)
            MobSkillInfo(id = 2753, animationId = 0x892, type = 4, logged = false), // Rockfin:Auto-Attack 1 (Charge)
            MobSkillInfo(id = 2754, animationId = 0x893, type = 4, logged = false), // Rockfin:Auto-Attack 2 (Spin)
            MobSkillInfo(id = 2755, animationId = 0x894, type = 4), // Rockfin:Protolithic Puncture
            MobSkillInfo(id = 2756, animationId = 0x895, type = 4), // Rockfin:Aquatic Lance
            MobSkillInfo(id = 2757, animationId = 0x896, type = 4), // Rockfin:Pelagic Cleaver
            MobSkillInfo(id = 2758, animationId = 0x897, type = 1), // Rockfin:Carcharian Verve
            MobSkillInfo(id = 2759, animationId = 0x898, type = 4), // Rockfin:Tidal Guillotine
            MobSkillInfo(id = 2760, animationId = 0x899, type = 4), // Rockfin:Marine Mayhem

            MobSkillInfo(id = 2771, animationId = 0x868, type = 4), // Obstacle (Blossom)
            MobSkillInfo(id = 2772, animationId = 0x869, type = 4), // Obstacle (Fungus)

            MobSkillInfo(id = 2774, animationId = 0x833, type = 4), // Panopt: Retinal Glare
            MobSkillInfo(id = 2775, animationId = 0x834, type = 4), // Panopt: Sylvan Slumber
            MobSkillInfo(id = 2776, animationId = 0x835, type = 4), // Panopt: Crushing Gaze
            MobSkillInfo(id = 2777, animationId = 0x836, type = 4), // Panopt: Vaskania

            MobSkillInfo(id = 2778, animationId = 0x837, type = 4, logged = false), // Giant Gnat: Auto Attack (Stab)
            MobSkillInfo(id = 2779, animationId = 0x838, type = 4, logged = false), // Giant Gnat: Auto Attack (Swipe)
            MobSkillInfo(id = 2780, animationId = 0x839, type = 4, logged = false), // Giant Gnat: Auto Attack (Slam)
            MobSkillInfo(id = 2781, animationId = 0x83A, type = 4), // Giant Gnat: Flesh Syphon
            MobSkillInfo(id = 2782, animationId = 0x83B, type = 4), // Giant Gnat: Umbral Expunction
            MobSkillInfo(id = 2783, animationId = 0x83C, type = 4), // Giant Gnat: Sticky Situation
            MobSkillInfo(id = 2784, animationId = 0x83D, type = 4), // Giant Gnat: Abdominal Assault
            MobSkillInfo(id = 2785, animationId = 0x83E, type = 4), // Giant Gnat: Mandibular Massacre

            MobSkillInfo(id = 2798, animationId = 0x89A, type = 4, logged = false), // Yggdreant: Auto-Attack (Poke)
            MobSkillInfo(id = 2799, animationId = 0x89B, type = 4, logged = false), // Yggdreant: Auto-Attack (Sweep)
            MobSkillInfo(id = 2800, animationId = 0x89C, type = 4, logged = false), // Yggdreant: Auto-Attack (Spin)
            MobSkillInfo(id = 2801, animationId = 0x89D, type = 4), // Yggdreant: Root of the Problem
            MobSkillInfo(id = 2802, animationId = 0x89E, type = 4), // Yggdreant: Potted Plant
            MobSkillInfo(id = 2803, animationId = 0x89F, type = 4), // Yggdreant: Uproot
            MobSkillInfo(id = 2804, animationId = 0x8A0, type = 4), // Yggdreant: Canopierce
            MobSkillInfo(id = 2805, animationId = 0x8A1, type = 4), // Yggdreant: Firefly Fandango
            MobSkillInfo(id = 2806, animationId = 0x8A2, type = 4), // Yggdreant: Tiiimbeeer

            MobSkillInfo(id = 2813, animationId = 0x8A6, type = 4, logged = false), // Waktza:Auto-Attack 0
            MobSkillInfo(id = 2814, animationId = 0x8A7, type = 4, logged = false), // Waktza:Auto-Attack 1
            MobSkillInfo(id = 2815, animationId = 0x8A8, type = 4, logged = false), // Waktza:Auto-Attack 2
            MobSkillInfo(id = 2816, animationId = 0x8A9, type = 4), // Waktza:Crashing Thunder
            MobSkillInfo(id = 2817, animationId = 0x8AA, type = 4), // Waktza:Reverberating Cry
            MobSkillInfo(id = 2818, animationId = 0x8AB, type = 4), // Waktza:Brown Out
            MobSkillInfo(id = 2819, animationId = 0x8AC, type = 4), // Waktza:Reverse Current
            MobSkillInfo(id = 2820, animationId = 0x8AD, type = 4), // Waktza:Sparkstorm
            MobSkillInfo(id = 2821, animationId = 0x8AE, type = 4), // Waktza:Static Prison

            MobSkillInfo(id = 2841, animationId = 0x87C, type = 4), // Snapweed: Tickling Tendrils
            MobSkillInfo(id = 2842, animationId = 0x87D, type = 4), // Snapweed: Stink Bomb
            MobSkillInfo(id = 2843, animationId = 0x87E, type = 4), // Snapweed: Nectarous Deluge
            MobSkillInfo(id = 2844, animationId = 0x87F, type = 4), // Snapweed: Nepenthic Plunge
            MobSkillInfo(id = 2845, animationId = 0x880, type = 4), // Snapweed: Infaunal Flop

            MobSkillInfo(id = 2871, animationId = 0x8DB, type = 4), // Slime: Frizz

            MobSkillInfo(id = 2895, animationId = 0x8E6, type = 4, logged = false), // Obstacle (Ice)

            MobSkillInfo(id = 3248, animationId = 0x9A7, type = 4), // Morbol:Rancid Breath
            MobSkillInfo(id = 3249, animationId = 0x9D1, type = 4), // Diremite:Geotic Spin (Ionos uses animation 0x341)
            MobSkillInfo(id = 3250, animationId = 0x9CB, type = 4), // Yztarg:Hellfire Arrow
            MobSkillInfo(id = 3251, animationId = 0x9CC, type = 4), // Yztarg:Incensed Pummel

            MobSkillInfo(id = 3295, animationId = 0x9E4, type = 4), // Fenrir:Lunar Bay
            MobSkillInfo(id = 3296, animationId = 0x9E5, type = 4), // Fenrir:Impact
            MobSkillInfo(id = 3297, animationId = 0x9E6, type = 4), // Carbuncle:Holy Mist

            MobSkillInfo(id = 3308, animationId = 0x9D4, type = 4), // Pteraketos:Aetheric Pull
            MobSkillInfo(id = 3309, animationId = 0x9D5, type = 4), // Pteraketos:Necrotic Wave

            MobSkillInfo(id = 3315, animationId = 0x9D6, type = 4), // Giant Gnat: Viscous Deluge

            MobSkillInfo(id = 3365, animationId = 0x9E3, type = 4), // Trust Selh'teus:Luminous Lance
            MobSkillInfo(id = 3366, animationId = 0x9E1, type = 1), // Trust Selh'teus:Rejuvenation
            MobSkillInfo(id = 3367, animationId = 0x9E2, type = 4), // Trust Selh'teus:Revelation

            MobSkillInfo(id = 3507, animationId = 0x9F1, type = 4), // Dragon: Discordant Note

        ).associateBy { it.id }
    }

}
