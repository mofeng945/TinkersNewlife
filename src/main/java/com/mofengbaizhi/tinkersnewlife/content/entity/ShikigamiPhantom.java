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

    private final ShikigamiState state = new ShikigamiState();

    public ShikigamiPhantom(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
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

    @Override
    public double getPassengersRidingOffset() {
        return 0.9 + getShikigamiScale() * 0.4;
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
            // 空格上升（PlayerRideableJumping.handleStartJump 会设置 this.jumping）
            if (jumping) {
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
        jumping = true;
    }

    @Override
    public boolean canJump() {
        return state.type == ShikigamiType.NUE && !getPassengers().isEmpty();
    }

    @Override
    public void handleStartJump(int jumpPower) {
        jumping = true;
    }

    @Override
    public void handleStopJump() {
        jumping = false;
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
