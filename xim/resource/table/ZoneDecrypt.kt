package xim.resource.table

import js.typedarrays.Uint8Array
import xim.resource.ByteReader
import xim.resource.SectionHeader
import xim.util.OnceLogger

object ZoneDecrypt {

    private val keyTable1: Uint8Array by lazy { MainDll.getZoneDecryptTable1() }
    private val keyTable2: Uint8Array by lazy { MainDll.getZoneDecryptTable2() }

    fun decryptZoneObjects(sectionHeader: SectionHeader, byteReader: ByteReader) {
        byteReader.offsetFrom(sectionHeader, 0x10)

        val metadata = byteReader.next32()
        var decodeLength = metadata and 0x00FFFFFF
        val mode = (metadata shr 24) and 0xFF

        if (mode <= 0x1A) {
            return
        }

        val keyNodeData = byteReader.next32()
        val nodeCount = keyNodeData and 0x00FFFFFF

        val keyIndex = (keyNodeData shr 24) and 0xFF

        var key = keyTable1[keyIndex xor 0xFF].toInt()
        var keyCounter  = 0
        var consumed = 0

        if (byteReader.position + decodeLength > sectionHeader.nextSectionOffset()) {
            val extra = (byteReader.position + decodeLength) - sectionHeader.nextSectionOffset()
            OnceLogger.warn("[${sectionHeader.sectionId}] Decode length was too long by: 0x${extra.toString(0x10)}")
            decodeLength -= extra
        }

        while (consumed < decodeLength) {
            val xorLength = ((key shr 4) and 7) + 16

            val shouldApplyMask = (key and 1) != 0
            val hasRemainingLength = consumed + xorLength < decodeLength

            if (shouldApplyMask && hasRemainingLength) {
                for (i in 0 until xorLength) {
                    byteReader.xorNext(0xFF.toByte())
                }
            } else {
                byteReader.position += xorLength
            }

            keyCounter += 1
            key += keyCounter
            consumed += xorLength
        }

        // Names are masked too
        byteReader.offsetFrom(sectionHeader, 0x30)

        val pos = byteReader.position
        for (i in 0 until nodeCount) {
            byteReader.position = pos + i * 0x64
            for (j in 0 until 0x10) { byteReader.xorNext(0x55) }
        }
    }

    fun decryptZoneMesh(sectionHeader: SectionHeader, byteReader: ByteReader, reencrypt: Boolean = false) {
        if (reencrypt) {
            decryptZoneMeshSecondPass(sectionHeader, byteReader)
            decryptZoneMeshFirstPass(sectionHeader, byteReader)
        } else {
            decryptZoneMeshFirstPass(sectionHeader, byteReader)
            decryptZoneMeshSecondPass(sectionHeader, byteReader)
        }

        byteReader.offsetFrom(sectionHeader, 0x10)
    }

    private fun decryptZoneMeshFirstPass(sectionHeader: SectionHeader, byteReader: ByteReader) {
        byteReader.offsetFrom(sectionHeader, 0x10)

        // 0123
        val metadata = byteReader.next32()
        val totalSize = metadata and 0x00FFFFFF
        val decodeLength = totalSize - 0x8          // 8 bytes of header data, rest is decoded

        val mode = (metadata shr 24) and 0xFF
        if (mode < 0x5) {
            return
        }

        // 4
        byteReader.next8() // not decrypt related

        // 5
        val keyIndex = byteReader.next8()

        // 67 - unused in first pass
        byteReader.next16()

        var key = keyTable1[keyIndex xor 0xF0].toInt()
        var keyCounter = 0

        for (i in 0 until decodeLength) {
            val keyMod = ((key and 0xFF) shl 8) or (key and 0xFF)
            key += ++keyCounter

            val mask = (keyMod ushr (key and 0x7)).toByte()
            byteReader.xorNext(mask)
            key += ++keyCounter
        }
    }

    private fun decryptZoneMeshSecondPass(sectionHeader: SectionHeader, byteReader: ByteReader) {
        byteReader.offsetFrom(sectionHeader, 0x10)

        // 0123
        val metadata = byteReader.next32()
        val totalSize = metadata and 0x00FFFFFF
        val decodeLength = totalSize - 0x8          // 8 bytes of header data, rest is decoded

        // 4
        byteReader.next8() // not decrypt related

        // 5
        var key1 = byteReader.next8() xor 0xF0
        var key2 = keyTable2[key1].toInt()

        val decodeCount = (decodeLength and (0xF.inv())) / 2

        // 67
        val needsSecondPass = byteReader.next16() == 0xFFFF
        if (!needsSecondPass) {
            return
        }

        // Swap
        for (i in 0 until decodeCount step 8) {
            if ((key2 and 1) == 1) {
                byteReader.swapNext8(decodeCount, 8)
            } else {
                byteReader.position += 0x8
            }

            key1 += 9
            key2 += key1
        }
    }

}
