package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.DomainBarrierBlock;

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
}