package xim.resource

import js.typedarrays.Uint8Array
import xim.poc.browser.ParserContext
import xim.resource.SectionType.*
import xim.resource.patch.DatPatchManager
import xim.resource.patch.DatPostProcessorManager
import xim.util.OnceLogger
import xim.util.OnceLogger.error

object DatParser {

    fun parse(resourceName: String, rawDat: Uint8Array, parserContext: ParserContext): DirectoryResource {
        var rootDirectory: DirectoryResource? = null
        var currentDirectory: DirectoryResource? = null
        val byteReader = ByteReader(rawDat, resourceName)

        DatPatchManager.patchIfNeeded(resourceName, byteReader)

        try {
            while (byteReader.hasMore()) {
                val header = SectionHeader()
                header.read(byteReader)
                if (currentDirectory != null) { header.localDir = currentDirectory }

                val parser = when (header.sectionType) {
                    S00_End -> EndSection(currentDirectory!!)
                    S01_Directory -> DirectorySection(header, currentDirectory)
                    S04_Table -> TableSection(header)
                    S05_ParticleGenerator -> ParticleGeneratorParser(header)
                    S06_Route -> RouteSection(header)
                    S07_EffectRoutine -> EffectRoutineSection(header)
                    S19_ParticleKeyFrameData -> ParticleKeyFrameValueSection(header)
                    S1C_ZoneDef -> ZoneDefSection(header)
                    S1F_ParticleMesh -> ParticleMeshSection(header, parserContext)
                    S20_Texture -> TextureSection(header)
                    S21_SpriteSheetMesh -> SpriteSheetSection(header)
                    S25_WeightedMesh -> WeightedMeshSection(header, parserContext)
                    S29_Skeleton -> SkeletonSection(header)
                    S2A_SkeletonMesh -> SkeletonMeshSection(header)
                    S2B_SkeletonAnimation -> SkeletonAnimationParser(header)
                    S2E_ZoneMesh -> ZoneMeshSection(header)
                    S2F_Environment -> EnvironmentSection(header)
                    S30_UiMenu -> UiMenuSection(header)
                    S31_UiElementGroup -> UiElementGroupParser(header)
                    S36_ZoneInteractions -> ZoneInteractionSection(header)
                    S3D_SoundEffectPointer -> SoundEffectPointerSection(header)
                    S3E_PointList -> PointListSection(header)
                    S45_Info -> InfoSection(header)
                    S49_SpellList -> SpellListSection(header)
                    S4A_Path -> PathSection(header)
                    S53_AbilityList -> AbilityListSection(header)
                    S54_WeaponTrace -> WeaponTraceSection(header)
                    S5D_BumpMap -> BumpMapSection(header)
                    S5E_Blur -> BlurResourceSection(header)
                    else -> UnhandledSection(header)
                }

                val result = try {
                    parser.getResource(byteReader)
                } catch (e: Exception) {
                    throw Exception("[$resourceName] Failed to parse: [${parser::class}] [${header}]", e)
                }
                byteReader.offsetFrom(header, header.sectionSize)

                if (result.popDirectory) {
                    currentDirectory = currentDirectory!!.parent
                    continue
                }

                val entry = result.entry ?: continue

                if (result.pushDirectory) {
                    currentDirectory?.addChild(header, entry.datEntry)
                    currentDirectory = entry.datEntry as DirectoryResource
                    if (rootDirectory == null) { rootDirectory = currentDirectory }
                } else {
                    currentDirectory!!.addChild(header, entry.datEntry)
                    (entry.datEntry as DatResource).localDir = currentDirectory
                }
            }
        } catch (e: Exception) {
            error("ByteReader Pos: ${byteReader.position.toString(0x10)}")
            e.printStackTrace()
            throw e
        }

        DatPostProcessorManager.patchIfNeeded(resourceName, rootDirectory!!)
        return rootDirectory
    }

}

data class ParserResult(val entry: ParserEntry?, val popDirectory: Boolean = false, val pushDirectory: Boolean = false) {
    companion object {
        fun none() = ParserResult(entry = null)
        fun from(datEntry: DatEntry) = ParserResult(ParserEntry(datEntry))
    }
}

