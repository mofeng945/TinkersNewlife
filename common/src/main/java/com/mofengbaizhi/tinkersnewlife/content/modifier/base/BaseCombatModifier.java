package com.mofengbaizhi.tinkersnewlife.content.modifier.base;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 战斗特性基类（占位）。
 * <p>
 * 实际钩子触发由各 Forge 事件 Handler 处理（事件总线）。
 * 注意：原 {@code getEffectiveLevel} 重载已删除——全代码库无任何调用方（死代码），
 * 且注释指向的 WeaponModifierHandler 类不存在。
 */
public abstract class BaseCombatModifier extends Modifier {
}
