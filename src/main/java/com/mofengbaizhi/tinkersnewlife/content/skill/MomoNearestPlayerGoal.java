package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoShadow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Keep a valid combat target and search for the nearest player when there is none. */
public final class MomoNearestPlayerGoal extends Goal {
    private final Mob mob;

    public MomoNearestPlayerGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override public boolean canUse() { return !mob.level().isClientSide; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { update(); }
    @Override public void tick() { update(); }

    public static void retaliate(Mob mob, DamageSource source, float amount) {
        if (mob.level().isClientSide || !mob.isAlive() || !(amount > 0)) return;
        // The causing entity is the shooter or spell caster for indirect damage.
        if (source.getEntity() instanceof LivingEntity attacker && isValidTarget(mob, attacker)) {
            mob.setLastHurtByMob(attacker);
            if (mob.getTarget() != attacker) mob.setTarget(attacker);
        }
    }

    private static boolean isValidTarget(Mob mob, LivingEntity target) {
        if (target == null || target == mob || target.level() != mob.level() || !target.isAlive()
                || target instanceof KtuluMomo || target instanceof MomoShadow
                || KtuluMomo.tnl$isMomoMinion(target) || mob.isAlliedTo(target)) return false;
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) return false;
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return mob.distanceToSqr(target) <= range * range && mob.canAttack(target);
    }

    private void update() {
        LivingEntity recentAttacker = mob.getLastHurtByMob();
        if (isValidTarget(mob, recentAttacker) && mob.getLastHurtByMobTimestamp() >= mob.tickCount - 100) {
            if (mob.getTarget() != recentAttacker) mob.setTarget(recentAttacker);
            if (mob.getTarget() == recentAttacker) return;
        }

        if (mob instanceof MomoShadow shadow && shadow.tnl$ownerId() != null
                && shadow.level() instanceof ServerLevel level
                && level.getEntity(shadow.tnl$ownerId()) instanceof KtuluMomo owner && owner.isAlive()) {
            LivingEntity ownerTarget = owner.getTarget();
            if (isValidTarget(mob, ownerTarget)) {
                if (mob.getTarget() != ownerTarget) mob.setTarget(ownerTarget);
                if (mob.getTarget() == ownerTarget) return;
            }
        }

        LivingEntity current = mob.getTarget();
        if (isValidTarget(mob, current)) return;

        if (current != null) mob.setTarget(null);
        double nearest = Double.POSITIVE_INFINITY;
        Player best = null;
        for (Player player : mob.level().players()) {
            if (!isValidTarget(mob, player)) continue;
            double distance = mob.distanceToSqr(player);
            if (distance < nearest) {
                best = player;
                nearest = distance;
            }
        }
        if (best != null) mob.setTarget(best);
    }
}
