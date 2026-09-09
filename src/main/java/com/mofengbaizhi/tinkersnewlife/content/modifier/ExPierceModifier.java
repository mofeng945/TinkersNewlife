package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;

/**
 * 升级槽强化·穿透EX：命中改为「穿透」伤害源结算（见 {@code ExPierceHandler}）。
 * 穿透源复用本 mod 的 tinkersnewlife:true_pierce 伤害类型——数据包标签已带
 * {@code minecraft:bypasses_invulnerability} 与 {@code minecraft:bypasses_cooldown} → 可穿透：
 * <ul>
 *   <li>各类无敌：灾变利维坦离水无敌/阶段二无敌、凋灵出生（充能）无敌；</li>
 *   <li>伤害上限：灾变 Boss 伤害桶（damageBucket/DamageCap）；</li>
 *   <li>无敌帧（bypasses_cooldown）；凋灵半血「免疫箭矢」——重打时 direct 实体取射手而非箭。</li>
 * </ul>
 * 占 1 升级槽，单级（配方：哈斯塔/尼古拉斯/奈亚/拉莱耶/格赫罗斯五古神材料各一），
 * 仅近战/远程工具。与天逆鉾（仅凋灵/诡厄受限 Boss）互补，此为通用工具版。
 */
public class ExPierceModifier extends Modifier {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "ex_pierce"));
}
