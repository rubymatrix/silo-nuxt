package xim.poc

import kotlinx.serialization.Serializable
import xim.poc.browser.DatLoader
import xim.poc.browser.DatWrapper
import xim.poc.browser.ParserContext.Companion.staticResource
import xim.poc.game.configuration.v0.interactions.ItemId
import xim.resource.*
import xim.resource.table.EquipmentModelTable
import xim.resource.table.FileTableManager
import xim.resource.table.MainDll
import xim.resource.table.NpcTable
import xim.util.OnceLogger

enum class ItemModelSlot(val index: Int, val prefix: Int = index * 0x1000) {
    Face(0),
    Head(1),
    Body(2),
    Hands(3),
    Legs(4),
    Feet(5),
    Main(6),
    Sub(7),
    Range(8),
    ;

    companion object {
        fun toSlot(value: Int): ItemModelSlot {
            val prefix = value and 0xF000
            return ItemModelSlot.values().firstOrNull { it.prefix == prefix } ?: throw IllegalStateException("Failed to match input: ${value.toString(0x10)}")
        }
    }
}

enum class GenderType {
    Male,
    Female,
    None,
}

@Serializable
enum class RaceGenderConfig(val index: Int, val equipmentTableIndex: Int = index, val equipmentFlag: Int = 1 shl index, val genderType: GenderType = GenderType.None, val chocobo: Boolean = false) {
    HumeM(index = 1, genderType = GenderType.Male),
    HumeF(index = 2, genderType = GenderType.Female),
    ElvaanM(index = 3, genderType = GenderType.Male),
    ElvaanF(index = 4, genderType = GenderType.Female),
    TaruM(index = 5, genderType = GenderType.Male),
    TaruF(index = 6, genderType = GenderType.Female),
    Mithra(index = 7, genderType = GenderType.Female),
    Galka(index = 8, genderType = GenderType.Male),
    MithraChild(index = 29, equipmentTableIndex = 9),
    HumeChildF(index = 30, equipmentTableIndex = 10),
    HumeChildM(index = 31, equipmentTableIndex = 11),
    Chocobo(index = 32, equipmentTableIndex = 12, chocobo = true),
    ChocoboBlack(index = 33, equipmentTableIndex = 13, chocobo = true),
    ChocoboBlue(index = 34, equipmentTableIndex = 14, chocobo = true),
    ChocoboRed(index = 35, equipmentTableIndex = 15, chocobo = true),
    ChocoboGreen(index = 36, equipmentTableIndex = 16, chocobo = true),
    ;

    companion object {
        fun from(race: Int?): RaceGenderConfig? {
            race ?: return null
            return RaceGenderConfig.values().firstOrNull { it.index == race }
        }
    }
}

class RaceGenderResources {
    lateinit var raceConfig: DirectoryResource
    lateinit var upperBodyMovementAnimations: DirectoryResource
    lateinit var skirtAnimations: DirectoryResource
    fun isFullyLoaded() = this::raceConfig.isInitialized && this::upperBodyMovementAnimations.isInitialized && this::skirtAnimations.isInitialized
}

object PcModelLoader {

    private var preloaded = false
    private var fullyLoaded = false

    private val raceResources = HashMap<RaceGenderConfig, RaceGenderResources>()

    fun preload() {
        if (preloaded) { return }
        preloaded = true

        for (config in RaceGenderConfig.values()) {
            val raceGenderResources = RaceGenderResources()
            raceResources[config] = raceGenderResources

            val fileTableIndex = MainDll.getBaseRaceConfigIndex(config)

            val raceConfigPath = FileTableManager.getFilePath(fileTableIndex + 0x00) ?: throw IllegalStateException("Failed to resolve race config DAT")
            DatLoader.load(raceConfigPath, parserContext = staticResource).onReady { raceGenderResources.raceConfig = it.getAsResource() }

            val upperBodyAnimPath = FileTableManager.getFilePath(fileTableIndex + 0x01) ?: throw IllegalStateException("Failed to resolve additional animation DAT")
            DatLoader.load(upperBodyAnimPath, parserContext = staticResource).onReady { raceGenderResources.upperBodyMovementAnimations = it.getAsResource() }

            val skirtAnimPath = FileTableManager.getFilePath(fileTableIndex + 0x04) ?: throw IllegalStateException("Failed to resolve additional animation DAT")
            DatLoader.load(skirtAnimPath, parserContext = staticResource).onReady { raceGenderResources.skirtAnimations = it.getAsResource() }

            OnceLogger.info("[$config] Preloaded: [$raceConfigPath], [$upperBodyAnimPath], [$skirtAnimPath]")
        }
    }

