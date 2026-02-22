package xim.poc.game.configuration.assetviewer

import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillApplierHelper
import xim.poc.game.configuration.SkillAppliers
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.basicElementDamage
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.basicEnhancingMagicStatusEffect
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.basicHealingMagic
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.sourceHpPercentDamage
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.summonBubble
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.summonLuopan
import xim.poc.game.configuration.assetviewer.AssetViewerSkillApplierHelpers.summonPet
import xim.poc.game.configuration.constants.*
import xim.poc.game.event.TrustSummonEvent
import xim.resource.MagicType
import xim.resource.SpellInfo
import xim.resource.table.SpellInfoTable
import xim.resource.table.SpellNameTable
import kotlin.time.Duration.Companion.seconds

object AssetViewerSpellAppliers {
    
    fun register() {
        // Cure
        SkillAppliers[spellCure_1] = SkillApplier(
            targetEvaluator = basicHealingMagic(PotencyConfiguration(potency = 2f))
        )

        // Cure II
        SkillAppliers[spellCureII_2] = SkillApplier(
            targetEvaluator = basicHealingMagic(PotencyConfiguration(potency = 4f)),
        )

        // Curaga
        SkillAppliers[spellCuraga_7] = SkillApplier(
            targetEvaluator = basicHealingMagic(PotencyConfiguration(potency = 2f)),
        )

        // Enfire
        SkillAppliers[spellEnfire_100] = SkillApplier(
            targetEvaluator = basicEnhancingMagicStatusEffect(status = StatusEffect.Enfire, baseDuration = 30.seconds),
        )

        // Enblizzard
        SkillAppliers[spellEnblizzard_101] = SkillApplier(
            targetEvaluator = basicEnhancingMagicStatusEffect(status = StatusEffect.Enblizzard, baseDuration = 30.seconds),
        )

        // Fire
        SkillAppliers[spellFire_144] = SkillApplier(
            targetEvaluator = basicElementDamage(PotencyConfiguration(potency = 1f)),
        )

        // Blizzard
        SkillAppliers[spellBlizzard_149] = SkillApplier(
            targetEvaluator = basicElementDamage(PotencyConfiguration(potency = 20f)),
        )

        // Firaga
        SkillAppliers[spellFiraga_174] = SkillApplier(
            targetEvaluator = basicElementDamage(PotencyConfiguration(potency = 1f)),
        )

        // Blizzaga
        SkillAppliers[spellBlizzaga_179] = SkillApplier(
            targetEvaluator = basicElementDamage(PotencyConfiguration(potency = 20f)),
        )

        // Blaze Spikes
        SkillAppliers[spellBlazeSpikes_249] = SkillApplier(
            targetEvaluator = basicEnhancingMagicStatusEffect(status = StatusEffect.BlazeSpikes, baseDuration = 30.seconds),
        )

        // Ice Spikes
        SkillAppliers[spellIceSpikes_250] = SkillApplier(
            targetEvaluator = basicEnhancingMagicStatusEffect(status = StatusEffect.IceSpikes, baseDuration = 30.seconds),
        )

        // Fire Spirit
        SkillAppliers[spellFireSpirit_288] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x08),
        )

        // Ice Spirit
        SkillAppliers[spellIceSpirit_289] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x09),
        )

        // Air Spirit
        SkillAppliers[spellAirSpirit_290] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x0A),
        )

        // Earth Spirit
        SkillAppliers[spellEarthSpirit_291] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x0B),
        )

        // Thunder Spirit
        SkillAppliers[spellThunderSpirit_292] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x0D),
        )

        // Water Spirit
        SkillAppliers[spellWaterSpirit_293] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x0C),
        )

        // Light Spirit
        SkillAppliers[spellLightSpirit_294] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x0E),
        )

        // Dark Spirit
        SkillAppliers[spellDarkSpirit_295] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x0F),
        )

        // Carbuncle
        SkillAppliers[spellCarbuncle_296] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x10),
        )

        // Fenrir
        SkillAppliers[spellFenrir_297] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x11),
        )

        // Ifrit
        SkillAppliers[spellIfrit_298] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x12),
        )

        // Titan
        SkillAppliers[spellTitan_299] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x13),
        )

        // Leviathan
        SkillAppliers[spellLeviathan_300] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x14),
        )

        // Garuda
        SkillAppliers[spellGaruda_301] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x15),
        )

        // Shiva
        SkillAppliers[spellShiva_302] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x16),
        )

        // Ramuh
        SkillAppliers[spellRamuh_303] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x17),
        )

        // Diabolos
        SkillAppliers[spellDiabolos_304] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x19),
        )

        // Odin
        SkillAppliers[spellOdin_305] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x1A),
        )

        // Alexander
        SkillAppliers[spellAlexander_306] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x1B),
        )

        // Cait Sith
        SkillAppliers[spellCaitSith_307] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x1C),
        )

        // Siren
        SkillAppliers[spellSiren_355] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x1E),
        )

        // Self Destruct
        SkillAppliers[spellSelfDestruct_533] = SkillApplier(
            targetEvaluator = sourceHpPercentDamage(PotencyConfiguration(potency = 0.5f)),
            additionalSelfEvaluator = sourceHpPercentDamage(PotencyConfiguration(potency = 1f)),
        )

        // Atomos
        SkillAppliers[spellAtomos_847] = SkillApplier(
            targetEvaluator = summonPet(lookId = 0x1D),
        )

        // All Geo spells
        (0 until 1024)
            .map { SpellInfoTable[it] }
            .filter { it.magicType == MagicType.Geomancy }
            .filter { SpellNameTable.first(it.index).startsWith("Geo") }
            .forEach { SkillAppliers[SpellSkillId(it.index)] = SkillApplier(targetEvaluator = summonLuopan(it)) }

        // All Indi spells
        (0 until 1024)
            .map { SpellInfoTable[it] }
            .filter { it.magicType == MagicType.Geomancy }
            .filter { SpellNameTable.first(it.index).startsWith("Indi") }
            .forEach { SkillAppliers[SpellSkillId(it.index)] = SkillApplier(targetEvaluator = summonBubble(it)) }


        // All trust spells
        (0 until 1024)
            .map { SpellInfoTable[it] }
            .filter { it.magicType == MagicType.Trust }
            .forEach { SkillAppliers[SpellSkillId(it.index)] = SkillApplier(targetEvaluator = summonTrust(it)) }


    }

    private fun summonTrust(spellInfo: SpellInfo): SkillApplierHelper.TargetEvaluator {
        return SkillApplierHelper.TargetEvaluator {
            listOf(TrustSummonEvent(it.sourceState.id, spellInfo.index))
        }
    }

}