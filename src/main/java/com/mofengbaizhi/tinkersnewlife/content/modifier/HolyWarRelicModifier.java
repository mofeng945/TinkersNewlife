package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.build.ModifierSlotModule;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 铁魔法联动特性·<b>圣战之遗</b>（材料「炽金」自带，<b>无等级</b>，通用特性）：
 *
 * <p>"自带的每种槽位数量各加一个，再额外增加一个能力槽" ✓ ——
 * TCon 3.11 的槽位类型只有三种（{@link SlotType#UPGRADE 升级} / {@link SlotType#DEFENSE 防御} /
 * {@link SlotType#ABILITY 能力}，没有"灵魂槽"）→ 因此实际效果是
 * <b>升级 +1、防御 +1、能力 +2</b>。
 */
public class HolyWarRelicModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "holy_war_relic"));

    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        // 每种槽位 +1，能力槽再额外 +1 → 能力共 +2
        hookBuilder.addModule(ModifierSlotModule.slot(SlotType.UPGRADE).amount(1, 0));
        hookBuilder.addModule(ModifierSlotModule.slot(SlotType.DEFENSE).amount(1, 0));
        hookBuilder.addModule(ModifierSlotModule.slot(SlotType.ABILITY).amount(2, 0));
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.holy_war_relic.tip"));
    }
}
