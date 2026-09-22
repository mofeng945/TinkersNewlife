package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * <b>万用能量转化器</b>（{@code tinkersnewlife:energy_converter}，§557）的方块本体。
 *
 * <ul>
 *   <li>行为全在 {@link EnergyConverterBlockEntity} ✓（本类只管形状 / 注册 / ticker ✓）；</li>
 *   <li><b>没有右键交互</b> ✓（单向、插上就转 ✓ 用户口径里没有界面需求 ✗）；</li>
 *   <li>形状 = 模型那几段的并集（§527 的口径 ✓ 见 {@link #SHAPE}）。</li>
 * </ul>
 */
public class EnergyConverterBlock extends BaseEntityBlock {

    /**
     * 碰撞/轮廓 = {@code models/block/energy_converter.json} 那几段的并集 ✓（照 §527 台座的写法 ✓）：
     * 底台 1~15 × y0~2、机体 3~13 × y2~12、四个角柱 1 格见方 × y0~13、天线 7~9 × y13~16。
     */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            net.minecraft.world.phys.shapes.Shapes.or(
                    net.minecraft.world.phys.shapes.Shapes.box(1.0D / 16.0D, 0.0D, 1.0D / 16.0D,
                            15.0D / 16.0D, 2.0D / 16.0D, 15.0D / 16.0D),
                    net.minecraft.world.phys.shapes.Shapes.box(3.0D / 16.0D, 2.0D / 16.0D, 3.0D / 16.0D,
                            13.0D / 16.0D, 12.0D / 16.0D, 13.0D / 16.0D),
                    net.minecraft.world.phys.shapes.Shapes.box(1.0D / 16.0D, 0.0D, 1.0D / 16.0D,
                            2.0D / 16.0D, 13.0D / 16.0D, 2.0D / 16.0D),
                    net.minecraft.world.phys.shapes.Shapes.box(14.0D / 16.0D, 0.0D, 1.0D / 16.0D,
                            15.0D / 16.0D, 13.0D / 16.0D, 2.0D / 16.0D),
                    net.minecraft.world.phys.shapes.Shapes.box(1.0D / 16.0D, 0.0D, 14.0D / 16.0D,
                            2.0D / 16.0D, 13.0D / 16.0D, 15.0D / 16.0D),
                    net.minecraft.world.phys.shapes.Shapes.box(14.0D / 16.0D, 0.0D, 14.0D / 16.0D,
                            15.0D / 16.0D, 13.0D / 16.0D, 15.0D / 16.0D),
                    net.minecraft.world.phys.shapes.Shapes.box(7.0D / 16.0D, 13.0D / 16.0D, 7.0D / 16.0D,
                            9.0D / 16.0D, 16.0D / 16.0D, 9.0D / 16.0D));

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    public EnergyConverterBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()   // 已加进 minecraft:mineable/pickaxe 标签 ✓
                .mapColor(MapColor.COLOR_CYAN));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;            // 普通方块模型 ✓ 不走 BER ✗
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyConverterBlockEntity(pos, state);
    }

    /** 服务端每 tick 驱动（收 + 转 + 推 ✓）；客户端不挂 ticker ✗ */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.ENERGY_CONVERTER.get(),
                EnergyConverterBlockEntity::serverTick);
    }
}
