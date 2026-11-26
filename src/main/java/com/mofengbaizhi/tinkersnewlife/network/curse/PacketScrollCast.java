package com.mofengbaizhi.tinkersnewlife.network.curse;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** §1277 client -> server: swing at air while holding the ancient cursed scroll. */
public class PacketScrollCast {

    public PacketScrollCast() {
    }

    public PacketScrollCast(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public static void handle(PacketScrollCast packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler.blocksUse(player)) {
                return;
            }
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.AncientCursedScrollItem) {
                    com.mofengbaizhi.tinkersnewlife.content.item.AncientCursedScrollItem.castFromScroll(player, stack);
                    return;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}