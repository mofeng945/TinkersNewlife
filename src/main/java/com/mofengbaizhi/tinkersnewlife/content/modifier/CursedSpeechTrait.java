package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 咒言术（术式特性，占用术式槽）
 * <p>
 * 顺转（C）：咏唱当前咒言——由「咏叹词，敬称+对象，祈求语+核心义，结谢语！」
 * 六段组成，每段词条通过古代咒术残卷学习、反转键（F）打开编辑器组合。
 * 咏叹词影响咒力消耗、敬称影响作用强度、祈求语影响持续时间、结谢语削减反噬；
 * 对象与核心义决定实际效果。咏唱需读条（时长随咒言稀有度增加），
 * 释放后依目标血量与自身输出/亲和结算反噬（扣除自身血量）。
 * 实际逻辑由 {@code CursedSpeechTechnique} 处理，本类仅作注册标记。
 */
public class CursedSpeechTrait extends Modifier {
}
