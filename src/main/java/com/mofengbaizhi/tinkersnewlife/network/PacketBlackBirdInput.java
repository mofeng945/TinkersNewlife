package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique;
import com.mofengbaizhi.tinkersnewlife.content.entity.BlackBirdEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：黑鸟操控输入（每 tick 相机绑定黑鸟时发送）。
 * 携带：前进/侧移输入（zza/xxa）、跳跃、Shift（俯冲）、玩家当前视角（yRot/xRot，黑鸟朝向用）。
 */
public class PacketBlackBirdInput {

    private final float zza;
    private final float xxa;
    private final boolean jumping;
    private final boolean shift;
    private final float yRot;
    private final float xRot;

    public PacketBlackBirdInput(float zza, float xxa, boolean jumping, boolean shift, float yRot, float xRot) {
        this.zza = zza;
        this.xxa = xxa;
        this.jumping = jumping;
        this.shift = shift;
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public PacketBlackBirdInput(FriendlyByteBuf buf) {
        this.zza = buf.readFloat();
        this.xxa = buf.readFloat();
        this.jumping = buf.readBoolean();
        this.shift = buf.readBoolean();
        this.yRot = buf.readFloat();
        this.xRot = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeFloat(zza);
        buf.writeFloat(xxa);
        buf.writeBoolean(jumping);
        buf.writeBoolean(shift);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
    }

    public static void handle(PacketBlackBirdInput packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlackBirdEntity bird = BlackBirdTechnique.findActiveBird(player);
            if (bird != null) {
                bird.setInput(packet.zza, packet.xxa, packet.jumping, packet.shift, packet.yRot, packet.xRot);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
