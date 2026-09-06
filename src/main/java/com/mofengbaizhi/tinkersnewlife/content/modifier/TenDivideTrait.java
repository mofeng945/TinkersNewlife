package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 十划咒法（术式特性，占用术式槽）
 * <p>
 * 对目标使用（术式键 C）：将目标十等分，在其 7:3 分界（第七划）处刻下弱点；
 * 之后该玩家对带弱点目标造成的任意伤害将触发一次"暴击"（伤害 × 暴击倍率），
 * 暴击倍率随咒力亲和与咒力输出提升；每个弱点仅暴击一次。
 * 实际逻辑由 {@code TenDivideTechnique} 处理，本类仅作注册标记。
 */
public class TenDivideTrait extends Modifier {
}
