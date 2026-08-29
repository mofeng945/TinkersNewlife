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
    }

    public void clearClashRegion() {
        entityData.set(CLASH_ACTIVE, false);
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
