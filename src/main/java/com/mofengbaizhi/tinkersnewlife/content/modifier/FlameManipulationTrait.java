package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 炎熔操术（术式特性，占用术式槽）
 * <p>
 * 顺转：将视线内目标脚下 3×3 的方块（基岩除外）融化为炽热岩浆，目标身处其中持续受到咒术伤害；
 * 反转（F）：召唤 5 只体型 1/8、1 点生命的幻翼（焰羽），1 秒前摇后依次飞速撞向敌人，
 * 每次撞击引发等同黑鸟自爆的爆炸（半径 3 线性衰减），并在敌人脚下 3×3 燃起火焰、点燃敌人。
 * 实际逻辑由 {@code TechniqueHandler} + {@code FlameManipulationTechnique} 处理。
 */
public class FlameManipulationTrait extends Modifier {
}
