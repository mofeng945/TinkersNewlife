package com.mofengbaizhi.tinkersnewlife.content.modifier.katana;

// 移植自 TiCEX (MIT): moffy.ticex.modifier.ModifierEmbossment

import com.mofengbaizhi.tinkersnewlife.integration.slashblade.EmbossmentMaterialCapability;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.EmbossmentModifierHook;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.KatanaModifierHooks;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/**
 * <b>「装裱」修饰符</b> —— 逐字移植 TiCEX {@code ModifierEmbossment}（MIT）。
 *
 * <p>它是 {@link EmbossmentMaterialCapability} 的<b>唯一消费者</b> ✓：
 * 装裱输入必须是一个<b>工具部件</b>（{@code ToolPartItem} ✓）✓，
 * 于是把该部件的"材料 × 统计类型"下的<b>全部特性（trait）</b>贴到工具上 ✓，
 * 并把这份材料记进工具的持久化数据 ✓（之后再装裱别的部件会先摘掉上一份 ✓）。
 *
 * <p>⚠ 与 TiCEX 的差异（如实记录 ✓，见备忘录 §1032）：
 * <ul>
 *   <li>TiCEX 把它注册在**通用**材料模块里 ✓，配方面向 {@code tconstruct:modifiable/durability}（所有耐久工具）✗；
 *       本仓的装裱能力只挂给拔刀剑（见 {@link com.mofengbaizhi.tinkersnewlife.integration.slashblade.SBItemCapabilityProvider} ✓）
 *       ⇒ 本修饰符也**只在拔刀剑在场时注册** ✓、配方面向 {@code tinkersnewlife:seram/slashblade} ✓；</li>
 *   <li>无等级 ✓、{@code shouldDisplay(advanced)} 只在进阶模式显示 ✓（与 TiCEX 一致 ✓）。</li>
 * </ul>
 */
public class ModifierEmbossment extends NoLevelsModifier implements EmbossmentModifierHook {

    @Override
    protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, KatanaModifierHooks.EMBOSSMENT);
    }

    @Override
    public boolean applyItem(EmbossmentContext context, int inputIndex, boolean secondary) {
        ItemStack toolStack = context.getToolStack();
        ItemStack inputStack = context.getInputStack(inputIndex);

        if (inputStack.getItem() instanceof ToolPartItem part) {
            toolStack
                    .getCapability(EmbossmentMaterialCapability.EMBOSSMENT_MATERIAL_CAPABILITY)
                    .ifPresent(embossment -> {
                        embossment.accept(toolStack, inputStack, part);
                    });
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldDisplay(boolean advanced) {
        return advanced;
    }
}
