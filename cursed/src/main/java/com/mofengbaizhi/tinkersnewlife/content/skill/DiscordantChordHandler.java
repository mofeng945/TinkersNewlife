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
 * <p>TNL1153 user: ten sonic waves per cast. The gap between two waves is a random 1.5 to 3 seconds
 * (floating point, so the rhythm is never the same twice), every wave fires from a random one of two
 * heights above the ground (0.55 or 2.3 blocks), the whole thing reaches 30 blocks, and the damage is
 * the warden sonic boom damage. The launch and the weakness/slowness stay as they were.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class DiscordantChordHandler {

    private static final int CHARGE_TICKS = 40;        // 2 s
    /** TNL1153 ten waves per cast */
    private static final int WAVES = 10;
    /** TNL1153 random gap between waves: 1.5 s to 3 s */
    private static final int GAP_MIN_TICKS = 30;
    private static final int GAP_RANGE_TICKS = 30;
    /** TNL1153 range is 30 blocks now */
    private static final double MAX_RADIUS = 30.0D;
    private static final double SPEED = 0.15D;         // TNL1106 user: much slower spread
    /** TNL1153 the two spawn heights above the ground */
    private static final double HEIGHT_LOW = 0.15D;
    private static final double HEIGHT_HIGH = 2.3D;
    /** vanilla warden sonic boom damage */
    private static final float SONIC_DAMAGE = 10.0F;
    /** TNL1154 the cooldown starts when the cast ENDS, not when it starts */
    private static final int COOLDOWN_TICKS = 2400;    // TNL1159 user: 120 s, measured from the end of the last wave
    private static final int EFFECT_TICKS = 100;       // 5 s of weakness and slowness

    /** per momo: charge until this game time, -1 when idle */
    private static final Map<UUID, Long> CHARGE_UNTIL = new ConcurrentHashMap<>();
    /** per momo: the live rings of the current cast */
    private static final Map<UUID, List<Ring>> RINGS_BY_MOMO = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    /** TNL1153 how many waves are still waiting to be released */
    private static final Map<UUID, Integer> QUEUED = new ConcurrentHashMap<>();
    /** TNL1153 when the next wave is due */
    private static final Map<UUID, Long> QUEUE_AT = new ConcurrentHashMap<>();
    /** TNL1157 when the hard control of this cast ends */
    private static final Map<UUID, Long> RELEASE_AT = new ConcurrentHashMap<>();

    /** TNL1155 who currently carries the chord jump buff */
    private static final List<LivingEntity> JUMPED = new ArrayList<>();

    /** TNL1153 rng for the gaps and the heights */
    private static final java.util.Random RNG = new java.util.Random();

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

        Long until = CHARGE_UNTIL.get(id);
        if (until != null) {
            if (now < until) {
                momo.setDeltaMovement(Vec3.ZERO);
                momo.hurtMarked = true;
                // TNL1164 user: never clear the AI for a wind up or a recovery -- it
                // sticks (teleporting high left the boss frozen two seconds). Rooting is done
                // by zeroing the movement every tick instead, which is already done above.
                if (momo.getNavigation() != null) {
                    momo.getNavigation().stop();
                }
                level.sendParticles(ParticleTypes.CLOUD,
                        momo.getX(), momo.getY() + 1.6D, momo.getZ(), 2, 0.4D, 0.4D, 0.4D, 0.01D);
                // TNL1157 the charge phase must also carry the chord marker, otherwise the client has
                // no way to know the chord (and not the resonance) is the one winding up.
                markChord(level, momo, 8);
                return;
            }
            CHARGE_UNTIL.remove(id);
            // TNL1154 hold the lock for the whole cast (ten waves can take a while)
            MomoCastLock.begin(id, now, CHARGE_TICKS + WAVES * (GAP_MIN_TICKS + GAP_RANGE_TICKS) + 260);
            QUEUE_AT.put(id, now);
            QUEUED.put(id, WAVES);
            RINGS_BY_MOMO.put(id, new ArrayList<>());
            level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, 2.0F, 0.6F);
            // TNL1155 user: while the chord plays, everything in range jumps 1.5x higher
            JUMPED.clear();
            var jumpMod = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    java.util.UUID.nameUUIDFromBytes("tnl1155_chord_jump".getBytes()),
                    "tnl1155_chord_jump", 0.5D,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class,
                    momo.getBoundingBox().inflate(MAX_RADIUS))) {
                if (le == momo) {
                    continue;
                }
                var attr = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
                if (attr != null && !attr.hasModifier(jumpMod)) {
                    attr.addTransientModifier(jumpMod);
                    JUMPED.add(le);
                }
            }
            TinkersNewlife.LOGGER.warn("TNL1104 Momo starts the Discordant Chord, {} waves queued, {} got the jump buff",
                    WAVES, JUMPED.size());
        }

        List<Ring> rings = RINGS_BY_MOMO.get(id);
        if (rings != null || QUEUED.containsKey(id)) {
            for (net.minecraft.world.entity.player.Player near : level.getEntitiesOfClass(
                    net.minecraft.world.entity.player.Player.class, momo.getBoundingBox().inflate(40.0D))) {
                near.getCooldowns().addCooldown(
                        com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_CHORD_MARKER.get(), 10);
            }
        }
        if (rings == null) {
            Long cd = COOLDOWN_UNTIL.get(id);
            if (cd != null && now < cd) {
                return;
            }
            // TNL1154 no other skill may run at the same time
            if (!MomoCastLock.canStart(id, now)) {
                return;
            }
            LivingEntity target = momo.getTarget();
            // TNL1153 range is 30 blocks now
            if (target == null || !target.isAlive() || momo.distanceTo(target) > MAX_RADIUS) {
                return;
            }
            CHARGE_UNTIL.put(id, now + CHARGE_TICKS);
            MomoCastLock.begin(id, now, CHARGE_TICKS + 8);
            momo.tnl$setChordCasting(true);      // TNL1161 drives the chord pose on the client
            markChord(level, momo, CHARGE_TICKS + 10);
            for (net.minecraft.world.entity.player.Player near : level.getEntitiesOfClass(
                    net.minecraft.world.entity.player.Player.class, momo.getBoundingBox().inflate(40.0D))) {
                near.getCooldowns().addCooldown(
                        com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_CHORD_MARKER.get(), 20);
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

        // TNL1156 keep the lock renewed while the cast is running, so it can never overstay
        MomoCastLock.begin(id, now, 60);
        // TNL1157 two seconds after the last wave the boss may act again, rings or no rings
        Long release = RELEASE_AT.get(id);
        if (release != null && now >= release) {
            RELEASE_AT.remove(id);
            // TNL1164 user: never clear the AI for a wind up or a recovery -- it
            // sticks (teleporting high left the boss frozen two seconds). Rooting is done
            // by zeroing the movement every tick instead, which is already done above.
            MomoCastLock.end(id, now, 0);
            TinkersNewlife.LOGGER.warn("TNL1157 chord hard control released, waves may still travel");
        }
        // TNL1157 nobody around (player logged off mid cast) -- do not stay frozen for ever
        if (momo.level().getNearestPlayer(momo, 80.0D) == null) {
            RELEASE_AT.remove(id);
            QUEUED.remove(id);
            QUEUE_AT.remove(id);
            RINGS_BY_MOMO.remove(id);
            // TNL1164 user: never clear the AI for a wind up or a recovery -- it
            // sticks (teleporting high left the boss frozen two seconds). Rooting is done
            // by zeroing the movement every tick instead, which is already done above.
            momo.tnl$setChordCasting(false);
            MomoCastLock.end(id, now, 0);
            TinkersNewlife.LOGGER.warn("TNL1157 chord aborted, no players nearby");
            return;
        }
        int left = QUEUED.getOrDefault(id, 0);
        Long due = QUEUE_AT.get(id);
        if (left > 0 && due != null && now >= due) {
            QUEUED.put(id, left - 1);
            // TNL1153 next gap: 1.5 s to 3 s
            int gap = GAP_MIN_TICKS + RNG.nextInt(GAP_RANGE_TICKS + 1);
            QUEUE_AT.put(id, now + gap);
            // TNL1153 this wave picks one of the two heights above the ground
            double baseY = momo.getY() + (RNG.nextBoolean() ? HEIGHT_LOW : HEIGHT_HIGH);
            Ring r = new Ring(baseY);
            r.radius = 0.0D;
            rings.add(r);
            level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.HOSTILE, 1.8F, 1.0F);
            TinkersNewlife.LOGGER.warn("TNL1104 chord wave {} of {} at height {}, next in {} ticks",
                    WAVES - left + 1, WAVES, String.format("%.2f", baseY - momo.getY()), gap);
            if (left - 1 <= 0) {
                // TNL1157 last wave is out: hard control ends two seconds later, the waves may still
                // be travelling and that is fine -- the boss may move again.
                RELEASE_AT.put(id, now + 10);   // TNL1158 0.5 s after the last wave
            }
        }

        // TNL1160 the release above was undone immediately: this ran every tick and
        // re-froze the boss, so the 0.5 s aftercast never happened until the rings were gone.
        // TNL1160 freeze only while the cast has not been released yet
        if (RINGS_BY_MOMO.containsKey(id) && RELEASE_AT.get(id) == null) {
            // TNL1164 user: never clear the AI for a wind up or a recovery -- it
            // sticks (teleporting high left the boss frozen two seconds). Rooting is done
            // by zeroing the movement every tick instead, which is already done above.
        }
        List<Ring> dead = new ArrayList<>();
        for (Ring r : rings) {
            r.radius += SPEED;
            if (r.radius > MAX_RADIUS) {
                dead.add(r);
                continue;
            }
            drawRing(level, momo, r.radius, r.baseY);
            hitRing(level, momo, r.radius, r.baseY);
        }
        rings.removeAll(dead);

        if (rings.isEmpty() && QUEUED.getOrDefault(id, 0) <= 0) {
            RINGS_BY_MOMO.remove(id);
            QUEUED.remove(id);
            QUEUE_AT.remove(id);
            // TNL1164 user: never clear the AI for a wind up or a recovery -- it
            // sticks (teleporting high left the boss frozen two seconds). Rooting is done
            // by zeroing the movement every tick instead, which is already done above.
            momo.tnl$setChordCasting(false);     // TNL1161 the chord pose ends with the cast
            // TNL1154 cooldown only starts now, when the whole cast is over
            COOLDOWN_UNTIL.put(id, now + COOLDOWN_TICKS);
            MomoCastLock.end(id, now, COOLDOWN_TICKS);
            level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.HOSTILE, 2.4F, 0.9F);
            for (LivingEntity le : JUMPED) {
                var attr = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
                if (attr != null) {
                    attr.removeModifier(java.util.UUID.nameUUIDFromBytes("tnl1155_chord_jump".getBytes()));
                }
            }
            JUMPED.clear();
            TinkersNewlife.LOGGER.warn("TNL1104 Discordant Chord finished");
        }
    }

    /** TNL1157 keep the chord marker alive on nearby players so the client can pick the chord pose. */
    private static void markChord(ServerLevel level, KtuluMomo momo, int ticks) {
        for (net.minecraft.world.entity.player.Player near : level.getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class, momo.getBoundingBox().inflate(48.0D))) {
            near.getCooldowns().addCooldown(
                    com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_CHORD_MARKER.get(), ticks);
        }
    }

    /** TNL1114 grey dust so it reads as a ring, not a white blob. */
    private static void drawRing(ServerLevel level, KtuluMomo momo, double radius, double baseY) {
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

    /**
     * TNL1153 anything the wave touches takes the warden sonic boom damage and is launched, then left
     * weak and slow. Each wave sits at its own height, so the low one can be jumped over.
     */
    private static void hitRing(ServerLevel level, KtuluMomo momo, double radius, double baseY) {
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
            // TNL1154 precise hit test: the wave plane must cut through the entity's own box, with a
            // small margin. Standing box is 0..1.8, crouching is 0..1.5, jumping lifts both. So the
            // low wave (0.55) can be jumped over and the high one (2.3) can be crouched under.
            double wave = baseY - momo.getY();
            double boxLow = le.getY() - momo.getY();
            double boxHigh = boxLow + le.getBbHeight();
            double margin = 0.5D;
            if (wave < boxLow - margin || wave > boxHigh + margin) {
                continue;
            }
            le.hurt(level.damageSources().sonicBoom(momo), SONIC_DAMAGE);
            Vec3 out = new Vec3(le.getX() - momo.getX(), 0.0D, le.getZ() - momo.getZ());
            if (out.lengthSqr() < 1.0E-4D) {
                out = new Vec3(1.0D, 0.0D, 0.0D);
            }
            Vec3 flat = out.normalize();
            le.setDeltaMovement(flat.x * 1.2D, 1.15D, flat.z * 1.2D);
            le.hurtMarked = true;
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, 1, false, true, true));
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, 1, false, true, true));
            TinkersNewlife.LOGGER.warn("TNL1104 chord wave r={} hit {}",
                    String.format("%.2f", radius), le.getName().getString());
        }
    }
}