data class ParserEntry(val datEntry: DatEntry, val resourceName: String? = null)

interface ResourceParser {
    fun getResource(byteReader: ByteReader) : ParserResult
}

class UnhandledSection(val sectionHeader: SectionHeader) : ResourceParser {
    override fun getResource(byteReader: ByteReader): ParserResult {
        OnceLogger.warn("[${sectionHeader.sectionId}] Unhandled section-type: ${sectionHeader.sectionType} at $byteReader. Size: ${sectionHeader.sectionSize}")
        return ParserResult.none()
    }
}

class SectionHeader {
    lateinit var sectionId: DatId
    lateinit var sectionType: SectionType
    lateinit var localDir: DirectoryResource

    var sectionStartPosition: Int = 0
    var dataStartPosition: Int = 0
    var sectionSize: Int = 0

    val sectionEndPosition: Int
        get() = sectionStartPosition + sectionSize

    fun read(byteReader: ByteReader) {
        sectionStartPosition = byteReader.position
        dataStartPosition = sectionStartPosition + 0x10
        sectionId = DatId(byteReader.nextString(0x4))

        val sectionMeta = byteReader.next32()
        sectionType = SectionType.fromCode(sectionMeta and 0x7F)
        sectionSize = (sectionMeta shr 7 and 0xFFFFF) * 0x10

        byteReader.align0x10()
    }

    fun nextSectionOffset(): Int {
        return sectionStartPosition + sectionSize
    }

    override fun toString(): String {
        return "$sectionId"
    }

}

class DirectorySection(private val sectionHeader: SectionHeader, private val parent: DirectoryResource?) : ResourceParser {
    override fun getResource(byteReader: ByteReader): ParserResult {
        if (parent != null && parent.hasSubDirectory(sectionHeader.sectionId)) {
            // Duplicate child directories... just return the existing one? Mainly for 0/0.DAT
            val existingChild = parent.getSubDirectory(sectionHeader.sectionId)
            return ParserResult(pushDirectory = true, entry = ParserEntry(existingChild))
        }

        val resource = DirectoryResource(parent = parent, id = sectionHeader.sectionId)
        return ParserResult(pushDirectory = true, entry = ParserEntry(resource))
    }
}

class EndSection(val directoryResource: DirectoryResource) : ResourceParser {

    override fun getResource(byteReader: ByteReader): ParserResult {
        // If the directory only has a single routine, then try running it automatically
        val effectRoutines = directoryResource.collectByType(EffectRoutineResource::class)
        val onlyRoutine = effectRoutines.singleOrNull()

        if (onlyRoutine != null) {
            val localDir = onlyRoutine.localDir

            // Exclude deeply nested routines, since those are generally part of cutscenes
            if (localDir.id == DatId.effect || localDir.parent?.id == DatId.effect) {
                onlyRoutine.effectRoutineDefinition.autoRunHeuristic = true
            }
        }

        return ParserResult(popDirectory = true, entry = null)
    }

}

fun expectFloat(byteReader: ByteReader, cond: (Float) -> Boolean): Float {
    val value = byteReader.nextFloat()
    if (!cond(value)) { oops(byteReader, "Predicate failed, was $value") }
    return value
}

fun expect32(byteReader: ByteReader, cond: (Int) -> Boolean) {
    val value = byteReader.next32()
    if (!cond(value)) { oops(byteReader, "Predicate failed, was ${value.toString(0x10)}") }
}

fun expect32(byteReader: ByteReader, min: Int, max: Int) {
    expect32(byteReader) { it in min..max }
}

fun expectZero(value: Int) {
    if (value != 0) { throw IllegalStateException("Wanted 0, but was ${value.toString(0x10)}!") }
}

fun expectZero32(byteReader: ByteReader) {
    if (byteReader.next32() != 0) { oops(byteReader, "Wanted 0!") }
}

fun expectZero32(byteReader: ByteReader, count: Int) {
    for (i in 0 until count) { expectZero32(byteReader) }
}


fun expectZero(value: Float) {
    if (value != 0f) { throw IllegalStateException("Wanted 0!") }
}

fun oops(byteReader: ByteReader, reason: String = "") {
    throw IllegalStateException("$byteReader | $reason")
}