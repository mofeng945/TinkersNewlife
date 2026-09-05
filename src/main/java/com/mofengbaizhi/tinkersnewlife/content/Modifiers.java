package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.content.modifier.*;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import slimeknights.tconstruct.library.modifiers.util.ModifierDeferredRegister;
import slimeknights.tconstruct.library.modifiers.util.StaticModifier;

public class Modifiers {
    public static final ModifierDeferredRegister MODIFIERS =
            ModifierDeferredRegister.create(TinkersNewlife.MOD_ID);

    public static final StaticModifier<CosmicOrderVoiceTrait> COSMIC_ORDER_VOICE =
            MODIFIERS.register("cosmic_order_voice", CosmicOrderVoiceTrait::new);

    public static final StaticModifier<DragonsteelFireTrait> DRAGONSTEEL_FIRE =
            MODIFIERS.register("dragonsteel_fire", DragonsteelFireTrait::new);

    public static final StaticModifier<DragonsteelIceTrait> DRAGONSTEEL_ICE =
            MODIFIERS.register("dragonsteel_ice", DragonsteelIceTrait::new);

    public static final StaticModifier<DragonsteelLightningTrait> DRAGONSTEEL_LIGHTNING =
            MODIFIERS.register("dragonsteel_lightning", DragonsteelLightningTrait::new);

    public static final StaticModifier<DragonsteelFireArmorTrait> DRAGONSTEEL_FIRE_ARMOR =
            MODIFIERS.register("dragonsteel_fire_armor", DragonsteelFireArmorTrait::new);

    public static final StaticModifier<DragonsteelIceArmorTrait> DRAGONSTEEL_ICE_ARMOR =
            MODIFIERS.register("dragonsteel_ice_armor", DragonsteelIceArmorTrait::new);

    public static final StaticModifier<DragonsteelLightningArmorTrait> DRAGONSTEEL_LIGHTNING_ARMOR =
            MODIFIERS.register("dragonsteel_lightning_armor", DragonsteelLightningArmorTrait::new);

    public static final StaticModifier<DragonBloodTankTrait> DRAGON_BLOOD_TANK =
            MODIFIERS.register("dragon_blood_tank", DragonBloodTankTrait::new);

    public static final StaticModifier<DragonboneTrait> DRAGONBONE =
            MODIFIERS.register("dragonbone", DragonboneTrait::new);

    public static final StaticModifier<DragonBloodInfusionTrait> DRAGON_BLOOD_INFUSION =
            MODIFIERS.register("dragon_blood_infusion", DragonBloodInfusionTrait::new);

    public static final StaticModifier<DragonStaffTrait> DRAGON_STAFF =
            MODIFIERS.register("dragon_staff", DragonStaffTrait::new);

    public static final StaticModifier<FusRoDahTrait> FUS_RO_DAH =
            MODIFIERS.register("fus_ro_dah", FusRoDahTrait::new);

    public static final StaticModifier<GhostClearingTrait> GHOST_CLEARING =
            MODIFIERS.register("ghost_clearing", GhostClearingTrait::new);

    public static final StaticModifier<GorgonImmunityTrait> GORGON_IMMUNITY =
            MODIFIERS.register("gorgon_immunity", GorgonImmunityTrait::new);

    public static final StaticModifier<DreadsteelTrait> DREADSTEEL =
            MODIFIERS.register("dreadsteel", DreadsteelTrait::new);

    public static final StaticModifier<DreadsteelArmorTrait> DREADSTEEL_ARMOR =
            MODIFIERS.register("dreadsteel_armor", DreadsteelArmorTrait::new);

    public static final StaticModifier<LuckyDropTrait> LUCKY_DROP =
            MODIFIERS.register("lucky_drop", LuckyDropTrait::new);

    public static final StaticModifier<CharmTrait> CHARM =
            MODIFIERS.register("charm", CharmTrait::new);

    public static final StaticModifier<HasturMaliceTrait> HASTUR_MALICE =
            MODIFIERS.register("hastur_malice", HasturMaliceTrait::new);

