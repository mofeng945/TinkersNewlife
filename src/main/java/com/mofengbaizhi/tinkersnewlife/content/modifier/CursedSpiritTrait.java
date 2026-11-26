package com.mofengbaizhi.tinkersnewlife.content.modifier;

import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 咒灵操术（术式特性，占用术式槽）
 * <p>
 * 顺转：对准濒死（≤2.5% 血量）亡灵可将其【收入 GUI（个体记录，含完整 NBT）】并令其消散，
 * 消耗取决于其生命上限与施术者输出/亲和，施术者获得 30s 饥饿与 10s 反胃；
 * 否则打开个体列表 GUI：选择可释放满血个体（以施术者为主人，只打威胁主人/主人攻击的目标），
 * 已释放的再选即收回；释放体战死则该个体记录消失。
 * 反转：在 GUI 选择一名未释放个体，其数据被清除并用于蓄力黑色漩涡，
 * 再次按反转键向视线笔直射出（伤害取决于该个体生命上限/攻击与施术者输出/亲和）。
 */
public class CursedSpiritTrait extends Modifier {
}
