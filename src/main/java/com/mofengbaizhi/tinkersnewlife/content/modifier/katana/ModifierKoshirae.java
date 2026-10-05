package com.mofengbaizhi.tinkersnewlife.content.modifier.katana;

// 移植自 TiCEX (MIT): moffy.ticex.modifier.ModifierKoshirae

import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.EmbossmentModifierHook;
import com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook.KatanaModifierHooks;
import java.util.EnumSet;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap.Builder;

/**
 * 拔刀剑特性「拵（Koshirae）」—— 逐字移植 TiCEX {@code ModifierKoshirae}（MIT）。
 *
 * <p>效果：把"装裱输入物品"里带的刀状态（proudSoul 耀魂 / killCount 击杀数 / RepairCounter 精炼）
 * <b>取较大值</b>合并进本刀 ✓（也就是把另一把刀的"底蕴"继承过来 ✓）。
 * 要求本刀是 {@code BEWITCHED}（妖刀）✓，否则给出错误提示并不生效 ✓。
 *
 * <p>★ §1032 起触发入口已经接通 ✓：配方 {@code data/tinkersnewlife/recipes/tools/modifiers/koshirae.json}
 * 的类型是 {@code tinkersnewlife:single_embossment_modifier} ✓ ⇒ 在<b>修补台/工匠砧</b>里
 * 放上刀 ＋ 被装裱过的 {@code tinkersnewlife:catalyst_slashblade} 时 ✓
 * {@link #applyItem} 会被 {@code SingleEmbossmentModifierRecipe#getValidatedResult} 直接调用 ✓。
 */
public class ModifierKoshirae extends NoLevelsModifier implements EmbossmentModifierHook {

    @Override
    protected void registerHooks(Builder hookBuilder) {
        hookBuilder.addHook(this, KatanaModifierHooks.EMBOSSMENT);
    }

    @Override
    public boolean applyItem(EmbossmentContext context, int inputIndex, boolean secondary) {
        ItemStack input = context.getInputStack(inputIndex);
        ItemStack toolStack = context.getToolStack();

        EnumSet<SwordType> swordTypes = SwordType.from(toolStack);
        if (!swordTypes.contains(SwordType.BEWITCHED)) {
            context.setErrorMsg(Component.translatable("recipe.tinkersnewlife.not_be_witched"));
            return false;
        }

        // ⚠ §1032 有意差异（TiCEX 没有这一步 ✗，见备忘录 §1032）：
        //   装裱输入必须是"被浇铸过一把刀"的催化部件（那份刀的存档在 input 的 embossed 标签里 ✓，
        //   由 EmbossmentCastingRecipe 写入 ✓）。若 embossed 里没有 bladeState（例如玩家拿了
        //   一个没装裱过的催化部件 ✗），TiCEX 会把一份**空**的 bladeState 反序列化回刀上 ✗
        //   ⇒ 由于 SlashBlade 的 deserializeNBT 对缺失 key 一律取默认值，
        //     这把刀的 maxDamage / translationKey / 基础攻击力会被**清零** ✗✗（实测等价于毁刀）。
        //   本仓这里直接拒绝并给错误提示 ✓ —— 正常流程（走过浇铸台的催化部件）行为与 TiCEX 完全一致 ✓。
        CompoundTag compoundTag = input.getOrCreateTag().getCompound("embossed");
        CompoundTag bladeStateTag = compoundTag.contains("tag")
                ? compoundTag.getCompound("tag").getCompound("bladeState")
                : compoundTag.getCompound("bladeState");
        if (bladeStateTag.isEmpty()) {
            context.setErrorMsg(Component.translatable("recipe.tinkersnewlife.embossment_not_allowed"));
            return false;
        }

        toolStack
                .getCapability(ItemSlashBlade.BLADESTATE)
                .ifPresent(resultState -> {
                    int currentProudSoul = resultState.getProudSoulCount();
                    int currentKillCount = resultState.getKillCount();
                    int currentRefineCount = resultState.getRefine();

                    bladeStateTag.putInt("proudSoul", Math.max(bladeStateTag.getInt("proudSoul"), currentProudSoul));
                    bladeStateTag.putInt("killCount", Math.max(bladeStateTag.getInt("killCount"), currentKillCount));
                    bladeStateTag.putInt("RepairCounter",
                            Math.max(bladeStateTag.getInt("RepairCounter"), currentRefineCount));

                    resultState.deserializeNBT(bladeStateTag);
                    toolStack.getOrCreateTag().put("bladeState", bladeStateTag);
                });
        return true;
    }

    @Override
    public boolean shouldDisplay(boolean advanced) {
        return advanced;
    }
}
