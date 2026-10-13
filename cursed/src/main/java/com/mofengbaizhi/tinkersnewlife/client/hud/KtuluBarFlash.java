package com.mofengbaizhi.tinkersnewlife.client.hud;

/** TNL907 \u2605\u514d\u75ab\u7a97\u6321\u4e0b\u65f6\u7684**\u91d1\u8272\u95ea\u70c1**\u72b6\u6001\uff08\u2605\u5ba2\u6237\u7aef \u2713\uff09 */
public final class KtuluBarFlash {

    /** \u2605\u95ea\u591a\u4e45\uff08ms\uff09\u2713 */
    private static final long DURATION = 600L;
    private static volatile long until = 0L;

    private KtuluBarFlash() {
    }

    public static void trigger() {
        until = System.currentTimeMillis() + DURATION;
    }

    /** \u2605\u5f53\u524d\u91d1\u8272\u5f3a\u5ea6 0~1 \u2713 */
    public static float strength() {
        long left = until - System.currentTimeMillis();
        if (left <= 0L) {
            return 0.0F;
        }
        float t = left / (float) DURATION;
        return t < 0.35F ? (t / 0.35F) * 0.6F + 0.4F : t * 0.55F + 0.45F;
    }
}
