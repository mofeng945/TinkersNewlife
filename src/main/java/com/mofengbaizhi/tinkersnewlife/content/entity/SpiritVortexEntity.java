package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 咒灵操术 · 反转：黑色漩涡弹（虚式直射）。
 * 笔直朝视线方向飞行，命中实体/方块或到达射程后爆散：
 * 半径 3 线性衰减伤害 = 由被献祭个体生命上限/攻击与施术者输出/亲和计算（发射时快照）。
 * 视觉：飞行中绕轨迹旋转的黑烟漩涡。
 */
public class SpiritVortexEntity extends Entity {

    public static final double SPEED = 1.6;
    public static final double MAX_RANGE = 40.0;
    public static final double TOUCH = 0.8;

    private UUID casterId;
    private float damage;
    private Vec3 dir = Vec3.ZERO;
    private double travelled = 0;

    public SpiritVortexEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void launch(ServerPlayer caster, float damage, Vec3 look) {
        this.casterId = caster.getUUID();
        this.damage = damage;
        this.dir = look.normalize();
        setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            swirlParticles();
            return;
        }
        if (casterId == null || dir == Vec3.ZERO) {
            discard();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        ServerPlayer caster = server.getServer() != null
                ? server.getServer().getPlayerList().getPlayer(casterId) : null;
        // 碰撞实体（非施术者同队）
        AABB box = getBoundingBox().inflate(TOUCH);
        List<LivingEntity> hit = server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !PuppetUtil.isAllyOf(e, caster));
        if (!hit.isEmpty()) {
            explode(server, caster, hit.get(0));
            return;
        }
        // 方块阻挡
        BlockState bs = level().getBlockState(blockPosition());
        if (!bs.isAir() && bs.isCollisionShapeFullBlock(level(), blockPosition())) {
            explode(server, caster, null);
            return;
        }
        // 直射
        setDeltaMovement(dir.scale(SPEED));
        move(MoverType.SELF, getDeltaMovement());
        travelled += SPEED;
        if (travelled > MAX_RANGE) {
            explode(server, caster, null);
        }
    }

    private void explode(ServerLevel server, ServerPlayer caster, LivingEntity direct) {
        double radius = 3.0;
        List<LivingEntity> victims = server.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), radius * 2, radius * 2, radius * 2),
                e -> e.isAlive() && !PuppetUtil.isAllyOf(e, caster));
        for (LivingEntity e : victims) {
            double d = e.distanceToSqr(this);
            if (d <= radius * radius) {
                double falloff = 1.0 - Math.sqrt(d) / radius;
                e.invulnerableTime = 0;
                e.hurt(caster != null ? caster.damageSources().mobAttack(caster)
                        : server.damageSources().magic(), damage * (float) falloff);
            }
        }
        server.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 60, 1.6, 1.6, 1.6, 0.02);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 24, 0.8, 0.8, 0.8, 0.01);
        discard();
    }

    /** 客户端旋转黑烟漩涡 */
    private void swirlParticles() {
        if (random.nextInt(3) != 0) return;
        double angle = tickCount * 0.5;
        for (int i = 0; i < 3; i++) {
            double a = angle + i * (Math.PI * 2.0 / 3.0);
            double r = 0.25 + (tickCount % 10) * 0.02;
            level().addParticle(ParticleTypes.SMOKE,
                    getX() + Math.cos(a) * r, getY() + Math.sin(a) * r * 0.5, getZ() + Math.sin(a) * r,
                    0, 0, 0);
        }
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean save(CompoundTag tag) { return false; }
}
