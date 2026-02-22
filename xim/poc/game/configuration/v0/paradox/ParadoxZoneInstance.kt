package xim.poc.game.configuration.v0.paradox

import xim.math.Matrix4f
import xim.math.Vector3f
import xim.poc.game.configuration.v0.FloorEntity
import xim.poc.game.configuration.v0.GameV0
import xim.poc.game.configuration.v0.ZoneLogic
import xim.poc.tools.ZoneConfig
import xim.util.PI_f

class ParadoxZoneInstance: ZoneLogic {

    companion object {
        val zoneConfig = ZoneConfig(zoneId = 36, startPosition = Vector3f(520f, 0f, 520f))
    }

    private val floorEntities = ArrayList<FloorEntity>()

    init {
        createCrystals()
        floorEntities += ParadoxTransfer(location = Vector3f(520f, 0f, 520f), destination = GameV0.configuration.startingZoneConfig)
    }

    override fun update(elapsedFrames: Float) {
        floorEntities.forEach { it.update(elapsedFrames) }
    }

    override fun cleanUp() {
        floorEntities.forEach { it.cleanup() }
    }

    override fun getEntryPosition(): ZoneConfig {
        return ZoneConfig(zoneId = 36, startPosition = Vector3f(520f, 0f, 520f) + Vector3f.North * 2f)
    }

    override fun toNew(): ZoneLogic {
        return ParadoxZoneInstance()
    }

    override fun getMusic(): Int {
        return 236
    }

    private fun createCrystals() {
        for (i in 0 until 6) {
            val type = CrystalType.values()[i]
            val offset = Matrix4f().rotateYInPlace(i * PI_f/3f).transform(Vector3f.North * 15f)
            floorEntities += CrystalNpc(zoneConfig.startPosition!! + offset, type)
        }

        floorEntities += CrystalNpc(zoneConfig.startPosition!! + Vector3f.North * 7.5f, CrystalType.Light)
        floorEntities += CrystalNpc(zoneConfig.startPosition!! + Vector3f.South * 7.5f, CrystalType.Dark)
    }

}