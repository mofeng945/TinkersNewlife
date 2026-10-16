package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;

/**
 * TNL1174 Astral Resonance as a real Goal, the first skill migrated to the Goety style.
 *
 * <p>Trigger: the target stays within two blocks for twenty seconds. Then the boss roots itself and
 * charges for two seconds, shrieks, and knocks the target past ten blocks.
 *
 * <p>Everything is now local to this instance: the near-tick counter is a plain field (one goal per
 * momo), the cooldown lives on the entity, the cast state goes through the synced spell channel, and
 * rooting is the goal flags plus the busy gate -- no global scanner, no uuid map, no {@code setNoAi}.
 */
public final class ResonanceGoal extends MomoSpellGoal {

    private static final double NEAR_RANGE = 2.0D;
    private static final int NEAR_TICKS_REQUIRED = 400;
    private static final int CHARGE_TICKS = 40;
    private static final int COOLDOWN_TICKS = 200;
    private static final int TOTAL_TICKS = CHARGE_TICKS + 10;

    /** TNL1090 user: keep the boss melee at zero while the animation is being tested. */
    private static final boolean ATTACK_ZERO = true;

    private int nearTicks;

    public ResonanceGoal(com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo momo) {
        super(momo, 2, COOLDOWN_TICKS, TOTAL_TICKS);
    }

    @Override
    protected int cooldownLeft() {
        return (int) Math.max(0L, this.momo.tnl$resonanceCoolUntil() - this.momo.level().getGameTime());
    }

    @Override
    public boolean canUse() {
        if (!MomoSkills.ASTRAL_RESONANCE) {
            return false;
        }
        if (ATTACK_ZERO) {
            var inst = this.momo.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (inst != null && inst.getBaseValue() != 0.0D) {
                inst.setBaseValue(0.0D);
            }
        }
        LivingEntity target = this.momo.getTarget();
        if (target == null || !target.isAlive()) {
            this.nearTicks = 0;
            return false;
        }
        if (this.momo.distanceTo(target) > NEAR_RANGE) {
            this.nearTicks = 0;
            return false;
        }
        this.nearTicks += 5;
        if (this.nearTicks < NEAR_TICKS_REQUIRED) {
            return false;
        }
        if (!this.ready()) {
            return false;
        }
        this.nearTicks = 0;
        return true;
    }

    @Override
    protected void onStart() {
        ServerLevel level = (ServerLevel) this.momo.level();
        LivingEntity target = this.momo.getTarget();
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
        // TNL1168 face the target once instead of calling the look control every tick
        if (target != null) {
            double dx = target.getX() - this.momo.getX();
            double dz = target.getZ() - this.momo.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            this.momo.setYRot(yaw);
            this.momo.setYHeadRot(yaw);
            this.momo.setYBodyRot(yaw);
        }
        level.playSound(null, this.momo.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 1.0F);
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                this.momo.getX(), this.momo.getY() + 1.2D, this.momo.getZ(), 16, 0.5D, 0.8D, 0.5D, 0.02D);
        TinkersNewlife.LOGGER.warn("TNL1174 Momo begins Astral Resonance charge on {}",
                target == null ? "?" : target.getName().getString());
    }

    @Override
    protected boolean onTick() {
        ServerLevel level = (ServerLevel) this.momo.level();
        if (this.momo.tnl$castTicks() <= 10) {
            // wind up is over: the shriek happens once, then the goal ends
            AstralResonanceHandler.shriek(level, this.momo, level.getGameTime());
            return true;
        }
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.momo.hurtMarked = true;
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                this.momo.getX(), this.momo.getY() + 1.2D, this.momo.getZ(), 1, 0.3D, 0.4D, 0.3D, 0.01D);
        return false;
    }

    @Override
    protected void onStop() {
        long now = this.momo.level().getGameTime();
        this.momo.tnl$setResonanceCoolUntil(now + COOLDOWN_TICKS);
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
}
