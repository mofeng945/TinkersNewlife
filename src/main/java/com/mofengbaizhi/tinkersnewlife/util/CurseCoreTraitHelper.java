package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 咒力核心材料特性触发助手
 * <p>
 * 攻击类术式（解/捌/灶·开）与领域攻击（伏魔御厨子斩击）不走匠魂近战管线，
 * 材料特性（MELEE_DAMAGE / MELEE_HIT 钩子）不会自动生效。
 * 本助手手动构造 {@link ToolAttackContext}，对佩戴咒力核心的修饰符
 * （含材料带来的特性）依次执行：
 * <ul>
 *   <li>{@link MeleeDamageModifierHook#getMeleeDamage}：伤害加成</li>
 *   <li>{@link MeleeHitModifierHook#beforeMeleeHit}：命中前进一步修正</li>
 *   <li>{@link MeleeHitModifierHook#afterMeleeHit}：命中后效果（点燃/药水等）</li>
 * </ul>
 */
public final class CurseCoreTraitHelper {

    private CurseCoreTraitHelper() {}

    /** 应用咒力核心材料特性后的伤害（无核心时原样返回） */
    public static double applyCurseCoreTraits(ServerPlayer player, LivingEntity target, double damage) {
        ToolStack tool = getCoreTool(player);
        if (tool == null) return damage;
        ToolAttackContext context = buildContext(player, target);
        float base = (float) damage;
        float current = base;

        // 材料特性的伤害加成（每级修饰符依次叠加）
        for (ModifierEntry entry : tool.getModifierList()) {
            MeleeDamageModifierHook hook = entry.getHook(ModifierHooks.MELEE_DAMAGE);
            if (hook != null) {
                current = hook.getMeleeDamage(tool, entry, context, base, current);
            }
        }
        // 命中前修正
        for (ModifierEntry entry : tool.getModifierList()) {
            MeleeHitModifierHook hook = entry.getHook(ModifierHooks.MELEE_HIT);
            if (hook != null) {
                current = hook.beforeMeleeHit(tool, entry, context, base, current, current);
            }
        }
        return current;
    }

    /** 命中后触发材料特性的命中效果（应在实体实际受到伤害后调用） */
    public static void afterCurseCoreHit(ServerPlayer player, LivingEntity target, double damageDealt) {
        ToolStack tool = getCoreTool(player);
        if (tool == null) return;
        ToolAttackContext context = buildContext(player, target);
        for (ModifierEntry entry : tool.getModifierList()) {
            MeleeHitModifierHook hook = entry.getHook(ModifierHooks.MELEE_HIT);
            if (hook != null) {
                hook.afterMeleeHit(tool, entry, context, (float) damageDealt);
            }
        }
    }

    private static ToolStack getCoreTool(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return null;
        return ToolHelper.getToolStack(core);
    }

    /** 构造近战攻击上下文：施法者为攻击者，目标为直接目标 */
    private static ToolAttackContext buildContext(ServerPlayer player, LivingEntity target) {
        return new ToolAttackContext(player, player, InteractionHand.MAIN_HAND,
                target, target, false, 1.0F, false);
    }
}
