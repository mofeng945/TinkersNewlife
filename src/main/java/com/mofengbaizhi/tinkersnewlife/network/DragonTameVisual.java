package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.DragonTameRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public record DragonTameVisual(ResourceLocation dimension, int playerId, UUID playerUuid,
                               int dragonId, UUID dragonUuid, Vec3 feet, float radius, float height, byte phase) {
    public static final byte ACTIVE = 0, SUCCESS = 1, FAILURE = 2;

    public static void start(ServerPlayer player, LivingEntity dragon) { send(player, dragon, ACTIVE); }
    public static void end(ServerPlayer player, LivingEntity dragon, boolean success) {
        send(player, dragon, success ? SUCCESS : FAILURE);
    }
    private static void send(ServerPlayer player, LivingEntity dragon, byte phase) {
        var bounds = dragon.getBoundingBox();
        var parts = dragon.getParts();
        if (parts != null) for (var part : parts) if (part != null) bounds = bounds.minmax(part.getBoundingBox());
        Vec3 feet = dragon.position();
        double x = Math.max(Math.abs(bounds.minX - feet.x), Math.abs(bounds.maxX - feet.x));
        double z = Math.max(Math.abs(bounds.minZ - feet.z), Math.abs(bounds.maxZ - feet.z));
        float radius = (float)Math.max(2, Math.hypot(x, z) + 1);
        var packet = new DragonTameVisual(dragon.level().dimension().location(), player.getId(), player.getUUID(),
                dragon.getId(), dragon.getUUID(), new Vec3(feet.x, bounds.minY, feet.z), radius, (float)(bounds.maxY - bounds.minY + .6), phase);
        for (var viewer : dragon.level().players()) {
            if (viewer instanceof ServerPlayer serverViewer && viewer != player && viewer.distanceToSqr(dragon) <= 96 * 96) {
                TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverViewer), packet);
            }
        }
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public DragonTameVisual(FriendlyByteBuf in) {
        this(in.readResourceLocation(), in.readVarInt(), in.readUUID(), in.readVarInt(), in.readUUID(),
                new Vec3(in.readDouble(), in.readDouble(), in.readDouble()), in.readFloat(), in.readFloat(), in.readByte());
    }
    public void encode(FriendlyByteBuf out) {
        out.writeResourceLocation(dimension); out.writeVarInt(playerId); out.writeUUID(playerUuid);
        out.writeVarInt(dragonId); out.writeUUID(dragonUuid);
        out.writeDouble(feet.x); out.writeDouble(feet.y); out.writeDouble(feet.z);
        out.writeFloat(radius); out.writeFloat(height); out.writeByte(phase);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DragonTameRenderer.receive(this)));
        ctx.setPacketHandled(true);
    }
}
