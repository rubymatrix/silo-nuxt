package xim.poc.gl

import js.typedarrays.Uint8Array
import xim.resource.ByteReader

object TextureHelper {

    fun convertDXT1ToRGBA(byteReader: ByteReader, width: Int, height: Int): Uint8Array {
        val buffer = Uint8Array(width * height * 4)

        val r = IntArray(4) { 0 }
        val g = IntArray(4) { 0 }
        val b = IntArray(4) { 0 }
        val a = IntArray(4) { 0 }

        for (y1 in 0 until height step 4) {
            for (x1 in 0 until width step 4) {
                val c0 = byteReader.next16()
                val c1 = byteReader.next16()

                // RGB 565
                r[0] = (c0 ushr 11 and 0x1F) * 255/31
                g[0] = (c0 ushr 5 and 0x3f) * 255/63
                b[0] = (c0 and 0x1F) * 255/31
                a[0] = 255

                // RGB 565
                r[1] = (c1 ushr 11 and 0x1F) * 255/31
                g[1] = (c1 ushr 5 and 0x3f) * 255/63
                b[1] = (c1 and 0x1F) * 255/31
                a[1] = 255

                if (c0 > c1) {
                    b[2] = (2 * b[0] + b[1]) / 3
                    b[3] = (b[0] + 2 * b[1]) / 3

                    r[2] = (2 * r[0] + r[1]) / 3
                    r[3] = (r[0] + 2 * r[1]) / 3

                    g[2] = (2 * g[0] + g[1]) / 3
                    g[3] = (g[0] + 2 * g[1]) / 3

                    a[2] = 255
                    a[3] = 255
                } else {
                    b[2] = (b[0] + b[1]) / 2
                    b[3] = 0

                    r[2] = (r[0] + r[1]) / 2
                    r[3] = 0

                    g[2] = (g[0] + g[1]) / 2
                    g[3] = 0

                    a[2] = 255
                    a[3] = 0
                }

                val indices = byteReader.next32BE()
                var count = 15

                for (y in y1 until y1+4) {
                    for (x in x1 + 3 downTo  x1) {
                        val pixelIndex = (indices ushr (2 * count)) and 0x3

                        count -= 1

                        buffer[4*y*width + 4*x + 0] = r[pixelIndex].toByte()
                        buffer[4*y*width + 4*x + 1] = g[pixelIndex].toByte()
                        buffer[4*y*width + 4*x + 2] = b[pixelIndex].toByte()
                        buffer[4*y*width + 4*x + 3] = a[pixelIndex].toByte()
                    }
                }
            }
        }

        return buffer
    }

    fun convertDXT3ToRGBA(byteReader: ByteReader, width: Int, height: Int): Uint8Array {
        val buffer = Uint8Array(width * height * 4)

        val r = IntArray(4) { 0 }
        val g = IntArray(4) { 0 }
        val b = IntArray(4) { 0 }

        for (y1 in 0 until height step 4) {
            for (x1 in 0 until width step 4) {

                val a0 = byteReader.next32().toULong() shl 32
                val a1 = byteReader.next32().toULong()
                val a = a0 or a1

                val c0 = byteReader.next16()
                val c1 = byteReader.next16()

                // RGB 565
                r[0] = (c0 ushr 11 and 0x1F) * 255/31
                g[0] = (c0 ushr 5 and 0x3f) * 255/63
                b[0] = (c0 and 0x1F) * 255/31

                // RGB 565
                r[1] = (c1 ushr 11 and 0x1F) * 255/31
                g[1] = (c1 ushr 5 and 0x3f) * 255/63
                b[1] = (c1 and 0x1F) * 255/31

                b[2] = (2 * b[0] + b[1]) / 3
                b[3] = (b[0] + 2 * b[1]) / 3

                r[2] = (2 * r[0] + r[1]) / 3
                r[3] = (r[0] + 2 * r[1]) / 3

                g[2] = (2 * g[0] + g[1]) / 3
                g[3] = (g[0] + 2 * g[1]) / 3

                val indices = byteReader.next32BE()
                var count = 15

                for (y in y1 until y1+4) {
                    for (x in x1 + 3 downTo  x1) {
                        val pixelIndex = (indices ushr (2 * count)) and 0x3
                        val pixelAlpha = ((a shr (4 * count)).toInt() and 0xF) * 255/16

                        count -= 1

                        buffer[4*y*width + 4*x + 0] = r[pixelIndex].toByte()
                        buffer[4*y*width + 4*x + 1] = g[pixelIndex].toByte()
                        buffer[4*y*width + 4*x + 2] = b[pixelIndex].toByte()
                        buffer[4*y*width + 4*x + 3] = pixelAlpha.toByte()
                    }
                }
            }
        }

        return buffer
    }

}