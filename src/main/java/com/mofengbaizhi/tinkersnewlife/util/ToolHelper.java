package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierId;
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
        // 悠悠球：发射后玩家手中已无该工具，从球实体携带的完整工具栈读取（特性才能正常触发）
        if (source.getDirectEntity() instanceof YoYoEntity yoYo) {
            return getValidTool(yoYo.getReturnStack());
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

    // ============================================================
    //  咒力核心兜底（术式/领域攻击时主手无武器，材料特性位于核心上）
    // ============================================================

    /**
     * 解析攻击者携带指定修饰符的匠魂战斗工具——<b>玩家与怪物通用</b>。
     * <ul>
     *   <li>玩家：近战/弹射双路径 + 无武器时咒力核心兜底</li>
     *   <li>非玩家（怪物/随从等）：仅检查主手物品是否为匠魂工具且含任一修饰符
     *       （怪物没有弹射武器与咒力核心，只查主手近战）</li>
     * </ul>
     * 供各攻击词条 Handler 把 {@code instanceof Player} 判定放宽为 {@code instanceof LivingEntity}
     * 后调用，使持匠魂武器的怪物也能触发命中效果。
     *
     * @param source   伤害来源（LivingAttackEvent/LivingDamageEvent 的 getSource()；非玩家时仅用主手，可为 null）
     * @param attacker 攻击者实体（玩家或怪物，须在服务端调用）
     * @param ids      需要匹配的修饰符（命中其一即可）
     * @return 携带任一指定修饰符的 ToolStack；主手与核心均无时返回主手解析结果（可能为 null）
     */
    @SafeVarargs
    @Nullable
    public static ToolStack getCombatToolWith(DamageSource source, LivingEntity attacker, ModifierId... ids) {
        if (attacker == null) return null;
        if (attacker instanceof Player player) {
            return getToolWithModifier(player, getCombatTool(source, player), ids);
        }
        // 非玩家：只查主手（怪物近战武器）
        return getValidToolWithModifier(attacker.getMainHandItem(), ids);
    }

    /**
     * 解析攻击者携带指定修饰符的匠魂工具（弹射物路径）——<b>玩家与怪物通用</b>。
     * 玩家走弹射武器解析 + 咒力核心兜底；非玩家（理论上怪物不用匠魂远程）回退查主手。
     *
     * @param projectile 弹射物实体
     * @param attacker   攻击者实体（玩家或怪物，须在服务端调用）
     * @param ids        需要匹配的修饰符（命中其一即可）
     * @return 携带任一指定修饰符的 ToolStack；无则 null
     */
    @SafeVarargs
    @Nullable
    public static ToolStack getCombatToolWith(Projectile projectile, LivingEntity attacker, ModifierId... ids) {
        if (attacker == null) return null;
        if (attacker instanceof Player player) {
            return getToolWithModifier(player, getCombatTool(projectile, player), ids);
        }
        return getValidToolWithModifier(attacker.getMainHandItem(), ids);
    }

    /**
     * 解析玩家携带指定修饰符的匠魂工具：给定的主工具携带则直接使用，
     * 否则兜底取佩戴的咒力核心；两者均无时返回主工具（可能为 null）。
     *
     * @param player  玩家（须在服务端调用）
     * @param primary 主手/弹射等路径解析出的工具（可为 null）
     * @param ids     需要匹配的修饰符（命中其一即可）
     */
    @SafeVarargs
    @Nullable
    public static ToolStack getToolWithModifier(Player player, ToolStack primary, ModifierId... ids) {
        if (hasAny(primary, ids)) return primary;
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return primary;
        ToolStack coreTool = getToolStack(core);
        if (hasAny(coreTool, ids)) return coreTool;
        return primary;
    }

    /** 工具是否携带任一指定修饰符（等级 &gt; 0） */
    private static boolean hasAny(ToolStack tool, ModifierId[] ids) {
        if (tool == null || ids.length == 0) return false;
        for (ModifierId id : ids) {
            if (tool.getModifierLevel(id) > 0) return true;
        }
        return false;
    }

    /** 物品是有效匠魂工具且含任一指定修饰符才返回，否则 null */
    @SafeVarargs
    @Nullable
    private static ToolStack getValidToolWithModifier(ItemStack stack, ModifierId... ids) {
        ToolStack tool = getValidTool(stack);
        if (hasAny(tool, ids)) return tool;
        return null;
    }
}
