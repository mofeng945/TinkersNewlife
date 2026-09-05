package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiPhantom;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：鵺（NUE）骑乘输入（每 tick 骑乘时发送）。
 * <p>
 * 原版 PlayerRideableJumping 的 START/STOP_RIDING_JUMP 命令链在客户端对非马坐骑
 * 只发 START 不发 STOP，会导致跳跃标志永远为 true（一直向上飞）；
 * 因此与黑鸟/傀儡一致，改为每 tick 显式上报空格（上升）/潜行（下马）。
 */
public class PacketNueInput {

    private final boolean jump;
    private final boolean shift;

    public PacketNueInput(boolean jump, boolean shift) {
        this.jump = jump;
        this.shift = shift;
    }

    public PacketNueInput(FriendlyByteBuf buf) {
        this.jump = buf.readBoolean();
        this.shift = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(jump);
        buf.writeBoolean(shift);
    }

    public static void handle(PacketNueInput packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof ShikigamiPhantom nue
                    && nue.getShikigamiType() == ShikigamiType.NUE
                    && nue.getState().tamed
                    && nue.getOwnerId() != null && nue.getOwnerId().equals(player.getUUID())) {
                nue.setRideInput(packet.jump, packet.shift);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
