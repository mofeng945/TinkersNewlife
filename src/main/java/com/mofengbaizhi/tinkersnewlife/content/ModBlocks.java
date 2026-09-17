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