    operator fun get(raceGenderConfig: RaceGenderConfig): RaceGenderResources {
        return raceResources[raceGenderConfig] ?: throw IllegalStateException("No resources for: $raceGenderConfig")
    }

    fun isFullyLoaded(): Boolean {
        if (fullyLoaded) { return true }
        fullyLoaded = raceResources.values.all { it.isFullyLoaded() }
        return fullyLoaded
    }

}

interface Model {

    fun isReadyToDraw(): Boolean

    fun getMeshResources(): List<DirectoryResource>

    fun getSkeletonResource(): SkeletonResource?

    fun getAnimationDirectories(): List<DirectoryResource>

    fun getMainBattleAnimationDirectory(): DirectoryResource?

    fun getSubBattleAnimationDirectory(): DirectoryResource?

    fun getEquipmentModelResource(modelSlot: ItemModelSlot): DirectoryResource?

    fun getMovementInfo(): InfoDefinition?

    fun getMainWeaponInfo(): InfoDefinition?

    fun getSubWeaponInfo(): InfoDefinition?

    fun getRangedWeaponInfo(): InfoDefinition?

    fun getBlurConfig(): BlurConfig?

    fun getScale(): Float = 1f

}

@Serializable
class ModelLook(
    val type: Int,
    val modelId: Int,
    val equipment: EquipmentLook
) {
    companion object {

        const val fileTableIndexType = -11
        const val particleType = -12

        fun blank(): ModelLook {
            return ModelLook(type = 0, modelId = 0, equipment = EquipmentLook())
        }

        fun npc(modelId: Int): ModelLook {
            return ModelLook(type = 0, modelId = modelId, equipment = EquipmentLook())
        }

        fun pc(raceGenderConfig: RaceGenderConfig, equipmentLook: EquipmentLook): ModelLook  {
            return ModelLook(type = 1, modelId = raceGenderConfig.index, equipment = equipmentLook)
        }

        fun npcWithBase(modelId: Int): ModelLook {
            return ModelLook(type = 6, modelId = modelId, equipment = EquipmentLook())
        }

        fun fileTableIndex(fileTableIndex: Int): ModelLook {
            return ModelLook(type = fileTableIndexType, modelId = fileTableIndex, equipment = EquipmentLook())
        }

        fun furniture(inventoryItemId: ItemId): ModelLook {
            val item = InventoryItems[inventoryItemId]
            val modelId = InventoryItems.getFurnitureModelId(item) ?: throw IllegalStateException("No model for item: $inventoryItemId")
            return fileTableIndex(modelId)
        }

        fun particle(): ModelLook {
            return ModelLook(type = particleType, modelId = 0, equipment = EquipmentLook())
        }

    }

    val race: RaceGenderConfig? = if (type == 1) { RaceGenderConfig.from(modelId) } else { null }

    fun copy(): ModelLook {
        return ModelLook(type, modelId, equipment.copy())
    }

    override fun toString(): String {
        return "ModelLook(type=${type.toString(0x10)}, modelId=${modelId.toString(0x10)}, equipment=$equipment)"
    }

}

@Serializable
class EquipmentLook {

    private val look = IntArray(9) { 0 }

    fun copy(): EquipmentLook {
        val copy = EquipmentLook()
        look.copyInto(copy.look)
        return copy
    }

    fun main() = get(ItemModelSlot.Main)
    fun sub() = get(ItemModelSlot.Sub)

    operator fun set(slot: ItemModelSlot, modelId: Int): EquipmentLook {
        look[slot.index] = modelId
        return this
    }

    operator fun get(slot: ItemModelSlot): Int {
        return look[slot.index]
    }

    override fun toString(): String {
        return "EquipmentLook(look=${look.contentToString()})"
    }

}

class PcModel(initialLook: ModelLook, val actor: Actor) : Model {

    val raceGenderConfig = initialLook.race ?: throw IllegalStateException("Unknown race index: ${initialLook.race}")
    private val raceResources = PcModelLoader[raceGenderConfig]

    private val look = EquipmentLook()
    private val meshResources = HashMap<ItemModelSlot, DatWrapper>()

