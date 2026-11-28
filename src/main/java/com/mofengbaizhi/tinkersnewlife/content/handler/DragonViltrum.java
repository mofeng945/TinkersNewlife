package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;

public final class DragonViltrum {
    public static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("dragon_viltrum"));
    private static final String ENABLED = "tnlDragonViltrum";
    private static final String READY_AT = "tnlViltrumReadyAt";
    private static final int COOLDOWN = 400;
    private DragonViltrum() {}

    public static void mark(Entity dragon, boolean enabled) {
        if (enabled) dragon.getPersistentData().putBoolean(ENABLED, true);
        else dragon.getPersistentData().remove(ENABLED);
    }

    public static void activate(net.minecraft.server.level.ServerPlayer rider) {
        if (!rider.isAlive() || rider.isSpectator() || !(rider.getVehicle() instanceof TamableAnimal dragon)
                || dragon.getControllingPassenger() != rider || !dragon.isOwnedBy(rider)
                || !(dragon.level() instanceof ServerLevel level) || !dragon.isAlive()
                || !dragon.getPersistentData().getBoolean(ENABLED) || !stageThree(dragon)
                || dragon.getPersistentData().getLong(READY_AT) > level.getGameTime()) return;
        var origin = dragon.getBoundingBox().getCenter();
        var forward = net.minecraft.world.phys.Vec3.directionFromRotation(0, dragon.getYRot());
        double range = dragon.getBbWidth() * .5 + 6;
        var victim = level.getEntitiesOfClass(LivingEntity.class, dragon.getBoundingBox().inflate(6), target -> {
            var delta = target.getBoundingBox().getCenter().subtract(origin);
            var flat = delta.multiply(1, 0, 1);
            return target != rider && eligible(dragon, target) && flat.lengthSqr() <= range * range
                    && forward.dot(flat.normalize()) >= .707 && dragon.hasLineOfSight(target);
        }).stream().min(java.util.Comparator.comparingDouble(dragon::distanceToSqr)).orElse(null);
        if (victim == null) return;
        if (dragon.getPassengers().size() != 1 || !victim.startRiding(dragon, true)) return;
        dragon.getPersistentData().putLong(READY_AT, level.getGameTime() + COOLDOWN);
    }

    private static boolean eligible(TamableAnimal dragon, LivingEntity victim) {
        return victim != dragon && victim.level() == dragon.level() && victim.isAlive() && !victim.isSpectator()
                && !dragon.isAlliedTo(victim) && !victim.isAlliedTo(dragon) && !dragon.isOwnedBy(victim)
                && !dragon.isPassenger() && !dragon.isOrderedToSit()
                && !victim.isVehicle() && !victim.isPassenger()
                && victim.getBbWidth() < EntityType.IRON_GOLEM.getWidth() && victim.getBbHeight() < EntityType.IRON_GOLEM.getHeight();
    }

    private static boolean stageThree(LivingEntity dragon) {
        try { return ((Number)dragon.getClass().getMethod("getDragonStage").invoke(dragon)).intValue() >= 3; }
        catch (ReflectiveOperationException ignored) { return false; }
    }

    public record Use() {
        public Use(net.minecraft.network.FriendlyByteBuf ignored) { this(); }
        public void encode(net.minecraft.network.FriendlyByteBuf ignored) {}
        public void handle(java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> supplier) {
            var ctx = supplier.get();
            ctx.enqueueWork(() -> { if (ctx.getSender() != null) activate(ctx.getSender()); });
            ctx.setPacketHandled(true);
        }
    }

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ClientInput {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void use(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
            if (!event.isUseItem() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null || !(player.getVehicle() instanceof TamableAnimal mount)) return;
            var id = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(mount.getType());
            if (id != null && id.getNamespace().equals("iceandfire") && id.getPath().endsWith("_dragon")) {
                TinkersNewlife.CHANNEL.sendToServer(new Use());
            }
        }
    }

}
