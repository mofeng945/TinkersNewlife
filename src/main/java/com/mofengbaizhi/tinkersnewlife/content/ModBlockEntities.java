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

    // ============================================================
    //  §557 EE 网络（抽取方块 + 万用能量转化器）
    // ============================================================

    /**
     * EE 抽取方块：抽相邻 EE → 自己的缓冲 → 推给相邻 EE 容器（见 {@code EeExtractorBlockEntity} ✓）。
     * <p>ticker 挂在方块上（{@code EeExtractorBlock#getTicker}）✓ 只跑服务端 ✓。
     */
    public static final RegistryObject<BlockEntityType<com.mofengbaizhi.tinkersnewlife.content.block.EeExtractorBlockEntity>>
            EE_EXTRACTOR =
            BLOCK_ENTITIES.register("ee_extractor",
                    () -> BlockEntityType.Builder.of(
                                    com.mofengbaizhi.tinkersnewlife.content.block.EeExtractorBlockEntity::new,
                                    ModBlocks.EE_EXTRACTOR.get())
                            .build(null));

    /**
     * 万用能量转化器：各种能量 ⇒ FE（单向 ✓ 见 {@code EnergyConverterBlockEntity} ✓）。
     * <p>ticker 挂在方块上（{@code EnergyConverterBlock#getTicker}）✓ 只跑服务端 ✓。
     * <h3>§559：装了 Create ⇒ 换成动能方块实体</h3>
     * 见 {@link #CREATE_ENERGY_CONVERTER} ✓。
     */
    public static final RegistryObject<BlockEntityType<com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlockEntity>>
            ENERGY_CONVERTER =
            BLOCK_ENTITIES.register("energy_converter",
                    () -> BlockEntityType.Builder.of(
                                    com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlockEntity::new,
                                    ModBlocks.ENERGY_CONVERTER.get())
                            .build(null));

    /**
     * <b>转化器的"动能方块实体"版本</b>（§559）：<b>只有装了 Create 时才注册</b> ✓，
     * 且与 {@link #ENERGY_CONVERTER} <b>同名</b>（二选一 ✓ 见 {@code ModBlocks} 的注释 ✓）。
     * <p>⚠ 类型参数用 {@code KineticBlockEntity} 的超类 {@code BlockEntity}（＝ {@code <?>}）✓ ——
     * 这样本文件**不必**写出 Create 的类型 ✓（真正的 Create 类型只在
     * {@code CreateEnergyConverterBlockEntity} 里出现 ✓）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final RegistryObject<BlockEntityType<?>> CREATE_ENERGY_CONVERTER =
            com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterModBridges.hasCreate()
                    ? BLOCK_ENTITIES.register("energy_converter",
                            () -> BlockEntityType.Builder.of(
                                            com.mofengbaizhi.tinkersnewlife.content.block
                                                    .CreateEnergyConverterBlockEntity::new,
                                            ModBlocks.CREATE_ENERGY_CONVERTER.get())
                                    .build(null))
                    : null;
}
