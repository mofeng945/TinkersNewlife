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

    /**
     * §508 **别人眼中的好感度**（配合双向认知阻碍面具：[用户口径]「善恶值和好感度都将对外视为 0（自己看不是 0）」✓）。
     *
     * @param observer 观察者：**自己 / 墨默的交易界面（自己看）**传 {@code null} 或 {@code target} ⇒ 真实值 ✓；
     *                 第三方（别的玩家、指令、NPC…）传它自己 ⇒ 戴面具者视为 **0** ✓
     */
    public static int favorAsSeenBy(Player target, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity observer) {
        if (target == null) return 0;
        if (observer == null || observer == target) return get(target);
        if (com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem.isWorn(target)) return 0;
        return get(target);
    }

    /**
     * §509 **墨默眼中的好感度**：戴着双向认知阻碍面具 ⇒ **她也认不出你** ⇒ 一律按 **0**（中立价）✓
     * （用户口径：「墨默也认不出」✓）。用在：服务端定价 + 发给客户端的界面好感快照 ✓
     * —— 两边都用同一个值 ⇒ **界面显示与实际扣费永远一致** ✓。
     */
    public static int favorAsSeenByMomo(Player target) {
        if (target == null) return 0;
        return com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem.isWorn(target) ? 0 : get(target);
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
        return priceFactor(get(player));
    }

    /** 同上，但直接吃一个好感度数值 ✓（客户端从同步包里拿到的那份也能算 ✓） */
    public static double priceFactor(int favor) {
        int f = Math.max(MIN, Math.min(MAX, favor));
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
