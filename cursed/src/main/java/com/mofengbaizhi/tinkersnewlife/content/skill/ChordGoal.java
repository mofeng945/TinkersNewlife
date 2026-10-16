package com.mofengbaizhi.tinkersnewlife.content.skill;

import java.util.concurrent.ConcurrentHashMap;

import java.util.UUID;

import java.util.Map;

import java.util.ArrayList;

import net.minecraftforge.fml.common.Mod;

import net.minecraft.world.effect.MobEffects;

import net.minecraft.world.effect.MobEffectInstance;

import net.minecraft.sounds.SoundSource;

import net.minecraft.sounds.SoundEvents;

import net.minecraft.core.particles.ParticleTypes;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * TNL1176 Discordant Chord as a Goal (spell id 3), the last skill on the new system.
 *
 * <p>Ten waves per cast. The gap between two waves is a random 1.5 to 3 seconds, each wave fires from
 * a random one of two heights (0.15 or 2.3 blocks above the ground), the range is 30 blocks, the damage
 * is the warden sonic boom, and everything in range jumps 1.5x while the chord plays. Hard control ends
 * half a second after the last wave -- the waves may still be travelling.
 */
public final class ChordGoal extends MomoSpellGoal {

    private static final int CHARGE_TICKS = 40;
    private static final int WAVES = 10;
    private static final int GAP_MIN_TICKS = 30;
    private static final int GAP_RANGE_TICKS = 30;
    private static final double MAX_RADIUS = 30.0D;
    private static final double SPEED = 0.15D;
    private static final double HEIGHT_LOW = 0.15D;
    private static final double HEIGHT_HIGH = 2.3D;
    private static final int COOLDOWN_TICKS = 2400;
    private static final int AFTER_LAST_TICKS = 10;

    /** longest possible cast: the charge plus ten waves at the slowest rhythm, plus slack */
    private static final int TOTAL_TICKS = CHARGE_TICKS + WAVES * (GAP_MIN_TICKS + GAP_RANGE_TICKS) + 260;   // TNL1200 rings must finish flying

    private static final Random RNG = new Random();

    private int wavesLeft;
    private long nextWaveAt;
    private boolean released;

    public ChordGoal(KtuluMomo momo) {
        super(momo, 3, COOLDOWN_TICKS, TOTAL_TICKS);
    }

