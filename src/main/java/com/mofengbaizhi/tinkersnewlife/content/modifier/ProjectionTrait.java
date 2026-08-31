package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 投射咒法（术式特性，占用术式槽）
 * <p>
 * 发动后在视线方向生成玩家虚影（距离 = 1s 最大直线移动距离的 2/3~1 倍），
 * 1s 内触碰虚影获得 10s 增益（伤害/跳跃/速度 ×2）。消耗咒力上限 1/12。
 * 实际逻辑由 {@code TechniqueHandler} 处理，本类仅作注册标记。
 */
public class ProjectionTrait extends Modifier {
}
