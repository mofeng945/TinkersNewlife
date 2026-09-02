package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：点击墨默交易界面的某个槽位，请求购买。
 * 服务端校验距离/存活/货币后执行交易，失败以 actionbar 消息反馈。
 */
public class PacketMomoBuy {

    private final int momoId;
    private final int slot;

    public PacketMomoBuy(int momoId, int slot) {
        this.momoId = momoId;
        this.slot = slot;
    }

    public PacketMomoBuy(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
        this.slot = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
        buf.writeVarInt(slot);
    }

    public static void handle(PacketMomoBuy packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.level().getEntity(packet.momoId) instanceof MomoMerchant momo)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.momo.gone"), true);
                return;
            }
            MomoMerchant.BuyResult result = momo.buyFrom(player, packet.slot);
            switch (result) {
                case OK -> {
                    momo.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
                    if (momo.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                                momo.getX(), momo.getY() + 1.6, momo.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                    }
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tinkersnewlife.momo.bought"), true);
                }
                case INSUFFICIENT -> player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.momo.insufficient"), true);
                case TOO_FAR -> player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.momo.too_far"), true);
                default -> {
                    // NO_OFFER / DEAD：静默
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
