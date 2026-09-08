package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.behavior.AttributeModule;
import slimeknights.tconstruct.library.modifiers.modules.build.ModifierSlotModule;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 无槽位强化·终末烙印：安装不占槽，给工具 +1 能力槽，代价是 -10% 移动速度
 * （movement_speed multiply_base，全装备槽生效——与匠魂原版 heavy/solid 一致）。
 * 单级（配方 level 1），不可洗掉（remove_blacklist）、不可剥离（extract_blacklist）。
 */
public class FinalBrandModifier extends Modifier implements TooltipModifierHook {

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        // +1 能力槽（volatile data）
        hookBuilder.addModule(ModifierSlotModule.slot(SlotType.ABILITY).amount(1, 0));
        // -10% 移动速度（multiply_base，全装备槽）
        hookBuilder.addModule(AttributeModule.builder(Attributes.MOVEMENT_SPEED, Operation.MULTIPLY_BASE)
                .uniqueFrom(new ResourceLocation(TinkersNewlife.MOD_ID, "final_brand"))
                .slots(EquipmentSlot.values())
                .amount(0, -0.1f));
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.final_brand.slot_bonus"));
    }
}
