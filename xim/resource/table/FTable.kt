package xim.resource.table

import xim.resource.ByteReader
import xim.poc.browser.DatLoader
import xim.util.OnceLogger
import kotlin.experimental.or

class VTable(val byteReader: ByteReader) {
    fun getVersion(fileId: Int): Int? {
        if (fileId < 0 || fileId > byteReader.bytes.length) {
            OnceLogger.error("FileId is invalid: ${fileId.toString(0x10)}")
            return null
        }

        return byteReader.bytes[fileId].toInt()
    }
}

class FTable(val byteReader: ByteReader) {
    fun getFile(fileId: Int): Pair<Int, Int> {
        byteReader.position = 2 * fileId
        val fileValue = byteReader.next16()

        val fileName = fileValue and 0x7F
        val folderName = fileValue ushr 7
        return Pair(folderName, fileName)
    }
}

object FileTableManager: LoadableResource {
    private const val numTables = 9

    private lateinit var vTable: VTable
    private var loadedVTables = 0

    private lateinit var fTable: FTable
    private var loadedFTables = 0

    private var preloaded = false

    fun getFilePath(fileId: Int?) : String? {
        if (fileId == null) { return null }

        val versionNumber = vTable.getVersion(fileId) ?: return null
        if (versionNumber == 0) { return null }
        val versionString = if (versionNumber == 1) { "" } else { versionNumber.toString() }

        val (folderNumber, fileNumber) = fTable.getFile(fileId)

        return "ROM$versionString/$folderNumber/$fileNumber.DAT"
    }

    override fun preload() {
        if (preloaded) { return }
        preloaded = true

        for (i in 1 .. numTables) {
            loadTable(i)
        }
    }

    override fun isFullyLoaded() : Boolean {
        return loadedVTables == numTables && loadedFTables == numTables
    }

    private fun loadTable(tableIndex: Int) {
        val prefix = if (tableIndex == 1) { "" } else { "ROM${tableIndex}/" }
        val postfix = if (tableIndex == 1) { "" } else { tableIndex.toString() }
        DatLoader.load("${prefix}VTABLE${postfix}.DAT").onReady { onVTableLoad(it.getAsBytes()) }
        DatLoader.load("${prefix}FTABLE${postfix}.DAT").onReady { onFTableLoad(it.getAsBytes()) }
    }

    private fun onVTableLoad(byteReader: ByteReader) {
        if (!this::vTable.isInitialized) { vTable = VTable(byteReader) } else { combine(byteReader, vTable.byteReader) }
        loadedVTables += 1
    }

    private fun onFTableLoad(byteReader: ByteReader) {
        if (!this::fTable.isInitialized) { fTable = FTable(byteReader) } else { combine(byteReader, fTable.byteReader) }
        loadedFTables += 1
    }

    private fun combine(source: ByteReader, destination: ByteReader) {
        for (i in 0 until source.bytes.length) {
            destination.bytes[i] = destination.bytes[i] or source.bytes[i]
        }
    }

}