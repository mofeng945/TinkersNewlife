package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 「兵士佩刀」的<b>刀光</b>（§810）—— 仿拔刀剑那种<b>弧形斩击面片</b>的载体实体 ✓。
 *
 * <h2>为什么做成实体而不是粒子</h2>
 * 用户口径：<b>「我要的是刀光光效，不是横扫粒子」</b> ✓ ⇒ {@code ParticleTypes.SWEEP_ATTACK}（原版横扫弧）
 * 是粒子 ✗ 做不到拔刀剑那种"贴图弧面划过"的观感 ✓。本仓既有的同类做法是
 * {@code FlyingSwordTrailRenderer}（自己组网格 ＋ 顶点色 ＋ <b>原版 shader getter</b> ✓ 见该类注释里
 * "自带着色器在装了光影包的整合包里整条看不见 ✗"的教训 ✓）⇒ 这里沿用同一套思路 ✓：
 * <ul>
 *   <li><b>服务端</b>只做一件事 ✓：在目标身上生成这个实体（带朝向/自转/大小/寿命/颜色 ✓）；</li>
 *   <li><b>客户端</b>{@code SoldierSlashRenderer} 用 {@code TRIANGLE_STRIP} 之外的 QUADS 拼一条
 *       <b>两端收尖的弧带</b> ✓ ＋ 弧光贴图 {@code textures/entity/soldier_slash.png} ✓（**新文件** ✓）
 *       ＋ 顶点色染成灰色 ✓ ⇒ 就是"刀光" ✓。</li>
 * </ul>
 *
 * <h2>字段（都走 {@link SynchedEntityData} ⇒ 客户端自己就能画 ✓ 不需要额外网络包 ✓）</h2>
 * <ul>
 *   <li>{@code LIFE}：寿命（tick ✓ 到点自己 {@code discard()} ✓ 只做视觉 ⇒ 不需要存档 ✓）；</li>
 *   <li>{@code DELAY}：延迟出现（tick ✓）—— 多段时让刀光<b>一段一段依次亮</b> ✓；</li>
 *   <li>{@code SCALE} / {@code ROLL}：弧的大小与自转角度 ✓（每次斩击角度不同 ⇒ 不呆板 ✓）；</li>
 *   <li>{@code TINT}：0xRRGGBB 顶点色 ✓（唐横刀是灰色 {@code 0xC9CFD9} ✓ 以后别的武器想换色也能用 ✓）。</li>
 * </ul>
 *
 * <p>实体本身<b>不移动、不碰撞、不可选、不可攻击</b> ✓（{@code noPhysics} ＋ {@code isPickable=false} ✓），
 *  并且 {@code noSave()} 注册 ⇒ 重进世界不会留下悬挂的光效 ✓。
 */
public class SoldierSlashEntity extends Entity {

    private static final EntityDataAccessor<Integer> LIFE_TICKS =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> START_DELAY =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SLASH_SCALE =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SLASH_ROLL =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SLASH_TINT =
            SynchedEntityData.defineId(SoldierSlashEntity.class, EntityDataSerializers.INT);

    public SoldierSlashEntity(EntityType<? extends SoldierSlashEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /**
     * 生成一道刀光 ✓。
     *
     * @param level      所在世界 ✓
     * @param pos        世界坐标（一般放目标身体中心 ✓）
     * @param yaw        弧面朝向（左右 ✓）
     * @param roll       弧面自转（决定这一刀是横劈/斜劈/竖劈 ✓）
     * @param scale      大小倍率 ✓
     * @param lifeTicks  寿命（tick ✓ 一般 6 ✓）
     * @param startDelay 延迟出现（tick ✓ 多段依次亮 ✓）
     * @param tint       0xRRGGBB 顶点色 ✓（灰色 = 0xC9CFD9 ✓）
     */
    public SoldierSlashEntity(Level level, Vec3 pos, float yaw, float roll, float scale,
                              int lifeTicks, int startDelay, int tint) {
        this(ModEntities.SOLDIER_SLASH.get(), level);
        this.setPos(pos.x, pos.y, pos.z);
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setLifeTicks(lifeTicks);
        this.setStartDelay(startDelay);
        this.setSlashScale(scale);
        this.setSlashRoll(roll);
        this.setSlashTint(tint);
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(LIFE_TICKS, 6);
        this.getEntityData().define(START_DELAY, 0);
        this.getEntityData().define(SLASH_SCALE, 1.0F);
        this.getEntityData().define(SLASH_ROLL, 0.0F);
        this.getEntityData().define(SLASH_TINT, 0xC9CFD9);
    }

    // ==================== 字段读写 ====================
    public void setLifeTicks(int v) { this.getEntityData().set(LIFE_TICKS, v); }
    public int getLifeTicks() { return this.getEntityData().get(LIFE_TICKS); }
    public void setStartDelay(int v) { this.getEntityData().set(START_DELAY, v); }
    public int getStartDelay() { return this.getEntityData().get(START_DELAY); }
    public void setSlashScale(float v) { this.getEntityData().set(SLASH_SCALE, v); }
    public float getSlashScale() { return this.getEntityData().get(SLASH_SCALE); }
    public void setSlashRoll(float v) { this.getEntityData().set(SLASH_ROLL, v); }
    public float getSlashRoll() { return this.getEntityData().get(SLASH_ROLL); }
    public void setSlashTint(int v) { this.getEntityData().set(SLASH_TINT, v); }
    public int getSlashTint() { return this.getEntityData().get(SLASH_TINT); }

    @Override
    public void tick() {
        super.tick();
        // 纯视觉 ⇒ 服务端到点收掉即可 ✓ 客户端自己按 tickCount 淡出 ✓（服务端不发多余包 ✓）
        if (!this.level().isClientSide
                && this.tickCount > this.getStartDelay() + this.getLifeTicks() + 2) {
            this.discard();
        }
    }

    /** 弧面比碰撞箱大得多 ⇒ 交给渲染剔除时用放大后的箱子 ✓ 免得贴脸时整片刀光被裁掉 ✗ */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(2.5D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 128.0D * 128.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setLifeTicks(tag.getInt("life"));
        this.setStartDelay(tag.getInt("delay"));
        this.setSlashScale(tag.getFloat("scale"));
        this.setSlashRoll(tag.getFloat("roll"));
        this.setSlashTint(tag.getInt("tint"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("life", this.getLifeTicks());
        tag.putInt("delay", this.getStartDelay());
        tag.putFloat("scale", this.getSlashScale());
        tag.putFloat("roll", this.getSlashRoll());
        tag.putInt("tint", this.getSlashTint());
    }
}
