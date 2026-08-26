package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.DreadsteelSlashEntity;
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
}