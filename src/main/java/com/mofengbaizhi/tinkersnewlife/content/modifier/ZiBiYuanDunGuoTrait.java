package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 领域·自闭圆顿裹（占用领域槽）
 * <p>
 * 展开后对领域内除施术者外的每个目标施加一次无为转变：
 * 生物 → 替换成所选形态的守护式神（认主、永久）；玩家 → 本体强制变形成所选形态（60s 限时、禁工具）。
 * 形态 = 施术者无为转变界面选定的对象；未设定时默认随机僵尸/骷髅。
 * 通用抵抗与新阴流三技巧均可抵挡本领域转变。
 * 实际逻辑由 {@code ZiBiYuanDunGuoDomain} 处理。
 */
public class ZiBiYuanDunGuoTrait extends Modifier {
}
