package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * 诡厄巫法（Goety）灵魂能量反射桥
 * <p>
 * 不依赖诡厄巫法编译：仅在运行时通过反射调用 Goety 的静态方法，诡厄巫法未安装时安全返回默认值。
 * <p>
 * Goety 灵魂能量有<b>两种存储</b>（按自身逻辑互斥）：
 * <ol>
 *   <li>SEActive（阿卡祭坛）模式 → 存在玩家能力：{@code SEHelper.getSESouls / setSESouls}</li>
 *   <li>灵魂图腾模式 → 存在图腾物品：{@code TotemFinder.FindTotem} + {@code ITotem.currentSouls / setSoulsamount}</li>
 * </ol>
 * 读取时合并两者；扣减时优先能力、不足部分由图腾兜底。
 */
public final class SoulEnergyBridge {

    private static final String SEHELPER = "com.Polarice3.Goety.utils.SEHelper";
    private static final String TOTEM_FINDER = "com.Polarice3.Goety.utils.TotemFinder";
    private static final String ITOTEM = "com.Polarice3.Goety.api.items.magic.ITotem";

    private static Method getSESoulsMethod;
    private static Method setSESoulsMethod;
    private static Method findTotemMethod;
    private static Method totemCurrentSoulsMethod;
    private static Method totemSetSoulsMethod;
    private static Method totemMaximumSoulsMethod;
    /** §1071 诡厄自己的"加灵魂"入口 ✓（按玩家模式分流 ✓ 走它的事件与钳制 ✓）——**必须优先用它** ✓ */
    private static Method increaseSoulsMethod;
    /** §1071 玩家是否处于 SEActive（阿卡祭坛）模式 ✓ —— 直接写能力值前必须先问这一句 ✓ */
    private static Method getSEActiveMethod;
    private static boolean resolved = false;

    private SoulEnergyBridge() {}