    init {
        updateEquipment(initialLook.equipment)
    }

    private fun fullyLoaded() : Boolean {
        return meshResources.values.all { it.isReady() }
    }

    override fun isReadyToDraw(): Boolean {
        return fullyLoaded()
    }

    override fun getMeshResources(): List<DirectoryResource> {
        if (!fullyLoaded()) { return emptyList() }
        return meshResources.mapNotNull { it.value.getAsResourceIfReady() }
    }

    override fun getSkeletonResource(): SkeletonResource? {
        return raceResources.raceConfig.getOnlyChildByType(SkeletonResource::class)
    }

    override fun getAnimationDirectories(): List<DirectoryResource> {
        return getFishingAnimationResource() + getSitChairResource() + listOfNotNull(
            getMountAnimationResource()?.getAsResourceIfReady(),
            raceResources.raceConfig,
            raceResources.upperBodyMovementAnimations,
            getSkirtBattleAnimationResource(),
            raceResources.skirtAnimations,
            getEquipmentModelResource(ItemModelSlot.Face),
            getEquipmentModelResource(ItemModelSlot.Main),
            getEquipmentModelResource(ItemModelSlot.Sub),
            getEquipmentModelResource(ItemModelSlot.Range),
        )
    }

    override fun getMainBattleAnimationDirectory(): DirectoryResource? {
        val weaponInfo = getMainWeaponInfo() ?: return null

        val offset = if (actor.isDualWield()) { MainDll.getBaseDualWieldMainHandAnimationIndex(raceGenderConfig) } else { MainDll.getBaseBattleAnimationIndex(raceGenderConfig) }
        val fileIndex = offset + weaponInfo.weaponAnimationType
        val filePath = resolveFile(fileIndex)
        return DatLoader.load(filePath).getAsResourceIfReady()
    }

    override fun getSubBattleAnimationDirectory(): DirectoryResource? {
        if (!actor.isDualWield()) { return null }

        val subWeaponInfo = getSubWeaponInfo() ?: return null
        val fileIndex = MainDll.getBaseDualWieldOffHandAnimationIndex(raceGenderConfig) + subWeaponInfo.weaponAnimationType
        val filePath = resolveFile(fileIndex)
        return DatLoader.load(filePath).getAsResourceIfReady()
    }

    override fun getMovementInfo(): InfoDefinition {
        if (!fullyLoaded()) { return InfoDefinition() }

        val raceInfo = raceResources.raceConfig.getOnlyChildByType(InfoResource::class).infoDefinition
        val feetInfo = getInfo(ItemModelSlot.Feet) ?: InfoDefinition()
        return InfoDefinition(movementType = raceInfo.movementType, movementChar = feetInfo.movementChar, shakeFactor = feetInfo.shakeFactor)
    }

    override fun getMainWeaponInfo(): InfoDefinition? {
        return getInfo(ItemModelSlot.Main)
    }

    override fun getSubWeaponInfo(): InfoDefinition? {
        return getInfo(ItemModelSlot.Sub)
    }

    override fun getRangedWeaponInfo(): InfoDefinition? {
        return getInfo(ItemModelSlot.Range)
    }

    override fun getBlurConfig(): BlurConfig? {
        return null
    }

    fun updateEquipment(newLook: EquipmentLook) {
        for (slot in ItemModelSlot.values()) {
            val current = look[slot]
            val new = newLook[slot]
            if (meshResources[slot] != null && current == new) { continue }
            onSwapEquipment(slot, new)
        }
    }

    private fun onSwapEquipment(modelSlot: ItemModelSlot, newModelId: Int) {
        val context = ActorContext(actor.id, modelSlot = modelSlot)

        val oldModelId = look[modelSlot]
        look[modelSlot] = newModelId

        val oldDat = resolveEquipmentResource(modelSlot, oldModelId)
        if (oldDat?.isReady() == true) { removeModelRoutines(modelSlot, oldDat.getAsResource(), context) }

        val newDat = resolveEquipmentResource(modelSlot, newModelId)
        newDat?.onReady { if (look[modelSlot] == newModelId) { executeModelRoutines(modelSlot, it.getAsResource(), context) } }

        if (newDat == null) { meshResources.remove(modelSlot) } else { meshResources[modelSlot] = newDat }
    }

