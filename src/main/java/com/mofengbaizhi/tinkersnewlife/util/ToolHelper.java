package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;

/**
 * 匠魂工具安全操作辅助类
 */
public final class ToolHelper {

    private ToolHelper() {}

    /**
     * 安全地获取 ToolStack
     * <p>
     * 只有在物品是匠魂可修改工具（实现了 IModifiable）时，才会构造 ToolStack，
     * 否则返回 null，从而避免 "non-modifiable tool" 警告。
     *
     * @param stack 物品栈
     * @return ToolStack 实例，或 null（如果 stack 为空、不是匠魂工具、或构造失败）
     */
    @Nullable
    public static ToolStack getToolStack(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof IModifiable)) return null;
        return ToolStack.from(stack);
    }

    /**
     * 从攻击伤害源获取攻击者使用的匠魂战斗工具。
     * <p>
     * 统一处理近战（主手）与弹射物（弓/弩/标枪等，经 {@link ProjectileWeaponHelper}）两条路径，
     * 并附带 isBroken 与 stats 检查。用于各战斗 Handler 的 {@code onLivingAttack} 事件，
     * 消除重复的"取武器→校验"样板代码。
     *
     * @param source 伤害来源（LivingAttackEvent.getSource()）
     * @param player 攻击者玩家
     * @return 可用的 ToolStack，或 null（无武器 / 非匠魂工具 / 已损坏 / 无有效统计）
     */
    @Nullable
    public static ToolStack getCombatTool(DamageSource source, Player player) {
        if (source.getDirectEntity() instanceof Projectile projectile) {
            return getCombatTool(projectile, player);
        }
        return getValidTool(player.getMainHandItem());
    }

    /**
     * 从弹射物获取攻击者使用的匠魂战斗工具。
     * <p>
     * 用于各战斗 Handler 的 {@code onProjectileImpact} 事件。
     *
     * @param projectile 弹射物实体
     * @param player     攻击者玩家
     * @return 可用的 ToolStack，或 null
     */
    @Nullable
    public static ToolStack getCombatTool(Projectile projectile, Player player) {
        ItemStack weapon = ProjectileWeaponHelper.getProjectileWeapon(projectile, player);
        if (weapon.isEmpty()) {
            weapon = player.getMainHandItem();
        }
        return getValidTool(weapon);
    }

    /**
     * 获取有效的匠魂工具（安全获取 + 未损坏 + 有有效统计）。
     */
    @Nullable
    private static ToolStack getValidTool(ItemStack weapon) {
        ToolStack tool = getToolStack(weapon);
        if (tool == null) return null;
        if (tool.isBroken()) return null;
        if (tool.getStats().getContainedStats().isEmpty()) return null;
        return tool;
    }
}
