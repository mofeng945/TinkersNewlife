package com.mofengbaizhi.tinkersnewlife.client.handler;

/**
 * 「不可名状」的客户端观感强度（0~1），带<b>平滑过渡</b>。
 *
 * <p>为什么要平滑：效果是服务端给的、随时可能被解除，如果强度直接跟着"有没有效果"跳变，
 * 花屏和低语会在一帧内"啪"地消失。这里保存一个目标值（每 tick 由
 * {@code UnnameableClientHandler} 按效果等级写入），渲染时每帧向目标趋近一点，
 * 于是<b>获得效果是渐渐浮现、效果结束是渐渐褪去</b>，同时也天然避免了"残留"。
 *
 * <p>写法参考了他人的实现（{@code DecayClientAmbience} / {@code UnnameableClientAmbience} 那一套）：
 * 目标值与当前值分离、渲染线程只读 {@link #current()}。
 *
 * <p>⚠ 两个值都在客户端主线程上被读写（tick 与渲染同线程），所以不加锁。
 */
public final class UnnameableAmbience {

    /** 低于这个强度就当成"没有效果"，不画任何东西（避免残留一层极淡的影子） */
    public static final float MIN_LEVEL = 0.02F;

    /** 每帧向目标趋近的比例（越大过渡越快；0.08 ≈ 半秒左右到位） */
    private static final float LERP = 0.08F;

    /** 目标强度（是否有不可名状 / 等级多少） */
    private static float target = 0.0F;
    /** 当前显示用的强度（每帧向 target 趋近） */
    private static float current = 0.0F;

    private UnnameableAmbience() {
    }

    /** 每 tick 由效果状态写入目标强度（0~1），自动钳制 */
    public static void setTarget(float value) {
        target = Math.max(0.0F, Math.min(1.0F, value));
    }

    /** 渲染线程每帧调用一次：推进平滑过渡并返回当前强度（0~1） */
    public static float current() {
        float diff = target - current;
        if (diff * diff < 1e-6F) {
            current = target;
        } else {
            current += diff * LERP;
        }
        return current;
    }

    /** 立刻归零：退出世界 / 断线 / 死亡重生 / 换维度时调用，保证不会有残留 */
    public static void reset() {
        target = 0.0F;
        current = 0.0F;
    }
}
