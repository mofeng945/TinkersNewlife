package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 雅各布天梯（术式特性，占用术式槽）
 * <p>
 * 对看向的目标，在其头顶上方 50 格召唤大型魔法阵（七芒星 + 十字架），
 * 2 秒蓄力后降下巨大光柱：被照射目标 60 秒内禁用术式与领域（持续咒术瞬间终止），
 * 并持续受到帧伤（2 tick 一次无视无敌帧），亡灵生物伤害 ×8。
 * 实际逻辑由 {@code JacobsLadderTechnique} 处理，本类仅作注册标记。
 */
public class JacobsLadderTrait extends Modifier {
}
