package com.mofengbaizhi.tinkersnewlife.network.stuck;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** TNL927 \u2605\u670d\u52a1\u7aef \u2192 \u5ba2\u6237\u7aef\uff1a\u2605\u67d0\u4e2a\u751f\u7269\u8eab\u4e0a\u662f\u5426\u6709\u9ed1\u7bad \u2713 */
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