    private fun executeModelRoutines(modelSlot: ItemModelSlot, resource: DirectoryResource, context: ActorContext) {
        val association = ActorAssociation(actor, context)

        val effects = resource.collectByTypeRecursive(EffectResource::class).filter { it.particleGenerator.autoRun }
        for (effect in effects) { EffectManager.registerEffect(association, effect) }

        val loadEvent = resource.getNullableChildRecursivelyAs(DatId.eventOnLoad, EffectRoutineResource::class)
        if (loadEvent != null) { EffectManager.registerActorRoutine(actor, context, loadEvent) }

        val itemEffectId = when(modelSlot) {
            ItemModelSlot.Main -> if (actor.isDisplayEngaged()) { DatId("!w01") } else { null }
            ItemModelSlot.Sub -> if (actor.isDisplayEngaged()) { DatId("!w11") } else { null }
            ItemModelSlot.Body -> DatId("!bd1")
            else -> null
        }

        if (itemEffectId != null) {
            val itemEffect = resource.getNullableChildRecursivelyAs(itemEffectId, EffectRoutineResource::class)
            if (itemEffect != null) { EffectManager.registerActorRoutine(actor, context, itemEffect) }
        }
    }

    private fun removeModelRoutines(modelSlot: ItemModelSlot, resource: DirectoryResource, context: ActorContext) {
        val association = ActorAssociation(actor, context)

        val effects = resource.collectByTypeRecursive(EffectResource::class).filter { it.particleGenerator.autoRun }
        for (effect in effects) { EffectManager.removeEffectsForAssociation(association, effect.id) }

        val unloadEvent = resource.getNullableChildRecursivelyAs(DatId.eventOnUnload, EffectRoutineResource::class)
        if (unloadEvent != null) { EffectManager.registerActorRoutine(actor, context, unloadEvent) }

        val itemEffectId = when (modelSlot) {
            ItemModelSlot.Main -> DatId("!w00")
            ItemModelSlot.Sub -> DatId("!w10")
            ItemModelSlot.Body -> DatId("!bd0")
            else -> null
        }

        if (itemEffectId != null) {
            val itemEffect = resource.getNullableChildRecursivelyAs(itemEffectId, EffectRoutineResource::class)
            if (itemEffect != null) { EffectManager.registerActorRoutine(actor, context, itemEffect) }
        }
    }

    override fun getEquipmentModelResource(modelSlot: ItemModelSlot): DirectoryResource? {
        return meshResources[modelSlot]?.getAsResourceIfReady()
    }

    private fun getEquipmentModelResource(): Map<ItemModelSlot, DirectoryResource?> {
        return ItemModelSlot.values().associateWith { getEquipmentModelResource(it) }
    }

    private fun resolveEquipmentResource(modelSlot: ItemModelSlot, modelId: Int): DatWrapper? {
        val datResource = EquipmentModelTable.getItemModelPath(raceGenderConfig, modelSlot, modelId) ?: return null
        return DatLoader.load(datResource)
    }

    private fun getInfo(slot: ItemModelSlot): InfoDefinition? {
        val directory = getEquipmentModelResource()[slot] ?: return null

        return directory.getNullableChildRecursivelyAs(DatId.info, InfoResource::class)
            ?.infoDefinition
    }

    private fun resolveFile(fileIndex: Int): String {
        return FileTableManager.getFilePath(fileIndex) ?: throw IllegalStateException("Couldn't resolve battle aimations: ${fileIndex.toString(0x10)}")
    }

    private fun getSkirtBattleAnimationResource(): DirectoryResource? {
        if (!actor.isDisplayEngaged()) { return null }
        val weaponInfo = getMainWeaponInfo() ?: return null

        val fileIndex = MainDll.getBaseSkirtAnimationIndex(raceGenderConfig, actor.isDualWield()) + weaponInfo.weaponAnimationType
        val filePath = resolveFile(fileIndex)

        return DatLoader.load(filePath).getAsResourceIfReady()
    }

    private fun getMountAnimationResource(): DatWrapper? {
        actor.getMount() ?: return null

        val index = MainDll.getActionAnimationIndex(raceGenderConfig) + 0x05
        val resource = FileTableManager.getFilePath(index) ?: return null

        return DatLoader.load(resource)
    }

