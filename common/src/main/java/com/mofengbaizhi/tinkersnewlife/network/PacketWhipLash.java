package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.item.WhipItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Input only: the server decides the hand, cooldown, direction and all hit results. */
public record PacketWhipLash() {
    public PacketWhipLash(FriendlyByteBuf ignored) { this(); }
    public void encode(FriendlyByteBuf ignored) {}

    public static void handle(PacketWhipLash ignored, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) WhipItem.requestLash(player);
        });
        context.setPacketHandled(true);
    }
}
