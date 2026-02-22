package xim.resource

import kotlinx.serialization.Serializable
import xim.math.Vector3f
import xim.poc.BoundingBox
import xim.poc.gl.Color
import xim.poc.gl.MeshBuffer
import xim.poc.gl.TextureReference
import xim.util.OnceLogger.info
import xim.util.OnceLogger.warn
import kotlin.reflect.KClass
import kotlin.reflect.cast

enum class SectionType(val code: Int, val resourceType: KClass<out DatEntry>) {
    S00_End(0x00, NotImplementedResource::class),
    S01_Directory(0x01, DirectoryResource::class),
    S04_Table(0x04, TableResource::class),
    S05_ParticleGenerator(0x05, EffectResource::class),
    S06_Route(0x06, RouteResource::class),
    S07_EffectRoutine(0x07, EffectRoutineResource::class),
    S19_ParticleKeyFrameData(0x19, KeyFrameResource::class),
    S1C_ZoneDef(0x1C, ZoneResource::class),
    S1F_ParticleMesh(0x1F, ParticleMeshResource::class),
    S20_Texture(0x20, TextureResource::class),
    S21_SpriteSheetMesh(0x21, SpriteSheetResource::class),
    S25_WeightedMesh(0x25, WeightedMeshResource::class),
    S29_Skeleton(0x29, SkeletonResource::class),
    S2A_SkeletonMesh(0x2A, SkeletonMeshResource::class),
    S2B_SkeletonAnimation(0x2B, SkeletonAnimationResource::class),
    S2E_ZoneMesh(0x2E, ZoneMeshResource::class),
    S2F_Environment(0x2F, EnvironmentResource::class),
    S30_UiMenu(0x30, UiMenuResource::class),
    S31_UiElementGroup(0x31, UiElementResource::class),
    S36_ZoneInteractions(0x36, ZoneInteractionResource::class),
    S3E_PointList(0x3E, PointListResource::class),
    S3D_SoundEffectPointer(0x3D, SoundPointerResource::class),
    S45_Info(0x45, InfoResource::class),
    S46_Unknown(0x46, NotImplementedResource::class),
    S49_SpellList(0x49, SpellListResource::class),
    S4A_Path(0x4A, PathResource::class),
    S53_AbilityList(0x53, AbilityListResource::class),
    S54_WeaponTrace(0x54, WeaponTraceResource::class),
    S5E_Blur(0x5E, BlurResource::class),
    S5D_BumpMap(0x5D, BumpMapResource::class),
    S5F_Unknown(0x5F, NotImplementedResource::class),
    ;

    companion object {
        fun fromCode(code: Int): SectionType {
            return SectionType.values().firstOrNull { it.code == code } ?: throw NoSuchElementException("Unknown: ${code.toString(0x10)}")
        }
    }
}

