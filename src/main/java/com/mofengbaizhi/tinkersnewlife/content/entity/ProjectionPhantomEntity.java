package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.ProjectionTechnique;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * 投射咒法 · 玩家虚影：
 * 生成在玩家视线方向同水平面，距离为玩家 1s 最大直线移动距离的 2/3~1 倍。
 * 1s 内玩家触碰虚影 → 虚影消失并触发 10s 增益（伤害/跳跃/速度 ×2）；超时未触碰自动消失。
 */
public class ProjectionPhantomEntity extends Entity {

    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(ProjectionPhantomEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID ownerId;
    private int lifeTicks;
    private int maxLife = 20; // 1 秒

    public ProjectionPhantomEntity(EntityType<? extends Entity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER, Optional.empty());
    }

    public void setOwner(ServerPlayer player) {
        this.ownerId = player.getUUID();
        this.entityData.set(OWNER, Optional.of(player.getUUID()));
    }

    public ServerPlayer getOwner() {
        if (ownerId == null) return null;
        return level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof ServerPlayer sp ? sp : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // 客户端虚影粒子（蓝色人形轮廓感）
            if (random.nextInt(3) == 0) {
                level().addParticle(new DustParticleOptions(new Vector3f(0.3F, 0.6F, 1.0F), 1.0F),
                        getX() + random.nextGaussian() * 0.25,
                        getY() + random.nextDouble() * 1.7,
                        getZ() + random.nextGaussian() * 0.25, 0, 0, 0);
            }
            return;
        }
        ServerPlayer owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        lifeTicks++;
        // 玩家触碰判定（水平距离 < 1.5 格 且 垂直重叠）
        double dx = owner.getX() - getX();
        double dz = owner.getZ() - getZ();
        boolean touch = dx * dx + dz * dz < 1.5 * 1.5
                && owner.getY() < getY() + 1.8 && owner.getY() + 1.8 > getY();
        if (touch) {
            ProjectionTechnique.applyBuff(owner);
            discard();
            return;
        }
        if (lifeTicks >= maxLife) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }
}