    public static final StaticModifier<ChildOfTheStarsTrait> CHILD_OF_THE_STARS =
            MODIFIERS.register("child_of_the_stars", ChildOfTheStarsTrait::new);

    public static final StaticModifier<StarChildArmorTrait> STAR_CHILD_ARMOR =
            MODIFIERS.register("star_child_armor", StarChildArmorTrait::new);

    public static final StaticModifier<YogSothothTrait> YOG_SOTHOTH_GIFT =
            MODIFIERS.register("yog_sothoth_gift", YogSothothTrait::new);

    public static final StaticModifier<NyarlathotepDesireTrait> NYARLATHOTEP_DESIRE =
            MODIFIERS.register("nyarlathotep_desire", NyarlathotepDesireTrait::new);
    
    public static final StaticModifier<QuantumBagModifier> QUANTUM_BAG =
            MODIFIERS.register("quantum_bag", QuantumBagModifier::new);

    public static final StaticModifier<SilentGloveTrait> SILENT_GLOVE =
        MODIFIERS.register("silent_glove", SilentGloveTrait::new);

    public static final StaticModifier<ChaosButterflyModifier> CHAOS_BUTTERFLY =
        MODIFIERS.register("chaos_butterfly", ChaosButterflyModifier::new);

    public static final StaticModifier<ModularStaffModifier> MODULAR_STAFF_MODIFIER =
        MODIFIERS.register("modular_staff", ModularStaffModifier::new);

    public static final StaticModifier<FlyingSwordTrait> FLYING_SWORD =
        MODIFIERS.register("flying_sword", FlyingSwordTrait::new);

    public static final StaticModifier<BlackFlashTrait> BLACK_FLASH =
        MODIFIERS.register("black_flash", BlackFlashTrait::new);

    public static final StaticModifier<WestTigerTrait> WEST_TIGER =
        MODIFIERS.register("west_tiger", WestTigerTrait::new);

    /** 咒力输出（咒力核心自带） */
    public static final StaticModifier<CurseOutputTrait> CURSE_OUTPUT =
        MODIFIERS.register("curse_output", CurseOutputTrait::new);

    /** 咒力总量（咒力核心自带） */
    public static final StaticModifier<CurseTotalTrait> CURSE_TOTAL =
        MODIFIERS.register("curse_total", CurseTotalTrait::new);

    /** 坐杀搏徒（领域特性，占用领域槽） */
    public static final StaticModifier<ZuoShaBoTuModifier> ZUOSHA_BOTU =
        MODIFIERS.register("zuosha_botu", ZuoShaBoTuModifier::new);

    /** 无量空处（领域特性，占用领域槽） */
    public static final StaticModifier<WuLiangKongChuTrait> WULIANG_KONGCHU =
        MODIFIERS.register("wuliang_kongchu", WuLiangKongChuTrait::new);

    /** 伏魔御厨子（领域特性，占用领域槽） */
    public static final StaticModifier<FuMoYuChuZiTrait> FUMO_YUCHUZI =
        MODIFIERS.register("fumo_yuchuzi", FuMoYuChuZiTrait::new);

    /** 解（术式特性，占用术式槽） */
    public static final StaticModifier<KaiTrait> KAI =
        MODIFIERS.register("kai", KaiTrait::new);

    /** 捌（术式特性，占用术式槽） */
    public static final StaticModifier<BaTrait> BA =
        MODIFIERS.register("ba", BaTrait::new);

    /** 灶·开（术式特性，占用术式槽） */
    public static final StaticModifier<ZaoKaiTrait> ZAO_KAI =
        MODIFIERS.register("zao_kai", ZaoKaiTrait::new);

    /** 赤血操术（术式特性，占用术式槽） */
    public static final StaticModifier<BloodManipulationTrait> BLOOD_MANIPULATION =
        MODIFIERS.register("blood_manipulation", BloodManipulationTrait::new);

