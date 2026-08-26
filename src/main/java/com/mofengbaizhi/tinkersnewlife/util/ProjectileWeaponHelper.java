package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.item.IModifiable;

import java.lang.reflect.Method;

/**
 * 弹射物武器获取工具类
 * 统一处理从各种弹射物（三叉戟、匠魂标枪、箭矢等）获取其发射武器的逻辑
 */
public final class ProjectileWeaponHelper {

    /** 箭矢持久数据中记录发射它的弓的键（由 ArrowBowHandler 写入） */
    public static final String KEY_FIRING_BOW = "tinkersnewlife_firing_bow";

    private ProjectileWeaponHelper() {}

    /**
     * 从弹射物获取对应的发射武器
     * <p>优先级：
     * <ol>
     *   <li>箭矢上绑定的发射弓（发射时由 {@code ArrowBowHandler} 写入箭矢持久数据）</li>
     *   <li>弹射物自身的 {@code getPickupItem()} —— 仅当返回的是匠魂工具（标枪、匠魂箭等）时采用</li>
     *   <li>弹射物自身的 {@code getItem()}（部分自定义弹射物）—— 同上仅当是匠魂工具</li>
     *   <li>回退到玩家主手（弓/弩）</li>
     *   <li>回退到玩家副手</li>
     * </ol>
     * <p>
     * ⭐ 修复：原实现对箭矢调用 {@code getPickupItem()} 会返回箭矢本身（非匠魂工具）并提前 return，
     * 导致"回退主手拿弓"的逻辑永远不执行 → 弓上的特性无法随箭矢命中触发。
     * 现在只有 {@code getPickupItem()} 返回匠魂工具时才采用，否则继续回退到玩家手上的弓。
     *
     * @param projectile 弹射物实体
     * @param shooter    发射者（玩家）
     * @return 对应的武器物品栈，若无法获取则返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack getProjectileWeapon(Projectile projectile, Player shooter) {
        // 0. 优先：箭矢上绑定的发射弓（发射瞬间写入，玩家切换武器后依然有效）
        if (projectile instanceof AbstractArrow arrow) {
            CompoundTag tag = arrow.getPersistentData();
            if (tag.contains(KEY_FIRING_BOW)) {
                ItemStack bow = ItemStack.of(tag.getCompound(KEY_FIRING_BOW));
                if (!bow.isEmpty()) {
                    return bow;
                }
            }
        }

        // 1. 尝试从弹射物本身获取物品 —— 仅当返回的是匠魂工具（标枪等）时采用
        //    （原版箭矢 getPickupItem 返回箭矢本身，不是匠魂工具，必须跳过）
        try {
            Method method = projectile.getClass().getMethod("getPickupItem");
            ItemStack stack = (ItemStack) method.invoke(projectile);
            if (isModifiableTool(stack)) {
                return stack;
            }
        } catch (Exception ignored) {}

        try {
            Method method = projectile.getClass().getMethod("getItem");
            ItemStack stack = (ItemStack) method.invoke(projectile);
            if (isModifiableTool(stack)) {
                return stack;
            }
        } catch (Exception ignored) {}

        // 2. 回退到玩家主手（弓/弩）
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

    /** 物品是否为匠魂可修改工具（IModifiable） */
    private static boolean isModifiableTool(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IModifiable;
    }
}
