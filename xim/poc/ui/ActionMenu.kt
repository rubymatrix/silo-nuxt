package xim.poc.ui

import xim.math.Vector2f
import xim.poc.UiResourceManager
import xim.resource.*

enum class ActionMenuBase(val numItems: Int, val menuName: String) {
    Items1(1, "menu    actionm1"),
    Items2(2, "menu    actionm2"),
    Items3(3, "menu    actionm3"),
    Items4(4, "menu    actionm4"),
    Items5(5, "menu    actionm5"),
    Items6(6, "menu    actionm6"),
    Items7(7, "menu    actionm7"),
    Items8(8, "menu    actionm8"),
    Items9(9, "menu    actionm9"),
    Items10(10, "menu    actiom10"),
}

enum class ActionMenuItem(val elementIndex: Int, val elementGroup: String) {
    Chat(1, "menu    keytops3"),
    Markers(2, "menu    keytops3"),
    WideScan(3, "menu    keytops3"),
    Abilities(4, "menu    keytops3"),
    Check(5, "menu    keytops3"),
    SwitchTarget(6, "menu    keytops3"),
    Magic(7, "menu    keytops3"),
    WhiteMagic(8, "menu    keytops3"),
    BlackMagic(9, "menu    keytops3"),
    Items(10, "menu    keytops3"),
    Summoning(11, "menu    keytops3"),
    Ninjutsu(12, "menu    keytops3"),
    Fish(13, "menu    keytops3"),
    Songs(14, "menu    keytops3"),
    BlueMagic(15, "menu    keytops3"),
    Attack(16, "menu    keytops3"),
    Dice(17, "menu    keytops3"),
    Party(18, "menu    keytops3"),
    // Check(19, "menu    keytops3"),
    WideScan_Blue(20, "menu    keytops3"),
    LinkShell(21, "menu    keytops3"),
    Disengage(22, "menu    keytops3"),
    CallForHelp(23, "menu    keytops3"),
    Job(24, "menu    keytops3"),
    Treasure(25, "menu    keytops3"),
    Allegiance(26, "menu    keytops3"),
    Race(27, "menu    keytops3"),
    Dig(38, "menu    keytops3"),
    Dismount(39, "menu    keytops3"),
    WeaponSkill(160, "menu    keytops3"),
    JobAbility(161, "menu    keytops3"),
    RangedAttack(162, "menu    keytops3"),
    PetCommand(163, "menu    keytops3"),
    JobTraits(164, "menu    keytops3"),
    Geomancy(496, "menu    keytops3"),
    MonsterSkills(497, "menu    keytops3"),
    Trust(505, "menu    keytops3"),
    Mount(551, "menu    keytops3"),
    Release(668, "menu    windowps"),
    ChangeJobs(200, "menu    windowps"),
}

object ActionMenuBuilder {

    fun register(name: String, items: List<ActionMenuItem>) {
        if (items.size < 1 || items.size > 10) { throw IllegalStateException("Invalid set of items: $items") }
        val base = ActionMenuBase.values().first { it.numItems == items.size }
        val template = UiResourceManager.getMenu(base.menuName) ?: throw IllegalStateException("No such template: ${base.menuName}")

        val frame = template.uiMenu.frame
        val elements = ArrayList<UiMenuElement>()

        val baseX = 21f
        var baseY = 6f
        val size = Vector2f(40f, 16f)

        for (i in items.indices) {
            val item = items[i]
            val option = UiMenuElementOption(elementIndex = item.elementIndex, elementGroupName = item.elementGroup)
            val next = getCursorMap(i + 1, items.size)
            elements += UiMenuElement(offset = Vector2f(baseX, baseY), size = size, options = mapOf(UiComponentType.Default to option), next = next, selectable = true)
            baseY += 16
        }

        val menu = UiMenu(name, frame, elements)
        UiResourceManager.register(UiMenuResource(DatId.zero, menu))
    }

    private fun getCursorMap(index: Int, totalOptions: Int): Map<UiMenuCursorKey, Int> {
        if (totalOptions == 1) { return emptyMap() }

        val next = HashMap<UiMenuCursorKey, Int>()

        if (index == 1) {
            next[UiMenuCursorKey.Up] = totalOptions
        } else {
            next[UiMenuCursorKey.Up] = index - 1
        }

        if (index == totalOptions) {
            next[UiMenuCursorKey.Down] = 1
        } else {
            next[UiMenuCursorKey.Down] = index + 1
        }

        next[UiMenuCursorKey.Right] = index
        next[UiMenuCursorKey.Left] = index

        return next
    }

}