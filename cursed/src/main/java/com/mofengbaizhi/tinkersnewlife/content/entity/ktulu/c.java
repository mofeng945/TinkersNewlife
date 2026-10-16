package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import net.minecraft.world.entity.Mob;

/**
 * TNL862 ★多个钓鱼入口（★轮换 ✓）
 *
 * <p>★用户反馈：★外挂作者可能不扫 {@code getMomoHealth} ✗，
 * 而是直接 Hook {@code EntityLivingBase#setHealth} ✗ ⇒
 * ★那就**多摆几个看起来像真的入口**，并且**随机轮换** ✓。
 *
 * <p>★它们全部只调空实现 ✓（★永远返回 0 ✗）。
 */
public final class c {

    private c() {
    }

    /** ★鱼饵 1 ✓ */
    public static float getMomoHealth(Mob momo) {
        return 0.0F;
    }

    /** ★鱼饵 2 ✓ */
    public static float getPlayerLife(Mob momo) {
        return 0.0F;
    }

    /** ★鱼饵 3 ✓ */
    public static boolean checkHP(Mob momo) {
        return false;
    }

    /** ★鱼饵 4 ✓ */
    public static int queryVitals(Mob momo) {
        return 0;
    }

    /** ★随机轮换调用一个（★结果丢弃 ✓） */
    public static void rotate(Mob momo) {
        try {
            switch ((int) (Math.random() * 4.0D)) {
                case 0 -> getMomoHealth(momo);
                case 1 -> getPlayerLife(momo);
                case 2 -> checkHP(momo);
                default -> queryVitals(momo);
            }
        } catch (Throwable ignored) {
        }
    }
}
