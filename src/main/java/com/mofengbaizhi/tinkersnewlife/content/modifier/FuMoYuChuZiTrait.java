package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 伏魔御厨子（领域特性，占用领域槽）
 * <p>
 * 领域内每 2 tick 对除开启者外的所有生物与玩家释放斩击（无视无敌帧，
 * 带横扫粒子特效，脚下留下红石粉血液），伤害可被护甲等防御衰减。
 * 实际逻辑由 {@code FuMoYuChuZiDomain} 处理，本类仅作注册标记。
 * 详细机制说明见帕秋莉手册/JEI（暂未编写）。
 */
public class FuMoYuChuZiTrait extends Modifier {
}
