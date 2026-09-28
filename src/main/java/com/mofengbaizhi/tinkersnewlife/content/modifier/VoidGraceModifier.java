package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;
import slimeknights.tconstruct.library.modifiers.util.ModifierLevelDisplay;

/**
 * 虚空金属盔甲特性·<b>虚无恩宠</b>（<b>无等级</b>）——「此时此刻，有人将统御虚空」
 *
 * <p>规格（用户口径）：
 * <ol>
 *   <li>穿戴者<b>完全免疫</b>「虚空块」({@code goety:void_block}) 与「液态虚空」({@code goety:void_fluid})
 *       带来的<b>伤害与负面效果</b> ✓；</li>
 *   <li>同时免疫<b>虚空之蚀</b>（{@code goety:void_touched}）与<b>缓慢</b>（{@code minecraft:slowness}）✓；</li>
 *   <li>施放<b>虚空法术</b>所需的灵魂能量<b>减半</b> ✓；</li>
 *   <li>获得 <b>20% 虚空系伤害抗性</b> ✓。</li>
 * </ol>
 *
 * <p>行为在 {@code content/modifier/events/VoidGraceHandler}（免疫与减伤）＋
 * {@code mixin/GoetyVoidSoulMixin}（灵魂减半，挂在诡厄 {@code ISpell#SoulCalculation} 上 ✓）。
 *
 * <h2>§733 显示：用匠魂<b>官方</b>的「无等级」方案 ✓</h2>
 * 为什么不直接用 {@code SingleLevelModifier} 的默认行为 ✗：它只在<b>恰好 1 级</b>时不写数字，
 * 而<b>材料特性是按部件累加的</b>（{@code ModifierEntry#merge} = {@code level + other.level}）⇒
 * 一件"镶板 + 锁链基底"都用虚空金属的盔甲上，本特性会是 <b>2 级</b>，
 * 于是退回基类 {@code ModifierLevelDisplay.DEFAULT}（拼罗马数字）⇒ 显示成「虚无恩宠 II」✗。
 * <p>匠魂本体就提供现成方案 ✓：{@link ModifierLevelDisplay#NO_LEVELS}
 * （字节码实测 = 永远只返回 {@code modifier.getDisplayName()}，<b>任何等级都不拼数字</b> ✓）。
 * 本特性是"有/无"型（行为判定只看 {@code level > 0} ✓），所以显示走它最贴切 ✓。
 */
public class VoidGraceModifier extends SingleLevelModifier {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "void_grace"));

    /** 无等级显示（匠魂官方方案 ✓）：不论匠魂给的 level 是几，名字都不带等级数字 ✓ */
    @Override
    public Component getDisplayName(int level) {
        return ModifierLevelDisplay.NO_LEVELS.nameForLevel(this, level);
    }
}
