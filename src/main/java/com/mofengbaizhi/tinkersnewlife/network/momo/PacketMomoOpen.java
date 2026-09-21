package com.mofengbaizhi.tinkersnewlife.network.momo;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.screen.MomoTradeScreen;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;
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
 * 服务端 → 客户端：打开墨默的交易界面。
 *
 * <p>携带：实体 id / 6 个报价（商品 + 原价）/ 受雇状态 / 雇主名 / **好感度** / **今日各槽已买次数**。
 * 后两项是给客户端算**折后价**与**缺货变灰**用的（持久数据客户端读不到，必须随包同步）。
 */
public class PacketMomoOpen {

    private final int momoId;
    private final List<MomoMerchant.Offer> offers;
    private final boolean hired;
    private final String employer;
    private final int favor;
    private final int[] sold;

    public PacketMomoOpen(int momoId, List<MomoMerchant.Offer> offers, boolean hired, String employer,
                          int favor, int[] sold) {
        this.momoId = momoId;
        this.offers = offers == null ? new ArrayList<>() : offers;
        this.hired = hired;
        this.employer = employer == null ? "" : employer;
        this.favor = favor;
        this.sold = sold == null ? new int[0] : sold;
    }

    public PacketMomoOpen(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
        int n = buf.readVarInt();
        List<MomoMerchant.Offer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack stack = buf.readItem();
            int price = buf.readVarInt();
            list.add(new MomoMerchant.Offer(stack, price));
        }
        this.offers = list;
        this.hired = buf.readBoolean();
        this.employer = buf.readUtf();
        this.favor = buf.readInt();
        int soldN = buf.readVarInt();
        this.sold = new int[soldN];
        for (int i = 0; i < soldN; i++) this.sold[i] = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
        buf.writeVarInt(offers.size());
        for (MomoMerchant.Offer offer : offers) {
            buf.writeItem(offer.result());
            buf.writeVarInt(offer.price());
        }
        buf.writeBoolean(hired);
        buf.writeUtf(employer);
        buf.writeInt(favor);
        buf.writeVarInt(sold.length);
        for (int s : sold) buf.writeVarInt(s);
    }

    public static void sendTo(ServerPlayer player, MomoMerchant momo) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketMomoOpen(momo.getId(), momo.getOffers(), momo.isHired(), momo.employerDisplayName(),
                        MomoFavor.get(player), momo.soldToday()));
    }

    public static void handle(PacketMomoOpen packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> Minecraft.getInstance().setScreen(new MomoTradeScreen(
                        packet.momoId, packet.offers, packet.hired, packet.employer, packet.favor, packet.sold))));
        ctx.get().setPacketHandled(true);
    }
}
