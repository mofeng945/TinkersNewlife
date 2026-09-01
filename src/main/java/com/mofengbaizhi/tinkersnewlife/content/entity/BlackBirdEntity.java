package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.network.PacketBlackBirdCamera;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 黑鸟操术 · 黑鸟（蝙蝠）：
 * 玩家视角切换到本实体，客户端每 tick 发送 {@link com.mofengbaizhi.tinkersnewlife.network.PacketBlackBirdInput}
 * 驱动其飞行（W 朝视线 / A/D 侧移 / 空格上升）；Shift 俯冲（2 倍速直线朝视线），
 * 撞到实体或方块自爆（不破坏方块，中心伤害 = (1+亲和/100)×(输出×3+蝙蝠血量)×10，半径 2 格）。
 * 玩家身体留在原地（隐形/无敌/钉位）。
 */
public class BlackBirdEntity extends Bat {

    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(BlackBirdEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private UUID ownerId;
    private Vec3 ownerRestPos;
    private float ownerRestYRot;
    private float ownerRestXRot;
    private boolean diving;
    private Vec3 diveDir;
    private int diveTicks;
    private int lifeTicks;
    // 客户端输入（由 PacketBlackBirdInput 更新）
    private float inputZza;
    private float inputXxa;
    private boolean inputJump;
    private boolean inputShift;
    private float inputYRot;
    private float inputXRot;

    public BlackBirdEntity(EntityType<? extends Bat> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Bat.createAttributes()
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
    }

    public void setOwner(ServerPlayer player) {
        this.ownerId = player.getUUID();
        this.entityData.set(OWNER, Optional.of(player.getUUID()));
        this.ownerRestPos = player.position();
        this.ownerRestYRot = player.getYRot();
        this.ownerRestXRot = player.getXRot();
    }

    public ServerPlayer getOwner() {
        if (ownerId == null) return null;
        return level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof ServerPlayer sp ? sp : null;
    }

    /** 客户端输入更新（服务端收到输入包后调用），含玩家视角（黑鸟朝向用，玩家本体不转头） */
    public void setInput(float zza, float xxa, boolean jumping, boolean shift, float yRot, float xRot) {
        this.inputZza = zza;
        this.inputXxa = xxa;
        this.inputJump = jumping;
        this.inputShift = shift;
        this.inputYRot = yRot;
        this.inputXRot = xRot;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerPlayer owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            finish(false);
            return;
        }
        // 玩家本体钉在原地（不移动/不转头；非无敌且不隐身，可被看到、可被攻击，死亡则视角回归）
        if (ownerRestPos != null) {
            owner.setNoGravity(true);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.teleportTo(ownerRestPos.x, ownerRestPos.y, ownerRestPos.z);
            owner.setYRot(ownerRestYRot);
            owner.setXRot(ownerRestXRot);
            owner.yBodyRot = ownerRestYRot;
            owner.yHeadRot = ownerRestYRot;
        }
        lifeTicks++;
        setNoGravity(true);
        getNavigation().stop();
        // 朝向：使用输入包携带的玩家视角（yRot+xRot），玩家本体 yRot/xRot 不变（不转头）
        float viewYaw = inputYRot;
        float viewPitch = inputXRot;
        setYRot(viewYaw);
        yBodyRot = viewYaw;
        yHeadRot = viewYaw;
        setXRot(viewPitch);
        xRotO = viewPitch;

        if (diving) {
            // 俯冲：2 倍速直线朝视线方向
            diveTicks++;
            setDeltaMovement(diveDir.scale(2.0));
            move(MoverType.SELF, getDeltaMovement());
            if (hitObstacle() || diveTicks > 80) {
                explode();
            }
            return;
        }
        // 普通操控：W 水平朝视线方向飞（不含俯仰）、A/D 侧移、空格上升
        Vec3 look = viewVector(viewPitch, viewYaw);
        Vec3 flatLook = new Vec3(look.x, 0, look.z);
        if (flatLook.lengthSqr() < 1e-6) flatLook = new Vec3(0, 0, 1);
        flatLook = flatLook.normalize();
        double speed = 0.7;
        Vec3 motion = Vec3.ZERO;
        if (inputZza != 0) {
            motion = motion.add(flatLook.scale(inputZza * speed));
        }
        if (inputXxa != 0) {
            // 左侧向量：视线顺时针旋转 90°（A=左移）
            Vec3 side = new Vec3(flatLook.z, 0, -flatLook.x).normalize();
            motion = motion.add(side.scale(inputXxa * speed * 0.6));
        }
        if (inputJump) {
            motion = motion.add(0, 0.6, 0);
        }
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        fallDistance = 0;
        // Shift：进入俯冲（记录当前视线方向）
        if (inputShift) {
            diving = true;
            diveDir = viewVector(viewPitch, viewYaw).normalize();
            diveTicks = 0;
        }
    }

