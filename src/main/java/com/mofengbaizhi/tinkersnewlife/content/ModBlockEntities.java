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

    /** §607 台座**上段**的方块实体（只把物品能力转发给下方台座 ✓ 见 {@code ElderManaPedestalTopBlockEntity} ✓） */
    public static final RegistryObject<BlockEntityType<com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalTopBlockEntity>>
            ELDER_MANA_PEDESTAL_TOP =
            BLOCK_ENTITIES.register("elder_mana_pedestal_top",
                    () -> BlockEntityType.Builder.of(
                                    com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalTopBlockEntity::new,
                                    ModBlocks.ELDER_MANA_PEDESTAL_TOP.get())
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
     * <h3>§559：装了 Create ⇒ 换成动能方块实体；§561：**注册只允许一次** ✗</h3>
     * ⚠⚠ <b>§561 崩溃教训（与 {@code ModBlocks} 那处同一个错 ✗）</b>：§559 我在这里也写了<b>两条</b>
     * {@code BLOCK_ENTITIES.register("energy_converter", …)} ✗ ⇒ 装了 Create 的实例上两条都执行 ✗
     * ⇒ {@code Duplicate registration energy_converter} ✗（崩在方块那一条 ⇒ 当时还没走到这里 ✓
     * 但这是**同一个 bug**，必须一起修 ✓）。
     * <p>⇒ 现在<b>只有一条</b> register ✓，选哪一支收进
     * {@link com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterModBridges#createBlockEntityType()} ✓
     * （那个类只 import Forge / 原版 ✓ 第一句就 {@code ModList.isLoaded("create")} ✓）。
     * <p>⚠ 这里**不能**用 {@code createTickerHelper}（那要求 {@code BlockEntityType<T>} 的泛型对上 ✗）；
     * ticker 已经挂在**方块/方块实体自己**身上（普通支在 {@code EnergyConverterBlock#getTicker} ✓、
     * Create 支在 {@code KineticBlockEntity#tick} 的覆写里 ✓）⇒ 本注册只需要类型本身 ✓。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final RegistryObject<BlockEntityType<?>> ENERGY_CONVERTER =
            BLOCK_ENTITIES.register("energy_converter",
                    com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterModBridges::createBlockEntityType);
}
