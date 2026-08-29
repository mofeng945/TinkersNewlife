package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 领域视觉实体（纯黑色空心圆球线框）
 * <p>
 * 无移动、无碰撞、无 AI，仅作为领域球心与半径的载体，
 * 由 {@code DomainVisualRenderer} 绘制黑色空心圆球形状（非方块）。
 */
public class DomainVisualEntity extends Entity {

    private static final EntityDataAccessor<Float> RADIUS =
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

    @Override
    protected void defineSynchedData() {
        entityData.define(RADIUS, 5.0f);
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
