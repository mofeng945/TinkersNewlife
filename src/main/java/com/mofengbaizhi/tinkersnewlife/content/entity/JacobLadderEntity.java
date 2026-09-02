package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
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
import net.minecraft.world.entity.MobType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * 雅各布天梯 法阵/光柱实体：
 * <ul>
 *   <li>阶段 1（0~40 tick）：法阵蓄力（七芒星 + 十字架 + 圆环粒子），2 秒</li>
 *   <li>阶段 2（40~140 tick）：光柱降下——法阵正下方半径内实体每 2 tick 受一次帧伤（无视无敌帧），
 *       亡灵生物伤害 ×8；首次命中施加 60 秒术式/领域封印，并瞬间终止持续性咒术（无限/领域/黑鸟）</li>
 *   <li>140 tick 后消失</li>
 * </ul>
 * 法阵半径 = (1+(亲和/10+输出)/10) × 输出 × 5；帧伤 = (1+(亲和/10+输出)/10) × (输出×8 + 玩家伤害) × 0.1。
 */
public class JacobLadderEntity extends Entity {

    /** 蓄力时长（tick）：2 秒 */
    public static final int CHARGE_TICKS = 40;
    /** 光柱持续时长（tick）：8 秒 */
    public static final int BEAM_TICKS = 160;

    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(JacobLadderEntity.class, EntityDataSerializers.FLOAT);

    private UUID casterId = null;
    /** 法阵半径（格） */
    private double radius = 8.0;
    /** 光柱基础帧伤 */
    private float frameDamage = 1.0F;
    /** 已对目标施加封印的标志（避免重复终止持续术式） */
    private boolean sealedApplied = false;
    /** 光柱期间锁定的已封印狱门疆（照射结束后碎裂释放囚犯） */
    private UUID lockedJailId = null;

    public JacobLadderEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** 生成法阵：位置 = 目标头顶上方 10 格 */
    public JacobLadderEntity(Level level, Vec3 pos, UUID casterId, double radius, float frameDamage) {
        this(ModEntities.JACOB_LADDER.get(), level);
        setPos(pos);
        this.casterId = casterId;
        this.radius = radius;
        this.frameDamage = frameDamage;
        entityData.set(RADIUS, (float) radius);
    }

