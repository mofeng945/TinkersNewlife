package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.AncientCursedScrollItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** §1277 air swing (left click at nothing) with the ancient cursed scroll. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScrollAirSwingHandler {

    private ScrollAirSwingHandler() {
    }

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        try {
            if (!event.isAttack()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.screen != null) {
                return;
            }
            if (mc.hitResult != null
                    && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                if (mc.player.getItemInHand(hand).getItem() instanceof AncientCursedScrollItem) {
                    TinkersNewlife.CHANNEL.sendToServer(
                            new com.mofengbaizhi.tinkersnewlife.network.curse.PacketScrollCast());
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}