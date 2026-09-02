package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.BloodNovaEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.DomainVisualEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.DreadsteelSlashEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlameArrowEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordFootEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiCow;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiFrog;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiGoat;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiIronGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiPhantom;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiPig;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiRabbit;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiSheep;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiSilverfish;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiWolf;
import com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TinkersNewlife.MOD_ID);

    public static final RegistryObject<EntityType<DreadsteelSlashEntity>> DREADSTEEL_SLASH =
            ENTITIES.register("dreadsteel_slash",
                    () -> EntityType.Builder.<DreadsteelSlashEntity>of(
                                    DreadsteelSlashEntity::new, MobCategory.MISC)
                            .sized(1.5f, 0.8f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build(TinkersNewlife.MOD_ID + ":dreadsteel_slash")
            );

    public static final RegistryObject<EntityType<FlyingSwordEntity>> FLYING_SWORD =
        ENTITIES.register("flying_sword",
                () -> EntityType.Builder.<FlyingSwordEntity>of(FlyingSwordEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.5f)
                        .clientTrackingRange(64)
                        .updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":flying_sword")
        );

    public static final RegistryObject<EntityType<FlyingSwordFootEntity>> FLYING_SWORD_FOOT =
        ENTITIES.register("flying_sword_foot",
                () -> EntityType.Builder.<FlyingSwordFootEntity>of(FlyingSwordFootEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.1f)  // 扁平的碰撞盒，几乎无碰撞
                        .clientTrackingRange(64)
                        .updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":flying_sword_foot")
        );

    /** 悠悠球实体 */
    public static final RegistryObject<EntityType<YoYoEntity>> YO_YO =
        ENTITIES.register("yo_yo",
                () -> EntityType.Builder.<YoYoEntity>of(YoYoEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.5f)
                        .clientTrackingRange(64)
                        .updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":yo_yo")
        );

    /** 领域视觉实体（黑色空心圆球线框，纯绘制形状） */
    public static final RegistryObject<EntityType<DomainVisualEntity>> DOMAIN_VISUAL =
        ENTITIES.register("domain_visual",
                () -> EntityType.Builder.<DomainVisualEntity>of(DomainVisualEntity::new, MobCategory.MISC)
                        .sized(0.1f, 0.1f)   // 几乎无碰撞盒，纯视觉
                        .clientTrackingRange(64)
                        .updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":domain_visual")
        );

    /** 灶·开 火焰箭（笔直慢速，命中爆炸） */
    public static final RegistryObject<EntityType<FlameArrowEntity>> FLAME_ARROW =
        ENTITIES.register("flame_arrow",
                () -> EntityType.Builder.<FlameArrowEntity>of(FlameArrowEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.5f)
                        .clientTrackingRange(64)
                        .updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":flame_arrow")
        );

    /** 赤血操术·超新星 血球（微小血球，0.8s 后爆炸，纯视觉/伤害载体） */
    public static final RegistryObject<EntityType<BloodNovaEntity>> BLOOD_NOVA =
        ENTITIES.register("blood_nova",
                () -> EntityType.Builder.<BloodNovaEntity>of(BloodNovaEntity::new, MobCategory.MISC)
                        .sized(0.2f, 0.2f)   // 直径约 0.2 格
                        .clientTrackingRange(64)
                        .updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":blood_nova")
        );

    // ============================================================
    //  十种影法术 式神（每个继承原版生物，渲染/动画/纹理/碰撞箱全复用原版）
    // ============================================================

    public static final RegistryObject<EntityType<ShikigamiWolf>> SHIKIGAMI_WOLF =
        ENTITIES.register("shikigami_wolf",
                () -> EntityType.Builder.<ShikigamiWolf>of(ShikigamiWolf::new, MobCategory.CREATURE)
                        .sized(0.6f, 0.85f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_wolf"));

    public static final RegistryObject<EntityType<ShikigamiPhantom>> SHIKIGAMI_PHANTOM =
        ENTITIES.register("shikigami_phantom",
                () -> EntityType.Builder.<ShikigamiPhantom>of(ShikigamiPhantom::new, MobCategory.CREATURE)
                        .sized(0.9f, 0.5f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_phantom"));

    public static final RegistryObject<EntityType<ShikigamiSilverfish>> SHIKIGAMI_SILVERFISH =
        ENTITIES.register("shikigami_silverfish",
                () -> EntityType.Builder.<ShikigamiSilverfish>of(ShikigamiSilverfish::new, MobCategory.CREATURE)
                        .sized(0.4f, 0.3f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_silverfish"));

    public static final RegistryObject<EntityType<ShikigamiFrog>> SHIKIGAMI_FROG =
        ENTITIES.register("shikigami_frog",
                () -> EntityType.Builder.<ShikigamiFrog>of(ShikigamiFrog::new, MobCategory.CREATURE)
                        .sized(0.5f, 0.5f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_frog"));

    public static final RegistryObject<EntityType<ShikigamiPig>> SHIKIGAMI_PIG =
        ENTITIES.register("shikigami_pig",
                () -> EntityType.Builder.<ShikigamiPig>of(ShikigamiPig::new, MobCategory.CREATURE)
                        .sized(0.9f, 0.9f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_pig"));

    public static final RegistryObject<EntityType<ShikigamiRabbit>> SHIKIGAMI_RABBIT =
        ENTITIES.register("shikigami_rabbit",
                () -> EntityType.Builder.<ShikigamiRabbit>of(ShikigamiRabbit::new, MobCategory.CREATURE)
                        .sized(0.4f, 0.5f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_rabbit"));

    public static final RegistryObject<EntityType<ShikigamiGoat>> SHIKIGAMI_GOAT =
        ENTITIES.register("shikigami_goat",
                () -> EntityType.Builder.<ShikigamiGoat>of(ShikigamiGoat::new, MobCategory.CREATURE)
                        .sized(0.9f, 1.3f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_goat"));

    public static final RegistryObject<EntityType<ShikigamiCow>> SHIKIGAMI_COW =
        ENTITIES.register("shikigami_cow",
                () -> EntityType.Builder.<ShikigamiCow>of(ShikigamiCow::new, MobCategory.CREATURE)
                        .sized(0.9f, 1.4f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_cow"));

    public static final RegistryObject<EntityType<ShikigamiSheep>> SHIKIGAMI_SHEEP =
        ENTITIES.register("shikigami_sheep",
                () -> EntityType.Builder.<ShikigamiSheep>of(ShikigamiSheep::new, MobCategory.CREATURE)
                        .sized(0.9f, 1.3f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_sheep"));

    public static final RegistryObject<EntityType<ShikigamiIronGolem>> SHIKIGAMI_IRON_GOLEM =
        ENTITIES.register("shikigami_iron_golem",
                () -> EntityType.Builder.<ShikigamiIronGolem>of(ShikigamiIronGolem::new, MobCategory.CREATURE)
                        .sized(1.4f, 2.7f).clientTrackingRange(64).updateInterval(2)
                        .build(TinkersNewlife.MOD_ID + ":shikigami_iron_golem"));

    /** 黑鸟操术 黑鸟（蝙蝠，玩家视角转移载体） */
    public static final RegistryObject<EntityType<com.mofengbaizhi.tinkersnewlife.content.entity.BlackBirdEntity>> BLACK_BIRD =
        ENTITIES.register("black_bird",
                () -> EntityType.Builder.<com.mofengbaizhi.tinkersnewlife.content.entity.BlackBirdEntity>of(
                                com.mofengbaizhi.tinkersnewlife.content.entity.BlackBirdEntity::new, MobCategory.MISC)
                        .sized(0.5f, 0.9f).clientTrackingRange(64).updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":black_bird"));

    /** 投射咒法 玩家虚影（触碰触发增益） */
    public static final RegistryObject<EntityType<com.mofengbaizhi.tinkersnewlife.content.entity.ProjectionPhantomEntity>> PROJECTION_PHANTOM =
        ENTITIES.register("projection_phantom",
                () -> EntityType.Builder.<com.mofengbaizhi.tinkersnewlife.content.entity.ProjectionPhantomEntity>of(
                                com.mofengbaizhi.tinkersnewlife.content.entity.ProjectionPhantomEntity::new, MobCategory.MISC)
                        .sized(0.6f, 1.8f).clientTrackingRange(64).updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":projection_phantom"));

    /** 无下限·苍/赫/茈 咒力球体（类型由实体数据区分：0=苍 1=赫 2=茈） */
    public static final RegistryObject<EntityType<com.mofengbaizhi.tinkersnewlife.content.entity.CursedOrbEntity>> CURSED_ORB =
        ENTITIES.register("cursed_orb",
                () -> EntityType.Builder.<com.mofengbaizhi.tinkersnewlife.content.entity.CursedOrbEntity>of(
                                com.mofengbaizhi.tinkersnewlife.content.entity.CursedOrbEntity::new, MobCategory.MISC)
                        .sized(0.4f, 0.4f).clientTrackingRange(64).updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":cursed_orb"));

    /** 雅各布天梯 法阵（视觉 + 光柱帧伤载体） */
    public static final RegistryObject<EntityType<com.mofengbaizhi.tinkersnewlife.content.entity.JacobLadderEntity>> JACOB_LADDER =
        ENTITIES.register("jacob_ladder",
                () -> EntityType.Builder.<com.mofengbaizhi.tinkersnewlife.content.entity.JacobLadderEntity>of(
                                com.mofengbaizhi.tinkersnewlife.content.entity.JacobLadderEntity::new, MobCategory.MISC)
                        .sized(0.1f, 0.1f).clientTrackingRange(64).updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":jacob_ladder"));

    /** 狱门疆（放置在地面的立方体咒具实体，边长 0.25） */
    public static final RegistryObject<EntityType<com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity>> GOURD_JAIL =
        ENTITIES.register("gourd_jail",
                () -> EntityType.Builder.<com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity>of(
                                com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity::new, MobCategory.MISC)
                        .sized(0.25f, 0.25f).clientTrackingRange(64).updateInterval(1)
                        .build(TinkersNewlife.MOD_ID + ":gourd_jail"));

    /** 中立商人「墨默」（玩家模型渲染，教堂/村钟前满月刷新，售卖咒具/咒术水晶/旧日遗物） */
    public static final RegistryObject<EntityType<com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant>> MOMO_MERCHANT =
        ENTITIES.register("momo_merchant",
                () -> EntityType.Builder.<com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant>of(
                                com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant::new, MobCategory.CREATURE)
                        .sized(0.6f, 1.8f).clientTrackingRange(16).updateInterval(3)
                        .build(TinkersNewlife.MOD_ID + ":momo_merchant"));
}