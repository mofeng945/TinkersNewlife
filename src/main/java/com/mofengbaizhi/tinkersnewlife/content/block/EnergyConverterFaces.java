package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>转化器的六面角色</b>（§578，用户口径 ✓）。
 *
 * <pre>
 *   正面  = 传动杆输入（Create ✓ §575/§576 已把关）
 *   左面  = AE 线缆
 *   右面  = 通用机械（焦耳）线缆
 *   顶面  = 放抽取器（EE 来源）
 *   背面  = FE 出口
 *   底面  = 万用（什么都接）
 * </pre>
 *
 * <p>朝向由 {@link EnergyConverterBlock#FACING}（`converter_facing` ✓ 我们自己的属性 ✓）决定；
 * 所有判定都**容忍 {@code side == null}**（无面查询 ⇒ 内部/自动化 ✓ 一律允许 ✓）。
 */
public final class EnergyConverterFaces {

    private EnergyConverterFaces() {}

    /** 正面朝向（状态里没有该属性时退回 NORTH ✓ 不崩 ✓） */
    public static Direction front(BlockState state) {
        if (state != null && state.hasProperty(EnergyConverterBlock.FACING)) {
            return state.getValue(EnergyConverterBlock.FACING);
        }
        return Direction.NORTH;
    }

    public static boolean isFront(BlockState s, Direction side) { return side != null && side == front(s); }
    public static boolean isBack(BlockState s, Direction side) { return side != null && side == front(s).getOpposite(); }
    public static boolean isLeft(BlockState s, Direction side) { return side != null && side == front(s).getCounterClockWise(); }
    public static boolean isRight(BlockState s, Direction side) { return side != null && side == front(s).getClockWise(); }
    public static boolean isTop(BlockState s, Direction side) { return side == Direction.UP; }
    public static boolean isBottom(BlockState s, Direction side) { return side == Direction.DOWN; }

    /** FE（Forge Energy）面：**背面出口 + 底面万用** ✓（§578 起：其它面不再暴露/不抽不推 ✓） */
    public static boolean allowsFe(BlockState s, Direction side) {
        return side == null || isBack(s, side) || isBottom(s, side);
    }

    /** 通用机械（J）面：**右面 + 底面** ✓ */
    public static boolean allowsMekanism(BlockState s, Direction side) {
        return side == null || isRight(s, side) || isBottom(s, side);
    }

    /** AE2 面：**左面 + 底面** ✓（§578 暂未接线缆侧的门 ✗ 见备忘录） */
    public static boolean allowsAe2(BlockState s, Direction side) {
        return side == null || isLeft(s, side) || isBottom(s, side);
    }

    /** EE（本模组）面：**顶面（放抽取器）+ 底面** ✓ */
    public static boolean allowsEe(BlockState s, Direction side) {
        return side == null || isTop(s, side) || isBottom(s, side);
    }
}