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
 * 服务端→客户端：雇佣状态实时同步（雇佣成功/到期/被他人雇佣等场景刷新交易界面，防止重复上交）。
 */
public class PacketMomoHireState {

    private final int momoId;
    private final boolean hired;
    private final String employer;
    /** TNL1317 剩余雇佣天数（0 = 未雇佣/已过期 ✓） */
    private final int remainingDays;

    public PacketMomoHireState(int momoId, boolean hired, String employer, int remainingDays) {
        this.momoId = momoId;
        this.hired = hired;
        this.employer = employer == null ? "" : employer;
        this.remainingDays = remainingDays;
    }

    public PacketMomoHireState(FriendlyByteBuf buf) {
        this.momoId = buf.readInt();
        this.hired = buf.readBoolean();
        this.employer = buf.readUtf();
        this.remainingDays = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(momoId);
        buf.writeBoolean(hired);
        buf.writeUtf(employer);
        buf.writeInt(remainingDays);
    }

    public static void sendTo(ServerPlayer player, MomoMerchant momo) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketMomoHireState(momo.getId(), momo.isHired(), momo.employerDisplayName(),
                        momo.hiredDaysLeft()));
    }

    public static void handle(PacketMomoHireState packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    if (Minecraft.getInstance().screen instanceof MomoTradeScreen screen
                            && screen.matches(packet.momoId)) {
                        screen.updateHireState(packet.hired, packet.employer, packet.remainingDays);
                    } else if (Minecraft.getInstance().screen
                            instanceof com.mofengbaizhi.tinkersnewlife.client.screen.MomoHireScreen hireScreen
                            && hireScreen.matches(packet.momoId)) {
                        // TNL1317 雇佣界面也要能收到"已雇佣/剩余天数"⇒ 续雇才看得出来 ✓
                        hireScreen.updateHireState(packet.hired, packet.employer, packet.remainingDays);
                    }
                }));
        ctx.get().setPacketHandled(true);
    }
}
