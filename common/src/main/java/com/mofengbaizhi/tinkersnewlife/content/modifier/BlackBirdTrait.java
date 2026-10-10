package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 黑鸟操术（术式特性，占用术式槽）
 * <p>
 * 发动后生成一只黑鸟（蝙蝠），玩家视角转移到黑鸟身上，玩家本体留在原地。
 * 操控方式类似骑乘：W 朝视线飞 / A/D 侧移 / 空格上升；
 * 按 Shift 黑鸟以 2 倍速直线朝视线俯冲，撞到实体或方块自爆（不破坏方块）。
 * 再次按释放键回收（返还一半咒力），黑鸟死亡视角回归。
 * 实际逻辑由 {@code TechniqueHandler} 处理，本类仅作注册标记。
 */
public class BlackBirdTrait extends Modifier {
}