    /** 惰性解析：仅当诡厄巫法已加载时反射绑定方法（失败则保持 null，之后每次调用都安全） */
    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            // ⭐ 存在性判定统一走 ModList（不再直接摸 ModList，改用联动层常量）
            if (!com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.isGoety()) return;
            Class<?> helper = Class.forName(SEHELPER);
            getSESoulsMethod = helper.getMethod("getSESouls", Player.class);
            setSESoulsMethod = helper.getMethod("setSESouls", Player.class, int.class);
            Class<?> totemFinder = Class.forName(TOTEM_FINDER);
            findTotemMethod = totemFinder.getMethod("FindTotem", Player.class);
            Class<?> iTotem = Class.forName(ITOTEM);
            totemCurrentSoulsMethod = iTotem.getMethod("currentSouls", ItemStack.class);
            totemSetSoulsMethod = iTotem.getMethod("setSoulsamount", ItemStack.class, int.class);
            try {
                totemMaximumSoulsMethod = iTotem.getMethod("maximumSouls", ItemStack.class);
            } catch (Throwable ignored) {
                totemMaximumSoulsMethod = null;
            }
            // §1071：诡厄自己的加灵魂入口（increaseSouls）与"是否 SEActive"判定 —— 都尽量绑上 ✓
            try {
                increaseSoulsMethod = helper.getMethod("increaseSouls", Player.class, int.class);
            } catch (Throwable ignored) {
                increaseSoulsMethod = null;
            }
            try {
                getSEActiveMethod = helper.getMethod("getSEActive", Player.class);
            } catch (Throwable ignored) {
                getSEActiveMethod = null;
            }
            TinkersNewlife.LOGGER.info("[TinkersNewlife] 诡厄巫法灵魂能量桥接成功 (SEHelper 能力 + 灵魂图腾 ITotem)；increaseSouls={} getSEActive={}",
                    increaseSoulsMethod != null, getSEActiveMethod != null);
        } catch (Throwable t) {
            getSESoulsMethod = setSESoulsMethod = findTotemMethod =
                    totemCurrentSoulsMethod = totemSetSoulsMethod = null;
            TinkersNewlife.LOGGER.warn("[TinkersNewlife] 诡厄巫法灵魂能量桥接初始化失败（无诡厄巫法或版本不兼容）: {}", t.toString());
        }
    }

    /** 当前灵魂能量 = 玩家能力（SEActive 模式） + 灵魂图腾（图腾模式）；未安装/异常返回 0 */
    public static int getSouls(Player player) {
        resolve();
        int total = 0;
        try {
            if (getSESoulsMethod != null && player != null) {
                total += (Integer) getSESoulsMethod.invoke(null, player);
            }
        } catch (Throwable ignored) {}
        try {
            ItemStack totem = findTotem(player);
            if (!totem.isEmpty() && totemCurrentSoulsMethod != null) {
                total += (Integer) totemCurrentSoulsMethod.invoke(null, totem);
            }
        } catch (Throwable ignored) {}
        return total;
    }

    /** 灵魂能量上限：仅统计灵魂图腾（ITotem.maximumSouls）；无图腾/未安装返回 0 */
    public static int getMaxSouls(Player player) {
        resolve();
        if (totemMaximumSoulsMethod == null) return 0;
        try {
            ItemStack totem = findTotem(player);
            if (!totem.isEmpty()) {
                return (Integer) totemMaximumSoulsMethod.invoke(null, totem);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * 增加灵魂能量。
     *
     * <p><b>§1071 关键修正（用户实测 bug ✓）</b>：<b>必须优先走诡厄自己的入口
     * {@code SEHelper.increaseSouls(player, amount)}</b> ✓ —— 它按玩家的存储模式正确分流 ✓、
     * 触发它自己的 {@code ChangeSoulEnergyEvent.Gain} ✓、并按自己的规则钳制 ✓。
     *
     * <p>⚠ 旧实现是"反射直接写能力值"（{@code setSESouls(cur + amount)}）✗，踩中了诡厄的一条状态规则 ✗：
     * <pre>
     * // com.Polarice3.Goety.common.events.SoulEnergyEvents:202（每 tick）
     * if (!soulEnergy.getSEActive() &amp;&amp; soulEnergy.getSoulEnergy() &gt; 0 &amp;&amp; !world.isClientSide) {
     *     player.addEffect(new MobEffectInstance(GoetyEffects.SOUL_HUNGER.get(), 60));   // ← 灵魂饥饿
     *     if (player.tickCount % 5 == 0) SEHelper.decreaseSESouls(player, 1);            // ← 每 5 tick 抽 1 点
     * }
     * </pre>
     * ⇒ 在"**非 SEActive 模式**（用灵魂图腾 / 没开阿卡祭坛）"的玩家身上直接写能力值 ✗ ⇒
     * 诡厄看到"没开 SEActive 却灵魂值 &gt; 0" ⇒ 判定为异常 ⇒ **每 tick 挂灵魂饥饿 ✗ 并每 5 tick 抽走 1 点灵魂** ✗
     * （用户症状：拿噬魂武器杀怪 ⇒ 自己上灵魂饥饿 ✗；翻倍时有时无 ✗ —— 刚补发的灵魂马上被抽掉 ✓）。
     *
     * <p>兜底顺序（仅在 {@code increaseSouls} 绑不上时才走 ✗）：① 仅当玩家**确实**处于 SEActive 模式时才直接写能力值 ✓；
     * ② 否则写灵魂图腾（图腾内的数值不会触发上面那条规则 ✓）。
     */
    public static void addSouls(Player player, int amount) {
        if (amount <= 0 || player == null) return;
        resolve();

        // ① 首选：诡厄自己的入口 ✓（模式分流 ✓ 事件 ✓ 钳制 ✓）
        if (increaseSoulsMethod != null) {
            try {
                increaseSoulsMethod.invoke(null, player, amount);
                return;
            } catch (Throwable ignored) {
            }
        }

        // ② 兜底：只有在 SEActive 模式下才允许直接写能力值 ✓（否则会触发灵魂饥饿 ✗ 见上面的类注释）
        try {
            if (getSESoulsMethod != null && setSESoulsMethod != null && isSEActive(player)) {
                int cur = (Integer) getSESoulsMethod.invoke(null, player);
                setSESoulsMethod.invoke(null, player, cur + amount);
                return;
            }
        } catch (Throwable ignored) {
        }

        // ③ 再兜底：写灵魂图腾 ✓（图腾模式 ✓）
        try {
            ItemStack totem = findTotem(player);
            if (!totem.isEmpty() && totemCurrentSoulsMethod != null && totemSetSoulsMethod != null) {
                int have = (Integer) totemCurrentSoulsMethod.invoke(null, totem);
                totemSetSoulsMethod.invoke(null, totem, have + amount);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 玩家是否处于 SEActive（阿卡祭坛）模式 ✓；读不到时**保守返回 false** ✓（宁可走图腾路径 ✓ 也不触发灵魂饥饿 ✗） */
    private static boolean isSEActive(Player player) {
        if (getSEActiveMethod == null) return false;
        try {
            return (Boolean) getSEActiveMethod.invoke(null, player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 消耗灵魂能量：优先扣能力，不足部分由图腾兜底；amount<=0 视为成功；总量不足/未安装返回 false */
    public static boolean decreaseSouls(Player player, int amount) {        resolve();
        if (amount <= 0) return true;
        int remaining = amount;

        // 1) 能力（SEActive 模式）
        try {
            if (getSESoulsMethod != null && setSESoulsMethod != null && player != null) {
                int cap = (Integer) getSESoulsMethod.invoke(null, player);
                if (cap > 0) {
                    int take = Math.min(cap, remaining);
                    setSESoulsMethod.invoke(null, player, cap - take);
                    remaining -= take;
                }
            }
        } catch (Throwable ignored) {}

        // 2) 灵魂图腾（图腾模式）
        if (remaining > 0) {
            try {
                ItemStack totem = findTotem(player);
                if (!totem.isEmpty() && totemCurrentSoulsMethod != null && totemSetSoulsMethod != null) {
                    int have = (Integer) totemCurrentSoulsMethod.invoke(null, totem);
                    if (have < remaining) return false; // 图腾也不够 → 判定领域关闭
                    totemSetSoulsMethod.invoke(null, totem, have - remaining);
                    remaining = 0;
                }
            } catch (Throwable ignored) {
                return false;
            }
        }
        return remaining <= 0;
    }

    private static ItemStack findTotem(Player player) {
        try {
            if (findTotemMethod != null && player != null) {
                return (ItemStack) findTotemMethod.invoke(null, player);
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }
}
