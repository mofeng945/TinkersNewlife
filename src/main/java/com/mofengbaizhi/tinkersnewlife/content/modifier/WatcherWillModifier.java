package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;

/**
 * 虚空金属盔甲特性·<b>守望意志</b>（<b>有等级</b>，<b>多件可叠加</b>）
 * ——「最强的战绩，铭刻于末地！」（§726 按用户口径改的 flavor 文案 ✓）
 *
 * <p>规格（用户口径）：<b>每级</b>
 * <ol>
 *   <li><b>10% 概率闪避此次攻击</b> ✓（多件叠加 ⇒ 按<b>身上所有护甲上该特性的等级之和</b>算，
 *       即 4 件 × 1 级 = 40%，上限 100% ✓）；</li>
 *   <li><b>诡厄巫法</b>与<b>铁魔法</b>的吟唱速度各 <b>+15%</b> ✓
 *       （铁魔法走它自己的 {@code CAST_TIME_REDUCTION} 属性 ✓；诡厄没有公开属性 ⇒ 见
 *       {@code mixin/GoetyCastSpeedMixin}，挂在它自己的 {@code ModAttributes#getCastingSpeed} 上 ✓）。</li>
 * </ol>
 *
 * <p>为什么是"有等级"：本类<b>不</b>继承 {@code SingleLevelModifier} ✓（和材料「秘银」的
 * {@code FocusModifier} 同一种注册方式 ⇒ 支持叠加等级 ✓）。默认材料给 1 级 ✓；
 * 等级越高越强，上限见 {@link #MAX_LEVEL} ✓。
 * 行为在 {@code content/modifier/events/WatcherWillHandler} ✓。
 */
public class WatcherWillModifier extends Modifier {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "watcher_will"));

    /** 等级上限（单件的等级上限；"多件叠加"不受此限制 ✓） */
    public static final int MAX_LEVEL = 5;
}
