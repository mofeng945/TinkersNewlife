package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 领域视觉实体（纯黑色空心圆球线框）
 * <p>
 * 无移动、无碰撞、无 AI，仅作为领域球心与半径的载体，
 * 由 {@code DomainVisualRenderer} 绘制黑色空心圆球形状（非方块）。
 * 领域对抗时同步对方领域球体，渲染器隐藏落入对方球体内的黑色边缘部分。
 */
public class DomainVisualEntity extends Entity {

    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(DomainVisualEntity.class, EntityDataSerializers.FLOAT);

    /** 对抗激活标记 */
    private static final EntityDataAccessor<Boolean> CLASH_ACTIVE =
            SynchedEntityData.defineId(DomainVisualEntity.class, EntityDataSerializers.BOOLEAN);
    /** 对抗中对方领域球心（1.20.1 无 VEC3 序列化器，用三个浮点） */
    private static final EntityDataAccessor<Float> CLASH_CX =
            SynchedEntityData.defineId(DomainVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CLASH_CY =
            SynchedEntityData.defineId(DomainVisualEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CLASH_CZ =
            SynchedEntityData.defineId(DomainVisualEntity.class, EntityDataSerializers.FLOAT);
    /** 对抗中对方领域半径 */
    private static final EntityDataAccessor<Float> CLASH_RADIUS =
            SynchedEntityData.defineId(DomainVisualEntity.class, EntityDataSerializers.FLOAT);

    private UUID ownerId = null;

    public DomainVisualEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public DomainVisualEntity(Level level, double x, double y, double z, float radius, UUID ownerId) {
        this(ModEntities.DOMAIN_VISUAL.get(), level);
        setPos(x, y, z);
        this.entityData.set(RADIUS, radius);
        this.ownerId = ownerId;
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    // ============================================================
    //  领域对抗视觉：隐藏落入对方球体内的黑色边缘
    // ============================================================

    public void setClashRegion(Vec3 center, double radius) {
        entityData.set(CLASH_ACTIVE, true);
        entityData.set(CLASH_CX, (float) center.x);
        entityData.set(CLASH_CY, (float) center.y);
        entityData.set(CLASH_CZ, (float) center.z);
        entityData.set(CLASH_RADIUS, (float) radius);
        invalidateRenderRegions();
    }

    public void clearClashRegion() {
        entityData.set(CLASH_ACTIVE, false);
        invalidateRenderRegions();
    }

    public boolean isClashActive() {
        return entityData.get(CLASH_ACTIVE);
    }

    public Vec3 getClashCenter() {
        return new Vec3(entityData.get(CLASH_CX), entityData.get(CLASH_CY), entityData.get(CLASH_CZ));
    }

    public float getClashRadius() {
        return entityData.get(CLASH_RADIUS);
    }

    // ============================================================
    //  ⭐ 渲染侧缓存：与本球相交的其它领域球体（每帧挖洞判定用）
    //  <p>原先渲染器<b>每帧</b>都要扫一遍身边 256 格范围内的领域视觉实体并新建一个 List，
    //  领域对抗时逐帧构建网格，等于每秒白扫 60 次。这里把结果按 gameTime 缓存：
    //  同一 tick 内多帧共用一份，几何变化（球心/半径/对抗区域）立即令缓存失效。
    // ============================================================

    /** 缓存：{x, y, z, 半径}，null 表示尚未计算 */
    private java.util.List<double[]> renderRegionsCache;
    /** 缓存所属 gameTime（±1 让同一次 tick 内的多帧命中） */
    private long renderRegionsTime = Long.MIN_VALUE;

    /** 令渲染相交缓存立即失效（球心/半径/对抗区域变化时调用） */
    public void invalidateRenderRegions() {
        renderRegionsCache = null;
        renderRegionsTime = Long.MIN_VALUE;
    }

    /**
     * 与本球相交的其它领域球体（含服务端兜底同步的对手区域）。
     * 结果按 tick 缓存；调用方只读，勿修改返回的列表。
     */
    public java.util.List<double[]> getOrComputeRenderRegions() {
        long t = level() == null ? 0L : level().getGameTime();
        long dt = t - renderRegionsTime;
        if (renderRegionsCache == null || dt < 0 || dt > 1) {
            renderRegionsTime = t;
            renderRegionsCache = computeRenderRegions();
        }
        return renderRegionsCache;
    }

    /** 渲染几何（球心/半径）变化时令缓存失效 */
    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        invalidateRenderRegions();
    }

    private java.util.List<double[]> computeRenderRegions() {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        if (level() == null) return out;
        Vec3 c = position();
        double r = getRadius();
        double reach = r + 256.0;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                c.x - reach, c.y - reach, c.z - reach,
                c.x + reach, c.y + reach, c.z + reach);
        for (DomainVisualEntity other : level().getEntitiesOfClass(DomainVisualEntity.class, box)) {
            if (other == this) continue;
            Vec3 oc = other.position();
            double or = other.getRadius();
            if (c.distanceTo(oc) < r + or) {
                out.add(new double[]{oc.x, oc.y, oc.z, or});
            }
        }
        // 兜底：服务端同步过来的对手区域（客户端还没收到对方视觉实体时）
        if (out.isEmpty() && isClashActive()) {
            Vec3 cc = getClashCenter();
            out.add(new double[]{cc.x, cc.y, cc.z, getClashRadius()});
        }
        return out;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 5.0f);
        entityData.define(CLASH_ACTIVE, false);
        entityData.define(CLASH_CX, 0.0f);
        entityData.define(CLASH_CY, 0.0f);
        entityData.define(CLASH_CZ, 0.0f);
        entityData.define(CLASH_RADIUS, 0.0f);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) {
            ownerId = tag.getUUID("owner");
        }
        entityData.set(RADIUS, tag.getFloat("radius"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("owner", ownerId);
        }
        tag.putFloat("radius", getRadius());
    }

    /**
     * ⭐ 修复"玩家移动到不合适的位置就看不到黑色球壳"：
     * 默认实现按 {@code 包围盒尺寸 × 64} 限制渲染距离，本实体只有 0.5 格 → 球心超过约 32 格就整颗球不画了
     * （而领域半径可达 40+ 格：站在球壳边缘时球心就在 40 格外，于是黑球"消失"）。
     * 这里取消距离剔除，并给出真实球体包围盒，让其它剔除模组（如 EntityCulling）也能正确判定。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        double r = Math.max(1.0, getRadius());
        return new net.minecraft.world.phys.AABB(
                getX() - r, getY() - r, getZ() - r,
                getX() + r, getY() + r, getZ() + r);
    }

    // 纯视觉实体：不移动、不推挤、不可碰撞
    @Override
    public boolean isPushable() { return false; }
    @Override
    public boolean isPickable() { return false; }
    @Override
    public void tick() {}

    /** 不写入存档：领域是临时状态，避免服务器重启后残留孤儿黑球 */
    @Override
    public boolean save(CompoundTag tag) { return false; }
}
