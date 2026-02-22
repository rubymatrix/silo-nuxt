package xim.poc.game

import kotlinx.serialization.Serializable

@Serializable
enum class Job(val index: Int) {
    Nop(0),
    War(1),
    Mnk(2),
    Whm(3),
    Blm(4),
    Rdm(5),
    Thf(6),
    Pld(7),
    Drk(8),
    Bst(9),
    Brd(10),
    Rng(11),
    Sam(12),
    Nin(13),
    Drg(14),
    Smn(15),
    Blu(16),
    Cor(17),
    Pup(18),
    Dnc(19),
    Sch(20),
    Geo(21),
    Run(22),
    ;

    companion object {
        fun byIndex(index: Int): Job? { return Job.values().firstOrNull { it.index == index } }
    }

    fun mask(): Int {
        return if (this == Nop) { 0 } else { 1 shl index }
    }

}