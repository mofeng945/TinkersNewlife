package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;

/**
 * 虚空金属工具特性·<b>虚空抚摸</b>（<b>无等级</b>）——「用身体触摸虚空的意志吧！」
 *
 * <p>规格（用户口径）：用虚空金属工具<b>近战或远程</b>命中时，给目标挂 <b>虚蚀</b>
 * （{@code goety_ladder:void_wane} ✓）持续 <b>3 秒</b>；目标<b>已有</b>虚蚀则等级 <b>+1</b>，<b>最高 5 级</b>。
 *
 * <p>行为不在本类里，而在 {@code content/modifier/events/VoidTouchHandler}
 * （与本模组其它特性同一套分工：强化类只声明 id，事件处理器负责判定与生效 ✓）。
 * 近战/远程两条路都直接复用 {@code util/ToolHelper#getCombatToolWith} ✓（它连悠悠球、弹射武器都解析得到 ✓）。
 */
public class VoidTouchModifier extends LevelLessModifier {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "void_touch"));
}
