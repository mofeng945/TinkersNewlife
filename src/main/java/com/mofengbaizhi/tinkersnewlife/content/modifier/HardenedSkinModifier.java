package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;

/**
 * 防御槽强化「硬化皮肤」——<b>护甲 · 无等级</b>，占 <b>1 个防御槽</b>（配方里写 {@code "defense": 1} ✓）。
 *
 * <p>规格（用户口径 ✓）：**5 级以上的血族**不再被阳光灼烧 ——
 * 既不吃 {@code vampirism:sun_damage} 那一串伤害，也不会在阳光下被点着，
 * 因此"暴露在太阳下时视野边缘那圈火焰光晕"也就不会出现 ✓。
 *
 * <p>行为不在本类里，而在 {@code content/modifier/events/HardenedSkinHandler}。
 */
public class HardenedSkinModifier extends SingleLevelModifier {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "hardened_skin"));

    /**
     * <b>无等级</b> ✓（用户口径 §818）——不论匠魂给的 level 是几，名字后面都<b>不拼罗马数字</b> ✓。
     * <p>为什么要显式写：材料特性是<b>按部件累加</b>的（{@code ModifierEntry#merge} = level + level ✓）
     * ⇒ 一件"镶板 + 锁链基底"都是该材料的盔甲会让 level 变成 2 ✗，于是退回基类的
     * {@code ModifierLevelDisplay.DEFAULT} 拼出「某特性 II」✗（匠魂官方方案 {@code NO_LEVELS} 可解 ✓）。
     */
    @Override
    public net.minecraft.network.chat.Component getDisplayName(int level) {
        return slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay.NO_LEVELS
                .nameForLevel(this, level);
    }
}
