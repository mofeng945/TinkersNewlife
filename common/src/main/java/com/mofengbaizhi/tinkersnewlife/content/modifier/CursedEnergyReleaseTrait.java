package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 咒力外放（术式特性，占用术式槽）
 * <p>
 * 顺转：朝视线发射纯粹咒力能量弹，命中单体高伤；
 * 反转（F）：冰沙冲击波（Granite Blast）激光——每 2 tick 无视无敌帧伤害持续 3 秒，
 * 可转动视角调整方向，结束后顺转/反转一同冷却 6 秒。不受术式熔断影响。
 * 实际逻辑由 {@code CursedEnergyReleaseTechnique} 处理，本类仅作注册标记。
 */
public class CursedEnergyReleaseTrait extends Modifier {
}
