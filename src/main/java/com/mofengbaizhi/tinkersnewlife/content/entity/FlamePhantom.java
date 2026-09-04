package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 炎熔操术 · 反转：自爆幻翼（焰羽）。
 * 生命 1 点；体型渲染为原版 1/8；1 秒前摇后依次飞速撞向锁定敌人，
 * 撞中（或受阻/超时）即引发等同黑鸟自爆的爆炸（半径 3 线性衰减，
 * 中心 = (1+亲和/100)×(输出×3+1)×10），并在敌人脚下 3×3 燃起火焰、点燃敌人。
 */
public class FlamePhantom extends Phantom {

    /** 飞行速度（每 tick 格） */
    private static final double DIVE_SPEED = 1.8;

    private UUID ownerId;
    private UUID targetId;
    private int preTicks;      // 前摇剩余 tick
    private int stuckTicks;
    private int lifeTicks;
    private Vec3 lastTargetPos;

    public FlamePhantom(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.FLYING_SPEED, 1.0);
    }

    /** 绑定主人与目标；preDelayTick = 前摇（含依次出发错峰） */
    public void setMission(ServerPlayer owner, LivingEntity target, int preDelayTick) {
        this.ownerId = owner.getUUID();
        this.targetId = target.getUUID();
        this.lastTargetPos = target.position();
        this.preTicks = preDelayTick;
        this.setHealth(1.0F);
        this.setNoGravity(true);
    }

    public ServerPlayer getOwner() {
        if (ownerId == null) return null;
        return level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof ServerPlayer sp ? sp : null;
    }

    /** 术式锁定的撞击目标（原版 getTarget 已被幻翼 AI 占用，这里单独命名） */
    private LivingEntity getMissionTarget() {
        if (targetId == null) return null;
        if (!(level() instanceof ServerLevel sl)) return null;
        if (sl.getEntity(targetId) instanceof LivingEntity le && le.isAlive()) {
            lastTargetPos = le.position();
            return le;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        setNoGravity(true);
        // 自爆小飞行器不该被点燃/烧死（白天也不会融化）
        if (isOnFire()) {
            setSecondsOnFire(0);
        }
        lifeTicks++;
        if (lifeTicks > 200) {
            explode(null);
            return;
        }
        LivingEntity target = getMissionTarget();
        if (preTicks > 0) {
            // 前摇：原地悬停（小幅上下浮动，营造扑翼加速感）
            double hoverY = getY() + Math.sin(lifeTicks * 0.6) * 0.03;
            setDeltaMovement(0, 0, 0);
            move(MoverType.SELF, new Vec3(0, hoverY - getY(), 0));
            preTicks--;
            return;
        }
        // 俯冲：朝目标（或最后已知位置）飞速撞击
        Vec3 targetPos = target != null ? target.position() : lastTargetPos;
        if (targetPos == null) {
            explode(null);
            return;
        }
        Vec3 to = targetPos.add(0, 0.3, 0).subtract(position());
        double dist = to.length();
        if (dist < 1.1) {
            explode(target);
            return;
        }
        Vec3 dir = to.normalize();
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
        setYRot((float) yaw);
        yBodyRot = (float) yaw;
        setDeltaMovement(dir.scale(DIVE_SPEED));
        move(MoverType.SELF, getDeltaMovement());
        // 受阻（撞墙等）累计 → 原地爆炸
        if (horizontalCollision) {
            if (++stuckTicks > 8) {
                explode(target);
            }
        } else {
            stuckTicks = 0;
        }
    }

    /** 自爆：等同黑鸟自爆 + 敌人脚下 3×3 燃火、点燃敌人 */
    private void explode(LivingEntity target) {
        if (!(level() instanceof ServerLevel level)) {
            discard();
            return;
        }
        ServerPlayer owner = getOwner();
        if (target == null) {
            target = getMissionTarget();
        }
        int output = owner != null ? CursePowerHelper.getCurseOutputLevel(owner) : 0;
        int affinity = owner != null ? CursePowerHelper.getCurseAffinity(owner) : 0;
        // 黑鸟自爆公式（幻翼生命 1）：(1+亲和/100)×(输出×3+1)×10，半径 3 线性衰减
        double center = (1.0 + affinity / 100.0) * (output * 3 + 1) * 10.0;
        double radius = 3.0;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), radius * 2, radius * 2, radius * 2),
                e -> e != this && e.isAlive() && !PuppetUtil.isAllyOf(e, owner));
        for (LivingEntity e : victims) {
            double d = e.distanceToSqr(this);
            if (d <= radius * radius) {
                double falloff = 1.0 - Math.sqrt(d) / radius;
                e.hurt(damageSources().explosion(this, owner), (float) (center * falloff));
            }
        }
        // ⭐ 咒术直伤保底：防火/抗火免疫对咒力伤害无效——被撞目标额外受一跳咒术伤害
        //    （共享伤害基底 × 25%，套魔杖增幅与核心材料特性；与顺转岩浆池每跳一致）
        if (owner != null && target != null && target.isAlive()) {
            double curse = com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique
                    .computeHitDamage(owner, target);
            target.invulnerableTime = 0;
            target.hurt(owner.damageSources().mobAttack(owner), (float) curse);
            com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                    .afterCurseCoreHit(owner, target, curse);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 24, 1.2, 1.2, 1.2, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.8F);
        // 目标脚下 3×3 燃起火焰 + 点燃敌人
        if (target != null && target.isAlive()) {
            target.setSecondsOnFire(4);
            FlameManipulationTechnique.startFireField(level, target.blockPosition());
        }
        discard();
    }

    /** 不参与原版幻翼 AI（由玩家术式驱动） */
    @Override
    protected void registerGoals() {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 1 点生命的小飞行器：正常受伤即可
        return super.hurt(source, amount);
    }
}
