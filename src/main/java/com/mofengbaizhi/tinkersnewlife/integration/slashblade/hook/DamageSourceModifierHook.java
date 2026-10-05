package com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook;

// 移植自 TiCEX (MIT): moffy.ticex.lib.hook.DamageSourceModifierHook

import java.util.Collection;
import net.minecraft.world.damagesource.DamageSource;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * TiCEX 的"改写伤害来源"修饰符钩子 —— 逐字移植（MIT）。
 *
 * <p>用途：拔刀剑的范围/技能伤害结算（{@code KatanaAttackManagerMixin}）里，
 * 由工具上的修饰符决定这一击到底以什么伤害源落地 ✓。
 *
 * <p>⚠ 与原文的<b>唯一有意差异</b>：原文在 {@link #modifyDamageSource(IToolStackView, DamageSource)}
 * 末尾用 <b>INFO</b> 打一行 {@code TicEX.LOGGER.info(currentDamageSource.type().toString())} ✗ ——
 * 那条会在**每一刀**都往日志里写一行（刷屏 ✗，用户口径"不许刷屏影响性能" ✗）
 * ⇒ 这里降为 {@code debug} 级 ✓（默认看不到，需要时调日志级别即可恢复 ✓），其余逐字一致 ✓。
 */
public interface DamageSourceModifierHook {

    DamageSource modifyDamageSource(IToolStackView tool, ModifierEntry modifierEntry, DamageSource currentSource, DamageSource original);

    class DefaultClass implements DamageSourceModifierHook {

        @Override
        public DamageSource modifyDamageSource(IToolStackView tool, ModifierEntry modifierEntry, DamageSource currentSource, DamageSource original) {
            return currentSource;
        }
    }

    record AllMerger(Collection<DamageSourceModifierHook> modules) implements DamageSourceModifierHook {

        @Override
        public DamageSource modifyDamageSource(IToolStackView tool, ModifierEntry modifierEntry, DamageSource currentSource, DamageSource original) {
            DamageSource source = original;
            for (DamageSourceModifierHook module : modules) {
                source = module.modifyDamageSource(tool, modifierEntry, source, original);
            }
            return source;
        }
    }

    static DamageSource modifyDamageSource(IToolStackView tool, DamageSource original) {
        DamageSource currentDamageSource = original;
        for (ModifierEntry entry : tool.getModifierList()) {
            currentDamageSource = entry.getHook(KatanaModifierHooks.DAMAGE_SOURCE)
                    .modifyDamageSource(tool, entry, currentDamageSource, original);
        }
        // 原文：TicEX.LOGGER.info(...) ✗ ⇒ 降为 debug ✓（见类注释）
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.debug(
                "[拔刀剑] 伤害源改写结果：{}", currentDamageSource.type());
        return currentDamageSource;
    }
}
