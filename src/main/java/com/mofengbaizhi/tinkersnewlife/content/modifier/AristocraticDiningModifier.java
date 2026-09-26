package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;

/**
 * 能力槽强化「贵族餐饮」——<b>护甲 · 无等级</b>，占 <b>1 个能力槽</b>（配方里写 {@code "abilities": 1} ✓）。
 *
 * <p>规格（用户口径 ✓）：**5 级以上的血族**进食普通食物时，除了照常回复饱食度，还能按食物的
 * **营养值**获得等量血液值（血液饱和度按食物的饱和度系数走，与血族自己的加血算法完全一致 ✓）。
 *
 * <p>行为不在本类里，而在 {@code content/modifier/events/AristocraticDiningHandler}（与"戈耳工免疫"
 * 同一套分工：强化类只声明 id，事件处理器负责判定与生效 ✓）。
 */
public class AristocraticDiningModifier extends SingleLevelModifier {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "aristocratic_dining"));
}
