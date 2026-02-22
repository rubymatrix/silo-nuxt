package xim.resource.patch

import xim.math.Vector3f
import xim.poc.gl.ByteColor
import xim.resource.*
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

private interface DatPostProcessor {
    fun process(root: DirectoryResource)
}

object DatPostProcessorManager {

    private val patches = mapOf(
        "ROM/0/0.DAT" to StaticResourceFix,
        "ROM5/0/25.DAT" to ThroneRoomZClippingFix,
        "ROM/118/48.DAT" to MegalithThrowFix,
        "ROM2/24/44.DAT" to MegalithThrowFix,
        "ROM/301/99.DAT" to YovraReplicaFix,
    )

    fun patchIfNeeded(resourceName: String, root: DirectoryResource) {
        patches[resourceName]?.process(root)
    }

}

private object StaticResourceFix: DatPostProcessor {

    override fun process(root: DirectoryResource) {
        // Fix "tracking" on en-aspir & en-drain effect
        val g261 = root.getNullableChildRecursivelyAs(DatId("g261"), EffectResource::class)
        val g301 = root.getNullableChildRecursivelyAs(DatId("g301"), EffectResource::class)

        listOfNotNull(g261, g301).forEach {
            val tracker = it.particleGenerator.generatorUpdaters.filterIsInstance<AssociationUpdater>().firstOrNull()
            tracker?.followAttachedFacing = true
        }
    }

}

private object ThroneRoomZClippingFix: DatPostProcessor {

    override fun process(root: DirectoryResource) {
        val zoneResource = root.getFirstChildByTypeRecursively(ZoneResource::class) ?: return

        val drawOrder = ArrayList(zoneResource.zoneObj)
        val z10 = drawOrder[10]
        drawOrder[10] = drawOrder[11]
        drawOrder[11] = z10

        zoneResource.objectDrawOrder = drawOrder
    }

}

private object MegalithThrowFix: DatPostProcessor {

    override fun process(root: DirectoryResource) {
        // [ii00] in [Megalith Throw] seems to be broken. The client seems to detect the skill, and make some adjustments.
        // For example, if [Rock Throw] is overwritten by [Megalith Throw], and then [Rock Throw] is used,
        // [ii00] looks completely different (even when executed by Titan). The particle-engine correctly displays _that_ version.
        val ii00 = root.getNullableChildRecursivelyAs(DatId("ii00"), EffectResource::class) ?: return
        val st02 = root.getNullableChildRecursivelyAs(DatId("st02"), EffectResource::class) ?: return

        // Originally [SourceWeapon], which uses the scale from the skeleton.
        ii00.particleGenerator.attachType = AttachType.SourceActor

        // Copy the scale & rotation from [st02], so the parent->child transition is smooth
        val st02Rotation = st02.particleGenerator.initializers.filterIsInstance<RotationInitializer>()
            .firstOrNull()?.rotation ?: return

        val st02Scale = st02.particleGenerator.initializers.filterIsInstance<ScaleInitializer>()
            .firstOrNull()?.scale ?: return

        ii00.particleGenerator.initializers.filterIsInstance<RotationInitializer>()
            .firstOrNull()?.rotation?.copyFrom(Vector3f(0f, st02Rotation.y, 0f))

        ii00.particleGenerator.initializers.filterIsInstance<ScaleInitializer>()
            .firstOrNull()?.scale?.copyFrom(st02Scale)

        // Disable the scaling-updater so that scale isn't overwritten
        ii00.particleGenerator.updaters.removeAll { it is ProgressValueUpdater && it.allocationOffset == 0x3 }
    }

}

private object YovraReplicaFix: DatPostProcessor {
    override fun process(root: DirectoryResource) {
        val popRoutine = root.getNullableChildRecursivelyAs(DatId.pop, EffectRoutineResource::class) ?: return

        popRoutine.patchEffect(ActorFadeRoutine::class,
            filter = { it.endColor == ByteColor.half },
            replacer = { it.copy(endColor = ByteColor(0x40, 0x40, 0x40, 0x80)) }
        )
    }
}

private fun <T : Effect> EffectRoutineResource.patchEffect(type: KClass<T>, filter: (T) -> Boolean, replacer: (T) -> Effect) {
    val effects = this.effectRoutineDefinition.effects

    for (i in effects.indices) {
        val effect = type.safeCast(effects[i]) ?: continue
        if (!filter.invoke(effect)) { continue }
        effects[i] = replacer.invoke(effect)
        break
    }
}