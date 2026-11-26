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
                               int dragonId, UUID dragonUuid, Vec3 feet, float radius, byte phase) {
    public static final byte ACTIVE = 0, SUCCESS = 1, FAILURE = 2;

    public static void start(ServerPlayer player, LivingEntity dragon) { send(player, dragon, ACTIVE); }
    public static void end(ServerPlayer player, LivingEntity dragon, boolean success) {
        send(player, dragon, success ? SUCCESS : FAILURE);
    }
    private static void send(ServerPlayer player, LivingEntity dragon, byte phase) {
        var packet = new DragonTameVisual(dragon.level().dimension().location(), player.getId(), player.getUUID(),
                dragon.getId(), dragon.getUUID(), dragon.position(), Math.max(1f, dragon.getBbWidth() * .8f), phase);
        for (var viewer : dragon.level().players()) {
            if (viewer instanceof ServerPlayer serverViewer && viewer != player && viewer.distanceToSqr(dragon) <= 96 * 96) {
                TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverViewer), packet);
            }
        }
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public DragonTameVisual(FriendlyByteBuf in) {
        this(in.readResourceLocation(), in.readVarInt(), in.readUUID(), in.readVarInt(), in.readUUID(),
                new Vec3(in.readDouble(), in.readDouble(), in.readDouble()), in.readFloat(), in.readByte());
    }
    public void encode(FriendlyByteBuf out) {
        out.writeResourceLocation(dimension); out.writeVarInt(playerId); out.writeUUID(playerUuid);
        out.writeVarInt(dragonId); out.writeUUID(dragonUuid);
        out.writeDouble(feet.x); out.writeDouble(feet.y); out.writeDouble(feet.z);
        out.writeFloat(radius); out.writeByte(phase);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DragonTameRenderer.receive(this)));
        ctx.setPacketHandled(true);
    }
}