    private fun getFishingAnimationResource(): List<DirectoryResource> {
        if (!actor.state.isFishing()) { return emptyList() }
        val index = MainDll.getActionAnimationIndex(raceGenderConfig) + 0x01
        val resource = FileTableManager.getFilePath(index) ?: return emptyList()
        val root = DatLoader.load(resource).getAsResourceIfReady() ?: return emptyList()
        return listOfNotNull(root, root.getNullableSubDirectory(DatId("fish")))
    }

    private fun getSitChairResource(): List<DirectoryResource> {
        if (!actor.state.isSittingOnChair()) { return emptyList() }

        val index = MainDll.getActionAnimationIndex(raceGenderConfig) + 0x04
        val resource = FileTableManager.getFilePath(index) ?: return emptyList()

        val index2 = MainDll.getActionAnimationIndex(raceGenderConfig) + 0x44
        val resource2 = FileTableManager.getFilePath(index2) ?: return emptyList()

        return listOfNotNull(DatLoader.load(resource).getAsResourceIfReady(), DatLoader.load(resource2).getAsResourceIfReady())
    }

}

class NpcModel private constructor(resourcePath: String, additionalAnimationPaths: List<String> = emptyList()) : Model {

    companion object {
        fun fromNpcLook(modelLook: ModelLook, additionalAnimationPaths: List<String> = emptyList()): NpcModel? {
            val fileTableIndex = if (modelLook.type == ModelLook.fileTableIndexType) { modelLook.modelId } else { NpcTable.getNpcModelIndex(modelLook) }
            val resourcePath = FileTableManager.getFilePath(fileTableIndex) ?: return null
            return NpcModel(resourcePath, additionalAnimationPaths)
        }

        fun fromName(nameId: Int): NpcModel? {
            if (nameId > 0x15) { return null }
            val resourcePath = FileTableManager.getFilePath(0x791C + nameId) ?: return null
            return NpcModel(resourcePath)
        }

    }

    val resource = DatLoader.load(resourcePath)
    val additionalAnimations = additionalAnimationPaths.map { DatLoader.load(it) }

    private val blurLink = DatLink<BlurResource>(DatId.zero)
    private val infoLink = DatLink<InfoResource>(DatId.info)

    override fun isReadyToDraw(): Boolean {
        return resource.isReady()
    }

    override fun getMeshResources(): List<DirectoryResource> {
        val root = resource.getAsResourceIfReady() ?: return emptyList()
        val modelDirectory = root.getNullableSubDirectory(DatId.model)
        return if (modelDirectory != null) { listOf(modelDirectory) } else { listOf(root) }
    }

    override fun getSkeletonResource(): SkeletonResource? {
        return resource.getAsResourceIfReady()?.getFirstChildByTypeRecursively(SkeletonResource::class)
    }

    override fun getAnimationDirectories(): List<DirectoryResource> {
        val baseAnimations = resource.getAsResourceIfReady() ?: return emptyList()
        val allAnimations = additionalAnimations.mapNotNull { it.getAsResourceIfReady() } + baseAnimations
        return allAnimations.flatMap { it.getSubDirectoriesRecursively() + it }
    }

    override fun getMainBattleAnimationDirectory(): DirectoryResource? {
        return resource.getAsResourceIfReady()
    }

    override fun getSubBattleAnimationDirectory(): DirectoryResource? {
        return resource.getAsResourceIfReady()
    }

    override fun getEquipmentModelResource(modelSlot: ItemModelSlot): DirectoryResource? {
        return resource.getAsResourceIfReady()
    }

    override fun getMovementInfo(): InfoDefinition? {
        val resource = resource.getAsResourceIfReady() ?: return null
        val infoResource = infoLink.getOrPut { resource.getNullableChildRecursivelyAs(it, InfoResource::class) }
        return infoResource?.infoDefinition
    }

    override fun getMainWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getSubWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getRangedWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getBlurConfig(): BlurConfig? {
        val root = resource.getAsResourceIfReady() ?: return null
        return blurLink.getOrPut { root.collectByTypeRecursive(BlurResource::class).firstOrNull() }?.blurConfig
    }

    override fun getScale(): Float {
        val scale = getMovementInfo()?.scale ?: return 1f
        return scale / 100f
    }

}

class NpcWithBaseModel private constructor(val resource: DatWrapper): Model {

    companion object {
        fun fromLook(modelLook: ModelLook): NpcWithBaseModel? {
            val fileTableIndex = NpcTable.getNpcModelIndex(modelLook)
            val resourcePath = FileTableManager.getFilePath(fileTableIndex) ?: return null
            return NpcWithBaseModel(DatLoader.load(resourcePath))
        }
    }