    /** 视线向量（由俯仰/偏航角计算，标准实体公式） */
    private static Vec3 viewVector(float pitch, float yaw) {
        float f = pitch * ((float) Math.PI / 180F);
        float g = -yaw * ((float) Math.PI / 180F);
        float h = net.minecraft.util.Mth.cos(g);
        float i = net.minecraft.util.Mth.sin(g);
        float j = net.minecraft.util.Mth.cos(f);
        float k = net.minecraft.util.Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    /** 俯冲碰撞检测：碰撞箱与方块/实体重叠 */
    private boolean hitObstacle() {
        AABB box = getBoundingBox().inflate(0.3);
        int minX = (int) Math.floor(box.minX), maxX = (int) Math.ceil(box.maxX);
        int minY = (int) Math.floor(box.minY), maxY = (int) Math.ceil(box.maxY);
        int minZ = (int) Math.floor(box.minZ), maxZ = (int) Math.ceil(box.maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    BlockState bs = level().getBlockState(pos);
                    if (!bs.isAir() && bs.isCollisionShapeFullBlock(level(), pos)) {
                        return true;
                    }
                }
            }
        }
        return !level().getEntitiesOfClass(LivingEntity.class, box.inflate(0.5),
                e -> e != this && e != getOwner() && e.isAlive()).isEmpty();
    }

    /** 自爆：中心伤害 (1+亲和/100)×(输出×3+蝙蝠血量)×10，半径 2 格，不破坏方块 */
    private void explode() {
        ServerLevel level = (ServerLevel) this.level();
        ServerPlayer owner = getOwner();
        if (owner == null) {
            discard();
            return;
        }
        int affinity = CursePowerHelper.getCurseAffinity(owner);
        int output = CursePowerHelper.getCurseOutputLevel(owner);
        double hp = getHealth();
        double center = (1.0 + affinity / 100.0) * (output * 3 + hp) * 10.0;
        double radius = 3.0;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), radius * 2, radius * 2, radius * 2),
                e -> e != owner && e.isAlive());
        for (LivingEntity e : victims) {
            double d = e.distanceToSqr(this);
            if (d <= radius * radius) {
                double falloff = 1.0 - Math.sqrt(d) / radius;
                e.hurt(damageSources().explosion(this, owner), (float) (center * falloff));
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 24, 1.2, 1.2, 1.2, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5F, 0.8F);
        finish(true);
    }

    /** 结束：黑鸟消失 + 玩家恢复（视野回归） */
    public void finish(boolean exploded) {
        ServerPlayer owner = getOwner();
        if (owner != null) {
            owner.setInvisible(false);
            owner.setInvulnerable(false);
            owner.setNoGravity(false);
            TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner), new PacketBlackBirdCamera(0, false));
        }
        discard();
    }

    /** 回收：返还一半咒力 + 消失 */
    public void recall(ServerPlayer player) {
        if (getOwner() == null || !getOwner().equals(player)) return;
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double base = (1.0 + (output + affinity / 10.0) / 10.0) * 40.0;
        int refund = Math.max(1, (int) Math.ceil(base / 2.0));
        CursePowerHelper.addCurse(player, refund);
        finish(false);
    }

    /** 黑鸟死亡（被击杀）→ 玩家视角回归并恢复 */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide) {
            finish(false);
        }
        super.die(source);
    }

    @Override
    protected void registerGoals() {
        // 完全清空原版蝙蝠 AI（黑鸟由玩家输入驱动）
    }

    /**
     * 跳过原版蝙蝠 aiStep（服务端）：Bat 的 FlyMoveControl/栖息逻辑会让黑鸟自行移动，
     * 与玩家操控冲突。客户端保留原版 aiStep 以维持翅膀动画。
     */
    @Override
    public void aiStep() {
        if (level().isClientSide) {
            super.aiStep();
        }
    }

    /** 黑鸟不移除/不自然消失 */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /** 黑鸟永不倒挂歇息（否则模型渲染成倒吊姿态） */
    @Override
    public boolean isResting() {
        return false;
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) { return false; }
}
