package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 傀儡操术（术式特性，占用术式槽）
 * <p>
 * 在玩家位置召唤铁傀儡/雪傀儡（GUI 选择），玩家视角转移到傀儡身上操控：
 * 铁傀儡：左键攻击（伤害=round((4+输出×4)×(1+亲和/100))）、右键击飞（无直接伤害）、Shift 自爆（半径5，不破坏方块）；
 * 雪傀儡：左键无效、右键投雪球（伤害=round((2+输出×2)×(1+亲和/100))，命中附短霜冻）、Shift 自爆（半径3，附霜冻）。
 * 操控期间本体定身留在原地，可被打；再次按术式键召回（返还30%咒力）；傀儡死亡视角回归。
 * 实际逻辑由 {@code TechniqueHandler} + {@code PuppetTechnique} 处理，本类仅作注册标记。
 */
public class PuppetTrait extends Modifier {
}