    /** 法阵半径（客户端渲染用，由 entityData 同步） */
    public float getRenderRadius() {
        return entityData.get(RADIUS);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 8.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // 法阵/光柱视觉由渲染器（JacobLadderRenderer）在所有客户端绘制
            return;
        }
        ServerLevel server = (ServerLevel) level();
        if (tickCount < CHARGE_TICKS) {
            // 蓄力：法阵粒子（大圆套小圆 + 转动三角形 + 转动七芒星）
            spawnChargeParticlesServer(server);
            if (tickCount == 1) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.5F, 0.6F);
            }
            return;
        }
        if (tickCount >= CHARGE_TICKS + BEAM_TICKS) {
            // 照射结束：先碎裂被锁定的狱门疆（释放囚犯），再消失
            breakLockedJailAtBeamEnd(server);
            discard();
            return;
        }
        // 光柱：每 2 tick 对法阵下方半径内实体造成帧伤
        if (tickCount % 2 == 0) {
            beamDamage(server);
        }
        // 光柱期间法阵保持显示 + 光柱粒子
        spawnChargeParticlesServer(server);
        spawnBeamParticlesServer(server);
        if (tickCount == CHARGE_TICKS) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0F, 1.0F);
        }
    }

    /** 光柱结束（即将消失前）：碎裂被锁定的已封印狱门疆并释放囚犯 */
    private void breakLockedJailAtBeamEnd(ServerLevel server) {
        if (lockedJailId == null) return;
        Entity jail = server.getEntity(lockedJailId);
        lockedJailId = null;
        if (jail instanceof com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity gourd
                && gourd.isSealed()) {
            gourd.releasePrisonerAndDestroy();
        }
    }

    /** 光柱伤害：法阵正下方（Y 由法阵到底面）半径内所有实体 */
    private void beamDamage(ServerLevel server) {
        Entity caster = casterId != null ? server.getEntity(casterId) : null;
        DamageSource source = caster instanceof LivingEntity living ? server.damageSources().mobAttack(living) : server.damageSources().magic();
        ServerPlayer casterPlayer = caster instanceof ServerPlayer sp ? sp : null;
        double r = radius;
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class,
                new AABB(getX() - r, getY() - 100, getZ() - r, getX() + r, getY() + 1, getZ() + r),
                e -> e.isAlive() && e != caster && !e.isSpectator());
        for (LivingEntity target : targets) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            if (dx * dx + dz * dz > r * r) continue;
            // 帧伤（亡灵 ×8）
            float dmg = frameDamage;
            if (target.getMobType() == MobType.UNDEAD) dmg *= 8.0F;
            if (casterPlayer != null) {
                dmg = (float) com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper
                        .applyCurseCoreTraits(casterPlayer, target, dmg);
            }
            target.invulnerableTime = 0; // 无视无敌帧
            // 记录原速度，伤害后恢复 → 光柱不造成击退（帧伤钉在原地）
            Vec3 preMotion = target.getDeltaMovement();
            target.hurt(source, dmg);
            target.setDeltaMovement(preMotion);
            if (casterPlayer != null) {
                com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.afterCurseCoreHit(casterPlayer, target, dmg);
            }
            // 施加 60 秒封印 + 瞬间终止持续性咒术（仅首次命中目标）
            if (!sealedApplied && target instanceof ServerPlayer sp) {
                sealedApplied = true;
                applySealTo(sp);
            }
        }
        // ⭐ 雅各布天梯照射已封印的狱门疆 → 锁定（照射结束才碎裂释放，见 breakLockedJailAtBeamEnd）
        // 已锁定目标失效（被拾取/消失/非封印态）则清除，允许重新锁定
        if (lockedJailId != null) {
            Entity locked = server.getEntity(lockedJailId);
            if (!(locked instanceof com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity gj)
                    || !gj.isSealed()) {
                lockedJailId = null;
            }
        }
        if (lockedJailId == null) {
            com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity nearest = null;
            double nearestDist = r * r;
            for (com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity jail :
                    server.getEntitiesOfClass(com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity.class,
                            new AABB(getX() - r, getY() - 100, getZ() - r, getX() + r, getY() + 1, getZ() + r))) {
                if (!jail.isSealed()) continue;
                double dx = jail.getX() - getX();
                double dz = jail.getZ() - getZ();
                double d = dx * dx + dz * dz;
                if (d <= nearestDist) {
                    nearestDist = d;
                    nearest = jail;
                }
            }
            if (nearest != null) lockedJailId = nearest.getUUID();
        }
        // 锁定视觉：金色粒子自光柱中心垂落到被锁定狱门疆（每 2 tick 一次）
        if (lockedJailId != null && server.getEntity(lockedJailId)
                instanceof com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity lockedJail) {
            Vector3f gold = new Vector3f(1.0F, 0.85F, 0.3F);
            for (double y = getY(); y > lockedJail.getY() + 0.1; y -= 1.2) {
                double ox = (server.random.nextDouble() - 0.5) * 0.3;
                double oz = (server.random.nextDouble() - 0.5) * 0.3;
                server.sendParticles(new DustParticleOptions(gold, 1.4F),
                        lockedJail.getX() + ox, y, lockedJail.getZ() + oz, 2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    /** 对目标施加封印：60 秒禁用术式/领域 + 瞬间终止持续咒术 */
    private void applySealTo(ServerPlayer target) {
        CursePowerHelper.applySeal(target, 60);
        target.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.tinkersnewlife.sealed.entered", CursePowerHelper.getSealedRemainingSeconds(target)), true);
        // 无下限·无限：瞬间终止
        com.mofengbaizhi.tinkersnewlife.content.curse.WuliangWuxianTechnique.deactivate(target);
        // 已展开领域：瞬间终止
        com.mofengbaizhi.tinkersnewlife.content.curse.DomainRegistry.closeBySeal(target);
        // 黑鸟操术：回收
        com.mofengbaizhi.tinkersnewlife.content.curse.BlackBirdTechnique.sealRecall(target);
    }

    // ============================================================
    //  粒子（服务端 sendParticles，所有客户端可见）
    // ============================================================

    /** 蓄力法阵：大圆套小圆 + 绕中心转动的三角形（小圆周上，3 顶点）+ 七芒星（大圆周上，7 顶点）。
     *  三角形转速为七芒星的 2 倍。 */
    private void spawnChargeParticlesServer(ServerLevel server) {
        Vector3f gold = new Vector3f(1.0F, 0.85F, 0.3F);
        Vector3f bright = new Vector3f(1.0F, 1.0F, 0.7F);
        double r = radius;
        double inner = r * 0.5;
        // 大圆
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32.0;
            server.sendParticles(new DustParticleOptions(gold, 1.3F),
                    getX() + Math.cos(a) * r, getY(), getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        // 小圆
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20.0;
            server.sendParticles(new DustParticleOptions(bright, 1.2F),
                    getX() + Math.cos(a) * inner, getY(), getZ() + Math.sin(a) * inner, 1, 0, 0, 0, 0);
        }
        // 七芒星：7 顶点在大圆上，绕中心轴转动（角速度 base）
        double starAngle = tickCount * 0.04; // 基准转速
        drawStar(server, r, 7, 2, starAngle, gold);
        // 三角形：3 顶点在小圆上，转速为七芒星的 2 倍
        double triAngle = tickCount * 0.08;
        drawStar(server, inner, 3, 1, triAngle, bright);
        // 中心能量光点
        server.sendParticles(new DustParticleOptions(bright, 1.8F), getX(), getY(), getZ(), 3, 0.3, 0.5, 0.3, 0.02);
    }

    /** 星形连线粒子：n 个顶点均匀分布在半径 radius 的圆上（初始角 baseAngle + 旋转角），
     *  按 step 间隔连线（step=1 正多边形，step=2 五芒星/七芒星等）。 */
    private void drawStar(ServerLevel server, double radius, int vertices, int step, double rotate, Vector3f color) {
        double[][] pts = new double[vertices][2];
        for (int i = 0; i < vertices; i++) {
            double a = rotate + Math.PI * 2 * i / vertices;
            pts[i][0] = getX() + Math.cos(a) * radius;
            pts[i][1] = getZ() + Math.sin(a) * radius;
        }
        for (int i = 0; i < vertices; i++) {
            int j = (i + step) % vertices;
            for (int s = 1; s <= 5; s++) {
                double t = s / 6.0;
                double px = pts[i][0] + (pts[j][0] - pts[i][0]) * t;
                double pz = pts[i][1] + (pts[j][1] - pts[i][1]) * t;
                server.sendParticles(new DustParticleOptions(color, 1.2F), px, getY(), pz, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 光柱粒子：发光光柱（白色核心 + 金色边缘）+ 底部光圈 */
    private void spawnBeamParticlesServer(ServerLevel server) {
        Vector3f white = new Vector3f(1.0F, 1.0F, 0.9F);
        Vector3f gold = new Vector3f(1.0F, 0.85F, 0.3F);
        for (int y = 1; y <= 20; y += 2) {
            double ox = (server.random.nextDouble() - 0.5) * radius * 0.5;
            double oz = (server.random.nextDouble() - 0.5) * radius * 0.5;
            server.sendParticles(new DustParticleOptions(white, 1.7F),
                    getX() + ox, getY() - y, getZ() + oz, 2, 0.2, 0.2, 0.2, 0.01);
            server.sendParticles(new DustParticleOptions(gold, 1.0F),
                    getX() + ox, getY() - y, getZ() + oz, 1, 0.4, 0.4, 0.4, 0.0);
        }
        // 法阵中心强光
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY() - 1, getZ(), 6, 0.4, 1.0, 0.4, 0.02);
        // 底部光圈
        for (int i = 0; i < 24; i++) {
            double a = server.random.nextDouble() * Math.PI * 2;
            double rr = server.random.nextDouble() * radius;
            server.sendParticles(new DustParticleOptions(white, 1.5F),
                    getX() + Math.cos(a) * rr, getY() - 20, getZ() + Math.sin(a) * rr, 1, 0.2, 0.2, 0.2, 0.01);
        }
    }

    // 纯视觉/伤害载体
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
