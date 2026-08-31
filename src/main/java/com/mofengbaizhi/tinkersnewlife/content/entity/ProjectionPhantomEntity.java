package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.ProjectionTechnique;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * 投射咒法 · 玩家虚影：
 * 生成在玩家视线方向同水平面，距离为玩家 1s 最大直线移动距离的 2/3~1 倍。
 * 1s 内玩家触碰虚影 → 虚影消失并叠加增益（伤害/跳跃/速度 ×2^层数）；
 * 超时未触碰 → 罚站 3 秒。
 */
public class ProjectionPhantomEntity extends LivingEntity {

    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(ProjectionPhantomEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID ownerId;
    private int lifeTicks;
    private int maxLife = 20; // 1 秒

    public ProjectionPhantomEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
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
            // 1s 内未触碰 → 罚站 3 秒
            ProjectionTechnique.startStun(owner);
            discard();
        }
    }

    @Override
    public net.minecraft.world.entity.HumanoidArm getMainArm() {
        return net.minecraft.world.entity.HumanoidArm.RIGHT;
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
        // 虚影不持有装备
    }

    @Override
    public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public Iterable<net.minecraft.world.item.ItemStack> getArmorSlots() {
        return java.util.Collections.emptyList();
    }

    @Override
    public Iterable<net.minecraft.world.item.ItemStack> getHandSlots() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }
}
