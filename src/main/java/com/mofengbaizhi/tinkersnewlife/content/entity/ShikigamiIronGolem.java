package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/** 魔虚罗：继承原版铁傀儡，渲染/动画/纹理复用原版 + 自定义头顶轮盘/铁剑（渲染器层） */
public class ShikigamiIronGolem extends IronGolem implements ShikigamiMob {

    private final ShikigamiState state = new ShikigamiState();

    /** 体型缩放（entityData 同步到客户端：渲染放大与碰撞箱一致，避免服务端 AABB 大、
     *  客户端模型小导致怪瞄准头顶天空/寻路错乱） */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> SCALE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ShikigamiIronGolem.class,
                    net.minecraft.network.syncher.EntityDataSerializers.FLOAT);

    public ShikigamiIronGolem(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SCALE, 1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes()
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override public ShikigamiType getShikigamiType() { return state.type; }
    @Override public ShikigamiState getState() { return state; }
    @Override public int getShikigamiVariant() { return state.variant; }
    @Override public float getShikigamiScale() { return this.entityData.get(SCALE); }
    @Override public boolean isTamed() { return state.tamed; }
    @Override public LivingEntity getLockedTarget() {
        return state.lockedId != null && level() instanceof net.minecraft.server.level.ServerLevel sl
                && sl.getEntity(state.lockedId) instanceof LivingEntity le ? le : null;
    }
    @Override public UUID getOwnerId() { return state.ownerId; }

    @Override
    public ServerPlayer getOwner() {
        if (state.ownerId == null) return null;
        return level() instanceof net.minecraft.server.level.ServerLevel sl && sl.getEntity(state.ownerId) instanceof ServerPlayer sp ? sp : null;
    }

    @Override
    public void initStats(ServerPlayer player, ShikigamiType type, boolean tamed, @Nullable LivingEntity locked, int variant) {
        ShikigamiBehavior.initStats(this, this, player, type, tamed, locked, variant);
        // 体型同步到客户端（渲染缩放用）
        this.entityData.set(SCALE, (float) state.scale);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            // 魔虚罗常驻限伤：每 0.5 秒累计受伤不超过 30（无限时长，定期兜底刷新防意外被清）
            if (tickCount % 20 == 0) {
                var dl = com.mofengbaizhi.tinkersnewlife.content.ModEffects.DAMAGE_LIMIT.get();
                if (dl != null) {
                    var cur = getEffect(dl);
                    if (cur == null || !cur.isInfiniteDuration()) {
                        addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                dl, -1, 0, false, false, true));
                    }
                }
            }
            ShikigamiBehavior.regenMahoraga(this, this);
            ShikigamiBehavior.tick(this, this);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, ShikigamiBehavior.adaptDamage(this, this, amount));
    }

    @Override
    public void die(DamageSource source) {
        ShikigamiBehavior.onDeath(this, this);
        super.die(source);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(Pose pose) {
        // 用同步后的 scale（客户端与服务端一致，渲染/碰撞/AABB 全部对齐）
        float s = getShikigamiScale();
        return net.minecraft.world.entity.EntityDimensions.fixed(1.4F * s * 0.85F, 2.7F * s * 0.85F);
    }

    @Override
    protected void registerGoals() {
        // 完全清空原版铁傀儡 AI（不调用 super）
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
}