    @Override
    protected int cooldownLeft() {
        return (int) Math.max(0L, this.momo.tnl$chordCoolUntil() - this.momo.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        if (!MomoSkills.DISCORDANT_CHORD) {
            return false;
        }
        LivingEntity target = this.momo.getTarget();
        if (target == null || !target.isAlive() || this.momo.distanceTo(target) > MAX_RADIUS) {
            return false;
        }
        return this.ready();
    }

    @Override
    protected void onStart() {
        ServerLevel level = (ServerLevel) this.momo.level();
        this.wavesLeft = WAVES;
        this.released = false;
        this.rings.clear();
        this.nextWaveAt = 0L;
        this.momo.setDeltaMovement(Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
        LivingEntity target = this.momo.getTarget();
        if (target != null) {
            double dx = target.getX() - this.momo.getX();
            double dz = target.getZ() - this.momo.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            this.momo.setYRot(yaw);
            this.momo.setYHeadRot(yaw);
            this.momo.setYBodyRot(yaw);
        }
        DiscordantChordHandler.startChargeFx(level, this.momo);
        DiscordantChordHandler.applyJumpBuff(level, this.momo);
        DiscordantChordHandler.markChord(level, this.momo, CHARGE_TICKS + 10);
        TinkersNewlife.LOGGER.warn("TNL1176 Momo starts the Discordant Chord, {} waves queued", WAVES);
    }

    @Override
    protected boolean onTick() {
        ServerLevel level = (ServerLevel) this.momo.level();
        long now = level.getGameTime();
        DiscordantChordHandler.markChord(level, this.momo, 8);

        if (this.momo.tnl$castTicks() > TOTAL_TICKS - CHARGE_TICKS) {
            // still winding up
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    this.momo.getX(), this.momo.getY() + 1.6D, this.momo.getZ(), 2, 0.4D, 0.4D, 0.4D, 0.01D);
            this.momo.setDeltaMovement(Vec3.ZERO);
            this.momo.hurtMarked = true;
            return false;
        }

        if (this.wavesLeft > 0 && (this.nextWaveAt == 0L || now >= this.nextWaveAt)) {
            this.wavesLeft--;
            int gap = GAP_MIN_TICKS + RNG.nextInt(GAP_RANGE_TICKS + 1);
            this.nextWaveAt = now + gap;
            double baseY = this.momo.getY() + (RNG.nextBoolean() ? HEIGHT_LOW : HEIGHT_HIGH);
            this.rings.add(new double[] {0.0D, baseY});
            level.playSound(null, this.momo.blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.8F, 1.0F);
            TinkersNewlife.LOGGER.warn("TNL1176 chord wave {} of {} at height {}, next in {} ticks",
                    WAVES - this.wavesLeft, WAVES,
                    String.format(java.util.Locale.ROOT, "%.2f", baseY - this.momo.getY()), gap);
            if (this.wavesLeft <= 0) {
                this.releaseAt = now + AFTER_LAST_TICKS;
            }
        }

        // several waves travel at once: the gap is shorter than the time a wave needs to cross 30 blocks
        java.util.Iterator<double[]> it = this.rings.iterator();
        while (it.hasNext()) {
            double[] ring = it.next();
            ring[0] += SPEED;
            if (ring[0] > MAX_RADIUS) {
                it.remove();
                continue;
            }
            DiscordantChordHandler.drawRing(level, this.momo, ring[0], ring[1]);
            DiscordantChordHandler.hitRing(level, this.momo, ring[0], ring[1]);
        }

        if (!this.released) {
            this.momo.setDeltaMovement(Vec3.ZERO);
            this.momo.hurtMarked = true;
            if (this.momo.getNavigation() != null) {
                this.momo.getNavigation().stop();
            }
        }
        if (!this.released && this.releaseAt > 0L && now >= this.releaseAt) {
            // TNL1200 control ends half a second after the last wave; the goal keeps running so the
            // rings finish travelling, but the boss is free (no more rooting, no more chord pose).
            this.releaseAt = 0L;
            this.released = true;
            this.momo.tnl$setChordCasting(false);
            TinkersNewlife.LOGGER.warn("TNL1200 chord control released, waves still flying");
        }
        if (this.released && this.rings.isEmpty()) {
            return true;
        }
        return this.momo.tnl$castTicks() <= 0;
    }

    /** live waves: [0] = radius, [1] = base y */
    private final java.util.List<double[]> rings = new java.util.ArrayList<>();
    private long releaseAt;

    @Override
    protected void onStop() {
        long now = this.momo.level().getGameTime();
        DiscordantChordHandler.clearJumpBuff();
        this.momo.tnl$setChordCoolUntil(now + COOLDOWN_TICKS);
        this.rings.clear();
        this.releaseAt = 0L;
        TinkersNewlife.LOGGER.warn("TNL1176 Discordant Chord finished");
    }
    // ===== TNL1213 和弦的实现整体收进技能类（★原 DiscordantChordHandler 原样成为内部类 ✓）=====
static final class DiscordantChordHandler {   // TNL1213 收编自原 DiscordantChordHandler.java

    /** TNL1153 ten waves per cast */
    /** TNL1153 random gap between waves: 1.5 s to 3 s */
    /** TNL1153 range is 30 blocks now */
    private static final double MAX_RADIUS = 30.0D;
    /** TNL1153 the two spawn heights above the ground */
    /** vanilla warden sonic boom damage */
    private static final float SONIC_DAMAGE = 10.0F;
    /** TNL1154 the cooldown starts when the cast ENDS, not when it starts */
    private static final int EFFECT_TICKS = 100;       // 5 s of weakness and slowness

    /** per momo: charge until this game time, -1 when idle */
    /** per momo: the live rings of the current cast */
    /** TNL1153 how many waves are still waiting to be released */
    /** TNL1153 when the next wave is due */
    /** TNL1157 when the hard control of this cast ends */

    /** TNL1155 who currently carries the chord jump buff */
    private static final List<LivingEntity> JUMPED = new ArrayList<>();

    /** TNL1153 rng for the gaps and the heights */

    private static final class Ring {
        double radius;
        final double baseY;

        Ring(double baseY) {
            this.baseY = baseY;
        }
    }

    private DiscordantChordHandler() {
    }



    /** TNL1176 entry used by ChordGoal: the wind up sound and smoke */
    public static void startChargeFx(ServerLevel level, KtuluMomo momo) {
        level.playSound(null, momo.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.HOSTILE, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.CLOUD,
                momo.getX(), momo.getY() + 1.6D, momo.getZ(), 6, 0.4D, 0.4D, 0.4D, 0.02D);
    }

    /** TNL1176 entry used by ChordGoal: everything in range jumps 1.5x while the chord plays */
    public static void applyJumpBuff(ServerLevel level, KtuluMomo momo) {
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
        TinkersNewlife.LOGGER.warn("TNL1176 chord jump buff on {} creatures", JUMPED.size());
    }

    /** TNL1176 entry used by ChordGoal: take the jump buff back */
    public static void clearJumpBuff() {
        for (LivingEntity le : JUMPED) {
            var attr = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
            if (attr != null) {
                attr.removeModifier(java.util.UUID.nameUUIDFromBytes("tnl1155_chord_jump".getBytes()));
            }
        }
        JUMPED.clear();
    }

    /** TNL1157 keep the chord marker alive on nearby players so the client can pick the chord pose. */
    public static void markChord(ServerLevel level, KtuluMomo momo, int ticks) {
        for (net.minecraft.world.entity.player.Player near : level.getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class, momo.getBoundingBox().inflate(48.0D))) {
            near.getCooldowns().addCooldown(
                    com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_CHORD_MARKER.get(), ticks);
        }
    }

    /** TNL1114 grey dust so it reads as a ring, not a white blob. */
    public static void drawRing(ServerLevel level, KtuluMomo momo, double radius, double baseY) {
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
    public static void hitRing(ServerLevel level, KtuluMomo momo, double radius, double baseY) {
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
}
