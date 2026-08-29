package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * 诡厄巫法（Goety）灵魂能量反射桥
 * <p>
 * 不依赖诡厄巫法编译：仅在运行时通过反射调用 {@code com.Polarice3.Goety.utils.SEHelper}
 * 的静态方法（getSESouls / decreaseSESouls），诡厄巫法未安装时所有方法安全返回默认值。
 */
public final class SoulEnergyBridge {

    /** Goety 的灵魂能量工具类 */
    private static final String SEHELPER_CLASS = "com.Polarice3.Goety.utils.SEHelper";

    private static Method getSoulsMethod;
    private static Method decreaseSoulsMethod;
    private static boolean resolved = false;

    private SoulEnergyBridge() {}

    /** 惰性解析：仅当诡厄巫法已加载时反射绑定方法（失败则保持 null，之后每次调用都安全） */
    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            if (!ModList.get().isLoaded("goety")) return;
            Class<?> clazz = Class.forName(SEHELPER_CLASS);
            getSoulsMethod = clazz.getMethod("getSESouls", Player.class);
            decreaseSoulsMethod = clazz.getMethod("decreaseSESouls", Player.class, int.class);
            TinkersNewlife.LOGGER.info("[TinkersNewlife] 诡厄巫法灵魂能量桥接成功 (SEHelper.getSESouls / decreaseSESouls)");
        } catch (Throwable t) {
            getSoulsMethod = null;
            decreaseSoulsMethod = null;
            TinkersNewlife.LOGGER.warn("[TinkersNewlife] 诡厄巫法灵魂能量桥接初始化失败（无诡厄巫法或版本不兼容）: {}", t.toString());
        }
    }

    /** 当前灵魂能量（诡厄巫法未安装/异常时返回 0） */
    public static int getSouls(Player player) {
        resolve();
        try {
            if (getSoulsMethod != null && player != null) {
                return (Integer) getSoulsMethod.invoke(null, player);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /** 消耗灵魂能量；amount<=0 视为成功，灵魂不足/未安装诡厄巫法时返回 false */
    public static boolean decreaseSouls(Player player, int amount) {
        resolve();
        if (amount <= 0) return true;
        try {
            if (decreaseSoulsMethod != null && player != null) {
                return (Boolean) decreaseSoulsMethod.invoke(null, player, amount);
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
