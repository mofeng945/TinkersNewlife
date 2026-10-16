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

    /**
     * TNL1171 user: "make it self driven like the goety apostle". The skill is no longer a
     * global scanner over every entity in every level; the momo itself calls this from its own
     * tick, so the whole state dies with the entity (logging out cannot leave it stuck).
     */
    public static void tickMomo(net.minecraft.server.level.ServerLevel level, KtuluMomo momo, long now) {
        if (!MomoSkills.DISCORDANT_CHORD) {
            return;
        }
        // TNL1176 the chord is a Goal now; the old handler must not drive it too
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
