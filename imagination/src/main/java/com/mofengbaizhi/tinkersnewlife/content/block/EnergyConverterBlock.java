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
 *   <li>形状 = <b>整格 1×1×1</b>（§608 起是纯方块 ✓ 不再自定义形状 ✓）。
 * </ul>
 */
public class EnergyConverterBlock extends BaseEntityBlock {

    // §608 用户口径「把转化器变成纯方块」⇒ 本类**不再自定义形状/轮廓** ✓
    //   默认 getShape 就是整格 1×1×1 ⇒ 碰撞箱与选中框都跟普通方块一致 ✓
    //   （原来那套"底座 + 机身 + 顶杆"的多段形状跟着旧模型一起退休 ✓ 旧模型文件仍留在仓库里 ✓ 只是没人引用了 ✓）

    /**
     * §574 水平朝向（= 正面指向 ✓）。**用自定义名 `converter_facing`** ✗ 而不是 vanilla 的 `facing` ✓：
     * 动能壳的父类自带一个**同名不同实例**的 `facing` ✗ ⇒ 两边混用会崩（§573 实测 ✓）
     * ⇒ 我们两个壳**统一用这一个自有属性** ✓ blockstate 的键也就确定了 ✓（紫黑块问题一并消掉 ✓）。
     */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.DirectionProperty.create(
                    "converter_facing", net.minecraft.core.Direction.Plane.HORIZONTAL);

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    /** 放置时正面朝向玩家（与原版熔炉同一套写法 ✓） */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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

    /**
     * 服务端每 tick 驱动（收 + 转 + 推 ✓）；客户端不挂 ticker ✗。
     *
     * <p>⚠ §561：<b>不能再用 {@code createTickerHelper}</b> ✗ ——
     * 那个助手要求"两个 {@code BlockEntityType} 的泛型完全对上"✓，
     * 而 §561 之后 {@code ModBlockEntities.ENERGY_CONVERTER} 的类型被刻意写成了
     * {@code BlockEntityType<?>} ✓（因为它装着"普通 / 动能"两支，选哪支收在中立分派类里 ✓）
     * ⇒ javac 直接报"无法将 createTickerHelper 应用到给定类型" ✗。
     * <p>⇒ 自己写一个 <b>先判类型</b> 的 ticker ✓（与 {@code CreateEnergyConverterBlock#getTicker}
     * 那一套逐字同款 ✓）：只有真的拿到普通那一支的方块实体才跑 ✓ 否则什么都不做 ✓。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof EnergyConverterBlockEntity plain) {
                EnergyConverterBlockEntity.serverTick(lvl, pos, st, plain);
            }
        };
    }
}
