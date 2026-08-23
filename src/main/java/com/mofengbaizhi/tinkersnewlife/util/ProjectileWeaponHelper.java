package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * 弹射物武器获取工具类
 * 统一处理从各种弹射物（三叉戟、匠魂标枪、箭矢等）获取其发射武器的逻辑
 */
public final class ProjectileWeaponHelper {

    private ProjectileWeaponHelper() {}

    /**
     * 从弹射物获取对应的发射武器
     * <p>优先级：
     * <ol>
     *   <li>尝试调用弹射物的 {@code getPickupItem()} 方法（三叉戟、匠魂标枪等）</li>
     *   <li>尝试调用弹射物的 {@code getItem()} 方法（部分自定义弹射物）</li>
     *   <li>回退到玩家主手</li>
     *   <li>回退到玩家副手</li>
     * </ol>
     *
     * @param projectile 弹射物实体
     * @param shooter    发射者（玩家）
     * @return 对应的武器物品栈，若无法获取则返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack getProjectileWeapon(Projectile projectile, Player shooter) {
        // 1. 尝试从弹射物本身获取物品（三叉戟、匠魂标枪等）
        try {
            Method method = projectile.getClass().getMethod("getPickupItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}

        try {
            Method method = projectile.getClass().getMethod("getItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}

        // 2. 若弹射物无物品，则从玩家主手获取（弓/弩）
        ItemStack mainHand = shooter.getMainHandItem();
        if (!mainHand.isEmpty()) {
            return mainHand;
        }

        // 3. 若主手为空，尝试副手
        ItemStack offHand = shooter.getOffhandItem();
        if (!offHand.isEmpty()) {
            return offHand;
        }

        return ItemStack.EMPTY;
    }
}