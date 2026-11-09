package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.UUID;

/** One lap and five non-healing charge rounds. Timing/damage are provisional framework tuning. */
public final class MomoFlightEntryGoal extends Goal {
    public static final int LAP_TICKS = 80, WINDUP_TICKS = 20, CHARGE_TICKS = 15;
    public static final int CHARGE_ROUNDS = 5;
    public static final double LAP_RADIUS = 10, HEIGHT = 4, CHARGE_SPEED = 0.8;
    private final KtuluMomo momo;
    private int ticks;
    private Vec3 center, direction = Vec3.ZERO;
    private final HashSet<UUID> hit = new HashSet<>();
    public MomoFlightEntryGoal(KtuluMomo momo) {
        this.momo = momo;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        return !momo.level().isClientSide && momo.tnl$isBossForm()
                && momo.tnl$combat().flightEntryPending() && !momo.tnl$combat().entryDomainPending()
                && !momo.tnl$isBusy() && !momo.tnl$isStunned() && momo.getTarget() != null;
    }
    @Override public boolean canContinueToUse() {
        return ticks < LAP_TICKS + CHARGE_ROUNDS * (WINDUP_TICKS + CHARGE_TICKS) && momo.isAlive();
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        ticks = 0;
        center = momo.position();
        momo.tnl$combat().beginFlightEntry();
        momo.getNavigation().stop();
        momo.setNoGravity(true);
        if (momo.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            level.playSound(null, momo.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                    net.minecraft.sounds.SoundSource.HOSTILE, 2, 1);
        }
    }
    @Override public void tick() {
        momo.getNavigation().stop();
        LivingEntity target = momo.getTarget();
        if (ticks < LAP_TICKS) {
            double angle = ticks * Math.PI * 2 / LAP_TICKS;
            Vec3 next = center.add(Math.cos(angle) * LAP_RADIUS, HEIGHT, Math.sin(angle) * LAP_RADIUS);
            Vec3 delta = next.subtract(momo.position());
            momo.setDeltaMovement(delta.lengthSqr() > 1 ? delta.normalize() : delta);
        } else {
            int step = (ticks - LAP_TICKS) % (WINDUP_TICKS + CHARGE_TICKS);
            if (step < WINDUP_TICKS) {
                momo.setDeltaMovement(Vec3.ZERO);
                if (target != null) momo.getLookControl().setLookAt(target, 60, 60);
                if (step == WINDUP_TICKS - 1) {
                    hit.clear();
                    direction = target == null ? momo.getLookAngle() : target.getBoundingBox().getCenter().subtract(momo.getBoundingBox().getCenter()).normalize();
                }
            } else {
                momo.setDeltaMovement(direction.scale(CHARGE_SPEED));
                for (LivingEntity victim : momo.level().getEntitiesOfClass(LivingEntity.class, momo.getBoundingBox().inflate(1))) {
                    if (victim == momo || KtuluMomo.tnl$isMomoMinion(victim) || !hit.add(victim.getUUID())) continue;
                    // Intentionally not doHurtTarget: transition collisions do not heal the boss.
                    victim.hurt(momo.damageSources().mobAttack(momo), 10F);
                    victim.knockback(1.5, momo.getX() - victim.getX(), momo.getZ() - victim.getZ());
                    StunHandler.apply(victim, MomoCombatState.STAGGER_TICKS, StunHandler.Mode.FULL);
                    com.mofengbaizhi.tinkersnewlife.content.effect.AeonianWeaken.add(victim,
                            com.mofengbaizhi.tinkersnewlife.content.effect.AeonianWeaken.PER_HIT);
                }
            }
        }
        ticks++;
    }
    @Override public void stop() {
        momo.setNoGravity(false);
        momo.setDeltaMovement(Vec3.ZERO);
        momo.tnl$combat().finishFlightEntry();
    }
}
