package com.mofengbaizhi.tinkersnewlife.content.modifier.katana;

// 移植自 TiCEX (MIT): moffy.ticex.modifier.ModifierKonpaku

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.KatanaSBUtils;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.EmbossmentModifierHook;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.KatanaModifierHooks;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 拔刀剑特性「魂魄（Konpaku）」—— 逐字移植 TiCEX {@code ModifierKonpaku}（MIT）。
 *
 * <p>效果：装裱输入物必须是<b>附魔书</b> ✓ ⇒ 把书上的附魔一条条贴到刀上 ✓，
 * 成功贴上的同时**自动给刀加 1 级「附魔供给」修饰符** ✓
 * （{@code Modifiers.ENCHANTMENT_SUPPLIER} ✓ = TiCEX 的 {@code ENCHANTMENT_SUPPLIER_MODIFIER} ✓，
 * 本轮一并搬了 ✓ 见 {@link ModifierEnchantmentSupplier} ✓）。
 *
 * <p>★ §1032 起触发入口已经接通 ✓：配方 {@code data/tinkersnewlife/recipes/tools/modifiers/konpaku.json}
 * 的类型是 {@code tinkersnewlife:embossment_modifier} ✓（{@code inputs = konpaku_core} ＋
 * {@code emboss_inputs = 附魔书} ✓）⇒ 在<b>修补台/工匠砧</b>里放上刀 ＋ 魂魄核心 ＋ 附魔书时 ✓
 * {@link #applyItem} 会被 {@code EmbossmentModifierRecipe#getValidatedResult} 直接调用 ✓。
 */
public class ModifierKonpaku extends NoLevelsModifier implements EmbossmentModifierHook {

    @Override
    protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, KatanaModifierHooks.EMBOSSMENT);
    }

    @Override
    public boolean applyItem(EmbossmentContext context, int inputIndex, boolean secondary) {
        ItemStack input = context.getInputStack(inputIndex);
        ItemStack toolStack = context.getToolStack();

        boolean result = false;
        if (input.getItem().equals(Items.ENCHANTED_BOOK)) {
            ToolStack tool = ToolStack.from(toolStack);
            Map<Enchantment, Integer> bookEnchantments = EnchantmentHelper.getEnchantments(input);

            for (Entry<Enchantment, Integer> entry : bookEnchantments.entrySet()) {
                if (KatanaSBUtils.disallowedEnchantments.contains(entry.getKey())) {
                    context.setErrorMsg(Component.translatable("recipe.tinkersnewlife.not_allowed_enchantment_slashblade"));
                    return false;
                }
                if (KatanaSBUtils.applyEnchantment(toolStack, entry.getKey(), entry.getValue())) {
                    result = true;
                    if (Modifiers.ENCHANTMENT_SUPPLIER != null
                            && tool.getModifierLevel(Modifiers.ENCHANTMENT_SUPPLIER.getId()) < 1) {
                        tool.addModifier(Modifiers.ENCHANTMENT_SUPPLIER.getId(), 1);
                    }
                }
            }
        } else {
            context.setErrorMsg(Component.translatable("recipe.tinkersnewlife.required_enchanted_book"));
        }
        return result;
    }
}
