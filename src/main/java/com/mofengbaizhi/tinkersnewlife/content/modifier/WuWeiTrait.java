package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 无为转变（术式特性，占用术式槽）
 * <p>
 * 顺转：玩家变形成已记录（击杀过）的指定生物，继承其基础属性但不继承能力（AI），视角转移操控，可再按恢复；
 * 反转：将视线目标（含玩家）限时变形成指定生物，到时自动恢复。
 * 实际逻辑由 {@code WuWeiTechnique}/{@code WuWeiHandler} 处理，本类仅作注册标记。
 */
public class WuWeiTrait extends Modifier {
}
