package xim.resource.table

import xim.poc.RaceGenderConfig
import xim.poc.browser.DatLoader
import xim.poc.game.configuration.constants.AbilitySkillId
import xim.poc.game.configuration.constants.skillExudation_175
import xim.poc.game.configuration.constants.skillFastBladeII_229
import xim.resource.*
import xim.util.OnceLogger
import kotlin.random.Random

val AbilityNameTable = StringTable("ROM/181/72.DAT")
val AbilityDescriptionTable = StringTable("ROM/181/74.DAT")

object AbilityInfoTable: LoadableResource {

    private lateinit var abilityListResource: AbilityListResource
    private lateinit var reinforcementPointsTable: TableResource
    private lateinit var levelTable: TableResource // this table is wrong, but it's a convenient approximation for now
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::abilityListResource.isInitialized
    }

    fun hasInfo(id: Int): Boolean {
        return abilityListResource.abilities[id] != null
    }

    fun getAbilityInfo(id: Int) : AbilityInfo {
        return abilityListResource.abilities[id] ?: throw IllegalStateException("No info for: $id")
    }

    fun getAbilityCount(): Int {
        return abilityListResource.abilities.size
    }

    fun getLevelTable(): TableResource {
        return levelTable
    }

    fun getReinforcementPointsTable(): TableResource {
        return reinforcementPointsTable
    }

    operator fun get(id: Int) = getAbilityInfo(id)

    private fun loadTable() {
        DatLoader.load("ROM/118/114.DAT").onReady {
            abilityListResource = it.getAsResource().getOnlyChildByType(AbilityListResource::class)
            levelTable = it.getAsResource().getChildAs(DatId("levc"), TableResource::class)
            reinforcementPointsTable = it.getAsResource().getChildAs(DatId("mnc2"), TableResource::class)
        }
    }

    fun AbilitySkillId.toAbilityInfo(): AbilityInfo {
        return AbilityInfoTable[id]
    }

    fun mutate(abilitySkillId: AbilitySkillId, fn: (AbilityInfo) -> AbilityInfo) {
        val existing = abilitySkillId.toAbilityInfo()
        val modified = fn.invoke(existing)
        abilityListResource.abilities[abilitySkillId.id] = modified
    }

}

data class AbilityAnimInfo(val id: Int, val type: Int, val animationId: Int)

// https://github.com/LandSandBoat/server/blob/base/sql/abilities.sql
// https://github.com/LandSandBoat/server/blob/base/sql/weapon_skills.sql
object AbilityTable: LoadableResource {

    private lateinit var animInfos: Map<Int, AbilityAnimInfo>
    private var preloaded = false

    override fun preload() {
        if (preloaded) { return }
        preloaded = true
        loadTable()
    }

    override fun isFullyLoaded() : Boolean {
        return this::animInfos.isInitialized
    }

    fun getAnimationId(abilityInfo: AbilityInfo, raceGenderConfig: RaceGenderConfig?): Int? {
        val animInfo = animInfos[abilityInfo.index] ?: return null

        val offset = Random.nextInt(0, abilityInfo.getNumVariants())

        return offset + if (abilityInfo.type == AbilityType.PetAbility || abilityInfo.type == AbilityType.PetWard || animInfo.type == 0x0D) {
            // Pet skills & Wyvern skills
            0x113C + 94
        } else if (abilityInfo.type == AbilityType.WeaponSkill || animInfo.type == 0x03) {
            raceGenderConfig ?: return null
            MainDll.getBaseWeaponSkillAnimationIndex(raceGenderConfig) + animInfo.animationId
        } else if (animInfo.type == 0x06) {
            0x113C + animInfo.animationId
        } else if (animInfo.type == 0x0E) {
            raceGenderConfig ?: return null
            MainDll.getBaseDanceSkillAnimationIndex(raceGenderConfig) + animInfo.animationId
        } else {
            OnceLogger.warn("[${animInfo.id}] Unknown animation type: $abilityInfo | $animInfo")
            return null
        }
    }

    private fun loadTable() {
        DatLoader.load("landsandboat/AbilitiesTable-2024-06-30.DAT").onReady { parseTable(it.getAsBytes()) }
    }

    private fun parseTable(byteReader: ByteReader) {
        val skills = HashMap<Int, AbilityAnimInfo>()
        while (byteReader.hasMore()) {
            val skill = AbilityAnimInfo(id = byteReader.next32(), type = byteReader.next32(), animationId = byteReader.next32())
            skills[skill.id] = skill
            byteReader.position += 4 // Padding
        }

        skills.putAll(additionalConfiguration())

        this.animInfos = skills
    }

    private fun additionalConfiguration(): Map<Int, AbilityAnimInfo> {
        return mapOf(
            skillExudation_175 to AbilityAnimInfo(id = skillExudation_175.id, type = 3, animationId = 75),
            skillFastBladeII_229 to AbilityAnimInfo(id = skillFastBladeII_229.id, type = 3, animationId = 1),
        ).mapKeys { it.key.id }
    }

}

object PetSkillTable {

    private const val baseFileTableIndex = 0xC0EF

    fun getAnimationId(playerSkill: AbilityInfo): Int? {
        // TODO - This is a very rough approximation
        return baseFileTableIndex + (playerSkill.index - 1024)
    }

}