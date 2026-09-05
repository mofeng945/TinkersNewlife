package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：构筑术式拟造物品栏中选定了某个物品（注册名）。
 * 服务端权威校验：术式选中、物品有合成配方、咒力足够 → 发放 60 秒临时物品。
 */
public class PacketConstructSelect {

    private final String itemId;

    public PacketConstructSelect(String itemId) {
        this.itemId = itemId;
    }

    public PacketConstructSelect(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId == null ? "" : itemId);
    }

    public static void handle(PacketConstructSelect packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            int result = ConstructTechnique.forge(player, packet.itemId);
            if (result == 2) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.construct.no_recipe"), true);
            } else if (result == 3) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.technique.no_curse"), true);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
