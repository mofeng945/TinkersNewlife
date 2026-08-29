package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 灶·开（术式特性，占用术式槽）
 * <p>
 * 按下术式释放按键，向看向目标方向发射一道笔直但速度较慢的火焰箭，
 * 命中目标或扎在方块上引发火焰爆炸：中心伤害 = (1+咒力亲和/100) ×
 * (当前攻击伤害+咒力输出×10) × 200%，随距离衰减，不破坏方块。
 * 咒力消耗为「解」的 10 倍。实际逻辑由 {@code ZaoKaiTechnique} 处理。
 */
public class ZaoKaiTrait extends Modifier {
}