@Serializable
data class DatId(val id: String) {
    companion object {
        val model = DatId("mode")
        val ship = DatId("ship")
        val effect = DatId("effe")
        val entrance = DatId("entr")
        val main = DatId("main")
        val tgt0 = DatId("tgt0")
        val sub0 = DatId("sub0")
        val info = DatId("info")
        val mount = DatId("moun")

        val fses = DatId("fses")
        val fefs = DatId("fefs")

        val door = DatId("door")
        val lift = DatId("lift")

        val open = DatId("open")
        val close = DatId("clos")
        val opened = DatId("into")
        val closed = DatId("intc")
        val eventStart = DatId("evst")
        val eventEnd = DatId("eved")

        val indoors = DatId("indo")

        val chargeWhiteMagic = DatId("cawh")
        val invokeWhiteMagic = DatId("shwh")
        val invokeBlueMagic = DatId("shbl")

        val stopMobTechnique = DatId("stnm")

        val eventOnLoad = DatId("!in1")
        val eventOnUnload = DatId("!kl1")

        val eventOnEngage = DatId("!w00")
        val eventOnEngaged = DatId("!w01")
        val eventOnDisengage = DatId("!w02")

        val startResting = DatId("res0")
        val resting = DatId("res1")
        val stopResting = DatId("res2")

        val disappear = DatId("kesu")

        val specialDeath = DatId("sdep")

        val pop = DatId("pop0")
        val kill = DatId("kill")
        val init = DatId("init")
        val spop = DatId("spop")

        val weather = DatId("weat")
        val weatherSunny = DatId("suny")
        val weatherWindy = DatId("wind")
        val weatherRain = DatId("rain")
        val weatherDusty = DatId("dust")
        val weatherFine = DatId("fine")

        val zero = DatId("${0.toChar()}${0.toChar()}${0.toChar()}${0.toChar()}")

        val synthesisBreak = DatId("edmm")
        val synthesisNq = DatId("ednn")
        val synthesisHq = DatId("edxx")

        fun fromHour(hourOfDayIn24: Int): DatId {
            return if (hourOfDayIn24 < 10) {
                DatId("0${hourOfDayIn24}00")
            } else {
                DatId("${hourOfDayIn24}00")
            }
        }

        fun castId(spellInfo: SpellInfo): DatId? {
            val suffix = castSuffix(spellInfo) ?: return null
            return DatId("ca${suffix}")
        }

        fun stopCastId(spellInfo: SpellInfo): DatId? {
            val suffix = castSuffix(spellInfo) ?: return null
            return DatId("sp${suffix}")
        }

        private fun castSuffix(spellInfo: SpellInfo): String? {
            return when (spellInfo.magicType) {
                MagicType.None -> null
                MagicType.WhiteMagic -> "wh"
                MagicType.BlackMagic -> "bk"
                MagicType.Summoning -> "sm"
                MagicType.Ninjutsu -> "nj"
                MagicType.Songs -> "so"
                MagicType.BlueMagic -> "bl"
                MagicType.Geomancy -> "ge"
                MagicType.Trust -> "fa"
            }
        }

        fun castId(itemInfo: InventoryItemInfo) : DatId {
            return DatId("cait")
        }

        fun startRangeAnimationId(rangeType: RangeType): DatId {
            val index = rangeType.index
            return DatId("lc${index.toString().padStart(2, '0')}")
        }

        fun finishRangeAnimationId(rangeType: RangeType): DatId {
            val index = rangeType.index
            return DatId("ls${index.toString().padStart(2, '0')}")
        }

        fun charsToBase36(chars: CharArray): Int {
            var shift = 1
            var sum = 0
            for (i in chars.indices.reversed()) {
                sum += charToBase36(chars[i]) * shift
                shift *= 36
            }
            return sum
        }

        fun charToBase36(char: Char): Int {
            return when (char) {
                in '0'..'9' -> char.digitToInt()
                in 'a' .. 'z' -> (char - 'a') + 10
                else -> throw IllegalStateException()
            }
        }

        fun base36ToChar(value: Int): Char {
            return when(value) {
                in 0 until 10 -> '0'.plus(value)
                in 10 until 36 -> 'a'.plus(value - 10)
                else -> throw IllegalStateException("Illegal base36 value: $value")
            }
        }

    }

    override fun toString() = id

    fun toHourOfDay() : Int {
        return  id.toInt() / 100
    }

    fun isZero(): Boolean {
        return id == zero.id
    }

    fun toNullIfZero(): DatId? {
        return if (isZero()) { null } else { this }
    }

    fun applyParam(value: Char): DatId {
        val newId = id.replaceFirst('?', value)
        return DatId(newId)
    }

    fun isDoorId(): Boolean {
        return id.startsWith('_')
    }

    fun isElevatorId(): Boolean {
        return id.startsWith('@')
    }

    fun toNumber(): Int {
        return id.toInt()
    }

    fun isNumeric() : Boolean {
        return id.toIntOrNull() != null
    }

    fun isParameterized(): Boolean {
        return id.endsWith("?")
    }

    fun parameterizedMatch(parameterizedId: DatId): Boolean {
        return if (!parameterizedId.isParameterized()) {
            id == parameterizedId.id
        } else {
            id.substring(0, 3) == parameterizedId.id.substring(0, 3)
        }
    }

