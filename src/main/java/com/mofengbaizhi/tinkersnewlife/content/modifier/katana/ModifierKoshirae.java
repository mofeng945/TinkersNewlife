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
 * <p>⚠ 触发入口是 TiCEX 自家的"装裱（embossment）"配方流程 ✗ —— 那一整套配方体系本轮**没有搬** ✓
 * （用户口径：配方先简化 ✓）⇒ 本特性目前是"注册上了、数据正确、可用简化配方获得"，
 * 但 {@link #applyItem} 暂时没有流程内调用者 ✓（如实记录在备忘录里 ✓）。
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
        if (swordTypes.contains(SwordType.BEWITCHED)) {
            toolStack
                    .getCapability(ItemSlashBlade.BLADESTATE)
                    .ifPresent(resultState -> {
                        CompoundTag compoundTag = input
                                .getOrCreateTag()
                                .getCompound("embossed");
                        CompoundTag bladeStateTag;
                        if (compoundTag.contains("tag")) {
                            bladeStateTag = compoundTag.getCompound("tag").getCompound("bladeState");
                        } else {
                            bladeStateTag = compoundTag.getCompound("bladeState");
                        }

                        int currentProudSoul = resultState.getProudSoulCount();
                        int currentKillCount = resultState.getKillCount();
                        int currentRefineCount = resultState.getRefine();

                        bladeStateTag.putInt("proudSoul", Math.max(bladeStateTag.getInt("proudSoul"), currentProudSoul));
                        bladeStateTag.putInt("killCount", Math.max(bladeStateTag.getInt("killCount"), currentKillCount));
                        bladeStateTag.putInt("RepairCounter", Math.max(bladeStateTag.getInt("RepairCounter"), currentRefineCount));

                        resultState.deserializeNBT(bladeStateTag);
                        toolStack.getOrCreateTag().put("bladeState", bladeStateTag);
                    });
            return true;
        } else {
            context.setErrorMsg(Component.translatable("recipe.tinkersnewlife.not_be_witched"));
        }
        return false;
    }

    @Override
    public boolean shouldDisplay(boolean advanced) {
        return advanced;
    }
}
