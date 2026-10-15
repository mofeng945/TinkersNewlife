package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TNL1104 Discordant Chord, a Momo skill.
 *
 * <p>Charge for two seconds, then stand and chant: sound rings pour out of Momo and spread.
 * Max spread 16 blocks, ring height 0.5 blocks, five rings per cast, five blocks between rings.
 * Anything the ring touches is launched into the air and left weak and slowed. The rings are only
 * half a block tall, so jumping over them works.
 *
 * <p>Animation: hands held high and waved, like an evoker casting, with white smoke all around.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class DiscordantChordHandler {

    private static final int CHARGE_TICKS = 40;        // 2 s
    private static final int RINGS = 5;                // five rings per cast
    private static final int RING_DELAY = 33;          // TNL1107 user: all rings start at Momo, spaced in time
    private static final int CAST_TICKS = 40 + 5 * RING_DELAY + (int) (16.0D / 0.15D);
    private static final double MAX_RADIUS = 16.0D;    // never spread further than this
    private static final double SPEED = 0.15D;         // TNL1106 user: much slower spread
    private static final double HEIGHT = 0.5D;         // ring height
    private static final int COOLDOWN_TICKS = 600;     // TNL1105 user: 30 s cooldown between casts
    private static final int EFFECT_TICKS = 100;       // 5 s of weakness and slowness

    /** per momo: charge until this game time, -1 when idle */
    private static final Map<UUID, Long> CHARGE_UNTIL = new ConcurrentHashMap<>();
    /** per momo: the live rings of the current cast */
    private static final Map<UUID, List<Ring>> RINGS_BY_MOMO = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    /** rings still waiting to be released, with the radius they start at */
    private static final Map<UUID, List<Double>> QUEUED = new ConcurrentHashMap<>();
    /** TNL1107 when the last ring was released, so the next one waits RING_DELAY */
    private static final Map<UUID, Long> QUEUE_AT = new ConcurrentHashMap<>();

    private static final class Ring {
        double radius;
        final double baseY;

        Ring(double baseY) {
            this.baseY = baseY;
        }
    }

    private DiscordantChordHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MomoSkills.DISCORDANT_CHORD) {
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

        // charging: rooted, hands high (client), white smoke gathering
        Long until = CHARGE_UNTIL.get(id);
        if (until != null) {
            if (now < until) {
                momo.setDeltaMovement(Vec3.ZERO);
                momo.hurtMarked = true;
                momo.setNoAi(true);   // TNL1113 no moving, no attacking while casting
                if (momo.getNavigation() != null) {
                    momo.getNavigation().stop();
                }
                // TNL1108 user: halve the particles, Momo has to stay visible
                level.sendParticles(ParticleTypes.CLOUD,
                        momo.getX(), momo.getY() + 1.6D, momo.getZ(), 2, 0.4D, 0.4D, 0.4D, 0.01D);
                return;
            }
            CHARGE_UNTIL.remove(id);
            // TNL1117 user: the whole ring phase is still the casting phase, it must not walk
            // while its own rings are travelling. No unfreeze here.
            // release: five rings, the first at once and the rest every RING_GAP / SPEED ticks
            // TNL1107 every ring is born at Momo itself (radius 0), they are separated in TIME
            List<Double> queue = new ArrayList<>();
            for (int i = 0; i < RINGS; i++) {
                queue.add(0.0D);
            }
            QUEUE_AT.put(id, now);
            QUEUED.put(id, queue);
            RINGS_BY_MOMO.put(id, new ArrayList<>());
            COOLDOWN_UNTIL.put(id, now + COOLDOWN_TICKS);
            level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, 2.0F, 0.6F);
            TinkersNewlife.LOGGER.warn("TNL1104 Momo starts the Discordant Chord, {} rings queued", RINGS);
        }

        // are we in a cast right now?
        List<Ring> rings = RINGS_BY_MOMO.get(id);
        if (rings != null || QUEUED.containsKey(id)) {
            // TNL1107 keep the marker alive for the whole cast so the client knows it is still going
            for (net.minecraft.world.entity.player.Player near : level.getEntitiesOfClass(
                    net.minecraft.world.entity.player.Player.class, momo.getBoundingBox().inflate(40.0D))) {
                near.getCooldowns().addCooldown(
                        com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_SKILL_MARKER.get(), 10);
            }
        }
        if (rings == null) {
            // idle: start a cast when a target is around and the cooldown allows
            Long cd = COOLDOWN_UNTIL.get(id);
            if (cd != null && now < cd) {
                return;
            }
            LivingEntity target = momo.getTarget();
            if (target == null || !target.isAlive() || momo.distanceTo(target) > 16.0D) {   // TNL1105 within 16 blocks
                return;
            }
            CHARGE_UNTIL.put(id, now + CHARGE_TICKS);
            for (net.minecraft.world.entity.player.Player near : level.getEntitiesOfClass(
                    net.minecraft.world.entity.player.Player.class, momo.getBoundingBox().inflate(40.0D))) {
                near.getCooldowns().addCooldown(
                        com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_SKILL_MARKER.get(), 20);
            }
            momo.setDeltaMovement(Vec3.ZERO);
            momo.hurtMarked = true;
            if (momo.getNavigation() != null) {
                momo.getNavigation().stop();
            }
            level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, 1.6F, 1.1F);
            TinkersNewlife.LOGGER.warn("TNL1104 Momo begins the Discordant Chord charge");
            return;
        }

        List<Double> queue = QUEUED.getOrDefault(id, new ArrayList<>());
        Long lastAt = QUEUE_AT.get(id);
        if (!queue.isEmpty() && lastAt != null && now - lastAt >= (queue.size() == RINGS ? 0L : RING_DELAY)) {
            queue.remove(0);
            QUEUE_AT.put(id, now);
            Ring r = new Ring(momo.getY());
            r.radius = 0.0D;
            rings.add(r);
        }

        // expand the rings, draw them, and hit anything standing in the shell
        momo.setNoAi(true);                    // TNL1117 stay rooted for the whole cast
        momo.setDeltaMovement(Vec3.ZERO);
        momo.hurtMarked = true;
        if (momo.getNavigation() != null) {
            momo.getNavigation().stop();
        }
        List<Ring> dead = new ArrayList<>();
        double baseY = momo.getY();
        for (Ring r : rings) {
            r.radius += SPEED;
            if (r.radius > MAX_RADIUS) {
                dead.add(r);
                continue;
            }
            drawRing(level, momo, r.radius, baseY);
            hitRing(level, momo, r.radius, baseY, now);
        }
        rings.removeAll(dead);

        if (rings.isEmpty() && queue.isEmpty()) {
            RINGS_BY_MOMO.remove(id);
            QUEUED.remove(id);
            momo.setNoAi(false);      // TNL1113 cast over
            level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.HOSTILE, 2.4F, 0.9F);
            TinkersNewlife.LOGGER.warn("TNL1104 Discordant Chord finished");
        }
    }

    /** a 0.5 block tall shell of white smoke at this radius */
    /** TNL1114 user: back to the particle ring. Grey dust so it reads as a ring, not a white blob. */
    private static void drawRing(ServerLevel level, KtuluMomo momo, double radius, double baseY) {
        // TNL1116 user: not pure white, a dark grey ring is far easier to see against the ground
        var grey = new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(0.10F, 0.10F, 0.12F), 1.6F);
        int points = Math.max(32, (int) (radius * 8.0D));
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0D) * i / points;
            double x = momo.getX() + Math.cos(a) * radius;
            double z = momo.getZ() + Math.sin(a) * radius;
            level.sendParticles(grey, x, baseY + 0.25D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** anything inside the shell and low enough is launched and left weak and slow */
    private static void hitRing(ServerLevel level, KtuluMomo momo, double radius, double baseY, long now) {
        double band = 0.9D;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                momo.getBoundingBox().inflate(MAX_RADIUS + 2.0D))) {
            if (le == momo || !le.isAlive()) {
                continue;
            }
            double d = Math.sqrt(Math.pow(le.getX() - momo.getX(), 2.0D)
                    + Math.pow(le.getZ() - momo.getZ(), 2.0D));
            if (Math.abs(d - radius) > band) {
                continue;
            }
            // the ring is only half a block tall: jumping clears it
            double feet = le.getY() - baseY;
            if (feet > HEIGHT + 0.05D || feet < -1.5D) {
                continue;
            }
            Vec3 out = new Vec3(le.getX() - momo.getX(), 0.0D, le.getZ() - momo.getZ());
            if (out.lengthSqr() < 1.0E-4D) {
                out = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 flat = out.normalize();
            le.setDeltaMovement(flat.x * 1.2D, 1.15D, flat.z * 1.2D);   // knocked up and out
            le.hurtMarked = true;
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, 1, false, true, true));
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, 1, false, true, true));
            TinkersNewlife.LOGGER.warn("TNL1104 chord ring r={} hit {}", String.format("%.2f", radius),
                    le.getName().getString());
        }
    }
}
