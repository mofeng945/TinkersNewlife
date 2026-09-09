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
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 近战特性·神圣之力（神灵金近战头自带，联动铁魔法/启示录）：
 * 提高持有者 180 点法力值；武器自带注入法术 火墙术Lv5 / 天使之翼Lv5 / 治愈之环Lv10（模仿
 * RevelationFix ValetteinItemMixin）；火墙无视无敌帧。见 {@code DivinePowerHandler}。
 */
public class DivinePowerModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "divine_power"));

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.divine_power.tip"));
    }
}