    fun toZoneId(): Int {
        // The first char is always 'z'
        // The other chars encode a zoneId + entranceId using a base-36 number system (0-9 a-z)
        // The middle two chars are the zoneId, while the last char is the entranceId
        val rawZoneId = charsToBase36(id.toCharArray(1,3))

        // However, [Pso'Xja] and [Ru'aun Gardens] have more than 36 entrances
        // It seems they worked around this by adding a large value (18*36) to the zoneId chars
        // But, [Mog House] lines ([zmr_], [zms_]) also seems to use a large ID, so don't "correct" those.
        val entranceOverflowOffset = 18*36

        return if (rawZoneId < entranceOverflowOffset) {
            rawZoneId
        } else if (rawZoneId == 0x333 /* [zmr_] */ || rawZoneId == 0x334 /* [zms_] */) {
            rawZoneId
        } else {
            rawZoneId - entranceOverflowOffset
        }
    }

    fun finalChar(): String {
        return id.substring(3, 4)
    }

    fun finalDigit(): Int? {
        return finalChar().toIntOrNull()
    }

}

sealed interface DatEntry {

    val id: DatId

    fun release() { }

    fun resourceName(): String? = null

    fun combine(datEntry: DatEntry): Boolean? = null

}

abstract class DatResource : DatEntry {

    lateinit var localDir: DirectoryResource

    private val path by lazy { pathHelper(localDir, arrayListOf(id.id)) }

    private val root by lazy {
        var dir = localDir
        while (dir.parent != null) dir = dir.parent!!
        dir
    }

    fun rootDirectory() : DirectoryResource = root

    fun path(): String = path

    private fun pathHelper(dir: DirectoryResource, dirs: ArrayList<String>): String {
        dirs += dir.id.id
        return if (dir.parent == null) {
            dirs.reversed().joinToString("/")
        } else {
            pathHelper(dir.parent, dirs)
        }
    }

}

data class TextureName(val nameSpace: String, val localName: String) {
    companion object {
        fun fromFullyQualified(name: String): TextureName {
            return TextureName(name.substring(0,8), name.substring(8, 16))
        }
    }
}

