package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * 赤血操术·超新星 血球实体
 * <p>
 * 在目标位置生成的微小血球（直径约 0.2 格），悬浮 0.8 秒（16 tick）后爆炸：
 * <ul>
 *   <li>不破坏方块（只对生物结算伤害）</li>
 *   <li>中心伤害在生成时由施法者计算并存入（含魔杖增幅）</li>
 *   <li>随距离线性衰减，无视无敌帧，材料战斗特性每目标触发</li>
 *   <li>纯视觉/伤害载体，不存档、无碰撞、无重力</li>
 * </ul>
 */
public class BloodNovaEntity extends Entity {

    /** 延迟（tick）：0.8 秒 */
    public static final int DELAY_TICKS = 16;
    /** 血球渲染半径（直径约 0.2） */
    public static final float BALL_RADIUS = 0.1F;

    /** 施法者（服务端） */
    private UUID casterId = null;
    /** 爆炸中心伤害（生成时算好，含魔杖增幅） */
    private float centerDamage = 0;
    /** 爆炸半径（格） */
    private float explosionRadius = 3.0F;

    public BloodNovaEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** 生成血球：位置 = 目标中心，延迟 0.8s 后爆炸 */
    public BloodNovaEntity(Level level, Vec3 pos, UUID casterId, float centerDamage, float explosionRadius) {
        this(ModEntities.BLOOD_NOVA.get(), level);
        setPos(pos);
        this.casterId = casterId;
        this.centerDamage = centerDamage;
        this.explosionRadius = explosionRadius;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        if (level().isClientSide) {
            // 血球脉冲粒子：红色 dust 环绕，强化"血球"视觉
            if (tickCount % 2 == 0) {
                double spread = BALL_RADIUS * 1.5;
                level().addParticle(new DustParticleOptions(new Vector3f(0.8F, 0.05F, 0.05F), 0.9F),
                        getX(), getY(), getZ(), 0.05, 0.05, 0.05);
                level().addParticle(ParticleTypes.SMOKE,
                        getX(), getY(), getZ(), 0, 0.02, 0);
            }
            return;
        }
        // 服务端：0.8 秒后爆炸
        if (tickCount >= DELAY_TICKS) {
            explode();
        }
    }

    /** 爆炸：不破坏方块，对范围内生物结算伤害（中心满额、线性衰减、无视无敌帧） */
    private void explode() {
        ServerLevel server = (ServerLevel) level();
        Vec3 c = position();
        // 音效 + 爆炸粒子 + 血雾
        server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.8F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        server.sendParticles(new DustParticleOptions(new Vector3f(0.85F, 0.05F, 0.05F), 1.2F),
                c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0);

        // 施法者（可能已下线，退化为通用伤害源）
        Entity caster = casterId != null ? server.getEntity(casterId) : null;
        DamageSource source = caster instanceof LivingEntity living
                ? server.damageSources().mobAttack(living)
                : server.damageSources().generic();

        float radius = explosionRadius;
        ServerPlayer casterPlayer = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(c, radius * 2, radius * 2, radius * 2));
        for (LivingEntity target : targets) {
            if (target == caster) continue;
            if (target.isSpectator() || !target.isAlive()) continue;
            Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
            double dist = center.distanceTo(c);
            if (dist > radius) continue;
            double dmg = centerDamage * (1.0 - dist / radius);
            if (dmg <= 0) continue;
            // 材料战斗特性 + 无视无敌帧
            dmg = com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                    .applyCurseCoreTraits(casterPlayer, target, dmg);
            target.invulnerableTime = 0;
            target.hurt(source, (float) dmg);
            if (casterPlayer != null) {
                com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper.afterCurseCoreHit(casterPlayer, target, dmg);
            }
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, center.x, center.y, center.z, 3, 0.2, 0.2, 0.2, 0);
        }
        discard();
    }

    // 纯伤害/视觉载体：无碰撞、不可交互、无重力
    @Override
    public boolean isPushable() { return false; }
    @Override
    public boolean isPickable() { return false; }
    @Override
    public boolean isAttackable() { return false; }

    /** 不写入存档：0.8 秒生命周期，避免残留 */
    @Override
    public boolean save(CompoundTag tag) { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
