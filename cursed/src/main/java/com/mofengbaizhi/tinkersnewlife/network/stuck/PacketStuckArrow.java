package com.mofengbaizhi.tinkersnewlife.network.stuck;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** TNL927 ★服务端 → 客户端：★某个生物身上是否有黑箭 ✓ */
public class PacketStuckArrow {

    private final UUID id;
    private final boolean present;

    public PacketStuckArrow(UUID id, boolean present) {
        this.id = id;
        this.present = present;
    }

    public PacketStuckArrow(FriendlyByteBuf buf) {
        this.id = buf.readUUID();
        this.present = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.id);
        buf.writeBoolean(this.present);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.mofengbaizhi.tinkersnewlife.client.handler.StuckArrowClientHandler.onSync(this.id, this.present)));
        ctx.get().setPacketHandled(true);
    }
}