class DirectoryResource(
    val parent: DirectoryResource?,
    override val id: DatId,
) : DatEntry {

    companion object {
        private val globalDir = DirectoryResource(null, DatId.zero)

        /** Should mainly only be used for UI elements, but can be useful as a fallback **/
        fun getGlobalTexture(name: String): TextureResource? {
            return globalDir.getTextureResourceByNameAs(name)
        }

        fun setGlobalTexture(textureResource: TextureResource) {
            globalDir.trackResourceByName(textureResource)
        }

        fun removeGlobalTexture(textureResource: TextureResource) {
            val textureName = TextureName.fromFullyQualified(textureResource.name)
            val current = globalDir.texturesByName[textureName]
            if (current == textureResource) {
                globalDir.texturesByName.remove(textureName)
            }
        }

        fun fakeRoot(directories: List<DirectoryResource>): DirectoryResource {
            val root = DirectoryResource(parent = null, id = DatId("fake"))
            val subdirectories = root.childrenByType.getOrPut(DirectoryResource::class) { HashMap() }
            directories.forEachIndexed { i, child -> subdirectories[DatId("fke$i")] = child }
            return root
        }

    }

    private val childrenByType: MutableMap<KClass<out DatEntry>, MutableMap<DatId, DatEntry>> = HashMap()

    private val texturesByName = HashMap<TextureName, TextureResource>()
    private val zoneMeshByName = HashMap<String, ZoneMeshResource>()
    private val bumpMapsByName = HashMap<String, BumpMapResource>()

    fun addChild(sectionHeader: SectionHeader, datEntry: DatEntry) {
        when (datEntry) {
            is ZoneMeshResource -> trackResourceByName(datEntry)
            is TextureResource -> trackResourceByName(datEntry)
            is BumpMapResource -> trackResourceByName(datEntry)
            else -> {}
        }

        val childrenById = childrenByType.getOrPut(sectionHeader.sectionType.resourceType) { HashMap() }

        val currentChild = childrenById[sectionHeader.sectionId]
        if (currentChild != null) {
            val result = currentChild.combine(datEntry)
            if (result == null || !result) {
                warn("[${sectionHeader.sectionId}] [${datEntry::class}] Failed to combine resources in same directory with same name")
            } else {
                info("[${sectionHeader.sectionId}] [${datEntry::class}] Combined resource with same name in same directory")
            }
            return
        }

        childrenById[sectionHeader.sectionId] = datEntry
    }

    override fun combine(datEntry: DatEntry): Boolean {
        return true // Handled by the parser
    }

    fun <T : DatEntry> collectByType(type: KClass<T>) : List<T> {
        val collection = ArrayList<T>()

        val byType = childrenByType[type] ?: return emptyList()
        for (child in byType.values) {
            if (type.isInstance(child)) {
                collection.add(type.cast(child))
            }
        }

        return collection
    }

    fun <T : DatEntry> collectByTypeRecursive(type: KClass<T>) : List<T> {
        val directoryChildren = getSubDirectories()
        val recursiveChildren = directoryChildren.map { it. collectByTypeRecursive(type) }.flatten()
        val currentChildren = collectByType(type)

        return currentChildren + recursiveChildren
    }

    fun hasSubDirectory(childId: DatId) : Boolean {
        return childrenByType[DirectoryResource::class]?.get(childId) != null
    }

    fun getSubDirectory(childId: DatId) : DirectoryResource {
        return getNullableSubDirectory(childId) ?: throw IllegalStateException("[${id}] No such child-dir [$childId].}")
    }

    fun getNullableSubDirectory(childId: DatId) : DirectoryResource? {
        val children = childrenByType[DirectoryResource::class]
        val child = children?.get(childId) ?: return null
        return castChild(child, DirectoryResource::class)
    }

    fun getSubDirectoriesRecursively() : List<DirectoryResource> {
        val children = getSubDirectories()
        return children + children.map { it.getSubDirectoriesRecursively() }.flatten()
    }

    fun getSubDirectories() : List<DirectoryResource> {
        val children = childrenByType[DirectoryResource::class] ?: return emptyList()
        return children.values.map { castChild(it, DirectoryResource::class) }
    }

    fun <T : DatEntry> getChildAs(childId: DatId, type: KClass<T>) : T {
        return getNullableChildAs(childId, type) ?: throw IllegalArgumentException("No such child '$childId' in directory '$id' of type $type; ${childrenByType[type]?.values?.map { it.id }}")
    }

    fun <T : DatEntry> getNullableChildAs(childId: DatId, type: KClass<T>) : T? {
        val children = childrenByType[type] ?: emptyMap()
        val child = children[childId] ?: return null
        return castChild(child, type)
    }

    fun <T : DatEntry> getNullableChildRecursivelyAs(childId: DatId, type: KClass<T>) : T? {
        val child = getNullableChildAs(childId, type)

        if (child != null) {
            return castChild(child, type)
        }

        val subDirs = getSubDirectories()
        for (subDir in subDirs) {
            val subDirChild = subDir.getNullableChildRecursivelyAs(childId, type)
            if (subDirChild != null) {
                return castChild(subDirChild, type)
            }
        }

        return null
    }

    fun <T : DatEntry> getOnlyChildByType(type: KClass<T>) : T {
        val children = childrenByType[type] ?: throw IllegalArgumentException("Directory $id has no children of type $type")
        if (children.size != 1) throw IllegalArgumentException("Directory $id does not have exactly one child of type $type")
        return castChild(children.values.first(), type)
    }

    fun <T : DatEntry> getOnlyNullableChildByType(type: KClass<T>) : T? {
        val children = childrenByType[type] ?: return null
        val child = children.values.singleOrNull() ?: return null
        return castChild(child, type)
    }

    fun <T : DatEntry> getFirstChildByTypeRecursively(type: KClass<T>) : T? {
        val children = childrenByType[type]

        if (!children.isNullOrEmpty()) {
            return castChild(children.values.first(), type)
        }

        return getSubDirectories().firstNotNullOfOrNull { it.getFirstChildByTypeRecursively(type) }
    }

    fun getTextureResourceByNameAs(name: String) : TextureResource? {
        val textureName = TextureName.fromFullyQualified(name)
        return getTextureResourceByNameAs(textureName)
    }

    private fun getTextureResourceByNameAs(textureName: TextureName) : TextureResource? {
        val fullMatch = texturesByName[textureName]
        if (fullMatch != null) { return fullMatch }

        return texturesByName.entries.firstOrNull { it.key.localName == textureName.localName }?.value
    }

    fun getZoneMeshResourceByNameAs(name: String) : ZoneMeshResource? {
        return zoneMeshByName[name]
    }

    fun getCorrespondingBumpMapResource(textureName: String?): BumpMapResource? {
        textureName ?: return null
        val fqName = TextureName.fromFullyQualified(textureName)
        return bumpMapsByName[fqName.localName]
    }

    private fun trackResourceByName(textureResource: TextureResource) {
        val name = TextureName.fromFullyQualified(textureResource.name)
        val existing = texturesByName[name]
        if (existing != null) { info("[${name}] overwrite from [${existing.id}] to [${textureResource.id}]") }
        texturesByName[name] = textureResource
        if (this != globalDir) { setGlobalTexture(textureResource) }
    }

    private fun trackResourceByName(zoneMeshResource: ZoneMeshResource) {
        val existing = zoneMeshByName[zoneMeshResource.name]
        if (existing != null) { info("[${zoneMeshResource.name}] overwrite from [${existing.id}] to [${zoneMeshResource.id}]") }
        zoneMeshByName[zoneMeshResource.name] = zoneMeshResource
    }

    private fun trackResourceByName(bumpMapResource: BumpMapResource) {
        val textureName = TextureName.fromFullyQualified(bumpMapResource.resourceName())
        bumpMapsByName[textureName.localName] = bumpMapResource
    }

    fun root(): DirectoryResource {
        if (parent != null) {
            return parent.root()
        }

        return this
    }

    fun <T: DatResource> findFirstInEntireTreeById(datId: DatId, type: KClass<T>) : T? {
        return root().getNullableChildRecursivelyAs(datId, type)
    }

    fun <T: DatResource> searchLocalAndParentsById(datId: DatId, type: KClass<T>) : T? {
        val local = getNullableChildAs(datId, type)
        if (local != null) {
            return local
        }

        if (parent != null) {
            return parent.searchLocalAndParentsById(datId, type)
        }

        return null
    }

    fun searchLocalAndParentsByName(name: String) : TextureResource? {
        val textureName = TextureName.fromFullyQualified(name)
        return searchLocalAndParentsByName(textureName)
    }

    private fun searchLocalAndParentsByName(name: TextureName) : TextureResource? {
        val local = getTextureResourceByNameAs(name)
        if (local != null) {
            return local
        }

        if (parent != null) {
            return parent.searchLocalAndParentsByName(name)
        }

        return null
    }

    private fun <T : DatEntry> castChild(child: DatEntry, type: KClass<T>) : T {
        if (type.isInstance(child)) {
            return type.cast(child)
        } else {
            throw IllegalArgumentException("Child [${child.id}] is not a $type; it is a ${child::class}")
        }
    }

    override fun release() {
        childrenByType.values.flatMap { it.values }.forEach { it.release() }
    }

    fun recursivePrint() {
        recursivePrintHelper(this, 0)
    }

    private fun recursivePrintHelper(node: DatEntry, depth: Int) {
        for (i in 0 until depth) {
            print("\t")
        }
        print("${node.id} | ${node::class.simpleName}")

        if (node is TextureResource) {
            print(" | ${node.name}")
        }

        println()

        if (node is DirectoryResource) {
            node.childrenByType.values.map { it.values }.flatten().forEach { recursivePrintHelper(it, depth+1) }
        }
    }

}

