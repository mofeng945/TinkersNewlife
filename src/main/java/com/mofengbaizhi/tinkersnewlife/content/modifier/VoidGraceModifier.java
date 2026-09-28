package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;

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
 */
public class VoidGraceModifier extends SingleLevelModifier {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "void_grace"));
}
