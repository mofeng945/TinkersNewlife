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
        boolean bow;
        if (phase == 1) {
            if (momo.tickCount >= nextDecision) {
                nextDecision = momo.tickCount + MomoCombatState.DECISION_INTERVAL;
                rareBow = momo.getRandom().nextFloat() < MomoCombatState.RARE_BOW_CHANCE;
                // Equal provisional weights: observe/orbit vs blink into a short melee opening.
                if (!rareBow && momo.getRandom().nextBoolean()) {
                    var point = target.position().add(target.getLookAngle().multiply(2, 0, 2));
                    if (momo.randomTeleport(point.x, target.getY(), point.z, true))
                        approachUntil = momo.tickCount + 20;
                }
            }
            bow = rareBow;
            if (bow) equip(true); else if (!momo.getMainHandItem().isEmpty())
                momo.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (momo.tickCount >= approachUntil) {
                orbit += 0.045;
                if (momo.tickCount % 10 == 0) momo.getNavigation().moveTo(
                        target.getX() + Math.cos(orbit) * MomoCombatState.ORBIT_RADIUS,
                        target.getY(), target.getZ() + Math.sin(orbit) * MomoCombatState.ORBIT_RADIUS, 1);
            } else momo.getNavigation().moveTo(target, 1);
        } else {
            bow = distance > 10;
            equip(bow);
            // Final phase alternates aerial manoeuvring with landing windows for spells.
            boolean aerial = phase == 3 && distance > 10 && momo.tickCount % 240 < 160;
            momo.setNoGravity(aerial);
            if (aerial) {
                momo.getNavigation().stop();
                orbit += 0.025;
                var point = target.position().add(Math.cos(orbit) * 12, 3, Math.sin(orbit) * 12);
                var delta = point.subtract(momo.position());
                double speed = state.flightMetersPerSecond() / 20.0;
                momo.setDeltaMovement(delta.lengthSqr() < speed * speed ? delta : delta.normalize().scale(speed));
            } else if (bow) {
                orbit += 0.035;
                if (momo.tickCount % 10 == 0) momo.getNavigation().moveTo(
                        target.getX() + Math.cos(orbit) * 12, target.getY(),
                        target.getZ() + Math.sin(orbit) * 12, 1);
            } else momo.getNavigation().moveTo(target, 1);
        }
        if (bow && momo.tickCount >= nextShot && momo.hasLineOfSight(target)) {
            nextShot = momo.tickCount + MomoCombatState.BOW_INTERVAL;
            StarfallLamentItem.fireAt(momo.level(), momo, target);
        } else if (!bow && distance <= 3 && momo.tickCount >= nextAttack
                && (phase > 1 || momo.tickCount < approachUntil) && momo.hasLineOfSight(target)) {
            nextAttack = momo.tickCount + 20;
            momo.doHurtTarget(target);
        }
    }
}
