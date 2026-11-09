package com.mofengbaizhi.tinkersnewlife.content.skill;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;

/** Re-evaluates nearest valid player without requiring retaliation or line of sight. */
public final class MomoNearestPlayerGoal extends Goal {
    private final Mob mob;
    public MomoNearestPlayerGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.TARGET)); }
    @Override public boolean canUse() { return !mob.level().isClientSide; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { update(); }
    @Override public void tick() { update(); }
    private void update() {
        double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double nearest = range * range;
        Player best = null;
        for (Player player : mob.level().players()) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative()
                    || mob.isAlliedTo(player)) continue;
            double distance = mob.distanceToSqr(player);
            if (distance < nearest) { best = player; nearest = distance; }
        }
        mob.setTarget(best);
    }
}
