package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 客户端→服务端：P 键请求打开 无为转变 形态选择界面。
 * 服务端校验已拥有术式后，下发击杀记录列表并打开 UI（S2C: PacketOpenWuWeiScreen）。
 */
public class PacketWuWeiOpenGui {

    public PacketWuWeiOpenGui() {}

    public PacketWuWeiOpenGui(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketWuWeiOpenGui packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!WuWeiHandler.hasTechnique(player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.domain.no_core"), true);
                return;
            }
            TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketOpenWuWeiScreen(WuWeiHandler.getRecordedForms(player)));
        });
        ctx.get().setPacketHandled(true);
    }
}
