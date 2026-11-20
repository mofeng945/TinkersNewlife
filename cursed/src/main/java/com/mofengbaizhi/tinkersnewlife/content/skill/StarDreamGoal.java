package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Target-anchored black-hole channel followed by a mixed-damage laser. */
public final class StarDreamGoal extends MomoSpellGoal {
    public static final int CHANNEL_TICKS = 200;
    public static final int LASER_TICKS = 100;
    public static final double RADIUS = 5.0D;
    private static final UUID SPELL_UUID = UUID.nameUUIDFromBytes("star_dream".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final int COOLDOWN_TICKS = 2400;
    private static final double MAX_PULL = 0.035D;
    private static final double MAX_PULL_SPEED = 0.12D;
    private final KtuluMomo momo;
    private int elapsed;
    private int sourceCursor;
    private long nextAllowedAt;
    private Vec3 center;
    private List<DamageSource> sources = List.of();
    public StarDreamGoal(KtuluMomo momo) {
        super(momo, 7, COOLDOWN_TICKS, CHANNEL_TICKS + LASER_TICKS);
        this.momo = momo;
    }

    @Override protected int cooldownLeft() {
        return (int) Math.max(0L, momo.tnl$starDreamCoolUntil() - momo.level().getGameTime());
    }
    @Override protected void onStart() { beginRitual(); }
    @Override protected boolean onTick() { tickRitual(); return elapsed >= CHANNEL_TICKS + LASER_TICKS; }
    @Override protected void onStop() { finishRitual(); }
    @Override public MomoPose poseKind() { return MomoPose.STAR_DREAM; }
    @Override protected boolean interruptible() { return false; }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public boolean canUse() {
        if (momo.level().isClientSide || !momo.tnl$isBossForm() || !MomoSkills.STAR_DREAM
                || momo.getTarget() == null || !momo.getTarget().isAlive()
                || momo.getTarget().isAlliedTo(momo) || momo.tnl$isBusy() || momo.tnl$isStunned()
                || momo.tnl$hurtStaggerTicks() > 0 || momo.tnl$combat().phase() < 2
                || momo.level().getGameTime() < momo.tnl$starDreamCoolUntil()) return false;
        return momo.distanceTo(momo.getTarget()) <= 32.0D && momo.tnl$mayCastAgainSoon(7)
                && !momo.tnl$combat().flightEntryPending() && !momo.tnl$combat().flightEntryRunning();
    }

    private void beginRitual() {
        elapsed = 0;
        sourceCursor = 0;
        sources = laserSources((ServerLevel) momo.level());
        nextAllowedAt = momo.level().getGameTime() + COOLDOWN_TICKS;
        momo.tnl$setStarDreamCoolUntil(nextAllowedAt);
        LivingEntity target = momo.getTarget();
        center = target != null ? target.position().add(0, target.getBbHeight() * 0.5D, 0) : momo.position();
        momo.getNavigation().stop();
        momo.setDeltaMovement(Vec3.ZERO);
        momo.setNoGravity(false);
        momo.tnl$stunFor(2);
        momo.tnl$setStunned(true);
        momo.tnl$setStarDream(center, true);
        momo.tnl$setSpell(7, CHANNEL_TICKS + LASER_TICKS);
        momo.tnl$setAnim(MomoPose.STAR_DREAM.ordinal(), 0.0F);
    }

    @Override
    public boolean canContinueToUse() {
        return momo.isAlive() && !momo.tnl$isDead() && elapsed < CHANNEL_TICKS + LASER_TICKS;
    }

    private void tickRitual() {
        elapsed++;
        momo.getNavigation().stop();
        momo.setDeltaMovement(Vec3.ZERO);
        momo.setNoGravity(false);
        if (center == null || !(momo.level() instanceof ServerLevel level)) return;

        momo.tnl$stunFor(2);
        boolean laser = elapsed > CHANNEL_TICKS;
        // The active flag covers both phases; the renderer derives age from remaining ticks.
        float progress = laser ? (float) (elapsed - CHANNEL_TICKS) / LASER_TICKS
                : (float) elapsed / CHANNEL_TICKS;
        momo.tnl$setAnim(MomoPose.STAR_DREAM.ordinal(), progress);

        pullEntities(level, laser);
        if (laser) damageEntities(level);
    }

    private void pullEntities(ServerLevel level, boolean laser) {
        double radius = RADIUS * (laser ? 1.0D : smooth(Math.min(1.0D, elapsed / (double) CHANNEL_TICKS)));
        if (radius <= 0.05D) return;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center.x - radius, center.y - radius, center.z - radius,
                        center.x + radius, center.y + radius, center.z + radius))) {
            if (!isHostileTarget(entity)) continue;
            Vec3 delta = center.subtract(entity.position().add(0, entity.getBbHeight() * 0.5D, 0));
            double distance = delta.length();
            if (distance < 0.15D || distance >= radius) continue;
            double strength = MAX_PULL * (0.35D + 0.65D * (1.0D - distance / radius));
            Vec3 pull = delta.normalize().scale(strength);
            Vec3 velocity = entity.getDeltaMovement().scale(0.88D).add(pull);
            if (velocity.length() > MAX_PULL_SPEED) velocity = velocity.normalize().scale(MAX_PULL_SPEED);
            entity.setDeltaMovement(velocity);
            entity.hurtMarked = true;
        }
    }

    private void damageEntities(ServerLevel level) {
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center.x - RADIUS, center.y - 32, center.z - RADIUS,
                        center.x + RADIUS, center.y + 32, center.z + RADIUS))) {
            if (!isHostileTarget(entity) || entity.distanceToSqr(center.x, entity.getY(), center.z) > RADIUS * RADIUS) continue;
            for (int i = 0; i < sources.size(); i++) {
                DamageSource source = sources.get((sourceCursor + i) % sources.size());
                if (!entity.isAlive()) break;
                entity.invulnerableTime = 0;
                entity.hurt(source, 3.0F);
            }
            entity.invulnerableTime = 10;
            sourceCursor = (sourceCursor + 1) % sources.size();
        }
    }

    private List<DamageSource> laserSources(ServerLevel level) {
        // Use ten distinct vanilla damage types; none is void or true damage.
        return List.of(
                level.damageSources().magic(),
                level.damageSources().inFire(),
                level.damageSources().lightningBolt(),
                level.damageSources().hotFloor(),
                level.damageSources().cactus(),
                level.damageSources().sweetBerryBush(),
                level.damageSources().freeze(),
                level.damageSources().wither(),
                level.damageSources().dragonBreath(),
                level.damageSources().sonicBoom(momo));
    }

    private boolean isHostileTarget(LivingEntity entity) {
        if (entity == momo || !entity.isAlive() || entity.isInvulnerable()
                || entity instanceof KtuluMomo || KtuluMomo.tnl$isMomoMinion(entity)) return false;
        if (!momo.canAttack(entity) || momo.isAlliedTo(entity)) return false;
        if (entity instanceof net.minecraft.world.entity.player.Player player
                && (player.isCreative() || player.isSpectator())) return false;
        return entity.distanceToSqr(momo) <= 64.0D * 64.0D;
    }

    private static double smooth(double t) {
        return t * t * (3.0D - 2.0D * t);
    }

    private void finishRitual() {
        nextAllowedAt = Math.max(nextAllowedAt, momo.level().getGameTime());
        momo.setDeltaMovement(Vec3.ZERO);
        momo.setNoGravity(false);
        momo.tnl$setStunned(false);
        momo.tnl$setStarDream(Vec3.ZERO, false);
        if (momo.tnl$spell() == 7) momo.tnl$setSpell(0, 0);
        momo.tnl$clearAnim();
        momo.tnl$markSpellCast(7);
        center = null;
    }
}
