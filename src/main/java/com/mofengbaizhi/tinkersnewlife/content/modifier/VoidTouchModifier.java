package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;

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
public class VoidTouchModifier extends SingleLevelModifier {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "void_touch"));

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
