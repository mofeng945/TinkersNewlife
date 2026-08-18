package com.mofengbaizhi.tinkersnewlife.content.modifier.base;

import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * 战斗特性基类 - 提供工具方法
 * 实际钩子触发由 WeaponModifierHandler 处理（Forge 事件总线）
 */
public abstract class BaseCombatModifier extends Modifier {

    protected int getEffectiveLevel(IToolStackView tool, ModifierEntry modifier) {
        if (tool != null) {
            return tool.getModifierLevel(modifier.getId());
        }
        return modifier.getLevel();
    }

    protected int getEffectiveLevel(ModifierEntry modifier) {
        return modifier.getLevel();
    }
}