package xim.resource

import xim.util.OnceLogger
import xim.util.PI_f

enum class RangeType(val index: Int, val subtype: Int) {
    None(0x00, 1),
    Wind(0x01, 1),
    String(0x02, 1),
    Marksmanship(0x03, 0),
    ThrowingWeapon(0x04, 0), // Boomerang-type?
    ThrowingAmmo(0x05, 0), // Boomerang-type?
    Archery(0x06, 0),
    // no 0x07
    // no 0x08
    // no 0x09
    HandbellIndi(0x0A, 2),
    HandbellGeo(0x0B, 2),
    Unset(0xFF, 0),
    ;

    companion object {
        fun fromIndex(index: Int) : RangeType? {
            return RangeType.values().firstOrNull { it.index == index }
        }
    }

}

enum class MovementType(val index: Int, val slopeOriented: Boolean = false) {
    Walking(0x0),
    Sliding(0x1, slopeOriented = true), //??? Pugil, Crawler, etc
    Large(0x2, slopeOriented = true), //??? Morbol, Treant, Behe, SeaMonk
    Flying(0x3), // bats, birds, bees, bombs, etc
    Unset(0xFF), // NPCs that don't move, footwear, etc
    ;

    companion object {
        fun fromIndex(index: Int) : MovementType {
            return MovementType.values().firstOrNull { it.index == index } ?: throw IllegalStateException("Unknown movement type: ${index.toString(0x10)}")
        }
    }
}

class InfoDefinition(
    val movementType: MovementType = MovementType.Unset,
    val movementChar: Char = '0',
    val shakeFactor: Int = 0,
    val weaponAnimationType: Int = 0,
    val weaponAnimationSubType: Int = 0,
    val standardJointIndex: Int? = null,
    val scale: Int? = null,
    val staticNpcScale: Int? = null,
    val rangeType: RangeType = RangeType.Unset,
)

class MountDefinition(
    val rotation: Float = 0f,
    val poseType: Int = 0,
)

class InfoSection(private val sectionHeader: SectionHeader) : ResourceParser {

    private var infoDefinition: InfoDefinition = InfoDefinition()
    private var mountDefinition: MountDefinition = MountDefinition()

    override fun getResource(byteReader: ByteReader): ParserResult {
        read(byteReader)
        val resource = InfoResource(sectionHeader.sectionId, infoDefinition, mountDefinition)
        return ParserResult.from(resource)
    }

    private fun read(byteReader: ByteReader) {
        when (sectionHeader.sectionId) {
            DatId.info -> readInfoDefinition(byteReader)
            DatId.mount -> readMountDefinition(byteReader)
            else -> {
                OnceLogger.error("[$byteReader] Unknown info type: $sectionHeader. Defaulting to standard info type.")
                readInfoDefinition(byteReader)
            }
        }
    }

    private fun readInfoDefinition(byteReader: ByteReader) {
        byteReader.offsetFromDataStart(sectionHeader, 0x0)

        val movementType = MovementType.fromIndex(byteReader.next8())
        val movementChar = toMovementChar(byteReader.next8())
        val shakeFactor = byteReader.next8()    // Not sure

        val weaponType = byteReader.next8()
        val weaponSubType = byteReader.next8()
        val weaponUnk1 = byteReader.next8()
        val standardJointIndex = nullIf0xFF(byteReader.next8())

        val unk0x07 = byteReader.next8()
        val unk0x08 = byteReader.next8()
        val unk0x09 = byteReader.next8()
        val scale = nullIf0xFF(byteReader.next8())
        val staticNpcScale = nullIf0xFF(byteReader.next8())
        val unk0x0C = byteReader.next8()
        val unk0x0D = byteReader.next8()
        val rawRangeType = byteReader.next8()
        val unk0x0F = byteReader.next8()

        val rangeType = RangeType.fromIndex(rawRangeType)
        if (rangeType == null) { OnceLogger.warn("[$byteReader] Unknown range type: ${rawRangeType.toString(0x10)}") }

        infoDefinition = InfoDefinition(
            movementType = movementType,
            movementChar = movementChar,
            shakeFactor = shakeFactor,
            weaponAnimationType = weaponType,
            weaponAnimationSubType = weaponSubType,
            standardJointIndex = standardJointIndex,
            scale = scale,
            staticNpcScale = staticNpcScale,
            rangeType = rangeType ?: RangeType.Unset,
        )
    }

    private fun readMountDefinition(byteReader: ByteReader) {
        val unk0x00 = byteReader.next8()
        val unk0x01 = byteReader.next8()
        val rotation = byteReader.next8()
        val unk0x03 = byteReader.next8()
        val unk0x04 = byteReader.next8()
        val unk0x05 = byteReader.next8()
        val unk0x06 = byteReader.next8()
        val unk0x07 = byteReader.next8()
        val unk0x08 = byteReader.next8()
        val unk0x09 = byteReader.next8()
        val poseType = byteReader.next8()
        val unk0x0B = byteReader.next8()
        val unk0x0C = byteReader.next8()
        val unk0x0D = byteReader.next8()
        val unk0x0E = byteReader.next8()
        val unk0x0F = byteReader.next8()

        mountDefinition = MountDefinition(
            rotation = 2 * PI_f * (rotation / 255f),
            poseType = poseType,
        )
    }

    private fun toMovementChar(value: Int): Char {
        return if (value == 0xFF) { '0' } else { DatId.base36ToChar(value) }
    }

    private fun nullIf0xFF(value: Int): Int? {
        return if (value == 0xFF) { null } else { value }
    }

}
