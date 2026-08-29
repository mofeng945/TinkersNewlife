package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 无量空处（领域特性，占用领域槽）
 * <p>
 * 领域内除开启者以外的所有生物与玩家陷入静止状态（无法攻击/移动/使用物品/
 * 切换物品栏/打开背包），领域关闭后按公式继续延续。
 * 实际逻辑由 {@code WuLiangKongChuDomain} 与 {@code StunHandler} 处理，
 * 本类仅作注册标记。详细机制说明见帕秋莉手册/JEI（暂未编写）。
 */
public class WuLiangKongChuTrait extends Modifier {
}
