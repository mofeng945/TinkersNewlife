package com.mofengbaizhi.tinkersnewlife.content.handler;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Server-only geometry and movement for a single Viltrum flight. */
public final class DragonViltrumFlight {
    private static final int CLIMB_TICKS = 14;
    private static final int DIVE_TICKS = 12;
    private static final int DRAG_START = CLIMB_TICKS + DIVE_TICKS;
    private static final int DURATION = DRAG_START + 20;
    private static final double CLIMB_HEIGHT = 6.5;
    private static final double DIVE_REACH = 7.0;
    private static final double DRAG_REACH = 8.0;
    private static final double MAX_STEP = 2.25;
    private static final double EPSILON = 1.0e-6;
    private static final int[] LANE_ANGLES = {0, -35, 35, -70, 70};

    private DragonViltrumFlight() {}

    /** Returns null if neither the pickup nor any complete dry landing lane is safe. */
    @Nullable
    public static Plan create(ServerLevel level, LivingEntity dragon, LivingEntity victim) {
        if (!eligible(level, dragon, victim)) return null;
        Vec3 dragonStart = dragon.position();
        Vec3 victimStart = victim.position();
        List<AABB> dragonHull = hull(dragon);
        List<AABB> victimHull = hull(victim);
        // Part boxes matter: a stage 3+ dragon's feet, neck and tail extend beyond the root box.
        double dragonBottom = dragonHull.stream().mapToDouble(box -> box.minY).min().orElse(dragonStart.y);
        double victimTop = victimHull.stream().mapToDouble(box -> box.maxY).max().orElse(victimStart.y);
        double gap = victimTop - victimStart.y + dragonStart.y - dragonBottom + .65;
        double forwardGrip = Mth.clamp(dragon.getBbWidth() * .23, .35, 1.4);
        Vec3 heading = Vec3.directionFromRotation(0, dragon.getYRot());
        List<Entity> ownParts = parts(dragon, victim);
        for (int angle : LANE_ANGLES) {
            Vec3 forward = heading.yRot((float) Math.toRadians(angle));
            Vec3 apex = victimStart.add(forward.scale(.75)).add(0, CLIMB_HEIGHT, 0);
            Vec3 landing = findLanding(level, victim, victimHull, apex.add(forward.scale(DIVE_REACH)), victimStart.y, 32);
            if (landing == null) continue;
            Vec3 grip = heading.scale(-forwardGrip).add(0, gap, 0);
            Pose[] poses = path(dragonStart, victimStart, apex, landing, forward, grip);
            double groundY = landing.y;
            int count = DRAG_START;
            // Follow small changes in ground height rather than requiring a level runway.
            for (; count < DURATION; count++) {
                Vec3 feet = poses[count].victim;
                Vec3 ground = findLanding(level, victim, victimHull, feet, groundY + .25, 6);
                if (ground == null) break;
                groundY = ground.y;
                poses[count] = new Pose(ground.add(grip), ground);
            }
            if (count < DRAG_START + 8) continue;
            poses = java.util.Arrays.copyOf(poses, count);
            Pose prev = new Pose(dragonStart, victimStart);
            boolean clear = true;
            for (int tick = 0; tick < poses.length && clear; tick++) {
                Pose next = poses[tick];
                clear = clearStep(level, dragon, victim, ownParts, dragonHull, victimHull,
                        dragonStart, victimStart, prev, next, tick);
                if (!clear && tick >= DRAG_START + 8) {
                    return new Plan(level, dragon, victim, java.util.Arrays.copyOf(poses, tick));
                }
                prev = next;
            }
            if (clear) return new Plan(level, dragon, victim, poses);
        }
        return null;
    }

