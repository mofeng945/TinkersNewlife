package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 反转术式（术式特性，占用术式槽）
 * <p>
 * 按术式键（C）：对自身释放反转术式，消耗咒力输出×10 点咒力，
 * 恢复 (1+(亲和/10+输出)/10)×输出×2 点生命。
 * 按术式反转键（F）：反转术式外放——对施术目标恢复生命；若目标是亡灵生物，
 * 则受到应恢复生命数值的 2 倍伤害。
 * 实际逻辑由 {@code ReverseCursedTechnique} 处理，本类仅作注册标记。
 */
public class ReverseCursedTrait extends Modifier {
}
