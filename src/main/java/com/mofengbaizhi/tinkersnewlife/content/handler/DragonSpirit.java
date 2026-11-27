package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class DragonSpirit {
    private static final String ACTIVE = "tnlDragonSpirit";
    private DragonSpirit() {}

    public static void mark(Entity dragon, boolean active) {
        if (active) dragon.getPersistentData().putBoolean(ACTIVE, true);
        else dragon.getPersistentData().remove(ACTIVE);
    }

    private record Progress(net.minecraft.world.phys.Vec3 position, java.util.UUID goal, long since) {}
    private static final java.util.Map<Entity, Progress> PROGRESS = new java.util.WeakHashMap<>();

    @SubscribeEvent
    public static void stepThrough(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.world.entity.TamableAnimal dragon)
                || !(dragon.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !dragon.getPersistentData().getBoolean(ACTIVE)) return;
        var goal = dragon.getTarget();
        if (goal == null && followsOwner(dragon)) goal = dragon.getOwner();
        if (!dragon.isAlive() || dragon.isOrderedToSit() || dragon.isVehicle() || dragon.isPassenger()
                || goal == null || !goal.isAlive() || goal.level() != level || dragon.distanceToSqr(goal) > 32 * 32) {
            PROGRESS.remove(dragon);
            return;
        }
        var pos = dragon.position();
        long now = level.getGameTime();
        var previous = PROGRESS.get(dragon);
        if (previous == null || !previous.goal.equals(goal.getUUID()) || pos.distanceToSqr(previous.position) > .25) {
            PROGRESS.put(dragon, new Progress(pos, goal.getUUID(), now));
            return;
        }
        if (now - previous.since < 20) return;
        PROGRESS.put(dragon, new Progress(pos, goal.getUUID(), now));
        var direction = goal.position().subtract(pos).multiply(1, 0, 1);
        if (direction.length() <= Math.max(3, dragon.getBbWidth() / 2 + 1)) return;
        var axis = direction.normalize();
        var box = dragon.getBoundingBox().deflate(.001);
        double limit = Math.min(direction.length() - 1, Math.min(16, dragon.getBbWidth() + 4));
        var sweep = box.expandTowards(axis.scale(limit));
        if (!level.getWorldBorder().isWithinBounds(sweep) || !level.hasChunksAt(
                net.minecraft.core.BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
                net.minecraft.core.BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ))) return;
        // Treat even bedrock off the centre ray as a barrier; never access unloaded chunks.
        for (var block : net.minecraft.core.BlockPos.betweenClosed(
                net.minecraft.core.BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
                net.minecraft.core.BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ))) {
            if (level.getBlockState(block).is(net.minecraft.world.level.block.Blocks.BEDROCK)) return;
        }
        boolean blocked = false;
        for (double distance = .5; distance <= limit; distance += .5) {
            var offset = axis.scale(distance);
            var destination = box.move(offset);
            if (level.getBlockCollisions(dragon, destination).iterator().hasNext()) {
                blocked = true;
                continue;
            }
            if (!blocked) {
                if (distance >= 1) return; // No immediate barrier: leave normal navigation alone.
                continue;
            }
            if (!level.noCollision(dragon, destination) || level.containsAnyLiquid(destination)
                    || dragon.onGround() && !level.getBlockCollisions(dragon, destination.move(0, -.5, 0)).iterator().hasNext()) return;
            // Atomic server move: no noPhysics window or intermediate position inside the wall.
            dragon.getNavigation().stop();
            dragon.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            dragon.teleportTo(pos.x + offset.x, pos.y, pos.z + offset.z);
            PROGRESS.put(dragon, new Progress(dragon.position(), goal.getUUID(), now));
            return;
        }
    }

    private static boolean followsOwner(net.minecraft.world.entity.TamableAnimal dragon) {
        try {
            return Integer.valueOf(2).equals(dragon.getClass().getMethod("getCommand").invoke(dragon));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventGrief(EntityMobGriefingEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(ACTIVE)) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void preventSuffocation(LivingAttackEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(ACTIVE) && (event.getSource().is(DamageTypes.IN_WALL)
                || event.getSource().is(DamageTypes.CRAMMING))) event.setCanceled(true);
    }
}
