package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方块实体注册表（呪蔵） */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TinkersNewlife.MOD_ID);

    public static final RegistryObject<BlockEntityType<CurseVaultBlockEntity>> CURSE_VAULT =
            BLOCK_ENTITIES.register("curse_vault",
                    () -> BlockEntityType.Builder.of(CurseVaultBlockEntity::new, ModBlocks.CURSE_VAULT.get()).build(null));
}
