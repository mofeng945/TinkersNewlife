package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YogSothothTraitHandler {

    private static final ModifierId YOG_SOTHOTH_GIFT = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "yog_sothoth_gift")
    );

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty()) return;

        ToolStack tool = ToolStack.from(mainHand);
        if (tool == null) return;

        int level = tool.getModifierLevel(YOG_SOTHOTH_GIFT);
        if (level <= 0) return;

        int original = event.getAmount();
        if (original > 0) {
            event.setAmount(original * 6);
        }
    }
}