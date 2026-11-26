package com.mofengbaizhi.tinkersnewlife.client.hud;

/**
 * TNL912 ★免疫窗挡下时的**金色闪烁**（★客户端 ✓）
 *
 * <p>★用户口径：★"你这怎么单次免疫窗内只亮一次啊，
 * 把亮的时间变短然后让每次拦截都亮" ✓
 *
 * <p>★旧写法 ✗：``until = now + 600`` ✓ ⇒ ★每次拦截都把尾巴**往后推** ✗
 * ⇒ ★整个窗口连成一片 ⇒ ★看起来只亮一次 ✓。
 *
 * <p>★现在 ✓：★保存**本次脉冲的起点** ✓
 * ⇒ ★每次拦截都**重新起一次短脉冲** ✓，
 * ★时长短（★200ms ✓），★一次拦截 = 一次亮 ✓。
 */
public final class KtuluBarFlash {

    /** ★单次脉冲时长（ms）✓（★用户要求变短 ✓） */
    private static final long DURATION = 200L;
    private static volatile long start = -10000L;

    private KtuluBarFlash() {
    }

    /** ★每次拦截都调这里 ⇒ ★重新起一次脉冲 ✓ */
    public static void trigger() {
        start = System.currentTimeMillis();
    }

    /** ★当前金色强度 0~1 ✓（★快速衰减 ✓） */
    public static float strength() {
        long el = System.currentTimeMillis() - start;
        if (el < 0L || el >= DURATION) {
            return 0.0F;
        }
        float t = 1.0F - el / (float) DURATION;
        return t * t;                     // ★前段最亮 ✗ 后段快速暗下去 ✓
    }
}
