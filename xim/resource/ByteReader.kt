package xim.resource

import js.typedarrays.Uint8Array
import xim.math.Vector2f
import xim.math.Vector3f
import xim.poc.gl.ByteColor
import kotlin.experimental.xor

class ByteReader(val bytes: Uint8Array, val resourceName: String = "") {

    var position: Int = 0

    fun nextDatId() : DatId {
        return DatId(nextString(0x4))
    }

    fun nextString(length: Int): String {
        val id = StringBuilder()

        for (i in 0 until length) {
            id.append(nextChar())
        }

        return id.toString()
    }

    fun next8() : Int {
        val byte = bytes[position]
        position += 1
        return 0xFF and byte.toInt()
    }

    fun next8Signed(): Int {
        return next8().toByte().toInt()
    }

    fun next16() : Int {
        return (next8()) or (next8() shl 8)
    }

    fun read16(offset: Int): Int {
        return bytes[offset].toInt() or (bytes[offset+1].toInt() shl 8)
    }

    fun next16Signed() : Int {
        return ((next8()) or (next8() shl 8)).toShort().toInt()
    }

    fun next32() : Int {
        return ((next16()) or (next16() shl 16))
    }

    fun next16BE() : Int {
        return (next8() shl 8) or (next8())
    }

    fun next32BE() : Int {
        return ((next16BE() shl 16) or (next16BE()))
    }

    fun next64BE() : Long {
        return ((next32BE().toLong() shl 32) or (next32BE().toLong()))
    }

    fun nextRGBA(): ByteColor {
        return ByteColor(r = next8(), g = next8(), b = next8(), a = next8())
    }

    fun nextBGRA(): ByteColor {
        return ByteColor(b = next8(), g = next8(), r = next8(), a = next8())
    }

    fun nextFloat(): Float {
        val readVal = next32()
        return Float.fromBits(readVal)
    }

    fun next32(amount: Int): IntArray {
        val dest = IntArray(amount) { 0 }
        next32(amount, dest)
        return dest
    }

    fun nextFloat(amount: Int): FloatArray {
        val dest = FloatArray(amount) { 0f }
        nextFloat(amount, dest)
        return dest
    }

    fun next32(amount: Int, dest: IntArray) {
        for (i in 0 until amount) {
            dest[i] = next32()
        }
    }

    fun nextFloat(amount: Int, dest: FloatArray) {
        for (i in 0 until amount) {
            dest[i] = nextFloat()
        }
    }

    fun nextVector2f(): Vector2f {
        return Vector2f(nextFloat(), nextFloat())
    }

    fun nextVector3f(): Vector3f {
        return Vector3f(nextFloat(), nextFloat(), nextFloat())
    }

    fun subBuffer(size: Int): Uint8Array {
        return subBuffer(position, size)
    }

    fun subBuffer(offset: Int, size: Int): Uint8Array {
        return bytes.subarray(offset, offset+size)
    }

    fun nextChar(): Char {
        return Char(next8())
    }

    fun nextCharJis(): Char {
        var code = next8()
        if (code and 0x80 != 0) {
            code = (code shl 8) or next8()
        }
        return Char(code)
    }

    fun align0x04() {
        val remainder = position % 0x04
        if (remainder != 0) {
            position += 0x04 - remainder
        }
    }

    fun align0x10() {
        val remainder = position % 0x10
        if (remainder != 0) {
            position += 0x10 - remainder
        }
    }

    fun offsetFrom(sectionHeader: SectionHeader, offset: Int) {
        position = sectionHeader.sectionStartPosition + offset
    }

    fun offsetFromDataStart(sectionHeader: SectionHeader, offset: Int = 0) {
        position = sectionHeader.dataStartPosition + offset
    }

    fun <T> wrapped(fn: () -> T): T {
        val start = position
        val ret = fn.invoke()
        position = start
        return ret
    }

    fun hasMore(): Boolean {
        return position < bytes.length
    }

    override fun toString(): String {
        return "[$resourceName] Pos: ${position.toString(0x10)} | Size: ${bytes.length.toString(0x10)}"
    }

    fun nextZeroTerminatedString(): String {
        val id = StringBuilder()

        while (true) {
            val next = nextCharJis()
            if (next == 0.toChar()) { break }
            id.append(next)
        }

        return id.toString()
    }

    fun rotateRight(amount: Int) {
        for (i in 0 until bytes.length) {
            bytes[i] = bytes[i].rotateRight(amount)
        }
    }

    fun xorNext(mask: Byte) {
        bytes[position] = bytes[position] xor mask
        position += 1
    }

    fun swapNext8(offset: Int, repetitions: Int = 1) {
        for (i in 0 until repetitions) {
            val value = bytes[position]
            bytes[position] = bytes[position + offset]
            bytes[position + offset] = value
            position += 1
        }
    }

    fun rotateNext8(amount: Int) {
        bytes[position] = bytes[position].rotateRight(amount)
        position += 1
    }

    fun xorNext8(mask: Byte) {
        bytes[position] = bytes[position] xor mask
        position += 1
    }

    fun write16(value: Int) {
        write8(value and 0xFF)
        write8(value ushr 8)
    }

    fun write(vararg values: Int) {
        values.forEach { write8(it) }
    }

    fun write8(value: Int) {
        bytes[position] = (value and 0xFF).toByte()
        position += 1
    }

}