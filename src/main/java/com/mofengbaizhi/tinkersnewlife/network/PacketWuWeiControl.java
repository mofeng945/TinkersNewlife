package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.client.ClientWuWeiData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端→客户端：通知客户端当前被无为转变操控的实体 id（0 = 无），
 * 客户端据此在相机绑定该实体时发送 {@link PacketWuWeiInput} 操控输入。
 */
public class PacketWuWeiControl {

    private final int entityId;

    public PacketWuWeiControl(int entityId) {
        this.entityId = entityId;
    }

    public PacketWuWeiControl(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public static void handle(PacketWuWeiControl packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientWuWeiData.setControlledEntity(packet.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
