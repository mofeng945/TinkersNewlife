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
    private static boolean resolved = false;

    private SoulEnergyBridge() {}

    /** 惰性解析：仅当诡厄巫法已加载时反射绑定方法（失败则保持 null，之后每次调用都安全） */
    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            if (!ModList.get().isLoaded("goety")) return;
            Class<?> helper = Class.forName(SEHELPER);
            getSESoulsMethod = helper.getMethod("getSESouls", Player.class);
            setSESoulsMethod = helper.getMethod("setSESouls", Player.class, int.class);
            Class<?> totemFinder = Class.forName(TOTEM_FINDER);
            findTotemMethod = totemFinder.getMethod("FindTotem", Player.class);
            Class<?> iTotem = Class.forName(ITOTEM);
            totemCurrentSoulsMethod = iTotem.getMethod("currentSouls", ItemStack.class);
            totemSetSoulsMethod = iTotem.getMethod("setSoulsamount", ItemStack.class, int.class);
            TinkersNewlife.LOGGER.info("[TinkersNewlife] 诡厄巫法灵魂能量桥接成功 (SEHelper 能力 + 灵魂图腾 ITotem)");
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

    /** 消耗灵魂能量：优先扣能力，不足部分由图腾兜底；amount<=0 视为成功；总量不足/未安装返回 false */
    public static boolean decreaseSouls(Player player, int amount) {
        resolve();
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
