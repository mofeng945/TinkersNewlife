package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoShadow;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/** Three independent minion roles, without ever borrowing the boss health kernel. */
public final class MomoShadowCombatGoal extends Goal {
    private final MomoShadow shadow;
    private int attackAt, castLeft;
    private Vec3 castOrigin;
    private double orbit;
    public MomoShadowCombatGoal(MomoShadow shadow) {
        this.shadow = shadow;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        LivingEntity target = shadow.getTarget();
        return target != null && target.isAlive() && !(target instanceof KtuluMomo)
                && !KtuluMomo.tnl$isMomoMinion(target);
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void stop() { shadow.getNavigation().stop(); castLeft = 0; }
    @Override public void tick() {
        LivingEntity target = shadow.getTarget();
        if (target == null) return;
        shadow.getLookControl().setLookAt(target, 30, 30);
        MomoShadow.Role role = shadow.tnl$role();
        if (role == MomoShadow.Role.MELEE) {
            equip(new ItemStack(ModItems.AEONIAN_ORDER.get()));
            shadow.getNavigation().moveTo(target, 1);
            if (shadow.distanceToSqr(target) <= 9 && shadow.hasLineOfSight(target) && shadow.tickCount >= attackAt) {
                attackAt = shadow.tickCount + 20;
                shadow.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                shadow.doHurtTarget(target);
            }
            return;
        }
        boolean archer = role == MomoShadow.Role.ARCHER || role == MomoShadow.Role.WANDER;
        equip(archer ? new ItemStack(ModItems.STARFALL_LAMENT.get()) : ItemStack.EMPTY);
        if (!archer && castLeft > 0) {
            shadow.getNavigation().stop();
            shadow.setDeltaMovement(Vec3.ZERO);
            castLeft--;
            if (castLeft <= 20 && shadow.level() instanceof ServerLevel level) {
                double radius = (20 - castLeft) * 0.6;
                ChordGoal.DiscordantChordHandler.drawRing(level, shadow, castOrigin.x, castOrigin.y,
                        castOrigin.z, radius, castOrigin.y + 0.8);
                ChordGoal.DiscordantChordHandler.hitRing(level, shadow, castOrigin.x, castOrigin.y,
                        castOrigin.z, radius, castOrigin.y + 0.8);
            }
            return;
        }
        orbit += 0.04;
        if (shadow.tickCount % 10 == 0) shadow.getNavigation().moveTo(target.getX() + Math.cos(orbit) * 10,
                target.getY(), target.getZ() + Math.sin(orbit) * 10, 1);
        if (shadow.tickCount < attackAt || !shadow.hasLineOfSight(target)) return;
        if (archer) {
            attackAt = shadow.tickCount + 40;
            StarfallLamentItem.fireAt(shadow.level(), shadow, target);
        } else if (shadow.distanceToSqr(target) <= 144) {
            attackAt = shadow.tickCount + 160;
            castLeft = 60; // 2-second charge followed by one expanding chord wave.
            castOrigin = shadow.position();
            shadow.getNavigation().stop();
            if (shadow.level() instanceof ServerLevel level)
                ChordGoal.DiscordantChordHandler.startChargeFx(level, shadow);
        }
    }
    private void equip(ItemStack desired) {
        if (!ItemStack.isSameItemSameTags(shadow.getMainHandItem(), desired))
            shadow.setItemSlot(EquipmentSlot.MAINHAND, desired);
    }
}
