package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import java.util.EnumSet;

/** Movement/weapon controller; spells retain ownership of their own casting locks. */
public final class MomoCombatGoal extends Goal {
    private final KtuluMomo momo;
    private int nextShot, nextAttack, nextDecision, approachUntil;
    private boolean rareBow;
    private double orbit;
    private LivingEntity tracked;
    private int misses, failedPaths, nextProbe, nextPath, side = 1;
    private boolean rangedLast;

    private net.minecraft.world.phys.Vec3 flank(LivingEntity target, int direction) {
        double dx = momo.getX()-target.getX(), dz = momo.getZ()-target.getZ();
        double angle = Math.atan2(dz,dx) + direction * 0.37;
        double range = Math.hypot(dx,dz);
        double radius = Math.max(13, Math.min(18, range));
        return new net.minecraft.world.phys.Vec3(target.getX()+Math.cos(angle)*radius, momo.getY(), target.getZ()+Math.sin(angle)*radius);
    }
    private boolean tryFlank(LivingEntity target, int direction) {
        var point = flank(target, direction);
        var path = momo.getNavigation().createPath(net.minecraft.core.BlockPos.containing(point), 1);
        if (path == null || !path.canReach()) return false;
        double safe = Math.min(8, Math.hypot(momo.getX()-target.getX(), momo.getZ()-target.getZ()) - 1);
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            if (Math.hypot(node.x + .5 - target.getX(), node.z + .5 - target.getZ()) < safe) return false;
        }
        return momo.getNavigation().moveTo(path, 1);
    }
    public MomoCombatGoal(KtuluMomo momo) {
        this.momo = momo;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    @Override public boolean canUse() {
        return !momo.level().isClientSide && momo.tnl$isBossForm()
                && momo.getTarget() != null && momo.getTarget().isAlive()
                && !momo.tnl$isBusy() && !momo.tnl$isStunned()
                && momo.tnl$hurtStaggerTicks() == 0;
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void stop() {
        momo.getNavigation().stop();
        momo.setNoGravity(false);
    }
    private void equip(boolean bow) {
        var item = bow ? ModItems.STARFALL_LAMENT.get() : ModItems.AEONIAN_ORDER.get();
        if (!momo.getMainHandItem().is(item)) momo.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }
    @Override public void tick() {
        LivingEntity target = momo.getTarget();
        if (target == null || momo.tnl$isBusy() || momo.tnl$isStunned()) { stop(); return; }
        MomoCombatState state = momo.tnl$combat();
        int phase = state.phase();
        momo.getLookControl().setLookAt(target, 30F, 30F);
        double distance = momo.distanceTo(target);
        boolean retaliating = momo.getLastHurtByMob() == target;
        if (tracked != target) {
            tracked = target;
            misses = failedPaths = 0;
            approachUntil = nextDecision = nextPath = 0;
            rareBow = false;
            rangedLast = false;
        }
        if (target.getY() - momo.getY() > 3 && !target.onGround()) state.rangedWindow(momo, 120);
        if (phase > 1 && distance > 10) state.rangedWindow(momo, 80);
        if (momo.tickCount >= nextProbe && !state.usingBow(momo) && distance > 3) {
            nextProbe = momo.tickCount + 20;
            var path = momo.getNavigation().createPath(target, 1);
            failedPaths = path == null || !path.canReach() ? failedPaths + 1 : 0;
            if (failedPaths >= 2) { state.rangedWindow(momo, 160); failedPaths = 0; }
        }
        if (phase == 1 && momo.tickCount >= nextDecision && !state.usingBow(momo)) {
            nextDecision = momo.tickCount + MomoCombatState.DECISION_INTERVAL;
            rareBow = momo.getRandom().nextFloat() < MomoCombatState.RARE_BOW_CHANCE;
            if (rareBow) state.rangedWindow(momo, MomoCombatState.DECISION_INTERVAL);
            else if (momo.getRandom().nextBoolean()) {
                var point = target.position().add(target.getLookAngle().multiply(2, 0, 2));
                if (momo.randomTeleport(point.x, target.getY(), point.z, true)) approachUntil = momo.tickCount + 20;
            }
        }
        boolean bow = state.usingBow(momo);
        if (bow != rangedLast) { momo.getNavigation().stop(); nextPath = 0; rangedLast = bow; }
        if (bow) {
            equip(true);
            boolean flying = phase == 3 && distance > 10 && momo.tickCount % 240 < 160;
            momo.setNoGravity(flying);
            if (flying) {
                momo.getNavigation().stop();
                var point = flank(target, 1).add(0, target.getY() - momo.getY() + 3, 0);
                var delta = point.subtract(momo.position());
                momo.setDeltaMovement(delta.normalize().scale(Math.min(delta.length(), state.flightMetersPerSecond()/20)));
            } else if (momo.tickCount >= nextPath) {
                nextPath = momo.tickCount + 12;
                boolean moved = tryFlank(target, side);
                if (!moved) { side = -side; if (!tryFlank(target, side)) momo.getNavigation().stop(); }
            }
        } else {
            momo.setNoGravity(false);
            if (phase > 1) equip(false);
            else if (!momo.getMainHandItem().isEmpty()) momo.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (phase > 1 || retaliating || momo.tickCount < approachUntil) momo.getNavigation().moveTo(target, 1);
            else if (momo.tickCount % 10 == 0) {
                orbit += 0.45;
                momo.getNavigation().moveTo(target.getX()+Math.cos(orbit)*8, target.getY(), target.getZ()+Math.sin(orbit)*8, 1);
            }
        }
        if (bow && momo.tickCount >= nextShot && momo.hasLineOfSight(target)) {
            nextShot = momo.tickCount + MomoCombatState.BOW_INTERVAL;
            StarfallLamentItem.fireAt(momo.level(), momo, target);
            momo.markBowShot();
        } else if (!bow && distance <= 3 && momo.tickCount >= nextAttack
                && (phase > 1 || retaliating || momo.tickCount < approachUntil) && momo.hasLineOfSight(target)) {
            nextAttack = momo.tickCount + 20;
            float before = target.getHealth() + target.getAbsorptionAmount();
            boolean landed = momo.doHurtTarget(target);
            if (landed && target.getHealth()+target.getAbsorptionAmount() < before) misses = 0;
            else if (++misses >= 4) { state.rangedWindow(momo, 200); misses = 0; }
        }
    }
}
