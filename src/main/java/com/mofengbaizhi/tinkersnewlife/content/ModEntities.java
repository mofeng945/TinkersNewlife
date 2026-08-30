package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.BloodNovaEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.DomainVisualEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.DreadsteelSlashEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlameArrowEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordFootEntity;
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
}