    private static Pose[] path(Vec3 dragonStart, Vec3 victimStart, Vec3 apex, Vec3 landing,
                               Vec3 forward, Vec3 grip) {
        Pose[] poses = new Pose[DURATION];
        for (int i = 0; i < CLIMB_TICKS; i++) {
            double t = smooth((i + 1.0) / CLIMB_TICKS);
            // Bring the two hulls into the carry position gradually rather than snapping the victim.
            poses[i] = new Pose(dragonStart.lerp(apex.add(grip), t), victimStart.lerp(apex, t));
        }
        for (int i = CLIMB_TICKS; i < DRAG_START; i++) {
            double t = (i - CLIMB_TICKS + 1.0) / DIVE_TICKS;
            double u = smooth(t);
            Vec3 dive = new Vec3(Mth.lerp(u, apex.x, landing.x),
                    Mth.lerp(u * u, apex.y, landing.y), Mth.lerp(u, apex.z, landing.z));
            poses[i] = new Pose(dive.add(grip), dive);
        }
        for (int i = DRAG_START; i < DURATION; i++) {
            double t = (i - DRAG_START + 1.0) / (DURATION - DRAG_START);
            Vec3 drag = landing.add(forward.scale(DRAG_REACH * t * (2 - t)));
            poses[i] = new Pose(drag.add(grip), drag);
        }
        return poses;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    @Nullable
    private static Vec3 findLanding(ServerLevel level, LivingEntity victim, List<AABB> hull, Vec3 at, double startY, int steps) {
        // A short downward search permits a lower landing, but never dives into an unknown drop.
        for (int i = 0; i <= steps; i++) {
            Vec3 feet = new Vec3(at.x, startY - i * .125, at.z);
            AABB box = hull.get(0).move(feet.subtract(victim.position()));
            if (safeArea(level, box) && !level.getBlockCollisions(victim, box).iterator().hasNext()
                    && supported(level, victim, box)) return feet;
        }
        return null;
    }

    private static boolean supported(ServerLevel level, LivingEntity victim, AABB body) {
        // Use a narrow probe beneath the stance; floor collisions are intentional only here.
        double inset = Math.min(.18, (body.maxX - body.minX) * .2);
        double insetZ = Math.min(.18, (body.maxZ - body.minZ) * .2);
        AABB foot = new AABB(body.minX + inset, body.minY - .13, body.minZ + insetZ,
                body.maxX - inset, body.minY, body.maxZ - insetZ);
        return safeArea(level, foot) && level.getBlockCollisions(victim, foot).iterator().hasNext();
    }

    private static boolean eligible(ServerLevel level, LivingEntity dragon, LivingEntity victim) {
        return dragon != victim && eligible(level, dragon) && eligible(level, victim) && !victim.isVehicle();
    }

    private static boolean eligible(ServerLevel level, LivingEntity entity) {
        return entity.level() == level && entity.isAlive() && !entity.isSpectator()
                && !entity.isPassenger() && !entity.noPhysics;
    }

    private static List<AABB> hull(LivingEntity entity) {
        List<AABB> boxes = new ArrayList<>();
        boxes.add(entity.getBoundingBox());
        for (var passenger : entity.getPassengers()) boxes.add(passenger.getBoundingBox());
        var parts = entity.getParts();
        if (parts != null) for (var part : parts) {
            if (part != null && !part.isRemoved()) boxes.add(part.getBoundingBox());
        }
        return boxes;
    }

    private static List<Entity> parts(LivingEntity dragon, LivingEntity victim) {
        List<Entity> parts = new ArrayList<>();
        addParts(parts, dragon);
        parts.addAll(dragon.getPassengers());
        addParts(parts, victim);
        return parts;
    }

    private static void addParts(List<Entity> result, LivingEntity entity) {
        var parts = entity.getParts();
        if (parts != null) for (var part : parts) {
            if (part != null && !part.isRemoved()) result.add(part);
        }
    }

    private static boolean clearStep(ServerLevel level, LivingEntity dragon, LivingEntity victim,
                                     List<Entity> ownParts, List<AABB> dragonHull, List<AABB> victimHull,
                                     Vec3 dragonOrigin, Vec3 victimOrigin, Pose from, Pose to, int tick) {
        Vec3 dragonMove = to.dragon.subtract(from.dragon);
        Vec3 victimMove = to.victim.subtract(from.victim);
        double distance = Math.max(dragonMove.length(), victimMove.length());
        if (!Double.isFinite(distance) || distance > MAX_STEP) return false;
        Vec3 dragonOffset = from.dragon.subtract(dragonOrigin);
        Vec3 victimOffset = from.victim.subtract(victimOrigin);
        // Swept enclosing boxes over short subsegments cannot tunnel past thin block shapes.
        int slices = Math.max(1, Mth.ceil(distance / .4));
        for (int j = 0; j < slices; j++) {
            double a = (double) j / slices, b = (j + 1.0) / slices;
            Vec3 da = dragonOffset.add(dragonMove.scale(a));
            Vec3 db = dragonOffset.add(dragonMove.scale(b));
            Vec3 va = victimOffset.add(victimMove.scale(a));
            Vec3 vb = victimOffset.add(victimMove.scale(b));
            for (AABB box : dragonHull) {
                if (!clear(level, dragon, victim, ownParts, box.move(da).minmax(box.move(db)))) return false;
            }
            for (AABB box : victimHull) {
                if (!clear(level, victim, dragon, ownParts, box.move(va).minmax(box.move(vb)))) return false;
            }
            // Test pair overlap in the dragon's moving frame (not two independent swept envelopes).
            Vec3 relativeA = va.subtract(da), relativeB = vb.subtract(db);
            for (AABB victimBox : victimHull) {
                AABB relativeSweep = victimBox.move(relativeA).minmax(victimBox.move(relativeB));
                for (AABB dragonBox : dragonHull) if (relativeSweep.intersects(dragonBox)) return false;
            }
            boolean checkSupport = tick >= DRAG_START || tick == DRAG_START - 1 && j == slices - 1;
            if (checkSupport && !supported(level, victim, victimHull.get(0).move(vb))) return false;
        }
        return true;
    }

    private static boolean clear(ServerLevel level, LivingEntity actor, LivingEntity other,
                                 List<Entity> ownParts, AABB box) {
        if (!safeArea(level, box) || level.getBlockCollisions(actor, box).iterator().hasNext()) return false;
        return level.getEntities(actor, box, entity -> entity != other && !ownParts.contains(entity)
                && !entity.isSpectator() && (entity.canBeCollidedWith()
                || entity instanceof LivingEntity && entity.isAlive() || entity instanceof PartEntity<?>)).isEmpty();
    }

    private static boolean safeArea(ServerLevel level, AABB box) {
        WorldBorder border = level.getWorldBorder();
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || box.minX < border.getMinX() || box.maxX > border.getMaxX()
                || box.minZ < border.getMinZ() || box.maxZ > border.getMaxZ()) return false;
        // Include the one-block neighbor margin used by block shape collision lookups.
        int minX = Mth.floor(box.minX - 1 - EPSILON) >> 4, maxX = Mth.floor(box.maxX + 1 + EPSILON) >> 4;
        int minZ = Mth.floor(box.minZ - 1 - EPSILON) >> 4, maxZ = Mth.floor(box.maxZ + 1 + EPSILON) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        }
        return !level.containsAnyLiquid(box);
    }

    private static void move(LivingEntity entity, Vec3 at, boolean ground, boolean first) {
        Vec3 change = at.subtract(entity.position());
        boolean moving = change.lengthSqr() > EPSILON * EPSILON;
        boolean resetClientMotion = first || entity.getDeltaMovement().lengthSqr() > .01;
        var parts = moving ? entity.getParts() : null;
        Vec3[] partPositions = parts != null ? new Vec3[parts.length] : null;
        if (parts != null) for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && !parts[i].isRemoved()) partPositions[i] = parts[i].position();
        }
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0;
        if (entity instanceof ServerPlayer player) {
            // A vanilla player owns its local movement: velocity impulses alone cannot enforce
            // the checked grip position. Correct only real displacement, preserving the camera.
            if (moving) player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot());
            if (resetClientMotion) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        } else if (moving) {
            entity.setPos(at.x, at.y, at.z);
        }
        if (parts != null) for (int i = 0; i < parts.length; i++) {
            var part = parts[i];
            // Use the swept geometry's origin even if a parent's setPos moved its parts too.
            if (part != null && !part.isRemoved() && partPositions[i] != null) {
                Vec3 pos = partPositions[i].add(change);
                part.setPos(pos.x, pos.y, pos.z);
            }
        }
        entity.setOnGround(ground);
    }

    private record Pose(Vec3 dragon, Vec3 victim) {}

    public static final class Plan {
        private final ServerLevel level;
        private final LivingEntity dragon;
        private final LivingEntity victim;
        private final Pose[] poses;
        private int nextTick;

        private Plan(ServerLevel level, LivingEntity dragon, LivingEntity victim, Pose[] poses) {
            this.level = level;
            this.dragon = dragon;
            this.victim = victim;
            this.poses = poses;
        }

        public int duration() { return poses.length; }

        public boolean isDragging(int tick) { return tick >= DRAG_START && tick < duration(); }

        /** Apply exactly one zero-based tick. False is terminal; the caller releases both entities. */
        public boolean step(int tick) {
            if (tick != nextTick || tick >= duration() || !eligible(level, dragon, victim)) return false;
            Pose from = new Pose(dragon.position(), victim.position());
            Pose to = poses[tick];
            boolean ground = tick >= DRAG_START - 1;
            // Recheck current dimensions, parts, chunks and obstacles before moving either entity.
            if (!clearStep(level, dragon, victim, parts(dragon, victim), hull(dragon), hull(victim),
                    from.dragon, from.victim, from, to, tick)) return false;
            move(dragon, to.dragon, false, tick == 0);
            move(victim, to.victim, ground, tick == 0);
            nextTick++;
            return true;
        }
    }
}
