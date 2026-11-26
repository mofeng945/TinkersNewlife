package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * 咒力外放 · 顺转能量弹：纯粹咒力压缩成的高速光弹。
 * <p>
 * 朝施术者视线方向直线飞行（最大 32 格），命中第一个非友方实体即造成
 * 生成时算好的纯能量伤害并消失；被方块阻挡也消失。纯粒子视觉（无模型）。
 */
public class CurseBoltEntity extends Entity {

    /** 最大飞行距离（格） */
    public static final double MAX_RANGE = 32.0;
    /** 飞行速度（格/tick） */
    public static final double SPEED = 1.6;

    /** 施术者 UUID */
    private UUID casterId = null;
    /** 命中伤害（生成时算好，含魔杖增幅/核心特性前置） */
    private float damage = 0;
    /** 飞行方向（单位向量） */
    private Vec3 dir = Vec3.ZERO;
    /** 已飞行距离 */
    private double travelled = 0;

    public CurseBoltEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public CurseBoltEntity(Level level, Vec3 pos, UUID casterId, float damage, Vec3 look) {
        this(ModEntities.CURSE_BOLT.get(), level);
        setPos(pos);
        this.casterId = casterId;
        this.damage = damage;
        Vec3 flat = look;
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(0, 0, 1);
        this.dir = flat.normalize();
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spawnClientParticles();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        travelled += SPEED;
        if (travelled > MAX_RANGE) {
            discard();
            return;
        }
        Vec3 next = position().add(dir.scale(SPEED));
        // 撞方块消失
        if (!level().getBlockState(net.minecraft.core.BlockPos.containing(next)).isAir()) {
            discard();
            return;
        }
        setPos(next);

        // 命中第一个非友方实体
        Entity caster = casterId != null ? server.getEntity(casterId) : null;
        List<LivingEntity> hits = server.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), 1.6, 1.6, 1.6),
                e -> e.isAlive() && e != caster && !e.isSpectator()
                        && !(caster instanceof ServerPlayer sp && PuppetUtil.isAllyOf(e, sp)));
        if (!hits.isEmpty()) {
            LivingEntity target = hits.get(0);
            net.minecraft.world.damagesource.DamageSource source = caster instanceof LivingEntity living
                    ? server.damageSources().mobAttack(living) : server.damageSources().magic();
            float dmg = damage;
            if (caster instanceof ServerPlayer sp) {
                dmg = (float) com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                        .applyCurseCoreTraits(sp, target, dmg);
            }
            target.invulnerableTime = 0;
            target.hurt(source, dmg);
            if (caster instanceof ServerPlayer sp) {
                com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper.afterCurseCoreHit(sp, target, dmg);
            }
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 5, 0.3, 0.3, 0.3, 0);
            discard();
            return;
        }
        // 服务端尾迹粒子（光弹本体视觉）
        server.sendParticles(new DustParticleOptions(new Vector3f(0.95F, 0.95F, 1.0F), 1.4F),
                getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0);
    }

    /** 客户端粒子（本地也画尾迹，保证低延迟视觉） */
    private void spawnClientParticles() {
        level().addParticle(new DustParticleOptions(new Vector3f(0.95F, 0.95F, 1.0F), 1.3F),
                getX(), getY(), getZ(), 0.05, 0.05, 0.05);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isAttackable() { return false; }

    @Override
    public boolean save(CompoundTag tag) { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
