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
}
