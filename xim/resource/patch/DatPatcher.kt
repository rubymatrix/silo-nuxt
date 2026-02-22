package xim.resource.patch

import xim.resource.ByteReader
import xim.resource.DatId
import xim.resource.SectionHeader
import xim.util.OnceLogger.warn

private interface DatPatcher {

    fun apply(byteReader: ByteReader)

    fun expectedSize(): Int?

}

private fun checkDat(byteReader: ByteReader, offset: Int, expected: String): Boolean {
    byteReader.position = offset
    val actual = byteReader.nextDatId()
    return actual.id == expected
}

private fun check16(byteReader: ByteReader, offset: Int, expected: Int): Boolean {
    byteReader.position = offset
    return expected == byteReader.next16()
}

private fun mutateDat(byteReader: ByteReader, offset: Int, value: String) {
    byteReader.position = offset
    val chars = value.toCharArray()

    for (i in 0 until 4) {
        byteReader.bytes[byteReader.position + i] = chars[i].code.toByte()
    }
}

private fun mutate16(byteReader: ByteReader, offset: Int, value: Int) {
    byteReader.position = offset
    byteReader.write16(value)
}

private fun seekTo(byteReader: ByteReader, datId: DatId): Boolean {
    byteReader.position = 0

    while (byteReader.hasMore()) {
        val current = SectionHeader()
        current.read(byteReader)

        if (current.sectionId == datId) {
            byteReader.position = current.sectionStartPosition
            return true
        }

        byteReader.offsetFrom(current, current.sectionSize)
    }

    return false
}

object DatPatchManager {

    private val patches = mapOf(
        "ROM/142/113.DAT" to FrenziedRageFix,
        "ROM/142/115.DAT" to ChargedWhiskerFix,
    )

    fun patchIfNeeded(resourceName: String, byteReader: ByteReader) {
        val patcher = patches[resourceName] ?: return

        val expectedSize = patcher.expectedSize()
        if (expectedSize != null && expectedSize != byteReader.bytes.length) {
            warn("[$resourceName] Patcher size-check failed: ${patcher.expectedSize()} vs $byteReader")
            return
        } else {
            warn("[$resourceName] Applying patch!")
        }

        patcher.apply(byteReader)
        byteReader.position = 0
    }

}

// Animation is bugged in-game - the skeletal animations are enqueued in reversed priority.
// Verified that the below fixes the animations in-game, as well.
private object ChargedWhiskerFix: DatPatcher {

    override fun expectedSize() = 0x26020

    override fun apply(byteReader: ByteReader) {
        // The skeleton animations are enqueued in reverse priority order
        if (!checkDat(byteReader, 0x01BD4, "wz22")) { return }
        if (!checkDat(byteReader, 0x01D04, "wz20")) { return }
        if (!checkDat(byteReader, 0x14700, "wz20")) { return }
        if (!checkDat(byteReader, 0x20BD0, "wz22")) { return }

        mutateDat(byteReader, 0x01BD4, "wz20")
        mutateDat(byteReader, 0x01D04, "wz22")
        mutateDat(byteReader, 0x14700, "wz22")
        mutateDat(byteReader, 0x20BD0, "wz20")
    }

}

private object FrenziedRageFix: DatPatcher {

    override fun expectedSize() = 0x1C240

    override fun apply(byteReader: ByteReader) {
        // The skeleton animations are enqueued in reverse priority order
        if (!checkDat(byteReader, 0x01080, "wz41")) { return }
        if (!checkDat(byteReader, 0x010A8, "wz40")) { return }
        if (!checkDat(byteReader, 0x0FA30, "wz40")) { return }
        if (!checkDat(byteReader, 0x14AC0, "wz41")) { return }

        mutateDat(byteReader, 0x01080, "wz40")
        mutateDat(byteReader, 0x010A8, "wz41")
        mutateDat(byteReader, 0x0FA30, "wz41")
        mutateDat(byteReader, 0x14AC0, "wz40")

        // After fixing the order, the loop-param needs adjusting
        if (!check16(byteReader, 0x010BE, 3)) { return }
        mutate16(byteReader, 0x010BE, 1)

        // Particle effects - the eyes are misconfigured (set to Target attach-type, but only specifying Source joint)
        listOf(0x170, 0x2B0, 0x3F0, 0x530).forEach {
            if (check16(byteReader, it, 0x0062)) { mutate16(byteReader, it, 0x1862) }
        }
    }

}