class TextureResource(override val id: DatId, val name: String, val textureReference: TextureReference) : DatResource() {

    override fun release() {
        textureReference.release()
        DirectoryResource.removeGlobalTexture(this)
    }

    override fun resourceName(): String {
        return name
    }

}

class EffectResource(override val id: DatId, val particleGenerator: ParticleGeneratorDefinition) : DatResource()

class KeyFrameResource(override val id: DatId, val particleKeyFrameData: ParticleKeyFrameData) : DatResource()

class ZoneResource(
    override val id: DatId,
    val zoneObj: List<ZoneObject>,
    val zoneCollisionMeshes: List<CollisionObjectGroup>,
    val zoneCullingTables: List<Set<ZoneObjId>>,
    val zoneCollisionMap: CollisionMap?,
    val zoneSpaceTreeRoot: SpacePartitioningNode,
    val pointLightLinks: List<DatId>
) : DatResource() {

    var objectDrawOrder = zoneObj

    val meshesByEffectLink = zoneObj.filter { it.effectLink != null }
        .associateBy { it.effectLink!!.id }

    override fun release() {
        for (collisionMeshes in zoneCollisionMeshes) {
            for (c in collisionMeshes.collisionObjects) {
                c.collisionMesh.meshBuffer.release()
            }
        }
    }

}

