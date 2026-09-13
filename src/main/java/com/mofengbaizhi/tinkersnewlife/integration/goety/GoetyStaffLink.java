package com.mofengbaizhi.tinkersnewlife.integration.goety;

import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 真法杖形态（{@link GoetyStaffItem}）的访问点。
 *
 * <h2>为什么要单独一层（2026-09-13 实测教训）</h2>
 * {@link GoetyStaffItem} {@code implements IWand}（诡厄类型）。**只要某个类的方法体里出现
 * {@code new GoetyStaffItem(...)} / {@code instanceof GoetyStaffItem}，JVM 在链接该类时就会去解析
 * {@code GoetyStaffItem} → 连带解析 {@code IWand}**；诡厄未安装时直接 {@code NoClassDefFoundError}
 * （即使这个分支"逻辑上"永远不会执行——链接先于执行！）。
 *
 * <p>所以：{@link GoetyIntegration} 只做分派、零诡厄类型/零 GoetyStaffItem 引用；
 * 真正引用真法杖类的代码全部收在本类里，而且**只允许在诡厄在场时**被调用
 * （调用点都在 {@code IntegrationLoader.isGoety()} 守卫内）。
 */
final class GoetyStaffLink {

    private GoetyStaffLink() {
    }

    /** 诡厄在场时才可调用：直接构造真法杖 */
    static ModularStaffItem createStaff(Item.Properties properties) {
        return new GoetyStaffItem(properties);
    }

    static boolean isStaff(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof GoetyStaffItem;
    }

    static void mirrorEquippedFocus(ServerPlayer player, ItemStack staff) {
        GoetyStaffItem.mirrorEquippedFocus(player, staff);
    }

    static void refreshSpellAttrs(ServerPlayer player, ItemStack staff) {
        GoetyStaffItem.refreshSpellAttrs(player, staff);
    }

    static void clearSpellAttrs(ServerPlayer player) {
        GoetyStaffItem.clearSpellAttrs(player);
    }
}
