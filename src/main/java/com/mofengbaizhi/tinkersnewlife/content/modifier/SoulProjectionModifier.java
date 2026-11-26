package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.astral.AstralProjection;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.*;
import slimeknights.tconstruct.library.modifiers.hook.interaction.KeybindInteractModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

public final class SoulProjectionModifier extends Modifier implements KeybindInteractModifierHook {
    @Override protected void registerHooks(ModuleHookMap.Builder builder){super.registerHooks(builder);builder.addHook(this,ModifierHooks.ARMOR_INTERACT);}
    @Override public boolean startInteract(IToolStackView tool, ModifierEntry modifier, Player player, EquipmentSlot slot, TooltipKey key){
        if(slot!=EquipmentSlot.HEAD || !IntegrationLoader.isIceAndFire())return false;
        if(player instanceof ServerPlayer sp) AstralProjection.toggle(sp);
        return true;
    }
}
