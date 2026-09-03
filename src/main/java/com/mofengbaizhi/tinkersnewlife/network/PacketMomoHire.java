package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：点击交易界面左侧"雇佣"栏位，支付 1 个拉莱耶的呼唤雇佣墨默一天。
 */
public class PacketMomoHire {

    private final int momoId;

    public PacketMomoHire(int momoId) {
        this.momoId = momoId;
    }

    public PacketMomoHire(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
    }

    public static void handle(PacketMomoHire packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.level().getEntity(packet.momoId) instanceof MomoMerchant momo)) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.momo.gone"), true);
                return;
            }
            MomoMerchant.HireResult result = momo.hireFrom(player);
            switch (result) {
                case HIRED -> {
                    momo.playTradeSuccessSound();
                    player.displayClientMessage(Component.translatable("message.tinkersnewlife.momo.hired"), true);
                }
                case RENEWED -> {
                    momo.playTradeSuccessSound();
                    player.displayClientMessage(Component.translatable("message.tinkersnewlife.momo.renewed"), true);
                }
                case HIRED_BY_OTHER -> player.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.momo.hired_by_other"), true);
                case NO_ITEM -> player.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.momo.need_rlyeh"), true);
                case TOO_FAR -> player.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.momo.too_far"), true);
                default -> {
                    // DEAD 静默
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
