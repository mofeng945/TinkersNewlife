package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 解（术式特性，占用术式槽）
 * <p>
 * 按下术式释放按键，对看向的实体释放一次斩击：
 * 伤害 = (1+(咒力输出等级+咒力亲和/10)/10) × (当前攻击伤害+咒力输出等级×5) × 70%，
 * 类似伏魔御厨子的小斩击（无视无敌帧、可被护甲衰减）。
 * 实际逻辑由 {@code TechniqueHandler} 处理，本类仅作注册标记。
 * 详细机制说明见帕秋莉手册/JEI（暂未编写）。
 */
public class KaiTrait extends Modifier {
}
