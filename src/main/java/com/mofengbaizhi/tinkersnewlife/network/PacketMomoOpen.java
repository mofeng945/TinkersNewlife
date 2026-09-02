package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.screen.MomoTradeScreen;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端→客户端：打开 墨默 交易界面。
 * 携带实体 id 与 6 个售卖槽位（商品 + 价格，货币按槽位固定：0-3 残骸 / 4-5 矿石）。
 */
public class PacketMomoOpen {

    private final int momoId;
    private final List<MomoMerchant.Offer> offers;

    public PacketMomoOpen(int momoId, List<MomoMerchant.Offer> offers) {
        this.momoId = momoId;
        this.offers = offers == null ? new ArrayList<>() : offers;
    }

    public PacketMomoOpen(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
        int n = buf.readVarInt();
        this.offers = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack stack = buf.readItem();
            int price = buf.readVarInt();
            this.offers.add(new MomoMerchant.Offer(stack, price));
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
        buf.writeVarInt(offers.size());
        for (MomoMerchant.Offer offer : offers) {
            buf.writeItem(offer.result());
            buf.writeVarInt(offer.price());
        }
    }

    public static void sendTo(ServerPlayer player, MomoMerchant momo) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketMomoOpen(momo.getId(), momo.getOffers()));
    }

    public static void handle(PacketMomoOpen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(new MomoTradeScreen(packet.momoId, packet.offers))));
        ctx.get().setPacketHandled(true);
    }
}
