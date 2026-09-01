package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * 无下限 · 苍 / 赫 / 茈 咒力球体（一个实体类，类型由实体数据区分）：
 * <ul>
 *   <li>苍（0）：天蓝球。飞行中把 10 格内实体拉向球心（不含施术者）；实体接触球心 → 爆炸</li>
 *   <li>赫（1）：亮红球（苍的术式反转）。把实体推离球心；被推开的实体撞到方块时按速度受伤；实体接触球心 → 爆炸（苍的 2 倍）</li>
 *   <li>茈（2）：紫球（苍+赫结合）。破坏所有撞到的方块（基岩除外，不掉落）；击中实体 → 苍 10 倍的爆炸伤害</li>
 * </ul>
 * 三者最大飞行距离 40 格；苍/赫撞到方块会消失。
 */
public class CursedOrbEntity extends Entity {

    public static final int TYPE_CANG = 0;
    public static final int TYPE_HE = 1;
    public static final int TYPE_ZI = 2;

    /** 最大飞行距离（格） */
    public static final double MAX_RANGE = 40.0;
    /** 飞行速度（格/tick） */
    public static final double SPEED = 1.2;
    /** 拉扯/推开半径（格） */
    public static final double FORCE_RADIUS = 10.0;
    /** 实体接触球心判定半径（格） */
    public static final double TOUCH_RADIUS = 0.9;
    /** 爆炸半径（格） */
    public static final float EXPLOSION_RADIUS = 3.0F;

    private static final EntityDataAccessor<Integer> ORB_TYPE =
            SynchedEntityData.defineId(CursedOrbEntity.class, EntityDataSerializers.INT);

    /** 施术者 UUID */
    private UUID casterId = null;
    /** 苍的基准爆炸中心伤害（生成时算好） */
    private float baseDamage = 0;
    /** 飞行方向（单位向量） */
    private Vec3 dir = Vec3.ZERO;
    /** 已飞行的累计距离（格） */
    private double travelled = 0;

    public CursedOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** 生成球体：位置 = 施术者眼前，朝 look 方向飞行 */
    public CursedOrbEntity(Level level, Vec3 pos, UUID casterId, int orbType, float baseDamage, Vec3 look) {
        this(ModEntities.CURSED_ORB.get(), level);
        setPos(pos);
        this.casterId = casterId;
        this.baseDamage = baseDamage;
        Vec3 flat = look;
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(0, 0, 1);
        this.dir = flat.normalize();
        setOrbType(orbType);
    }

    public int getOrbType() { return entityData.get(ORB_TYPE); }
    public void setOrbType(int t) { entityData.set(ORB_TYPE, t); }

    @Override
    protected void defineSynchedData() {
        entityData.define(ORB_TYPE, TYPE_CANG);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            spawnClientParticles();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        // 距离上限：40 格
        travelled += SPEED;
        if (travelled > MAX_RANGE) {
            discard();
            return;
        }
        // 前方下一格
        Vec3 next = position().add(dir.scale(SPEED));
        if (getOrbType() != TYPE_ZI) {
            // 苍/赫：撞到方块消失
            BlockPos front = BlockPos.containing(next.add(dir.scale(0.4)));
            if (!level().getBlockState(front).isAir()) {
                discard();
                return;
            }
        }
        // 移动（茈球不因方块消失，由 tickZi 持续破坏路径）
        setPos(next);

        switch (getOrbType()) {
            case TYPE_CANG -> tickCang(server);
            case TYPE_HE -> tickHe(server);
            case TYPE_ZI -> tickZi(server);
        }
    }

    // ============================================================
    //  苍：吸引实体；接触爆炸
    // ============================================================

