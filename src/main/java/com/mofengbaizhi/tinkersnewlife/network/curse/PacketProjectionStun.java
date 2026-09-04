package com.mofengbaizhi.tinkersnewlife.network.curse;

import com.mofengbaizhi.tinkersnewlife.client.data.ClientProjectionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：投射咒法 罚站状态同步。
 * stunned=true 时客户端禁用鼠标键盘（移动/跳跃/视角/攻击/交互），并锁定视角到传入 yaw/pitch。
 */
public class PacketProjectionStun {

    private final boolean stunned;
    private final float yaw;
    private final float pitch;

    public PacketProjectionStun(boolean stunned, float yaw, float pitch) {
        this.stunned = stunned;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public PacketProjectionStun(FriendlyByteBuf buf) {
        this.stunned = buf.readBoolean();
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(stunned);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static void handle(PacketProjectionStun packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientProjectionData.update(packet.stunned, packet.yaw, packet.pitch)));
        ctx.get().setPacketHandled(true);
    }
}
