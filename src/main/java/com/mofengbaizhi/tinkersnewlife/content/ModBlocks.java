package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.BloodRedstoneBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.DomainBarrierBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultVisualBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.GourdJailVisualBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TinkersNewlife.MOD_ID);

    /**
     * 呪蔵：可放置的咒力容器（容量 10 万咒力，放置即绑定使用者，无法破坏，
     * 使用者空手潜行右键可回收并保留其中咒力）。
     */
    public static final RegistryObject<Block> CURSE_VAULT = BLOCKS.register("curse_vault",
            com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlock::new);

    // 格赫罗斯矿石
    public static final RegistryObject<Block> GHELOTH_ORE = BLOCKS.register("gheloth_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .mapColor(MapColor.COLOR_RED)
            ));

    // 霜冻冰块（龙霜钢特性用）- 不会融化成水，一段时间后自动消失
    public static final RegistryObject<Block> FROST_ICE = BLOCKS.register("frost_ice",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.ICE)
                    .mapColor(MapColor.ICE)
                    .strength(0.5f)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
            ) {
                @Override
                public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
                    // 计划刻到达时自动移除冰块
                    level.removeBlock(pos, false);
                }
            }
    );

    /** 领域阻挡方块（隐形物理墙，仅由领域系统生成/移除，无物品形态） */
    public static final RegistryObject<Block> DOMAIN_BARRIER = BLOCKS.register("domain_barrier",
            () -> new DomainBarrierBlock());

    /** 血液（红石粉外观）：伏魔御厨子斩击留下的血泊，无战利品表 → 挖掉无掉落，无物品形态 */
    public static final RegistryObject<Block> BLOOD_REDSTONE = BLOCKS.register("blood_redstone",
            () -> new BloodRedstoneBlock());

    /** 狱门疆视觉方块：仅作为狱门疆实体的模型渲染载体（方块模型+贴图），无物品形态、不会真实生成 */
    public static final RegistryObject<Block> GOURD_JAIL_VISUAL = BLOCKS.register("gourd_jail_visual",
            () -> new GourdJailVisualBlock());

    // ===== 呪蔵笼内能量（纯渲染载体，见 client/renderer/CurseVaultRenderer）=====
    /** 呪蔵主能量团模型载体：blockstate → tinkersnewlife:block/curse_vault_core */
    public static final RegistryObject<Block> CURSE_VAULT_CORE_VISUAL = BLOCKS.register("curse_vault_core_visual",
            CurseVaultVisualBlock::new);

    /** 呪蔵环绕火花模型载体：blockstate → tinkersnewlife:block/curse_vault_spark */
    public static final RegistryObject<Block> CURSE_VAULT_SPARK_VISUAL = BLOCKS.register("curse_vault_spark_visual",
            CurseVaultVisualBlock::new);

    // ============================================================
    //  纯合金材料「物品形态」的储存块（锭 ×9 ↔ 块）
    //  与 ModItems 里的三个锭（魔金锭 / 圣灵锭 / 源钻合金锭）配对，
    //  走的是原版"9 锭 ↔ 1 块"的常规口径 ✓
    //  物品（BlockItem）在 ModItems 里注册；方块这里只登记本体 ✓
    //
    //  ⚠⚠ 命名必须避开 `<流体名>_block` ✗ —— {@code FluidRegistrar} 给**每支流体**注册的
    //  液体方块就叫 `name + "_block"`，而「神圣灵液」这支流体的名字是 {@code holy_spirit}
    //  → 它的液体方块**已经**叫 {@code tinkersnewlife:holy_spirit_block} ✗。
    //  第一版我把储存块也叫 holy_spirit_block，于是同一个注册表里两个 DeferredRegister
    //  抢同一个名字 → 后者把前者覆盖成 `Block{minecraft:air}`（"Override did not have an
    //  associated owner object"）→ 紧接着 `ForgeRegistry.sync` 的 ID 对不上 → **启动即崩** ✗。
    //  所以三个储存块统一用 `_storage_block` 后缀，跟液体方块彻底分开 ✓
    //  （magic_gold 的流体叫 magic_gold_essence、origin_alloy 的叫 origin_polymer，
    //   本来不撞；统一后缀是为了以后加流体时不会再踩同一个坑 ✓）
    // ============================================================

    /** 魔金块（储存块，不是「魔金精华」的液体方块——那个叫 magic_gold_essence_block） */
    public static final RegistryObject<Block> MAGIC_GOLD_BLOCK =
            metalBlock("magic_gold_storage_block", MapColor.COLOR_ORANGE);

    /** 圣灵块（储存块，不是「神圣灵液」的液体方块——那个叫 holy_spirit_block） */
    public static final RegistryObject<Block> HOLY_SPIRIT_BLOCK =
            metalBlock("holy_spirit_storage_block", MapColor.COLOR_YELLOW);

    /** 源钻合金块（储存块，不是「源流聚合物」的液体方块——那个叫 origin_polymer_block） */
    public static final RegistryObject<Block> ORIGIN_ALLOY_BLOCK =
            metalBlock("origin_alloy_storage_block", MapColor.COLOR_PURPLE);

    // ============================================================
    //  古老者水晶（§519 P1）
    // ============================================================

    /**
     * 古老者水晶矿石：<b>只在 {@code minecraft:deep_dark} 生成</b>
     * （见 {@code worldgen/configured_feature} + {@code placed_feature} + {@code forge/biome_modifier}）✓
     * <p>数值：深板岩底（硬度 4.5 / 抗爆 3.0 / 深板岩音效 ✓ 与原版深板岩矿石同档），
     * <b>需要铁镐</b>（{@code minecraft:needs_iron_tool} ✓ 深板岩档），
     * 采掘掉<b>水晶</b>（时运影响数量、精准采集掉原矿 ✓ 见 loot_tables/blocks/elder_crystal_ore.json）。
     */
    public static final RegistryObject<Block> ELDER_CRYSTAL_ORE = BLOCKS.register("elder_crystal_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops()
                    .mapColor(MapColor.DEEPSLATE)
            ));

    /**
     * 古老者水晶方块：容量 4000 EE 的水晶方块（= 4 个水晶）。
     * <p>挖掉掉自己、EE 一起带走 ✓（原版 BlockEntityTag 机制 ✓ 见 {@link com.mofengbaizhi.tinkersnewlife.content.block.ElderCrystalBlock}）。
     */
    public static final RegistryObject<Block> ELDER_CRYSTAL_BLOCK = BLOCKS.register("elder_crystal_block",
            com.mofengbaizhi.tinkersnewlife.content.block.ElderCrystalBlock::new);

    /**
     * <b>魔力台座</b>（§523）：把古老者水晶放上去就自动吸收环境能量充能
     * （规律 = <b>亮度越低越快</b> ✓ 见 {@code content/energy/LightLevelEnergySource}）。
     * <p>方块本体 + 方块实体在 {@code content/block/ElderManaPedestalBlock(Entity)}；
     * 贴图/模型/配方/战利品表/手册条目与它配套 ✓。
     */
    public static final RegistryObject<Block> ELDER_MANA_PEDESTAL = BLOCKS.register("elder_mana_pedestal",
            com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalBlock::new);

    // ============================================================
    //  §557 EE 网络：抽取方块 + 万用能量转化器
    // ============================================================

    /**
     * <b>EE 抽取方块</b>（§557）：把相邻方块（台座缓存 / 水晶方块 / 别的 EE 容器）里的 EE
     * 抽进自己的缓冲，再<b>主动推</b>给相邻的 EE 容器 ✓（push 模式 ✓）。
     * <p>方块实体在 {@code content/block/EeExtractorBlockEntity}；
     * 接口是 {@code content/energy/EeStorage} ✓ 速率/缓冲全在 {@code [ee_network]} 配置段 ✓。
     */
    public static final RegistryObject<Block> EE_EXTRACTOR = BLOCKS.register("ee_extractor",
            com.mofengbaizhi.tinkersnewlife.content.block.EeExtractorBlock::new);

    /**
     * <b>万用能量转化器</b>（§557）：<b>单向</b>把各种能量折成 <b>FE</b> 推给相邻方块 ✓。
     * <p>输入：相邻 EE（{@code EeStorage} ✓）+ 相邻 Forge Energy（含 RF/Tesla/μI/FF 这些 1:1 别名 ✓）
     * + 四家模组的能量（通用机械 J / Create RPM / IC2 EU / AE2 AE ✓ 反射软依赖 ✓）。
     * <p><b>不做</b> FE ⇒ EE 的反向 ✗（用户口径明确单向 ✓）。
     */
    public static final RegistryObject<Block> ENERGY_CONVERTER = BLOCKS.register("energy_converter",
            com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlock::new);

    /** 金属储存块的统一属性（对齐原版铁块：5.0 硬度 / 6.0 抗爆 / 需要正确工具 / 金属音效） */
    private static RegistryObject<Block> metalBlock(String name, MapColor color) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of()
                .strength(5.0f, 6.0f)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .mapColor(color)
        ));
    }
}