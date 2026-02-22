package xim.poc.game

enum class AugmentId(val id: Int) {
    HP(9),
    MP(10),
    STR(11),
    DEX(12),
    VIT(13),
    AGI(14),
    INT(15),
    MND(16),
    CHR(17),

    CriticalHitRate(26),
    EnemyCriticalHitRate(27),

    WeaponDamage(30),

    Haste(32),

    SpellInterruptDown(36),
    PhysicalDamageTaken(37),
    MagicalDamageTaken(38),
    DamageTaken(41),

    LatentRegain(48),

    TpBonus(53),
    ParryingRate(54),

    MagicAttackBonus(133),

    Regen(137),
    Refresh(138),

    FastCast(140),
    ConserveMp(141),
    StoreTp(142),
    DoubleAttack(143),
    TripleAttack(144),
    DualWield(146),

    KickAttacks(194),
    SubtleBlow(195),

    WeaponSkillDamage(327),
    CriticalHitDamage(328),
    SkillChainDamage(332),
    ConserveTp(333),
    MagicBurstDamage(334),
    QuadrupleAttack(354),
    SaveTp(360),

    OccAttack2x(554),
    OccAttack2x3x(555),
    OccAttack2x4x(556),
    OccDoubleDamage(557),

    Blank(1023),

    CriticalTpGain(1283),
    MagicBurstII(1286),
    ElementalWeaponSkillDamage(1293),
    DoubleDamage(1296),
    FollowUpAttack(1298),

    AlterEgo(2042),
}