class ParticleMeshResource(override val id: DatId, val particleDef: ParticleDef) : DatResource() {

    override fun release() {
        particleDef.particleMeshes.forEach { it.release() }
    }
}

class SkeletonResource(override val id: DatId, val joints: List<Joint>, val jointReference: List<JointReference>, val boundingBoxes: List<BoundingBox>) : DatResource() {
    val size = Vector3f()
}

class SkeletonAnimationResource(override val id: DatId, val skeletonAnimation: SkeletonAnimation) : DatResource()

class ZoneMeshResource(override val id: DatId, val meshes: ArrayList<MeshBuffer>, val name: String, val boundingBox0: ZoneMeshSection.BoundingBox?, val boundingBox1: ZoneMeshSection.BoundingBox?) : DatResource() {

    override fun release() {
        meshes.forEach { it.release() }
    }

    override fun resourceName(): String {
        return name
    }

}

class SkeletonMeshResource(override val id: DatId, val meshes: ArrayList<MeshBuffer>, val occlusionType: Int) : DatResource() {

    override fun release() {
        meshes.forEach { it.release() }
    }

    override fun combine(datEntry: DatEntry): Boolean {
        val other = datEntry as SkeletonMeshResource
        meshes.addAll(other.meshes)
        return true
    }

}

class EnvironmentResource(override val id: DatId, val skyBox: SkyBox, val environmentLighting: EnvironmentLighting, val drawDistance: Float, val clearColor: Color) : DatResource()

class SpriteSheetResource(override val id: DatId, val spriteSheet: SpriteSheet) : DatResource() {

    override fun release() {
        spriteSheet.meshes.forEach { it.release() }
    }

}

class EffectRoutineResource(override val id: DatId, val effectRoutineDefinition: EffectRoutineDefinition) : DatResource()

class InfoResource(override val id: DatId, val infoDefinition: InfoDefinition, val mountDefinition: MountDefinition): DatResource()

class SoundPointerResource(override val id: DatId, val soundId: Int, val folderId: String, val fileId: String) : DatResource()

class WeightedMeshResource(override val id: DatId, val weightedMesh: WeightedMesh) : DatResource() {

    override fun release() {
        weightedMesh.release()
    }

}

class PointListResource(override val id: DatId, val pointList: PointList) : DatResource()

class UiElementResource(override val id: DatId, val uiElementGroup: UiElementGroup) : DatResource()

class UiMenuResource(override val id: DatId, val uiMenu: UiMenu) : DatResource()

class SpellListResource(override val id: DatId, val spells: MutableMap<Int, SpellInfo>) : DatResource()

class AbilityListResource(override val id: DatId, val abilities: MutableMap<Int, AbilityInfo>) : DatResource()

class ZoneInteractionResource(override val id: DatId, val interactions: List<ZoneInteraction>) : DatResource()

class PathResource(override val id: DatId, val pathDefinition: Path): DatResource()

class RouteResource(override val id: DatId, val route: Route): DatResource()

class BlurResource(override val id: DatId, val blurConfig: BlurConfig): DatResource()

class TableResource(override val id: DatId, val table: Table): DatResource()

class WeaponTraceResource(override val id: DatId, val trace: WeaponTrace): DatResource()

class BumpMapResource(override val id: DatId, val textureReference: TextureReference) : DatResource() {

    override fun release() {
        BumpMapLinks.clear()
        textureReference.release()
    }

    override fun resourceName(): String {
        return textureReference.name
    }

}

class NotImplementedResource(override val id: DatId) : DatEntry
