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

    /** 堕落（诅咒金属材料自带词条：消耗灵魂增幅伤害/击退） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.CorruptionTrait> CORRUPTION =
            MODIFIERS.register("corruption",
                    com.mofengbaizhi.tinkersnewlife.content.modifier.CorruptionTrait::new);

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

    /** 御厨子（术式特性，占用术式槽；整合解/捌/灶·开三招） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.YuchuziTrait> YUCHUZI =
        MODIFIERS.register("yuchuzi",
                com.mofengbaizhi.tinkersnewlife.content.modifier.YuchuziTrait::new);

    /** 赤血操术（术式特性，占用术式槽；整合穿血/百敛/超新星三招） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.BloodManipulationTrait> BLOOD_MANIPULATION =
        MODIFIERS.register("blood_manipulation",
                com.mofengbaizhi.tinkersnewlife.content.modifier.BloodManipulationTrait::new);

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

    /** 咒力外放（术式特性，占用术式槽；顺转能量弹/反转冰沙冲击波激光，不受熔断影响） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.CursedEnergyReleaseTrait> CURSED_ENERGY_RELEASE =
        MODIFIERS.register("cursed_energy_release",
                com.mofengbaizhi.tinkersnewlife.content.modifier.CursedEnergyReleaseTrait::new);

    /** 构筑术式（术式特性，占用术式槽；顺转无限弹药/反转拟造临时物品） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.ConstructTrait> CONSTRUCT =
        MODIFIERS.register("construct",
                com.mofengbaizhi.tinkersnewlife.content.modifier.ConstructTrait::new);

    /** 咒言术（术式特性，占用术式槽；顺转咏唱咒言/反转编辑咒言） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.CursedSpeechTrait> CURSED_SPEECH =
        MODIFIERS.register("cursed_speech",
                com.mofengbaizhi.tinkersnewlife.content.modifier.CursedSpeechTrait::new);

    /** 反重力机构（术式特性，占用术式槽；顺转漂浮 AOE/反转压力场） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.AntiGravityTrait> ANTI_GRAVITY =
        MODIFIERS.register("anti_gravity",
                com.mofengbaizhi.tinkersnewlife.content.modifier.AntiGravityTrait::new);

    /** 十划咒法（术式特性，占用术式槽；7:3 弱点标记，命中暴击） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.TenDivideTrait> TEN_DIVIDE =
        MODIFIERS.register("ten_divide",
                com.mofengbaizhi.tinkersnewlife.content.modifier.TenDivideTrait::new);

    /** 技巧·弥虚葛笼（技巧特性，占用技巧槽；被他人领域包裹时被动全防） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.MixuGelongTrait> MIXU_GELONG =
        MODIFIERS.register("mixu_gelong",
                com.mofengbaizhi.tinkersnewlife.content.modifier.MixuGelongTrait::new);

    /** 技巧·落花之情（技巧特性，占用技巧槽；能抗多数领域但防不住无量空处） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.LuohuaTrait> LUOHUA =
        MODIFIERS.register("luohua",
                com.mofengbaizhi.tinkersnewlife.content.modifier.LuohuaTrait::new);

    /** 技巧·新阴流·简易领域（技巧特性，占用技巧槽；抵御领域效果但期间禁用术式） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.JianyiLingyuTrait> JIANYI_LINGYU =
        MODIFIERS.register("jianyi_lingyu",
                com.mofengbaizhi.tinkersnewlife.content.modifier.JianyiLingyuTrait::new);

    /** 伏诛赐死（领域特性，占用领域槽；指定被告审判并处刑） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.FuzhuCisiTrait> FUZHU_CISI =
        MODIFIERS.register("fuzhu_cisi",
                com.mofengbaizhi.tinkersnewlife.content.modifier.FuzhuCisiTrait::new);

    /** 胎藏遍野（领域特性，占用领域槽；全体重压 2×反重力，可被技巧抵挡） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.TaizangBianyeTrait> TAIZANG_BIANYE =
        MODIFIERS.register("taizang_bianye",
                com.mofengbaizhi.tinkersnewlife.content.modifier.TaizangBianyeTrait::new);

    /** 真赝相爱（领域特性，占用领域槽；领域内可切换使用本存档已解锁全部术式） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.ZhenyanXiangaiTrait> ZHENYAN_XIANGAI =
        MODIFIERS.register("zhenyan_xiangai",
                com.mofengbaizhi.tinkersnewlife.content.modifier.ZhenyanXiangaiTrait::new);

    /** 嵌合影翳庭（领域特性，占用领域槽；强控全场 + 全体十影式神×2 群殴） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.QianheYingyiTrait> QIANHE_YINGYI =
        MODIFIERS.register("qianhe_yingyi",
                com.mofengbaizhi.tinkersnewlife.content.modifier.QianheYingyiTrait::new);

    /** 铁棺盖围山（领域特性，占用领域槽；点燃全场 + 每 5t 无视无敌帧咒力灼烧） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.TieGuanGaiWeiShanTrait> TIE_GUAN_GAI_WEI_SHAN =
        MODIFIERS.register("tie_guan_gai_wei_shan",
                com.mofengbaizhi.tinkersnewlife.content.modifier.TieGuanGaiWeiShanTrait::new);

    /** 自闭圆顿裹（领域特性，占用领域槽；域内全体施加一次无为转变，默认僵尸/骷髅） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.ZiBiYuanDunGuoTrait> ZI_BI_YUAN_DUN_GUO =
        MODIFIERS.register("zi_bi_yuan_dun_guo",
                com.mofengbaizhi.tinkersnewlife.content.modifier.ZiBiYuanDunGuoTrait::new);

    /** 荡蕴平线（领域特性，占用领域槽；域内注水 + 溺尸军团，结束复原） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.DangYunPingXianTrait> DANG_YUN_PING_XIAN =
        MODIFIERS.register("dang_yun_ping_xian",
                com.mofengbaizhi.tinkersnewlife.content.modifier.DangYunPingXianTrait::new);

    /** 时胞月宫殿（领域特性，占用领域槽；全员罚站定身，天与暴君豁免） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.ShiBaoYueGongDianTrait> SHI_BAO_YUE_GONG_DIAN =
        MODIFIERS.register("shi_bao_yue_gong_dian",
                com.mofengbaizhi.tinkersnewlife.content.modifier.ShiBaoYueGongDianTrait::new);

    /** 三重疾苦（领域特性，占用领域槽；必中领域：弹射物导引 + 拟造/远程空挥必中） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.SanChongJiKuTrait> SAN_CHONG_JI_KU =
        MODIFIERS.register("san_chong_ji_ku",
                com.mofengbaizhi.tinkersnewlife.content.modifier.SanChongJiKuTrait::new);

    /** 强化·灵魂修复（占用升级槽；每 20t 耗灵魂修耐久，5%×级 概率额外修 1 点；上限 3 级） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.SoulRepairModifier> SOUL_REPAIR =
        MODIFIERS.register("soul_repair",
                com.mofengbaizhi.tinkersnewlife.content.modifier.SoulRepairModifier::new);

    /** 词条·破法（黑暗金属材料工具部件自带：攻击给衰弱 I，击杀魔法抗性生物 45% 掉绿宝石） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.DarkMetalBreakerModifier> DARK_METAL_BREAKER =
        MODIFIERS.register("dark_metal_breaker",
                com.mofengbaizhi.tinkersnewlife.content.modifier.DarkMetalBreakerModifier::new);

    /** 词条·抗魔（黑暗金属材料护甲部件自带：魔法伤害 5%×级+1.5 点转物理减免） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.DarkMetalMagicResistModifier> DARK_METAL_MAGIC_RESIST =
        MODIFIERS.register("dark_metal_magic_resist",
                com.mofengbaizhi.tinkersnewlife.content.modifier.DarkMetalMagicResistModifier::new);

    /** 强化·噬魂（1 级占 1 升级槽，2/3 级无槽；每级 +25% 灵魂获取，一级后额外 +1，最高 3 级） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.SoulEaterModifier> SOUL_EATER =
        MODIFIERS.register("soul_eater",
                com.mofengbaizhi.tinkersnewlife.content.modifier.SoulEaterModifier::new);

    /** 无槽位强化·苍白镀层（安装不占槽，直接给工具 +1 升级槽；单级，不可洗/不可剥） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.PalePlatingModifier> PALE_PLATING =
        MODIFIERS.register("pale_plating",
                com.mofengbaizhi.tinkersnewlife.content.modifier.PalePlatingModifier::new);

    /** 强化·统御者（占用能力槽；半径8格刌民30%概率优先锁定你攻击的非刌民目标；单级可洗可剥） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.CommanderModifier> COMMANDER =
        MODIFIERS.register("commander",
                com.mofengbaizhi.tinkersnewlife.content.modifier.CommanderModifier::new);

    /** 无槽位强化·终末烙印（安装不占槽：+1 能力槽，-10% 移动速度；单级，不可洗/不可剥） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.FinalBrandModifier> FINAL_BRAND =
        MODIFIERS.register("final_brand",
                com.mofengbaizhi.tinkersnewlife.content.modifier.FinalBrandModifier::new);

    /** 无槽位强化·渎神（安装不占槽：护甲 +2 防御槽，受到的伤害 +10%；单级，不可洗/不可剥） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.BlasphemyModifier> BLASPHEMY =
        MODIFIERS.register("blasphemy",
                com.mofengbaizhi.tinkersnewlife.content.modifier.BlasphemyModifier::new);

    /** 升级槽强化·穿透EX（占1升级槽，近战/远程：命中改写为穿透伤害源，可穿透无敌/限伤/无敌帧） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.ExPierceModifier> EX_PIERCE =
        MODIFIERS.register("ex_pierce",
                com.mofengbaizhi.tinkersnewlife.content.modifier.ExPierceModifier::new);

    /** 近战特性·莱万汀（神灵金材料近战头自带：真伤/禁疗/砍上限/拆柱/突刺/仆从光环/防复活锁血） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.LaevatainModifier> LAEVATAIN =
        MODIFIERS.register("laevatain",
                com.mofengbaizhi.tinkersnewlife.content.modifier.LaevatainModifier::new);

    /** 近战特性·神圣之力（神灵金近战头自带，联动铁魔法/启示录：+180法力、注入火墙术Lv5/天使之翼Lv5/治愈之环Lv10、火墙无视无敌帧） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.DivinePowerModifier> DIVINE_POWER =
        MODIFIERS.register("divine_power",
                com.mofengbaizhi.tinkersnewlife.content.modifier.DivinePowerModifier::new);

    /** 远程特性·冈格尼尔（神灵金远程武器自带，模仿原版 Gungnir：虚影投掷/80%伤害/震撼/狱火） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.GungnirModifier> GUNGNIR =
        MODIFIERS.register("gungnir",
                com.mofengbaizhi.tinkersnewlife.content.modifier.GungnirModifier::new);

    /** 远程特性·命运因果贯穿之物（神灵金远程武器自带：下蹲自动锁定，攻击锁定目标必中） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.FatePierceModifier> FATE_PIERCE =
        MODIFIERS.register("fate_pierce",
                com.mofengbaizhi.tinkersnewlife.content.modifier.FatePierceModifier::new);

    /** 盔甲特性·越过神域（神灵金盔甲自带：免疫爆炸/火焰/魔法） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.GodrealmOverstepModifier> GODREALM_OVERSTEP =
        MODIFIERS.register("godrealm_overstep",
                com.mofengbaizhi.tinkersnewlife.content.modifier.GodrealmOverstepModifier::new);

    /** 盔甲特性·天地所铸（神灵金盔甲自带：每次耐久损伤≤20） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.HeavenEarthForgedModifier> HEAVEN_EARTH_FORGED =
        MODIFIERS.register("heaven_earth_forged",
                com.mofengbaizhi.tinkersnewlife.content.modifier.HeavenEarthForgedModifier::new);

    /** 盔甲特性·灵魂折扣（神灵金盔甲自带：每级 5% 灵魂能量消耗减免） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.SoulDiscountModifier> SOUL_DISCOUNT =
        MODIFIERS.register("soul_discount",
                com.mofengbaizhi.tinkersnewlife.content.modifier.SoulDiscountModifier::new);

    /** 词条·咒上·腺速（诅咒金属材料护甲部件自带：受敌对/摔落伤害耗10灵魂给5秒迅捷II，冷却1分钟） */
    public static final StaticModifier<com.mofengbaizhi.tinkersnewlife.content.modifier.CursedAdrenalineModifier> CURSED_ADRENALINE =
        MODIFIERS.register("cursed_adrenaline",
                com.mofengbaizhi.tinkersnewlife.content.modifier.CursedAdrenalineModifier::new);
}