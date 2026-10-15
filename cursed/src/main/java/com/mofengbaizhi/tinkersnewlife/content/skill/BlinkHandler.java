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
    private static final int AFTERCAST_TICKS = 4;   // 0.5 s of recovery, the afterimages show here
    private static final double RANGE = 20.0D;       // TNL1126 user: fires when the target is FARTHER than this
    private static final int COOLDOWN_TICKS = 200;   // TNL1126 user: 10 s

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
        // TNL1136 user: "every teleport makes it lose its AI". It blinks from MORE than twenty blocks
        // away, and a mob drops a target that is outside its follow range, so after landing it simply
        // stands there. Keep the follow range comfortably above the trigger distance.
        try {
            var follow = momo.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            if (follow != null && follow.getBaseValue() < 64.0D) {
                follow.setBaseValue(64.0D);
            }
        } catch (Throwable ignored) {
        }

        // TNL1137 user: "it looks like the recovery lasts forever". Whatever path froze it, this
        // guarantees the AI comes back: if the deadline passed, unfreeze and re-acquire unconditionally.
        Long deadline = FREEZE_DEADLINE.get(id);
        if (deadline != null && now > deadline) {
            FREEZE_DEADLINE.remove(id);
            if (momo.isNoAi()) {
                // TNL1164 user: never clear the AI for a wind up or a recovery -- it
                // sticks (teleporting high left the boss frozen two seconds). Rooting is done
                // by zeroing the movement every tick instead, which is already done above.
            }
            if (momo.getNavigation() != null) {
                momo.getNavigation().stop();
            }
            net.minecraft.world.entity.player.Player nearest =
                    momo.level().getNearestPlayer(momo, 64.0D);
            if (nearest != null && momo.getTarget() == null) {
                momo.setTarget(nearest);
            }
            TinkersNewlife.LOGGER.warn("TNL1137 freeze deadline passed, AI force released");
        }

        Line line = LINE.get(id);
        if (line != null) {
            if (line.sent >= GHOSTS) {
                LINE.remove(id);
            } else if (now >= line.nextTick) {
                // TNL1134 user: "the momo cannot keep up with its own ghosts" -- release four per tick
                for (int k = 0; k < 4 && line.sent < GHOSTS; k++) {
                double t = (double) line.sent / (GHOSTS - 1.0D);
                double f = 1.0D - Math.pow(1.0D - t, 2.2D);      // denser toward the arrival point
                Vec3 gp = line.origin.add(line.dest.subtract(line.origin).scale(f));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                        gp.x, gp.y + 0.6D, gp.z, 1, 0.2D, 0.3D, 0.2D, 0.02D);
                TinkersNewlife.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> momo),
                        new com.mofengbaizhi.tinkersnewlife.network.tools.PacketThrustGhost(
                                momo.getId(), gp.x, gp.y, gp.z,
                                (float) Math.toDegrees(Math.atan2(
                                        line.dest.z - line.origin.z, line.dest.x - line.origin.x)), 0.0F));
                    line.sent++;
                }
                line.nextTick = now + 1;
            }
        }

        Long after = AFTERCAST_UNTIL.get(id);
        if (after != null) {
            if (now < after) {
                momo.setDeltaMovement(Vec3.ZERO);
                momo.hurtMarked = true;
                return;
            }
            AFTERCAST_UNTIL.remove(id);
            momo.tnl$setSpell(0, 0);      // TNL1169 blink cast is over
            MomoCastLock.end(id, now, 0);       // TNL1154 cast is over, other skills may run again
            // TNL1134 release the AI properly: unfreeze, drop the stale path and re-acquire the target
            // TNL1164 user: never clear the AI for a wind up or a recovery -- it
            // sticks (teleporting high left the boss frozen two seconds). Rooting is done
            // by zeroing the movement every tick instead, which is already done above.
            if (momo.getNavigation() != null) {
                momo.getNavigation().stop();
            }
            // TNL1136 re-acquire: the blink left the old target behind, so take the nearest player
            net.minecraft.world.entity.player.Player closest = momo.level().getNearestPlayer(momo, 64.0D);
            if (closest != null) {
                momo.setTarget(closest);
                momo.setLastHurtByMob(closest);
            }
        }

        Long until = LOCK_UNTIL.get(id);
        if (until != null) {
            LivingEntity target = momo.getTarget();
            if (now < until && target != null && target.isAlive()) {
                // stand still and keep the eyes on the target, nothing else
                // TNL1164 user: never clear the AI for a wind up or a recovery -- it
                // sticks (teleporting high left the boss frozen two seconds). Rooting is done
                // by zeroing the movement every tick instead, which is already done above.
                FREEZE_DEADLINE.put(id, now + MAX_FROZEN_TICKS);
                momo.setDeltaMovement(Vec3.ZERO);
                momo.hurtMarked = true;
                if (momo.getNavigation() != null) {
                    momo.getNavigation().stop();
                }
                momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
                return;
            }
            LOCK_UNTIL.remove(id);
            // TNL1164 user: never clear the AI for a wind up or a recovery -- it
            // sticks (teleporting high left the boss frozen two seconds). Rooting is done
            // by zeroing the movement every tick instead, which is already done above.
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
        if (target == null || !target.isAlive() || momo.distanceTo(target) < RANGE) {   // TNL1126 farther than 20
            return;
        }
        // TNL1154 no other skill may run at the same time
        if (!MomoCastLock.canStart(id, now)) {
            return;
        }
        LOCK_UNTIL.put(id, now + LOCK_TICKS);
        momo.tnl$setSpell(1, LOCK_TICKS + AFTERCAST_TICKS);   // TNL1169 blink is spell 1
        MomoCastLock.begin(id, now, LOCK_TICKS + AFTERCAST_TICKS + 10);
        FREEZE_DEADLINE.put(id, now + MAX_FROZEN_TICKS);
        COOLDOWN_UNTIL.put(id, now + LOCK_TICKS + COOLDOWN_TICKS);
        // TNL1164 user: never clear the AI for a wind up or a recovery -- it
        // sticks (teleporting high left the boss frozen two seconds). Rooting is done
        // by zeroing the movement every tick instead, which is already done above.
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
        // TNL1133 user: 24 ghosts, released ONE PER TICK so the line grows from the start towards the
        // arrival point. The ones born first are the ones near the start, so they fade out first --
        // "the start point disappears fastest" comes for free from the staggered birth ticks.
        // TNL1135 user: "the blink is instant but the momo still flies over, and the ghosts outrun it".
        // The client interpolates the position over a few ticks, so start the trail AFTER that has
        // settled: then the ghosts are always behind the visible momo instead of ahead of it.
        LINE.put(momo.getUUID(), new Line(origin, dest, 0, now + 4));
        momo.teleportTo(dest.x, dest.y, dest.z);
        momo.setDeltaMovement(Vec3.ZERO);
        momo.hurtMarked = true;
        // TNL1134 user: "after blinking it gets stuck on its AI". Teleporting invalidates the old
        // path, so clear it, re-target and only then freeze for the short recovery.
        // TNL1164 user: never clear the AI for a wind up or a recovery -- it
        // sticks (teleporting high left the boss frozen two seconds). Rooting is done
        // by zeroing the movement every tick instead, which is already done above.
        if (momo.getNavigation() != null) {
            momo.getNavigation().stop();
        }
        momo.setTarget(target);
        momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // TNL1164 user: never clear the AI for a wind up or a recovery -- it
        // sticks (teleporting high left the boss frozen two seconds). Rooting is done
        // by zeroing the movement every tick instead, which is already done above.
        FREEZE_DEADLINE.put(momo.getUUID(), now + MAX_FROZEN_TICKS);
        TinkersNewlife.LOGGER.warn("TNL1120 Momo blinked behind {} to ({}, {}, {})",
                target.getName().getString(),
                String.format("%.1f", dest.x), String.format("%.1f", dest.y), String.format("%.1f", dest.z));
    }
    /** TNL1133 a blink trail still being released, one ghost per tick */
    private static final class Line {
        final Vec3 origin;
        final Vec3 dest;
        int sent;
        long nextTick;

        Line(Vec3 origin, Vec3 dest, int sent, long nextTick) {
            this.origin = origin;
            this.dest = dest;
            this.sent = sent;
            this.nextTick = nextTick;
        }
    }

    private static final Map<UUID, Line> LINE = new ConcurrentHashMap<>();

    /** TNL1137 hard safety net: however we got frozen, never stay frozen longer than this. */
    private static final Map<UUID, Long> FREEZE_DEADLINE = new ConcurrentHashMap<>();
    private static final int MAX_FROZEN_TICKS = 60;
    private static final int GHOSTS = 24;

}
