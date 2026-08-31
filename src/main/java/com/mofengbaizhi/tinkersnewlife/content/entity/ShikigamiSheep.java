package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/** 怒角：继承原版net.minecraft.world.entity.animal.Sheep，渲染/动画/纹理/碰撞箱全复用原版 */
public class ShikigamiSheep extends net.minecraft.world.entity.animal.Sheep implements ShikigamiMob {

    private final ShikigamiState state = new ShikigamiState();

    public ShikigamiSheep(EntityType<? extends net.minecraft.world.entity.animal.Sheep> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.animal.Sheep.createAttributes()
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override public ShikigamiType getShikigamiType() { return state.type; }
    @Override public ShikigamiState getState() { return state; }
    @Override public int getShikigamiVariant() { return state.variant; }
    @Override public float getShikigamiScale() { return (float) state.scale; }
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
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            ShikigamiBehavior.tick(this, this);
        }
    }

    /**
     * 原版 Sheep.customServerAiStep 会访问 registerGoals 里初始化的 eatBlockGoal，
     * 而我们把 registerGoals 清空导致该字段为 null（NPE）。覆写为空即可。
     */
    @Override
    protected void customServerAiStep() {}

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
        float s = (float) state.scale;
        return net.minecraft.world.entity.EntityDimensions.fixed(0.9F * s, 1.3F * s);
    }

    /** 怒角：橙色羊毛（原版 SheepRenderer 的羊毛层按 getColor 染色） */
    @Override
    public net.minecraft.world.item.DyeColor getColor() {
        return net.minecraft.world.item.DyeColor.ORANGE;
    }

    @Override
    protected void registerGoals() {
        // 完全清空原版 AI（不调用 super）
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
}