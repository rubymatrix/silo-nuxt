package xim.poc.game.configuration.assetviewer

import xim.poc.game.StatusEffect
import xim.poc.game.configuration.SkillApplier
import xim.poc.game.configuration.SkillAppliers
import xim.poc.game.configuration.constants.itemCopseCandy_6008

object AssetViewerItemDefinitions {

    fun register() {
        // Copse Candy
        SkillAppliers[itemCopseCandy_6008] = SkillApplier(targetEvaluator = {
            val status = it.targetState.gainStatusEffect(StatusEffect.Costume)
            status.counter = 0x9E2
            emptyList()
        })
    }

}