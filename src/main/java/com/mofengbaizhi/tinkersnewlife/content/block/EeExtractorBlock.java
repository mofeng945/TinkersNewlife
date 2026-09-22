package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * <b>EE 抽取方块</b>（{@code tinkersnewlife:ee_extractor}，§557）的方块本体。
 *
 * <ul>
 *   <li>行为全在 {@link EeExtractorBlockEntity} ✓（本类只有形状 / 注册 / ticker 三件事 ✓）；</li>
 *   <li><b>没有右键交互</b> ✓（插上就工作，用户口径里的"主动"✓ 不需要开关界面 ✗）；</li>
 *   <li>形状 = 模型那几段的并集（§527 的口径 ✓ 见 {@link #SHAPE}）。</li>
 * </ul>
 */
public class EeExtractorBlock extends BaseEntityBlock {

    /**
     * 碰撞/轮廓 = {@code models/block/ee_extractor.json} 那几段的并集 ✓（照 §527 台座的写法 ✓）：
     * 基座 2~14 × y0~2、机体 4~12 × y2~10（x/z 都是 2~14）、顶部结晶 5~11 × y10~14。
     * <p>⚠ 四角那四根"腿"（1 格见方）<b>没有</b>进形状 ✗ —— 它们纯粹是贴图上的装饰，
     * 让碰撞箱贴合"能站上去的那块实体"就够了 ✓（把腿也算进来只会让玩家在方块缝里卡住 ✗）。
     */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            net.minecraft.world.phys.shapes.Shapes.box(2.0D / 16.0D, 0.0D, 2.0D / 16.0D,
                    14.0D / 16.0D, 2.0D / 16.0D, 14.0D / 16.0D),
            net.minecraft.world.phys.shapes.Shapes.box(4.0D / 16.0D, 2.0D / 16.0D, 4.0D / 16.0D,
                    12.0D / 16.0D, 10.0D / 16.0D, 12.0D / 16.0D),
            net.minecraft.world.phys.shapes.Shapes.box(5.0D / 16.0D, 10.0D / 16.0D, 5.0D / 16.0D,
                    11.0D / 16.0D, 14.0D / 16.0D, 11.0D / 16.0D));

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    public EeExtractorBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .sound(SoundType.COPPER)
                .requiresCorrectToolForDrops()   // 已加进 minecraft:mineable/pickaxe 标签 ✓
                .mapColor(MapColor.COLOR_PURPLE));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;            // 普通方块模型 ✓ 不走 BER ✗
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EeExtractorBlockEntity(pos, state);
    }

    /** 服务端每 tick 驱动（抽 + 推 ✓）；客户端不挂 ticker ✗（数据是服务端权威的 ✓） */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.EE_EXTRACTOR.get(),
                EeExtractorBlockEntity::serverTick);
    }
}
