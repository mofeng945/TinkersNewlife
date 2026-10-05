package com.mofengbaizhi.tinkersnewlife.content.modifier.katana;

// 移植自 TiCEX (MIT): moffy.ticex.modifier.ModifierHiddenProud

import com.mofengbaizhi.tinkersnewlife.integration.slashblade.KatanaSBUtils;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.EmbossmentModifierHook;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.KatanaModifierHooks;
import java.util.Map.Entry;
import java.util.Random;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;

/**
 * 拔刀剑特性「隐耀魂（Hidden Proud）」—— 逐字移植 TiCEX {@code ModifierHiddenProud}（MIT）。
 *
 * <p>效果：把装裱输入物上的附魔按概率<b>贴到刀上</b>（耀魂碎片 25% / 耀魂 50% / 耀魂锭 75% ✓，
 * 按物品数量每个各掷一次 ✓），同时按输入物给刀加耀魂（每件最多 5000、按附魔值 ×10 计 ✓）、
 * 抬精炼上限（至少 10，上限 200 以内每点还 +1 最大耐久 ✓），并把输入物 NBT 里的
 * {@code SpecialAttackType}／{@code SpecialEffectType} 搬到刀上 ✓。
 *
 * <p>⚠ 与 {@link ModifierKoshirae} 同样：触发入口（TiCEX 的装裱配方流程 ✗）本轮未搬 ✓。
 */
public class ModifierHiddenProud extends NoLevelsModifier implements EmbossmentModifierHook {

    protected TagKey<Item> proudSoulKey;

    public ModifierHiddenProud() {
        proudSoulKey = TagKey.create(Registries.ITEM, new ResourceLocation(SlashBlade.MODID, "proudsouls"));
    }

    @Override
    protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, KatanaModifierHooks.EMBOSSMENT);
    }

    @Override
    public boolean applyItem(EmbossmentContext context, int inputIndex, boolean secondary) {
        ItemStack input = context.getInputStack(inputIndex);
        ItemStack toolStack = context.getToolStack();

        int enchantmentLevel = context.getInputStack(inputIndex).getEnchantmentValue();
        int refineLimit = Math.max(10, enchantmentLevel);

        if (input.isEnchanted()) {
            Random random = new Random();
            for (Entry<Enchantment, Integer> enchantmentEntry : input.getAllEnchantments().entrySet()) {
                if (KatanaSBUtils.disallowedEnchantments.contains(enchantmentEntry.getKey())) {
                    context.setErrorMsg(Component.translatable("recipe.tinkersnewlife.not_allowed_enchantment_slashblade"));
                    return false;
                }
                for (int i = 0; i < input.getCount(); i++) {
                    var probability = 1.0F;
                    if (input.is(SBItems.proudsoul_tiny)) probability = 0.25F;
                    if (input.is(SBItems.proudsoul)) probability = 0.5F;
                    if (input.is(SBItems.proudsoul_ingot)) probability = 0.75F;
                    if (random.nextFloat() <= probability) {
                        KatanaSBUtils.applyEnchantment(toolStack, enchantmentEntry.getKey(), enchantmentEntry.getValue());
                    }
                }
            }
        }

        toolStack
                .getCapability(ItemSlashBlade.BLADESTATE)
                .ifPresent(s -> {
                    s.deserializeNBT(toolStack.getOrCreateTag().getCompound("bladeState"));
                    s.setProudSoulCount(s.getProudSoulCount() + input.getCount() * Math.min(5000, enchantmentLevel * 10));

                    if (input.hasTag()) {
                        CompoundTag nbt = input.getTag();
                        if (nbt.contains("SpecialAttackType")) {
                            s.setSlashArtsKey(ResourceLocation.tryParse(nbt.getString("SpecialAttackType")));
                        } else if (nbt.contains("SpecialEffectType")) {
                            s.addSpecialEffect(ResourceLocation.tryParse(nbt.getString("SpecialEffectType")));
                        }
                    }

                    if (s.getRefine() < refineLimit) {
                        s.setRefine(Math.min(refineLimit, s.getRefine() + input.getCount()));
                        if (s.getRefine() < 200) s.setMaxDamage(s.getMaxDamage() + 1);
                    }

                    toolStack.getOrCreateTag().put("bladeState", s.serializeNBT());
                });

        return true;
    }

    @Override
    public boolean shouldDisplay(boolean advanced) {
        return advanced;
    }
}
