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

    /** 古老者水晶方块：存 EE（容量 4000）；absorb/Query 接口现已被魔力台座用上（§523） */
    public static final RegistryObject<BlockEntityType<com.mofengbaizhi.tinkersnewlife.content.block.ElderCrystalBlockEntity>>
            ELDER_CRYSTAL_BLOCK =
            BLOCK_ENTITIES.register("elder_crystal_block",
                    () -> BlockEntityType.Builder.of(
                                    com.mofengbaizhi.tinkersnewlife.content.block.ElderCrystalBlockEntity::new,
                                    ModBlocks.ELDER_CRYSTAL_BLOCK.get())
                            .build(null));

    /**
     * 魔力台座（§523）：存"台座上那颗水晶"（ItemStack）+ 每秒从环境来源取 EE 灌进去。
     * <p>ticker 挂在方块上（{@code ElderManaPedestalBlock#getTicker}）✓ 只跑服务端 ✓。
     */
    public static final RegistryObject<BlockEntityType<com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalBlockEntity>>
            ELDER_MANA_PEDESTAL =
            BLOCK_ENTITIES.register("elder_mana_pedestal",
                    () -> BlockEntityType.Builder.of(
                                    com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalBlockEntity::new,
                                    ModBlocks.ELDER_MANA_PEDESTAL.get())
                            .build(null));
}