    /** 赤血操术·百敛（术式特性，占用术式槽） */
    public static final StaticModifier<BloodManipulationHyakurenTrait> BLOOD_MANIPULATION_HYAKUREN =
        MODIFIERS.register("blood_manipulation_hyakuren", BloodManipulationHyakurenTrait::new);

    /** 赤血操术·超新星（术式特性，占用术式槽） */
    public static final StaticModifier<BloodManipulationSupernovaTrait> BLOOD_MANIPULATION_SUPERNOVA =
        MODIFIERS.register("blood_manipulation_supernova", BloodManipulationSupernovaTrait::new);

    /** 十影术式（术式特性，占用术式槽） */
    public static final StaticModifier<TenShadowsTrait> TEN_SHADOWS =
        MODIFIERS.register("ten_shadows", TenShadowsTrait::new);

    /** 黑鸟操术（术式特性，占用术式槽） */
    public static final StaticModifier<BlackBirdTrait> BLACK_BIRD =
        MODIFIERS.register("black_bird", BlackBirdTrait::new);

    /** 傀儡操术（术式特性，占用术式槽） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.PuppetTrait> PUPPET =
        MODIFIERS.register("puppet", com.mofengbaizhi.tinkersnewlife.content.modifier.PuppetTrait::new);

    /** 草木操术（术式特性，占用术式槽） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.PlantManipulationTrait> PLANT_MANIPULATION =
        MODIFIERS.register("plant_manipulation", com.mofengbaizhi.tinkersnewlife.content.modifier.PlantManipulationTrait::new);

    /** 炎熔操术（术式特性，占用术式槽） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.FlameManipulationTrait> FLAME_MANIPULATION =
        MODIFIERS.register("flame_manipulation", com.mofengbaizhi.tinkersnewlife.content.modifier.FlameManipulationTrait::new);

    /** 咒灵操术（术式特性，占用术式槽） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.CursedSpiritTrait> CURSED_SPIRIT =
        MODIFIERS.register("cursed_spirit", com.mofengbaizhi.tinkersnewlife.content.modifier.CursedSpiritTrait::new);

    /** 雷电操术（术式特性，占用术式槽） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.LightningManipulationTrait> LIGHTNING_MANIPULATION =
        MODIFIERS.register("lightning_manipulation", com.mofengbaizhi.tinkersnewlife.content.modifier.LightningManipulationTrait::new);

    /** 天空操术（术式特性，占用术式槽） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.SkyManipulationTrait> SKY_MANIPULATION =
        MODIFIERS.register("sky_manipulation", com.mofengbaizhi.tinkersnewlife.content.modifier.SkyManipulationTrait::new);

    /** 投射咒法（术式特性，占用术式槽） */
    public static final StaticModifier<ProjectionTrait> PROJECTION =
        MODIFIERS.register("projection", ProjectionTrait::new);

    /** 无下限·无限（术式特性，占用术式槽） */
    public static final StaticModifier<WuliangWuxianTrait> WULIANG_WUXIAN =
        MODIFIERS.register("wuliang_wuxian", WuliangWuxianTrait::new);

    /** 无下限·苍（术式特性，占用术式槽；含术式反转·赫与虚式·茈） */
    public static final StaticModifier<WuliangCangTrait> WULIANG_CANG =
        MODIFIERS.register("wuliang_cang", WuliangCangTrait::new);

    /** 雅各布天梯（术式特性，占用术式槽） */
    public static final StaticModifier<JacobsLadderTrait> JACOBS_LADDER =
        MODIFIERS.register("jacobs_ladder", JacobsLadderTrait::new);

    /** 反转术式（术式特性，占用术式槽；含术式反转·外放） */
    public static final StaticModifier<ReverseCursedTrait> REVERSE_CURSED =
        MODIFIERS.register("reverse_cursed", ReverseCursedTrait::new);

    /** 无为转变（术式特性，占用术式槽；顺转变自己/反转目标，需先击杀记录形态） */
    public static final StaticModifier<WuWeiTrait> WU_WEI =
        MODIFIERS.register("wu_wei", WuWeiTrait::new);
}