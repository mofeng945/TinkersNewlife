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

    /** 骑乘输入（PacketNueInput 每 tick 更新：空格=上升、潜行=下马） */
    private boolean rideJump;
    private boolean rideShift;

    /** 服务端收到客户端骑乘输入后写入（每 tick 刷新，避免原版跳跃命令粘滞） */
    public void setRideInput(boolean jump, boolean shift) {
        this.rideJump = jump;
        this.rideShift = shift;
    }

    /** 式神类型（entityData 跨端同步，客户端骑乘逻辑依赖它识别 NUE） */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> TYPE_ID =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ShikigamiPhantom.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);

    private final ShikigamiState state = new ShikigamiState();

    public ShikigamiPhantom(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TYPE_ID, (byte) 0);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.6)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override public ShikigamiType getShikigamiType() {
        int ord = entityData.get(TYPE_ID) & 0xFF;
        return ord >= 0 && ord < ShikigamiType.values().length ? ShikigamiType.values()[ord] : ShikigamiType.NUE;
    }
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
        entityData.set(TYPE_ID, (byte) type.ordinal());
        ShikigamiBehavior.initStats(this, this, player, type, tamed, locked, variant);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            ShikigamiBehavior.tick(this, this);
        }
    }

    /** 骑乘时每 tick 强制朝向玩家视线（双端），避免被原版 AI/移动控制覆盖 */
    @Override
    public void tick() {
        super.tick();
        if (getShikigamiType() == ShikigamiType.NUE && !getPassengers().isEmpty()) {
            Entity rider = getFirstPassenger();
            if (rider instanceof Player player) {
                setYRot(player.getYRot());
                yBodyRot = player.getYRot();
                yBodyRotO = player.getYRot();
                yHeadRot = player.getYRot();
                yHeadRotO = player.getYRot();
            }
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
        // 保持原版幻翼尺寸（双端一致；NUE 骑乘时乘客贴背不悬浮）
        return net.minecraft.world.entity.EntityDimensions.fixed(0.9F, 0.5F);
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
        if (getShikigamiType() == ShikigamiType.NUE
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
        // 骑乘偏移设为 0（乘客贴合坐骑，避免悬浮）
        return 0.0;
    }

    @Override
    protected void tickRidden(Player player, Vec3 movement) {
        super.tickRidden(player, movement);
        if (getShikigamiType() != ShikigamiType.NUE) return;
        // 原版骑乘移动由"客户端预测"驱动：客户端实体本地执行移动，位置经
        // ServerboundMoveVehiclePacket 上报，服务端采纳客户端位置（handleMoveVehicle）。
        // 因此移动逻辑必须双端执行（客户端预测 + 服务端权威一致推进），
        // 若客户端跳过则客户端实体不动 → 上报原地 → 服务端也不动（无法移动）。
        setNoGravity(true);
        setYRot(player.getYRot());
        yBodyRot = player.getYRot();
        yHeadRot = player.getYRot();
        // 潜行=下马（原版语义）：仅服务端终止骑乘；客户端等待下马同步，不移动
        if (rideShift) {
            if (!level().isClientSide) {
                player.stopRiding();
            }
            return;
        }
        // 前进 = 完整视线方向（含俯仰）：抬头上仰飞行、低头俯冲，空格额外上升。
        // 侧移 = 水平方向（A/D 不改变高度）。
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-8) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        double speed = 0.55 + getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.4;
        Vec3 motion = Vec3.ZERO;
        // 客户端：LocalPlayer 每 tick 由 input 更新 zza/xxa（并随 ServerboundPlayerInputPacket 上报），
        // 服务端：ServerPlayer.zza/xxa 由 setPlayerInput 每 tick 同步，两端数值一致
        float forward = player.zza;
        float strafe = player.xxa;
        if (forward != 0) {
            // 朝完整视线方向（含俯仰角）前进/后退
            motion = motion.add(look.scale(forward * speed));
        }
        if (strafe != 0) {
            // 左侧向量：水平视线顺时针旋转 90°（A=左移）
            Vec3 side = new Vec3(flat.z, 0, -flat.x).normalize();
            motion = motion.add(side.scale(strafe * speed * 0.6));
        }
        // 空格上升（客户端 ClientEventHandler 每 tick 写入本地实体 rideJump，
        // 服务端由 PacketNueInput 写入，每 tick 刷新不粘滞）
        if (rideJump) {
            motion = motion.add(0, 0.6, 0);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        fallDistance = 0;
    }

    /** 骑乘输入向量：从玩家读取（原版马式），确保 W/A/S/D 生效 */
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 movement) {
        if (getShikigamiType() != ShikigamiType.NUE) return super.getRiddenInput(player, movement);
        float forward = player.zza;
        float strafe = player.xxa * 0.5F;
        return new Vec3(strafe, 0, forward);
    }

    // ============================================================
    //  玩家骑乘（空格=上升 / 潜行=下马）：不使用原版 PlayerRideableJumping 命令链
    //  （客户端对非马坐骑只发 START 不发 STOP，会导致跳跃标志粘滞、一直向上飞），
    //  输入改由 PacketNueInput 每 tick 显式上报，见 setRideInput/tickRidden。
    // ============================================================

    @Override
    public void onPlayerJump(int jumpPower) {
        // 不再使用：输入走 PacketNueInput
    }

    @Override
    public boolean canJump() {
        // 关闭原版骑乘跳跃命令链（否则客户端会发 START_RIDING_JUMP 而永不发 STOP）
        return false;
    }

    @Override
    public void handleStartJump(int jumpPower) {
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    public int getJumpCooldown() {
        return 0;
    }

    @Override
    public void travel(Vec3 movement) {
        if (getShikigamiType() == ShikigamiType.NUE && !getPassengers().isEmpty()) {
            return;
        }
        super.travel(movement);
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
}
