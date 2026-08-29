package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 捌（术式特性，占用术式槽）
 * <p>
 * 按下术式释放按键，对 3 格内的接触目标同时发动 3 道横向斩击与 3 道纵向斩击，
 * 每道斩击伤害为「解」的二分之一（无视无敌帧，可被护甲衰减），咒力消耗为「解」的 2 倍。
 * 实际逻辑由 {@code BaTechnique} 处理，本类仅作注册标记。
 * 详细机制说明见帕秋莉手册/JEI（暂未编写）。
 */
public class BaTrait extends Modifier {
}