    private var fakeRoot: DirectoryResource? = null
    private val blurLink = DatLink<BlurResource>(DatId.zero)

    fun setBase(modelId: Int): DatWrapper? {
        val fileTableIndex = NpcTable.getNpcModelIndex(modelId)
        val resourcePath = FileTableManager.getFilePath(fileTableIndex) ?: return null

        return DatLoader.load(resourcePath).onReady {
            fakeRoot = DirectoryResource.fakeRoot(listOf(it.getAsResource(), resource.getAsResource()))
        }
    }

    private fun resource(): DirectoryResource? {
        return fakeRoot ?: resource.getAsResourceIfReady()
    }

    override fun isReadyToDraw(): Boolean {
        return resource.isReady()
    }

    override fun getMeshResources(): List<DirectoryResource> {
        return resource()?.let { listOf(it) } ?: emptyList()
    }

    override fun getSkeletonResource(): SkeletonResource? {
        return resource()?.getFirstChildByTypeRecursively(SkeletonResource::class)
    }

    override fun getAnimationDirectories(): List<DirectoryResource> {
        return resource()?.let { it.getSubDirectoriesRecursively() + it } ?: emptyList()
    }

    override fun getMainBattleAnimationDirectory(): DirectoryResource? {
        return resource()
    }

    override fun getSubBattleAnimationDirectory(): DirectoryResource? {
        return resource()
    }

    override fun getEquipmentModelResource(modelSlot: ItemModelSlot): DirectoryResource? {
        return resource()
    }

    override fun getMovementInfo(): InfoDefinition? {
        return resource()?.getFirstChildByTypeRecursively(InfoResource::class)?.infoDefinition
    }

    override fun getMainWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getSubWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getRangedWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getBlurConfig(): BlurConfig? {
        val root = fakeRoot ?: return null
        return blurLink.getOrPut { root.collectByTypeRecursive(BlurResource::class).firstOrNull() }?.blurConfig
    }

    override fun getScale(): Float {
        val scale = getMovementInfo()?.scale ?: return 1f
        return scale / 100f
    }

}

class ZoneObjectModel(val id: DatId, val scene: Scene): Model {

    private val animDir by lazy {
        scene.getMainAreaRootDirectory().getNullableChildRecursivelyAs(id, DirectoryResource::class)
    }

    override fun isReadyToDraw(): Boolean {
        return true
    }

    override fun getMeshResources(): List<DirectoryResource> {
        return emptyList()
    }

    override fun getSkeletonResource(): SkeletonResource? {
        return null
    }

    override fun getAnimationDirectories(): List<DirectoryResource> {
        return  if (animDir != null) { listOf(animDir!!) } else { emptyList() }
    }

    override fun getMainBattleAnimationDirectory(): DirectoryResource? {
        return null
    }

    override fun getSubBattleAnimationDirectory(): DirectoryResource? {
        return null
    }

    override fun getEquipmentModelResource(modelSlot: ItemModelSlot): DirectoryResource? {
        return null
    }

    override fun getMovementInfo(): InfoDefinition? {
        return null
    }

    override fun getMainWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getSubWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getRangedWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getBlurConfig(): BlurConfig? {
        return null
    }

}

class ParticleModel(val modelDirectory: DirectoryResource): Model {

    override fun isReadyToDraw(): Boolean {
        return true
    }

    override fun getMeshResources(): List<DirectoryResource> {
        return listOf(modelDirectory)
    }

    override fun getSkeletonResource(): SkeletonResource? {
        return modelDirectory.getOnlyNullableChildByType(SkeletonResource::class)
    }

    override fun getAnimationDirectories(): List<DirectoryResource> {
        return listOfNotNull(modelDirectory.root())
    }

    override fun getMainBattleAnimationDirectory(): DirectoryResource? {
        return null
    }

    override fun getSubBattleAnimationDirectory(): DirectoryResource? {
        return null
    }

    override fun getEquipmentModelResource(modelSlot: ItemModelSlot): DirectoryResource? {
        return null
    }

    override fun getMovementInfo(): InfoDefinition? {
        return null
    }

    override fun getMainWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getSubWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getRangedWeaponInfo(): InfoDefinition? {
        return null
    }

    override fun getBlurConfig(): BlurConfig? {
        return null
    }

}