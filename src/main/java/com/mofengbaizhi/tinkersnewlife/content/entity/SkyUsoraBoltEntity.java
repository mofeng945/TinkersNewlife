package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 天空操术 · 反转「宇守罗弹」：对视线锁定目标发射的追踪弹。
 * 轻追踪锁定目标；命中目标（或目标消失后首个敌对）时如击碎薄冰——
 * 白色冰晶碎屑爆散 + 将"空间"连同对手一起击飞（巨量击退）+ 咒术伤害（命中时套核心材料特性）。
 * 视觉：飞行中细碎雪晶尾迹。
 */
public class SkyUsoraBoltEntity extends Entity {

    public static final double SPEED = 1.8;
    public static final double MAX_RANGE = 48.0;
    public static final double TOUCH = 0.8;

    private UUID casterId;
    private int targetId = -1;
    private float damage;
    private float knock;
    private Vec3 dir = Vec3.ZERO;
    private double travelled = 0;

    public SkyUsoraBoltEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void launch(ServerPlayer caster, LivingEntity target, float damage, float knock) {
        this.casterId = caster.getUUID();
        this.targetId = target.getId();
        this.damage = damage;
        this.knock = knock;
        Vec3 to = target.getEyePosition(1.0F).subtract(position());
        this.dir = to.lengthSqr() > 1e-4 ? to.normalize() : caster.getLookAngle();
        setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trailParticles();
            return;
        }
        if (casterId == null || dir == Vec3.ZERO) {
            discard();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        ServerPlayer caster = server.getServer() != null
                ? server.getServer().getPlayerList().getPlayer(casterId) : null;
        // 轻追踪锁定目标（目标仍存活且在射程内 → 每 tick 微调方向）
        LivingEntity locked = null;
        if (targetId >= 0) {
            Entity e = server.getEntity(targetId);
            if (e instanceof LivingEntity le && le.isAlive()
                    && distanceToSqr(e) < MAX_RANGE * MAX_RANGE) {
                locked = le;
                Vec3 to = le.getEyePosition(1.0F).subtract(position());
                if (to.lengthSqr() > 1e-4) {
                    Vec3 want = to.normalize();
                    dir = dir.add(want.subtract(dir).scale(0.25)).normalize();
                }
            }
        }
        // 碰撞：命中锁定目标优先；目标丢失后命中首个敌对
        AABB box = getBoundingBox().inflate(TOUCH);
        List<LivingEntity> hits = server.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !PuppetUtil.isAllyOf(e, caster));
        if (!hits.isEmpty()) {
            LivingEntity victim = null;
            if (locked != null && hits.contains(locked)) {
                victim = locked;
            } else {
                victim = hits.get(0);
            }
            shatter(server, caster, victim);
            return;
        }
        // 方块阻挡：碎裂消散（不造成伤害）
        BlockState bs = level().getBlockState(blockPosition());
        if (!bs.isAir() && bs.isCollisionShapeFullBlock(level(), blockPosition())) {
            poof(server);
            return;
        }
        setDeltaMovement(dir.scale(SPEED));
        move(MoverType.SELF, getDeltaMovement());
        travelled += SPEED;
        if (travelled > MAX_RANGE) {
            poof(server);
        }
    }

    /** 命中：击碎薄冰——伤害 + 将目标连"空间"一起击飞 */
    private void shatter(ServerLevel server, ServerPlayer caster, LivingEntity victim) {
        float finalDamage = damage;
        if (caster != null) {
            finalDamage = (float) CurseCoreTraitHelper.applyCurseCoreTraits(caster, victim, damage);
        }
        victim.invulnerableTime = 0;
        victim.hurt(caster != null ? caster.damageSources().mobAttack(caster)
                : server.damageSources().magic(), finalDamage);
        if (caster != null) {
            CurseCoreTraitHelper.afterCurseCoreHit(caster, victim, finalDamage);
        }
        // 巨量击退：沿"施术者→目标"水平方向 + 上抛，连同空间一起飞出去
        Vec3 away = position().subtract(victim.position());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 1e-4) {
            away = dir;
            away = new Vec3(away.x, 0, away.z);
        }
        Vec3 vel = victim.getDeltaMovement()
                .add(away.normalize().scale(knock))
                .add(0, 0.5 + knock * 0.25, 0);
        victim.setDeltaMovement(vel);
        victim.hurtMarked = true;
        // 碎冰/空间碎裂视觉
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SNOWBALL)),
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                40, 0.9, 0.9, 0.9, 0.12);
        server.sendParticles(ParticleTypes.POOF,
                victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                16, 0.6, 0.4, 0.6, 0.02);
        server.sendParticles(ParticleTypes.SNOWFLAKE,
                victim.getX(), victim.getY() + victim.getBbHeight(), victim.getZ(),
                30, 1.2, 0.8, 1.2, 0.05);
        discard();
    }

    private void poof(ServerLevel server) {
        server.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 10, 0.5, 0.5, 0.5, 0.02);
        discard();
    }

    /** 客户端：细碎雪晶尾迹 */
    private void trailParticles() {
        if (random.nextInt(2) != 0) return;
        level().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(),
                (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.1,
                (random.nextDouble() - 0.5) * 0.1);
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
