package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 构筑术式（术式特性，占用术式槽）
 * <p>
 * 顺转：开启「无限弹药模式」——手持会从背包请求弹药的武器（原版弓弩 / 匠魂弓弩 / TACZ 枪械等）时，
 * 每当弹药耗尽就消耗咒力凝结出对应类型的箭矢 / 子弹，无需再囤积弹药。
 * 反转（F）：临时打开拟造物品栏——从所有有合成配方的物品中挑选一件，按珍贵程度与威力
 * 换算咒力消耗并拟造一个仅存在 60 秒的临时物品。
 * 实际逻辑由 {@code ConstructTechnique} 处理，本类仅作注册标记。
 */
public class ConstructTrait extends Modifier {
}
