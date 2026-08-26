package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.modifier.QuantumBagModifier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketOpenBag {

    private final int hand;

    public PacketOpenBag(int hand) {
        this.hand = hand;
    }

    public PacketOpenBag(FriendlyByteBuf buf) {
        this.hand = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(hand);
    }

    public static void handle(PacketOpenBag packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // ⭐ 校验 hand 参数（0=主手，1=副手），防止恶意客户端传其他值
            ItemStack stack = packet.hand == 0 ? player.getMainHandItem()
                    : packet.hand == 1 ? player.getOffhandItem()
                    : ItemStack.EMPTY;
            QuantumBagModifier.tryOpenBag(player, stack);
        });
        ctx.get().setPacketHandled(true);
    }
}