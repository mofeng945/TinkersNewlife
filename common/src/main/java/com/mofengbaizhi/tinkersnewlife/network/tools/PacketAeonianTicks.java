package com.mofengbaizhi.tinkersnewlife.network.tools;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Explicit pollution count sync; item cooldown percentages do not encode a duration. */
public record PacketAeonianTicks(int ticks) {
    public PacketAeonianTicks(FriendlyByteBuf buf) { this(buf.readVarInt()); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeVarInt(ticks); }
    public static void handle(PacketAeonianTicks packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> Client.accept(packet.ticks));
        ctx.get().setPacketHandled(true);
    }
    private static final class Client {
        static void accept(int ticks) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) player.getPersistentData().putLong("tnl_ao_client_until",
                    player.level().getGameTime() + ticks);
        }
    }
}
