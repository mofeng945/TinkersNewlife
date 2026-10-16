package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.server.level.ServerLevel;
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
        MomoCastLock.begin(this.momo.getUUID(), level.getGameTime(), TOTAL_TICKS + 10);
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
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.momo.hurtMarked = true;
        return this.momo.tnl$castTicks() <= 0;
    }

    @Override
    protected void onStop() {
        long now = this.momo.level().getGameTime();
        this.momo.tnl$setBlinkCoolUntil(now + COOLDOWN_TICKS);
        MomoCastLock.end(this.momo.getUUID(), now, 0);
        this.momo.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (this.momo.getNavigation() != null) {
            this.momo.getNavigation().stop();
        }
    }
}
