package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 领域·胎藏遍野（占用领域槽）
 * <p>
 * 展开后对领域内除施术者外的所有目标施加"压力"，压力大小等于
 * 反重力机构（反转压力场）压力的 2 倍：目标按体型与血量上限计算压力阈值，
 * 未超阈值受迟缓（越接近等级越高）；超出则脚下非基岩方块被压碎、目标被定身
 * 并持续受到咒术伤害。本领域的压力可被新阴流三技巧（弥虚葛笼/落花之情/简易领域）抵挡。
 * 实际逻辑由 {@code TaizangBianyeDomain} 处理。
 */
public class TaizangBianyeTrait extends Modifier {
}
