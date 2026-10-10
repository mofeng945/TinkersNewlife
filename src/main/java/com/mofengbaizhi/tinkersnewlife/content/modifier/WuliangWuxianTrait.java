package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 无下限·无限（术式特性，占用术式槽）
 * <p>
 * 按下术式释放键（C）开启 / 关闭。开启期间：
 * - 受到的伤害若低于阈值则完全无效；
 * - 若高于阈值，每点溢出伤害消耗咒力，咒力耗尽自动关闭并对施术者造成破盾伤害。
 * 切换术式时持续开启，切回后按 C 可正常关闭。
 * 实际逻辑由 {@code WuliangWuxianTechnique} / 伤害事件处理，本类仅作注册标记。
 */
public class WuliangWuxianTrait extends Modifier {
}