    private void tickCang(ServerLevel server) {
        Entity caster = casterId != null ? server.getEntity(casterId) : null;
        List<LivingEntity> entities = server.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), FORCE_RADIUS * 2, FORCE_RADIUS * 2, FORCE_RADIUS * 2),
                e -> e.isAlive() && e != caster && !e.isSpectator());
        Vec3 c = position();
        for (LivingEntity e : entities) {
            double d = e.distanceToSqr(this);
            if (d > FORCE_RADIUS * FORCE_RADIUS) continue;
            // 拉向球心：速度朝向球心，强度随距离增大
            Vec3 pull = c.subtract(e.position()).normalize().scale(0.35);
            e.setDeltaMovement(e.getDeltaMovement().add(pull).scale(0.9));
        }
        // 实体接触球心 → 爆炸
        for (LivingEntity e : entities) {
            if (e.distanceToSqr(this) < TOUCH_RADIUS * TOUCH_RADIUS) {
                explode(server, 1.0F);
                return;
            }
        }
        // 尾迹粒子（服务端）
        server.sendParticles(new DustParticleOptions(new Vector3f(0.4F, 0.7F, 1.0F), 1.1F),
                getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0);
    }

    // ============================================================
    //  赫：推开实体；被推实体撞方块按速度受伤；接触爆炸（苍 2 倍）
    // ============================================================

    private void tickHe(ServerLevel server) {
        Entity caster = casterId != null ? server.getEntity(casterId) : null;
        List<LivingEntity> entities = server.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), FORCE_RADIUS * 2, FORCE_RADIUS * 2, FORCE_RADIUS * 2),
                e -> e.isAlive() && e != caster && !e.isSpectator());
        Vec3 c = position();
        DamageSource source = caster instanceof LivingEntity living ? server.damageSources().mobAttack(living) : server.damageSources().magic();
        ServerPlayer casterPlayer = caster instanceof ServerPlayer sp ? sp : null;
        for (LivingEntity e : entities) {
            double d = Math.sqrt(e.distanceToSqr(this));
            if (d > FORCE_RADIUS || d < 1e-4) continue;
            // 推离球心
            Vec3 push = e.position().subtract(c).normalize().scale(0.55);
            e.setDeltaMovement(e.getDeltaMovement().add(push).scale(0.85));
            // 被推开后撞到方块 → 按当前速度受伤
            double speed = e.getDeltaMovement().length();
            if (speed > 0.25 && isBlockedInFront(e)) {
                float dmg = (float) (speed * (1.0 + getAffinity() / 100.0) * (5.0 + getOutput() * 2.0));
                if (dmg > 0) {
                    if (casterPlayer != null) {
                        dmg = (float) com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper
                                .applyCurseCoreTraits(casterPlayer, e, dmg);
                    }
                    e.invulnerableTime = 0;
                    e.hurt(source, dmg);
                    if (casterPlayer != null) {
                        com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.afterCurseCoreHit(casterPlayer, e, dmg);
                    }
                    server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 3, 0.2, 0.2, 0.2, 0);
                }
            }
        }
        // 实体接触球心 → 爆炸（苍的 2 倍）
        for (LivingEntity e : entities) {
            if (e.distanceToSqr(this) < TOUCH_RADIUS * TOUCH_RADIUS) {
                explode(server, 2.0F);
                return;
            }
        }
        server.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.15F), 1.1F),
                getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0);
    }

    /** 实体移动方向前方 0.5 格内是否有实心方块 */
    private static boolean isBlockedInFront(LivingEntity e) {
        Vec3 vel = e.getDeltaMovement();
        if (vel.lengthSqr() < 1e-6) return false;
        Vec3 front = e.position().add(vel.normalize().scale(0.6));
        return !e.level().getBlockState(BlockPos.containing(front)).isAir();
    }

    // ============================================================
    //  茈：破坏方块；击中实体 → 苍 10 倍爆炸伤害
    // ============================================================

    private void tickZi(ServerLevel server) {
        Entity caster = casterId != null ? server.getEntity(casterId) : null;
        // 以球心为球心，破坏半径 3 格范围内所有方块（基岩除外，不掉落）；球体继续飞行
        breakSphere();
        // 对球心破坏范围（半径 3 格）内所有实体造成苍 10 倍爆炸伤害
        // （保留无敌帧，实体约每 0.5 秒受一次全额伤害）
        DamageSource source = caster instanceof LivingEntity living ? server.damageSources().mobAttack(living) : server.damageSources().magic();
        ServerPlayer casterPlayer = caster instanceof ServerPlayer sp ? sp : null;
        float dmg = baseDamage * 10.0F;
        List<LivingEntity> inRange = server.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), 6, 6, 6),
                e -> e.isAlive() && e != caster && !e.isSpectator());
        for (LivingEntity e : inRange) {
            if (e.distanceToSqr(this) > 9) continue;
            float finalDmg = casterPlayer != null
                    ? (float) com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.applyCurseCoreTraits(casterPlayer, e, dmg)
                    : dmg;
            e.hurt(source, finalDmg);
            if (casterPlayer != null) {
                com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.afterCurseCoreHit(casterPlayer, e, finalDmg);
            }
        }
        server.sendParticles(new DustParticleOptions(new Vector3f(0.7F, 0.3F, 1.0F), 1.4F),
                getX(), getY(), getZ(), 3, 0.06, 0.06, 0.06, 0);
    }

    /** 虚式·茈：以球心为球心破坏半径 3 格内所有方块（基岩除外，不掉落） */
    private void breakSphere() {
        int radius = 3;
        int cx = (int) Math.floor(getX());
        int cy = (int) Math.floor(getY());
        int cz = (int) Math.floor(getZ());
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius) continue;
                    BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
                    var state = level().getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.BEDROCK)) continue;
                    level().destroyBlock(pos, false);
                }
            }
        }
    }

    // ============================================================
    //  爆炸（苍基准 × 倍率；范围衰减；材料特性；无视无敌帧）
    // ============================================================

    private void explode(ServerLevel server, float multiplier) {
        Vec3 c = position();
        server.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.9F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        float color = getOrbType() == TYPE_HE ? 1.0F : (getOrbType() == TYPE_ZI ? 0.85F : 0.6F);
        server.sendParticles(new DustParticleOptions(new Vector3f(color, 0.4F, 1.0F), 1.3F),
                c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0);

        Entity caster = casterId != null ? server.getEntity(casterId) : null;
        DamageSource source = caster instanceof LivingEntity living ? server.damageSources().mobAttack(living) : server.damageSources().magic();
        ServerPlayer casterPlayer = caster instanceof ServerPlayer sp ? sp : null;
        float radius = EXPLOSION_RADIUS;
        float center = baseDamage * multiplier;
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(c, radius * 2, radius * 2, radius * 2));
        for (LivingEntity target : targets) {
            if (target == caster) continue;
            if (target.isSpectator() || !target.isAlive()) continue;
            double dist = target.position().add(0, target.getBbHeight() * 0.5, 0).distanceTo(c);
            if (dist > radius) continue;
            double dmg = center * (1.0 - dist / radius);
            if (dmg <= 0) continue;
            if (casterPlayer != null) {
                dmg = com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.applyCurseCoreTraits(casterPlayer, target, dmg);
            }
            target.invulnerableTime = 0;
            target.hurt(source, (float) dmg);
            if (casterPlayer != null) {
                com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.afterCurseCoreHit(casterPlayer, target, dmg);
            }
        }
        discard();
    }

    private int getAffinity() {
        Entity caster = casterId != null ? level() instanceof ServerLevel sl ? sl.getEntity(casterId) : null : null;
        return caster instanceof ServerPlayer sp ? com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper.getCurseAffinity(sp) : 0;
    }

    private int getOutput() {
        Entity caster = casterId != null ? level() instanceof ServerLevel sl ? sl.getEntity(casterId) : null : null;
        return caster instanceof ServerPlayer sp ? com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper.getCurseOutputLevel(sp) : 0;
    }

    /** 客户端粒子：按类型给颜色 */
    private void spawnClientParticles() {
        Vector3f col = switch (getOrbType()) {
            case TYPE_CANG -> new Vector3f(0.4F, 0.7F, 1.0F);
            case TYPE_HE -> new Vector3f(1.0F, 0.2F, 0.15F);
            default -> new Vector3f(0.7F, 0.3F, 1.0F);
        };
        if (tickCount % 2 == 0) {
            level().addParticle(new DustParticleOptions(col, 1.0F),
                    getX(), getY(), getZ(), 0.05, 0.05, 0.05);
        }
    }

    // 纯弹道/视觉载体：无碰撞、不可交互
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
