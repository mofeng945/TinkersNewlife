package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * 真法杖形态（content.item.GoetyStaffItem，implements 诡厄 IWand）的反射桥。
 * <p>铁律：GoetyStaffItem 的任何「编译期直接引用」（new / instanceof / 静态调用 / 方法引用）都会让
 * JVM 在类加载验证阶段解析 {@code implements IWand}——诡厄巫法未装（dev 测试环境 / 无 goety 的整合包）
 * 时直接 NoClassDefFoundError（本 mod 构造即崩）。因此所有触碰点必须只经本桥（纯字符串 + 反射），
 * 且先过 {@link #isLoaded()}。本类自身不 import 诡厄/GoetyStaffItem 任何类型。
 */
public final class GoetyStaffBridge {
    private static final String STAFF_CLASS = "com.mofengbaizhi.tinkersnewlife.content.item.GoetyStaffItem";

    private GoetyStaffBridge() {
    }

    /** 诡厄巫法是否作为 mod 加载（运行时守卫） */
    public static boolean isLoaded() {
        try {
            return ModList.get() != null && ModList.get().isLoaded("goety");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 创建模块化魔杖：诡厄在场 → 真法杖形态（原生施法），否则普通形态。ModItems 注册专用。 */
    public static ModularStaffItem createStaff(Item.Properties properties) {
        if (isLoaded()) {
            try {
                Class<?> c = Class.forName(STAFF_CLASS);
                return (ModularStaffItem) c.getConstructor(Item.Properties.class).newInstance(properties);
            } catch (Throwable t) {
                // 反射失败（类不存在/构造异常）→ 退回普通形态，绝不让注册崩溃
            }
        }
        return new ModularStaffItem(properties);
    }

    /** 物品是否为真法杖形态（替代 instanceof GoetyStaffItem） */
    public static boolean isGoetyStaffItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isLoaded()) return false;
        try {
            return Class.forName(STAFF_CLASS).isInstance(stack.getItem());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 把「装备中聚晶」镜像写入真法杖本体槽（GoetyStaffItem.mirrorEquippedFocus） */
    public static void mirrorEquippedFocus(ServerPlayer player, ItemStack staff) {
        invoke("mirrorEquippedFocus", new Class<?>[]{ServerPlayer.class, ItemStack.class}, player, staff);
    }

    /** 按真法杖强度刷新持有者诡厄 Spell 属性（GoetyStaffItem.refreshSpellAttrs） */
    public static void refreshSpellAttrs(ServerPlayer player, ItemStack staff) {
        invoke("refreshSpellAttrs", new Class<?>[]{ServerPlayer.class, ItemStack.class}, player, staff);
    }

    /** 清空真法杖给持有者上的 Spell 属性（GoetyStaffItem.clearSpellAttrs） */
    public static void clearSpellAttrs(ServerPlayer player) {
        invoke("clearSpellAttrs", new Class<?>[]{ServerPlayer.class}, player);
    }

    private static void invoke(String method, Class<?>[] types, Object... args) {
        if (!isLoaded()) return;
        try {
            Class.forName(STAFF_CLASS).getMethod(method, types).invoke(null, args);
        } catch (Throwable t) {
            // 真法杖形态缺失/方法签名变化：静默跳过（普通形态下无 Spell 属性概念）
        }
    }
}
