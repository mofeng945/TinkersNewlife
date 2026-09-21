package com.mofengbaizhi.tinkersnewlife.content.handler;

import net.minecraft.world.entity.player.Player;

/**
 * 墨默**好感度**（用户口径 ✓ 备忘录 §455/§456）。
 *
 * <ul>
 *   <li>**每个玩家各自记** ✓ 存玩家持久数据（键 {@code tn_momo_favor} ✓）；</li>
 *   <li>范围 **−50 ~ +50** ✓（下限扣不穿 ✓ 上限交易 +1 封顶 ✓）；</li>
 *   <li>成功交易 1 次 **+1** ✓；**攻击墨默 −1** ✓；</li>
 *   <li>**负数**：不能对话 ✗ 不能雇佣 ✗ 并且**涨价** ✓。</li>
 * </ul>
 */
public final class MomoFavor {

    private MomoFavor() {}

    public static final int MIN = -50;
    public static final int MAX = 50;
    private static final String KEY = "tn_momo_favor";

    public static int get(Player player) {
        return player == null ? 0 : player.getPersistentData().getInt(KEY);
    }

    public static void set(Player player, int value) {
        if (player == null) return;
        player.getPersistentData().putInt(KEY, Math.max(MIN, Math.min(MAX, value)));
    }

    public static void add(Player player, int delta) {
        if (delta != 0) set(player, get(player) + delta);
    }

    /** 能不能对话 ✓（负极不行 ✓ 用户口径） */
    public static boolean canTalk(Player player) {
        return get(player) >= 0;
    }

    /** 能不能雇佣 ✓（负极不行 ✓ 用户口径） */
    public static boolean canHire(Player player) {
        return get(player) >= 0;
    }

    /**
     * 价格倍率（好坏感全在这一条里 ✓）：
     * 0 好感 = **1.00** ✓；满好感 +50 = **0.70（七折 ✓）**；
     * 负好感每点 **+1.5%** 涨价 ⇒ −50 时 **1.75 倍** ✓（用户口径"负数会涨价" ✓）。
     */
    public static double priceFactor(Player player) {
        int f = get(player);
        return f >= 0 ? 1.0D - 0.3D * (f / (double) MAX) : 1.0D + 0.015D * (-f);
    }

    /** 交易成功一次 ✓ +1（封顶 ✓） */
    public static void onTrade(Player player) {
        add(player, +1);
    }

    /** 攻击墨默一次 ✓ −1（下限 ✓） */
    public static void onHit(Player player) {
        add(player, -1);
    }
}
