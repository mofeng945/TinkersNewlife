package com.mofengbaizhi.tinkersnewlife.content;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 墨封白织fufu 的方块实体（§880）：本身不存数据 ✓ 只是给"玩家模型渲染器"当载体 ✓。
 *
 * <p>§908 起多了一件事：<b>抚摸时的挤压动画</b> ——
 * 结构照诡厄巫法的玩偶（{@code PlushieBlockEntity}，已反编译核对 ✓ **只学结构** ✓）：
 * 只存两个 tick 计数 ＋ 一个开关 ✓，渲染器拿 {@link #getAnimation(float)} 去压扁 ✓。
 */
public class FumoMoBlockEntity extends BlockEntity {

    /** 挤压动画总时长（tick）——与诡厄玩偶同为 <b>12 tick = 0.6 秒</b> ✓ */
    public static final int MAX_ANIMATION_TICKS = 12;

    /** 压到最扁用几个 tick（之后都是弹回 ✓ 诡厄也是 4 ✓） */
    public static final int PRESS_TICKS = 4;

    /** 当前 tick 计数 / 上一 tick 的计数（渲染时 lerp 用 ✓ 照诡厄的写法 ✓） */
    private int animationTickCount;
    private int oAnimationTickCount;
    /** 正在播放挤压 ✓ */
    private boolean animating;

    public FumoMoBlockEntity(BlockPos pos, BlockState state) {
        super(FumoMoDoll.FUMO_MO_BE.get(), pos, state);
    }

    /**
     * 右键摸头时调用 —— <b>客户端和服务器都调</b> ✓（与诡厄 {@code PlushieBlock#use} 完全一致的写法 ✓：
     * 客户端本地放动画 ✓，服务器负责播音效 ✓）。
     * <p>⚠ 由此带来一个已知限制（诡厄同样如此 ✓）：别人看不到你摸出来的挤压 ——
     * 因为本方块实体没有做数据同步 ✗。要多人可见得再补 {@code getUpdateTag}/{@code getUpdatePacket} ✓（备忘录 §908 记着 ✓）。
     */
    public void startAnimating() {
        this.animating = true;
    }

    /**
     * 推进动画（由 {@code FumoMoBlock#getTicker} 每 tick 调用 ✓ 客户端也跑 ✓）。
     * <p>逻辑照诡厄玩偶的 {@code PlushieBlockEntity#animation} ✓：
     * 先记下 {@code o = 当前值} ⇒ 超过总时长就停 ✓ ⇒ 在播就 +1 ✓ ⇒ 没在播且还有余量就整体归零 ✓。
     */
    public static void tick(Level level, BlockPos pos, BlockState state, FumoMoBlockEntity be) {
        be.oAnimationTickCount = be.animationTickCount;
        if (be.animationTickCount > MAX_ANIMATION_TICKS) {
            be.animating = false;
        }
        if (be.animating) {
            be.animationTickCount++;
        } else if (be.animationTickCount > 0) {
            be.oAnimationTickCount = 0;
            be.animationTickCount = 0;
        }
    }

    /** 渲染用：0 → 12 的平滑进度（两个 tick 计数之间 lerp ✓ 照诡厄的 {@code getAnimation} ✓） */
    public float getAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.oAnimationTickCount, this.animationTickCount);
    }
}
