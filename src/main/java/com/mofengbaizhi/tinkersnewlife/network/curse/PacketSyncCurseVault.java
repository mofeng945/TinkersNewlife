package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.data.ClientVaultData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 服务端 → 客户端：某个呪蔵的存量（回应 {@link PacketQueryCurseVault}，供准星提示显示） */
public class PacketSyncCurseVault {

    private final BlockPos pos;
    private final double power;

    public PacketSyncCurseVault(BlockPos pos, double power) {
        this.pos = pos;
        this.power = power;
    }

    public PacketSyncCurseVault(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.power = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeDouble(power);
    }

    public static void handle(PacketSyncCurseVault packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.accept(packet)));
        ctx.get().setPacketHandled(true);
    }

    private static final class Client {
        private static void accept(PacketSyncCurseVault packet) {
            ClientVaultData.update(packet.pos, packet.power);
        }
    }
}
