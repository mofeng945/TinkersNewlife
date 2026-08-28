package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 咒力总量特性（咒力核心自带）
 * <p>
 * 咒力上限 = 咒力总量等级 × (咒力输出等级 + 咒力亲和/10) × 100。
 * 数值计算由 {@code CursePowerHelper} 统一处理，本类仅作注册标记。
 * 详细机制说明见帕秋莉手册/JEI（暂未编写）。
 */
public class CurseTotalTrait extends Modifier {
}
