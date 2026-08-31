package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/** 鵺：继承原版幻翼（飞行/骑乘），渲染/动画/纹理全复用原版 */
public class ShikigamiPhantom extends Phantom implements ShikigamiMob, net.minecraft.world.entity.PlayerRideableJumping {

    /** 骑乘跳跃状态（entityData 同步：客户端 onPlayerJump 设值，服务端 tickRidden 读取） */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> RIDING_JUMP =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ShikigamiPhantom.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private final ShikigamiState state = new ShikigamiState();

    public ShikigamiPhantom(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(RIDING_JUMP, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.6)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
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
        return net.minecraft.world.entity.EntityDimensions.fixed(0.9F * s, 0.5F * s);
    }

    /** 鵺是式神不是亡灵：白天不燃烧 */
    @Override
    public boolean isSunBurnTick() {
        return false;
    }

    /** 式神不因和平模式被清除（原版幻翼是敌对生物会被清除） */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    protected void registerGoals() {
        // 完全清空原版幻翼 AI（不调用 super）
    }

    // ============================================================
    //  骑乘（同旧版 ShikigamiEntity 已验证逻辑）
    // ============================================================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (state.type == ShikigamiType.NUE
                && state.tamed && player.getUUID().equals(state.ownerId) && player.getMainHandItem().isEmpty()) {
            if (player.isPassenger()) {
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            player.startRiding(this);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public LivingEntity getControllingPassenger() {
        Entity e = getFirstPassenger();
        return e instanceof LivingEntity le ? le : null;
    }

    /**
     * 骑乘时必须返回 true，否则 travelRidden 走 else 分支把 deltaMovement 清零，
     * 且服务端（isLocalPlayer=false）不会执行任何移动，导致完全无法操控。
     * 返回 true 后双端都走 travel(vec3)，而我们的 travel 覆写在骑乘时 no-op，
     * 移动完全由 tickRidden 的 move() 驱动。
     */
    @Override
    public boolean isControlledByLocalInstance() {
        return true;
    }

    @Override
    public double getPassengersRidingOffset() {
        // 幻翼本体很小（碰撞箱 0.9×0.5），骑乘偏移取低值，避免乘客悬浮过高
        return 0.35 + getShikigamiScale() * 0.2;
    }

    @Override
    protected void tickRidden(Player player, Vec3 movement) {
        super.tickRidden(player, movement);
        if (state.type == ShikigamiType.NUE) {
            setNoGravity(true);
            setYRot(player.getYRot());
            yBodyRot = player.getYRot();
            yHeadRot = player.getYRot();
            Vec3 look = player.getLookAngle();
            double speed = 0.55 + getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.4;
            Vec3 motion = Vec3.ZERO;
            if (player.zza != 0) {
                motion = motion.add(look.scale(player.zza * speed));
            }
            if (player.xxa != 0) {
                Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
                motion = motion.add(side.scale(player.xxa * speed * 0.6));
            }
            // 空格上升（entityData 同步：客户端 onPlayerJump / 服务端 handleStartJump 写值）
            if (entityData.get(RIDING_JUMP) || jumping) {
                motion = motion.add(0, 0.6, 0);
            }
            setDeltaMovement(motion);
            move(MoverType.SELF, motion);
            fallDistance = 0;
        }
    }

    /** 骑乘输入：从玩家读取（原版马式），确保 W/A/S/D 生效 */
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 movement) {
        if (state.type != ShikigamiType.NUE) return super.getRiddenInput(player, movement);
        float forward = player.zza;
        float strafe = player.xxa * 0.5F;
        return new Vec3(strafe, 0, forward);
    }

    // ============================================================
    //  玩家骑乘跳跃（空格上升）：PlayerRideableJumping
    // ============================================================

    @Override
    public void onPlayerJump(int jumpPower) {
        // 客户端本地调用：写 entityData，随数据同步到服务端
        jumping = true;
        entityData.set(RIDING_JUMP, true);
    }

    @Override
    public boolean canJump() {
        return state.type == ShikigamiType.NUE && !getPassengers().isEmpty();
    }

    @Override
    public void handleStartJump(int jumpPower) {
        jumping = true;
        entityData.set(RIDING_JUMP, true);
    }

    @Override
    public void handleStopJump() {
        jumping = false;
        entityData.set(RIDING_JUMP, false);
    }

    @Override
    public int getJumpCooldown() {
        return 0;
    }

    @Override
    public void travel(Vec3 movement) {
        if (state.type == ShikigamiType.NUE && !getPassengers().isEmpty()) {
            return;
        }
        super.travel(movement);
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
}
