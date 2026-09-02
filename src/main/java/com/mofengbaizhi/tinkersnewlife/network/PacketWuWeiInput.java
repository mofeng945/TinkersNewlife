package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：无为转变 变形实体的操控输入（每 tick 相机绑定变形实体时发送）。
 * 携带：目标实体 id、前进/侧移（zza/xxa）、跳跃、玩家当前视角（yRot/xRot）。
 */
public class PacketWuWeiInput {

    private final int entityId;
    private final float zza;
    private final float xxa;
    private final boolean jumping;
    private final float yRot;
    private final float xRot;

    public PacketWuWeiInput(int entityId, float zza, float xxa, boolean jumping, float yRot, float xRot) {
        this.entityId = entityId;
        this.zza = zza;
        this.xxa = xxa;
        this.jumping = jumping;
        this.yRot = yRot;
        this.xRot = xRot;
    }

    public PacketWuWeiInput(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.zza = buf.readFloat();
        this.xxa = buf.readFloat();
        this.jumping = buf.readBoolean();
        this.yRot = buf.readFloat();
        this.xRot = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeFloat(zza);
        buf.writeFloat(xxa);
        buf.writeBoolean(jumping);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
    }

    public static void handle(PacketWuWeiInput packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            WuWeiHandler.setInput(player, packet.entityId, packet.zza, packet.xxa,
                    packet.jumping, packet.yRot, packet.xRot);
        });
        ctx.get().setPacketHandled(true);
    }
}
