package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 反重力机构（术式特性，占用术式槽）
 * <p>
 * 独立术式，与无下限系列无关。
 * 顺转（C）：以自身为中心的影响范围内，除施术者外的所有生物被施加漂浮，
 * 每有一个目标消耗一份咒力；范围随咒力亲和与输出扩大。
 * 反转（F）：展开压力场——场内目标承受"压力"：按目标体型与血量上限计算压力阈值，
 * 阈值内仅受迟缓（越接近阈值越强）；超出阈值则脚下非基岩方块被压碎、
 * 目标被定身并在压力场内持续受到伤害（超出越多单次伤害越高）。
 * 实际逻辑由 {@code AntiGravityTechnique} 处理，本类仅作注册标记。
 */
public class AntiGravityTrait extends Modifier {
}
