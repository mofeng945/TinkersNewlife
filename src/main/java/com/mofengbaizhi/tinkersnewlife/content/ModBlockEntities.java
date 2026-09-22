package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方块实体注册表（呪蔵 / 古老者水晶方块） */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TinkersNewlife.MOD_ID);

    public static final RegistryObject<BlockEntityType<CurseVaultBlockEntity>> CURSE_VAULT =
            BLOCK_ENTITIES.register("curse_vault",
                    () -> BlockEntityType.Builder.of(CurseVaultBlockEntity::new, ModBlocks.CURSE_VAULT.get()).build(null));

    /** 古老者水晶方块：存 EE（容量 4000）；本轮只留 absorb/Query 接口（台座 P2 再接） */
    public static final RegistryObject<BlockEntityType<com.mofengbaizhi.tinkersnewlife.content.block.ElderCrystalBlockEntity>>
            ELDER_CRYSTAL_BLOCK =
            BLOCK_ENTITIES.register("elder_crystal_block",
                    () -> BlockEntityType.Builder.of(
                                    com.mofengbaizhi.tinkersnewlife.content.block.ElderCrystalBlockEntity::new,
                                    ModBlocks.ELDER_CRYSTAL_BLOCK.get())
                            .build(null));
}