enum class StatusEffect(
    val id: Int,
    val buff: Boolean = false,
    val debuff: Boolean = false,
    val canDispel: Boolean = false,
    val canErase: Boolean = false,
    val canEsuna: Boolean = false,
    val displayCounter: Boolean = false,
    val displaySkillIcon: Boolean = false,
) {

    Sleep(2, debuff = true, canEsuna = true),
    Poison(3, debuff = true, canEsuna = true),
    Paralysis(4, debuff = true, canEsuna = true),
    Blind(5, debuff = true, canEsuna = true),
    Silence(6, debuff = true, canEsuna = true),
    Petrify(7, debuff = true, canEsuna = true),
    Disease(8, debuff = true, canEsuna = true),
    Curse(8, debuff = true, canEsuna = true),
    Stun(10, debuff = true),
    Bind(11, debuff = true, canErase = true),
    Weight(12, debuff = true, canErase = true, canEsuna = true),
    Slow(13, debuff = true, canErase = true),
    Doom(15, debuff = true),
    Amnesia(16, debuff = true, canEsuna = true),
    Addle(21, debuff = true, canEsuna = true),
    Terror(28, debuff = true),
    Plague(31, debuff = true, canEsuna = true),

    Haste(33, buff = true, canDispel = true),
    BlazeSpikes(34, buff = true, canDispel = true),
    IceSpikes(35, buff = true, canDispel = true),
    Blink(36, buff = true, canDispel = true, displayCounter = true),
    Stoneskin(37, buff = true, canDispel = true),
    ShockSpikes(38, buff = true, canDispel = true),
    Aquaveil(39, buff = true, canDispel = true),
    Protect(40, buff = true, canDispel = true),
    Shell(41, buff = true, canDispel = true),
    Regen(42, buff = true, canDispel = true),
    Refresh(43, buff = true, canDispel = true),
    Boost(45, buff = true, canDispel = true),

    HundredFists(46, buff = true),
    Manafont(47, buff = true),
    Chainspell(48, buff = true),
    MeikyoShisui(54, buff = true),

    Berserk(56, buff = true, canDispel = true),
    Counterstance(61, buff = true, canDispel = true),
    Warcry(68, buff = true, canDispel = true),

    StrBoost(80, buff = true, canDispel = true),
    DexBoost(81, buff = true, canDispel = true),
    VitBoost(82, buff = true, canDispel = true),
    AgiBoost(83, buff = true, canDispel = true),
    IntBoost(84, buff = true, canDispel = true),
    MndBoost(85, buff = true, canDispel = true),
    ChrBoost(86, buff = true, canDispel = true),

    AttackBoost(91, buff = true, canDispel = true),
    EvasionBoost(92, buff = true, canDispel = true),
    DefenseBoost(93, buff = true, canDispel = true),
    Enfire(94, buff = true, canDispel = true),
    Enblizzard(95, buff = true, canDispel = true),
    Enthunder(98, buff = true, canDispel = true),

    Barfire(100, buff = true, canDispel = true),
    Barblizzard(101, buff = true, canDispel = true),
    Baraero(102, buff = true, canDispel = true),
    Barstone(103, buff = true, canDispel = true),
    Barthunder(104, buff = true, canDispel = true),
    Barwater(105, buff = true, canDispel = true),

    Costume(127, buff = true, canDispel = true),

    Burn(128, debuff = true, canErase = true),
    Frost(129, debuff = true, canErase = true),
    Choke(130, debuff = true, canErase = true),
    Rasp(131, debuff = true, canErase = true),
    Shock(132, debuff = true, canErase = true),
    Drown(133, debuff = true, canErase = true),
    Dia(134, debuff = true, canErase = true),
    Bio(135, debuff = true, canErase = true),

    StrDown(136, debuff = true, canErase = true),
    DexDown(137, debuff = true, canErase = true),
    VitDown(138, debuff = true, canErase = true),
    AgiDown(139, debuff = true, canErase = true),
    IntDown(140, debuff = true, canErase = true),
    MndDown(141, debuff = true, canErase = true),
    HPDown(144, debuff = true, canErase = true),
    MPDown(144, debuff = true, canErase = true),

    AttackDown(147, debuff = true, canErase = true),
    DefenseDown(149, debuff = true, canErase = true),
    ShiningRuby(154, buff = true, canDispel = true),
    Medicated(155, debuff = true),
    Flash(156, debuff = true, canErase = true),
    Provoke(158),

    Enchantment(162, buff = true, canDispel = false),

    ChainAffinity(164, buff = true),
    BurstAffinity(165, buff = true),

    MagicDefDown(167, debuff = true, canErase = true),
    Regain(170, buff = true, canDispel = true),
    DreadSpikes(173, buff = true, canDispel = true),
    MagicAtkDown(175, debuff = true, canErase = true),

    MagicAtkBoost(190, buff = true, canDispel = true),
    MagicDefBoost(191, buff = true, canDispel = true),

    Elegy(194, debuff = true, canErase = true),
    Ballad(196, buff = true, canDispel = true),
    March(214, buff = true, canDispel = true),

    Spontaneity(230, buff = true, canDispel = true),

    Mounted(252),

    Encumbrance(259, debuff = true),

    Visitant(285),

    DoubleUpChance(308),
    Bust(309),
    FightersRoll(310),

    WarriorsCharge(340, buff = true, canDispel = true, displaySkillIcon = true),

    Retaliation(405, buff = true, canDispel = true, displayCounter = true, displaySkillIcon = true),

    MultiStrikes(432, buff = true, canDispel = true),
    Restraint(435, buff = true, canDispel = true, displayCounter = true, displaySkillIcon = true),

    BloodRage(460, buff = true, canDispel = true, displaySkillIcon = true),
    Impetus(461, buff = true, canDispel = true, displayCounter = true, displaySkillIcon = true),

    Immanence(470, buff = true),

    Prowess(474, displayCounter = true),

    CounterBoost(486, buff = true, canDispel = true),
    Enaspir(488),

    Regen2(539, buff = true, canDispel = true),

    AttackBoost2(549, buff = true, canDispel = true),
    MagicAtkBoost2(551, buff = true, canDispel = true),
    MagicDefBoost2(552, buff = true, canDispel = true),

    DelugeSpikes(573, buff = true, canDispel = true),

    CostumeDebuff(585, debuff = true),

    Vorseal(602),
    GaleSpikes(605, buff = true, canDispel = true),

    Indicolure(612),
}
