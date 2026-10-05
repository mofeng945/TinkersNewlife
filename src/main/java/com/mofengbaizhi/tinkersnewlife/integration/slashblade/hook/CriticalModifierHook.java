package com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook;

// 移植自 TiCEX (MIT): moffy.ticex.lib.hook.CriticalModifierHook

import java.util.Collection;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * TiCEX 的"暴击"修饰符钩子 —— 逐字移植（MIT）。
 *
 * <p>用途：拔刀剑的范围/技能伤害结算（{@code KatanaAttackManagerMixin}）里，先问一遍
 * 攻击者身上所有匠魂工具上的这个钩子，得到"这一击算不算暴击 + 暴击倍率" ✓。
 *
 * <p>⚠ 保持与原版一致的两点（哪怕看起来怪 ✗）：
 * <ol>
 *   <li>{@link #modifyCritical} 返回的是 {@code new CriticalContext(currentCrit, criticalModifier)} ——
 *       <b>用的是入参的 {@code criticalModifier} 而不是累加出来的 {@code currentModifier}</b> ✓
 *       （TiCEX 原文如此 ⇒ 1:1 保留 ✓，不"顺手修" ✗）；</li>
 *   <li>它遍历的是<b>全部装备槽</b>（而不是只看主手）✓ —— 也就是说副手/盔甲上的匠魂工具也会参与 ✓。</li>
 * </ol>
 */
public interface CriticalModifierHook {

    default boolean isCritical(IToolStackView tool, ModifierEntry entry, boolean isCritical, boolean original) {
        return isCritical;
    }

    default float setCriticalRate(IToolStackView tool, ModifierEntry entry, float currentRate, float originalRate) {
        return currentRate;
    }

    static CriticalContext modifyCritical(LivingEntity entity, boolean isCritical, float criticalModifier) {
        boolean currentCrit = isCritical;
        float currentModifier = criticalModifier;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof IModifiable) {
                ToolStack tool = ToolStack.from(stack);

                for (ModifierEntry entry : tool.getModifierList()) {
                    CriticalModifierHook hook = entry.getHook(KatanaModifierHooks.CRITICAL);
                    currentCrit = hook.isCritical(tool, entry, currentCrit, isCritical);
                    currentModifier = hook.setCriticalRate(tool, entry, currentModifier, criticalModifier);
                }
            }
        }

        return new CriticalContext(currentCrit, criticalModifier);
    }

    class DefaultClass implements CriticalModifierHook {

    }

    record AllMerger(Collection<CriticalModifierHook> hooks) implements CriticalModifierHook {

        @Override
        public boolean isCritical(IToolStackView tool, ModifierEntry entry, boolean isCritical, boolean original) {
            boolean currentValue = original;
            for (CriticalModifierHook hook : hooks) {
                currentValue = hook.isCritical(tool, entry, currentValue, original);
            }
            return currentValue;
        }

        @Override
        public float setCriticalRate(IToolStackView tool, ModifierEntry entry, float currentRate, float originalRate) {
            float rate = originalRate;
            for (CriticalModifierHook hook : hooks) {
                rate = hook.setCriticalRate(tool, entry, rate, originalRate);
            }
            return rate;
        }
    }

    record CriticalContext(boolean isCritical, float criticalModifier) {

    }
}
