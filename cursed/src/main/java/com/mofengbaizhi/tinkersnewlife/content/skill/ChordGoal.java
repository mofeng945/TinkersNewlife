package com.mofengbaizhi.tinkersnewlife.content.skill;

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
    private static final int TOTAL_TICKS = CHARGE_TICKS + WAVES * (GAP_MIN_TICKS + GAP_RANGE_TICKS) + 60;

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
        MomoCastLock.begin(this.momo.getUUID(), level.getGameTime(), TOTAL_TICKS + 20);
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

        this.momo.setDeltaMovement(Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
        if (this.releaseAt > 0L && now >= this.releaseAt) {
            this.releaseAt = 0L;
            this.released = true;
            TinkersNewlife.LOGGER.warn("TNL1176 chord hard control released, waves may still travel");
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
        MomoCastLock.end(this.momo.getUUID(), now, 0);
        this.rings.clear();
        this.releaseAt = 0L;
        TinkersNewlife.LOGGER.warn("TNL1176 Discordant Chord finished");
    }
}
