package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * 咒力核心仪式的<b>信标光束</b>载体。
 *
 * <p>原版信标光柱是 {@code BeaconBlockEntity} 在客户端渲染的，服务端没有"放一束光"的接口，
 * 所以这里用一个纯视觉实体承载：服务端在仪式开始时在量器位置生成它（带光束颜色与高度，
 * 通过同步数据下发），客户端 {@code RitualBeamRenderer} 直接调用原版
 * {@code BeaconRenderer.renderBeaconBeam} 画真正的信标光柱——而不是以前那串
 * {@code ENTITY_EFFECT} 彩色粒子（看着像泡泡，不是光柱）。
 *
 * <p>无重力、无碰撞、无 AI、不参与任何伤害判定；活够 {@code lifeTicks} 自动消失。
 */
public class RitualBeamEntity extends Entity {

    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(RitualBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEAM_HEIGHT =
            SynchedEntityData.defineId(RitualBeamEntity.class, EntityDataSerializers.INT);

    /** 剩余存活 tick（服务端自减，到 0 消失） */
    private int lifeTicks = 20;
    /** 光束起始高度偏移（相对实体位置，格） */
    private static final int Y_OFFSET = 0;

    public RitualBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = true;
    }

    public RitualBeamEntity(Level level, double x, double y, double z, int color, int beamHeight, int lifeTicks) {
        this(ModEntities.RITUAL_BEAM.get(), level);
        this.setPos(x, y, z);
        this.entityData.set(COLOR, color & 0xFFFFFF);
        this.entityData.set(BEAM_HEIGHT, Math.max(1, beamHeight));
        this.lifeTicks = lifeTicks;
    }

    /** 光束颜色（RGB） */
    public int getBeamColor() {
        return this.entityData.get(COLOR);
    }

    /** 光束高度（格） */
    public int getBeamHeight() {
        return this.entityData.get(BEAM_HEIGHT);
    }

    public int getYOffset() {
        return Y_OFFSET;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(COLOR, 0xFFFFFF);
        this.entityData.define(BEAM_HEIGHT, 24);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (--this.lifeTicks <= 0) {
                this.discard();
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.entityData.set(COLOR, tag.getInt("BeamColor"));
        this.entityData.set(BEAM_HEIGHT, Math.max(1, tag.getInt("BeamHeight")));
        this.lifeTicks = tag.getInt("BeamLife");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BeamColor", this.getBeamColor());
        tag.putInt("BeamHeight", this.getBeamHeight());
        tag.putInt("BeamLife", this.lifeTicks);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
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
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 256.0 * 256.0;   // 光柱很高，远处也要看见
    }
}
