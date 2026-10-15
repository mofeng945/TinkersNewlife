package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNL1120 Blink, a Momo skill.
 *
 * <p>Momo locks its gaze on the target, stands perfectly still for 1.5 s and then, with no warning at
 * all, appears three blocks behind it. No particles, no sound, nothing to react to.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class BlinkHandler {

    private static final int LOCK_TICKS = 30;        // 1.5 s
    private static final double AROUND = 3.0D;       // TNL1121 a random spot within three blocks
    private static final int AFTERCAST_TICKS = 10;   // 0.5 s of recovery, the afterimages show here
    private static final double RANGE = 16.0D;       // only when the target is this close
    private static final int COOLDOWN_TICKS = 400;   // 20 s

    private static final Map<UUID, Long> LOCK_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    /** TNL1121 recovery window after the teleport, used by the client for the afterimages */
    public static final Map<UUID, Long> AFTERCAST_UNTIL = new ConcurrentHashMap<>();
    public static final Map<UUID, net.minecraft.world.phys.Vec3> LAST_ORIGIN = new ConcurrentHashMap<>();

    private BlinkHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MomoSkills.BLINK) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                if (e instanceof KtuluMomo momo && momo.isAlive()) {
                    tickOne(level, momo, now);
                }
            }
        }
    }

    private static void tickOne(ServerLevel level, KtuluMomo momo, long now) {
        UUID id = momo.getUUID();

        Long after = AFTERCAST_UNTIL.get(id);
        if (after != null) {
            if (now < after) {
                momo.setDeltaMovement(Vec3.ZERO);
                momo.hurtMarked = true;
                return;
            }
            AFTERCAST_UNTIL.remove(id);
            momo.setNoAi(false);
        }

        Long until = LOCK_UNTIL.get(id);
        if (until != null) {
            LivingEntity target = momo.getTarget();
            if (now < until && target != null && target.isAlive()) {
                // stand still and keep the eyes on the target, nothing else
                momo.setNoAi(true);
                momo.setDeltaMovement(Vec3.ZERO);
                momo.hurtMarked = true;
                if (momo.getNavigation() != null) {
                    momo.getNavigation().stop();
                }
                momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
                return;
            }
            LOCK_UNTIL.remove(id);
            momo.setNoAi(false);
            if (target != null && target.isAlive()) {
                teleportBehind(level, momo, target);
            }
            return;
        }

        Long cd = COOLDOWN_UNTIL.get(id);
        if (cd != null && now < cd) {
            return;
        }
        LivingEntity target = momo.getTarget();
        if (target == null || !target.isAlive() || momo.distanceTo(target) > RANGE) {
            return;
        }
        LOCK_UNTIL.put(id, now + LOCK_TICKS);
        COOLDOWN_UNTIL.put(id, now + LOCK_TICKS + COOLDOWN_TICKS);
        momo.setNoAi(true);
        momo.setDeltaMovement(Vec3.ZERO);
        momo.hurtMarked = true;
        if (momo.getNavigation() != null) {
            momo.getNavigation().stop();
        }
        TinkersNewlife.LOGGER.warn("TNL1120 Momo locks onto {} and starts the Blink wind up",
                target.getName().getString());
    }

    /**
     * TNL1124 walk the segment; if a block is in the way, return the last free point before it.
     * Returns null when the whole line is clear.
     */
    private static Vec3 tnl$clipLine(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0E-4D) {
            return null;
        }
        int steps = (int) Math.ceil(len / 0.25D);
        Vec3 lastFree = null;
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.add(delta.scale((double) i / steps));
            BlockPos bp = BlockPos.containing(p);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) {
                return lastFree;              // stop just before the wall
            }
            lastFree = p;
        }
        return null;
    }

    /** appear three blocks behind the target, facing it, with no warning whatsoever. */
    private static void teleportBehind(ServerLevel level, KtuluMomo momo, LivingEntity target) {
        long now = level.getGameTime();
        // TNL1121 a random spot inside three blocks of the target, anywhere around it
        Vec3 origin = momo.position();
        Vec3 dest = null;
        for (int attempt = 0; attempt < 12 && dest == null; attempt++) {
            double ang = level.random.nextDouble() * Math.PI * 2.0D;
            double rad = 1.0D + level.random.nextDouble() * (AROUND - 1.0D);
            Vec3 cand = target.position().add(Math.cos(ang) * rad, 0.0D, Math.sin(ang) * rad);
            BlockPos bp = BlockPos.containing(cand);
            if (level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()
                    && level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()) {
                dest = cand;
            }
        }
        if (dest == null) {
            dest = target.position();
        }
        // TNL1124 user: instead of cancelling, stop just short of the wall the line runs into
        Vec3 from = origin.add(0.0D, 1.0D, 0.0D);
        Vec3 to = dest.add(0.0D, 1.0D, 0.0D);
        Vec3 clipped = tnl$clipLine(level, from, to);
        if (clipped != null) {
            dest = clipped.subtract(0.0D, 1.0D, 0.0D);
            TinkersNewlife.LOGGER.warn("TNL1124 Blink stopped short at ({}, {}, {})",
                    String.format("%.1f", dest.x), String.format("%.1f", dest.y), String.format("%.1f", dest.z));
        }
        LAST_ORIGIN.put(momo.getUUID(), origin);
        // TNL1123 user: the afterimages must cover the whole line between the two points
        int ghosts = 14;
        for (int i = 0; i < ghosts; i++) {
            // TNL1125 user: denser the closer it gets to the ARRIVAL point
            double t = (double) i / (ghosts - 1.0D);
            double f = 1.0D - Math.pow(1.0D - t, 1.6D);
            Vec3 p = origin.add(dest.subtract(origin).scale(f));
            // send it exactly like PacketThrustGhost.broadcast does, but with our own coordinates
            // TNL1125 smoke along the path to sell the sheer speed
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    p.x, p.y + 0.6D, p.z, 2, 0.25D, 0.35D, 0.25D, 0.02D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    p.x, p.y + 0.4D, p.z, 1, 0.2D, 0.3D, 0.2D, 0.01D);
            TinkersNewlife.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> momo),
                    new com.mofengbaizhi.tinkersnewlife.network.tools.PacketThrustGhost(
                            momo.getId(), p.x, p.y, p.z,
                            (float) Math.toDegrees(Math.atan2(dest.z - origin.z, dest.x - origin.x)), 0.0F));
        }
        AFTERCAST_UNTIL.put(momo.getUUID(), now + AFTERCAST_TICKS);
        momo.teleportTo(dest.x, dest.y, dest.z);
        momo.setDeltaMovement(Vec3.ZERO);
        momo.hurtMarked = true;
        momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
        momo.setNoAi(true);      // TNL1121 half a second of recovery before it may act again
        TinkersNewlife.LOGGER.warn("TNL1120 Momo blinked behind {} to ({}, {}, {})",
                target.getName().getString(),
                String.format("%.1f", dest.x), String.format("%.1f", dest.y), String.format("%.1f", dest.z));
    }
}
