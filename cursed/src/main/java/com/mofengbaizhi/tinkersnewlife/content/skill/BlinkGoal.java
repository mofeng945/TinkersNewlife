package com.mofengbaizhi.tinkersnewlife.content.skill;

import java.util.concurrent.ConcurrentHashMap;

import java.util.UUID;

import java.util.Map;

import net.minecraft.core.BlockPos;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;

/**
 * TNL1175 Blink as a Goal (spell id 1).
 *
 * <p>The boss locks its eyes on a target further than twenty blocks away, stands for 1.5 s, then is
 * gone: it reappears in a random spot within three blocks of the target (stopping just short of any
 * wall on the way), leaves a line of afterimages and is rooted for another 0.2 s.
 * The teleport itself is still done by {@link BlinkHandler}, this goal only drives the phases.
 */
public final class BlinkGoal extends MomoSpellGoal {

    private static final int LOCK_TICKS = 30;
    private static final int AFTERCAST_TICKS = 4;
    private static final int COOLDOWN_TICKS = 200;
    private static final double RANGE = 20.0D;
    private static final int TOTAL_TICKS = LOCK_TICKS + AFTERCAST_TICKS;

    private boolean jumped;

    public BlinkGoal(KtuluMomo momo) {
        super(momo, 1, COOLDOWN_TICKS, TOTAL_TICKS);
    }

    @Override
    protected int cooldownLeft() {
        return (int) Math.max(0L, this.momo.tnl$blinkCoolUntil() - this.momo.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        if (!MomoSkills.BLINK) {
            return false;
        }
        LivingEntity target = this.momo.getTarget();
        if (target == null || !target.isAlive() || this.momo.distanceTo(target) < RANGE) {
            return false;
        }
        return this.ready();
    }

    @Override
    protected void onStart() {
        ServerLevel level = (ServerLevel) this.momo.level();
        this.jumped = false;
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
        LivingEntity target = this.momo.getTarget();
        if (target != null) {
            this.momo.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        TinkersNewlife.LOGGER.warn("TNL1175 Momo locks onto {} and starts the Blink wind up",
                target == null ? "?" : target.getName().getString());
    }

    @Override
    protected boolean onTick() {
        ServerLevel level = (ServerLevel) this.momo.level();
        LivingEntity target = this.momo.getTarget();
        if (!this.jumped && this.momo.tnl$castTicks() <= AFTERCAST_TICKS) {
            this.jumped = true;
            if (target != null && target.isAlive()) {
                BlinkHandler.teleportBehind(level, this.momo, target);
            }
        }
        BlinkHandler.tickGhostLine(level, this.momo, level.getGameTime());   // TNL1211 技能自己驱动残影
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.momo.hurtMarked = true;
        boolean tnl$trailDone = !BlinkHandler.hasTrail(this.momo.getUUID());
        return this.momo.tnl$castTicks() <= 0 && tnl$trailDone;
    }

    @Override
    protected void onStop() {
        long now = this.momo.level().getGameTime();
        this.momo.tnl$setBlinkCoolUntil(now + COOLDOWN_TICKS);
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
    }
    // ===== TNL1211 瞬身的实现整体收进技能类（★原 BlinkHandler 原样成为内部类 ✓）=====
static final class BlinkHandler {   // TNL1211 收编自原 BlinkHandler.java

    private static final double AROUND = 3.0D;       // TNL1121 a random spot within three blocks

    /** TNL1121 recovery window after the teleport, used by the client for the afterimages */

    private BlinkHandler() {
    }



    /**
     * TNL1184 the afterimage trail of a blink: four ghosts per server tick. Moved out of the old
     * global scanner; BlinkGoal drives it now.
     */
    public static void tickGhostLine(ServerLevel level, KtuluMomo momo, long now) {
        // TNL1201 the queue is static, so cap it: if a momo is removed mid trail its entry would
        // otherwise sit there for ever.
        if (LINE.size() > 8) {
            LINE.clear();
        }
        UUID id = momo.getUUID();
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
    public static void teleportBehind(ServerLevel level, KtuluMomo momo, LivingEntity target) {
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

        /** TNL1212 残影队列里还有没有这个实体（★技能靠它判断残影放完没有 ✓）*/
        static boolean hasTrail(UUID id) {
            return LINE.containsKey(id);
        }

    /** TNL1137 hard safety net: however we got frozen, never stay frozen longer than this. */
    private static final int GHOSTS = 24;

}
}
