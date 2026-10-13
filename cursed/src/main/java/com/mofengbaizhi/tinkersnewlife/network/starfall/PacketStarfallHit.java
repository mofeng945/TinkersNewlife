package com.mofengbaizhi.tinkersnewlife.network.starfall;

import com.mofengbaizhi.tinkersnewlife.client.StarfallHitCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** TNL879 \u547d\u4e2d\u7279\u6548\u5305\uff08\u2605\u7167\u642c\u53c2\u8003\u5b9e\u73b0 StarHitPacket \u2713\uff09 */
public class PacketStarfallHit {

    private final double x;
    private final double y;
    private final double z;
    private final double dx;
    private final double dy;
    private final double dz;

    public PacketStarfallHit(Vec3 pos, Vec3 dir) {
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.dx = dir.x;
        this.dy = dir.y;
        this.dz = dir.z;
    }

    public PacketStarfallHit(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.dx = buf.readDouble();
        this.dy = buf.readDouble();
        this.dz = buf.readDouble();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(dx);
        buf.writeDouble(dy);
        buf.writeDouble(dz);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            StarfallHitCache.add(new Vec3(x, y, z), new Vec3(dx, dy, dz), mc.level.getGameTime());
        }));
        ctx.get().setPacketHandled(true);
    }
}
