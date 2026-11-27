package com.mofengbaizhi.tinkersnewlife.network.momo;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.screen.MomoTradeScreen;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：**今日各槽已买次数**的增量同步（用户报"缺货必须重开界面才变" ✗）。
 *
 * <p>买成功一次就发一次 ✓ 客户端**就地**更新当前交易屏（不重开界面 ⇒ 不闪 ✗）✓
 * ⇒ 缺货行**立刻**变灰 ✓。做法照 `PacketMomoHireState` 那套（服务端发包 ⇒ 客户端 `instanceof` 当前屏 ⇒ 直接改字段 ✓）。
 */
public class PacketMomoSoldState {

    private final int momoId;
    private final int[] sold;

    private final Integer favor;

    public PacketMomoSoldState(int momoId, int[] sold, int favor) {
        this.momoId = momoId;
        this.sold = sold == null ? new int[0] : sold;
        this.favor = favor;
    }

    public PacketMomoSoldState(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
        int n = buf.readVarInt();
        this.sold = new int[n];
        for (int i = 0; i < n; i++) this.sold[i] = buf.readVarInt();
        this.favor = buf.isReadable() ? buf.readVarInt() : null;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
        buf.writeVarInt(sold.length);
        for (int s : sold) buf.writeVarInt(s);
        if (favor != null) buf.writeVarInt(favor);
    }

    public static void sendTo(ServerPlayer player, MomoMerchant momo) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketMomoSoldState(momo.getId(), momo.soldToday(),
                        com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor.favorAsSeenByMomo(player)));
    }

    public static void handle(PacketMomoSoldState packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.update(packet)));
        ctx.get().setPacketHandled(true);
    }

    private static final class Client {
        private static void update(PacketMomoSoldState packet) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof MomoTradeScreen screen && screen.matches(packet.momoId)) {
                screen.updatePurchase(packet.sold, packet.favor);
            } else if (packet.favor != null && mc.player != null) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.momo.bought"), true);
            }
        }
    }
}
