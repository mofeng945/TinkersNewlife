package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdCamera;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * 傀儡操术 · 雪傀儡（玩家视角转移操控）。
 * <p>
 * 左键无效；右键投雪球（弹道命中伤害 = round((2 + 输出×2) × (1 + 亲和/100))，命中附霜冻
 * amp0 时长 (40 + 输出×20) tick，4 tick 一发）；Shift 自爆：中心伤害 = (12 + 输出×6) ×
 * (1 + 亲和/100)，半径 3 格线性衰减，命中附霜冻 amp=min(2,输出/2) 时长 (60 + 输出×30) tick，
 * 不破坏方块。无原版近战/生雪/怕雨等行为。
 */
public class PuppetSnowGolem extends SnowGolem implements PuppetGolemMob {

    private static final int SNOW_CD = 4;

    private UUID ownerId;
    private Vec3 ownerRestPos;
    private float ownerRestYRot;
    private float ownerRestXRot;

    private float inputZza;
    private float inputXxa;
    private float inputYRot;
    private float inputXRot;
    private boolean inputJump;
    private boolean inputShift;
    private boolean inputRight;
    private boolean prevShift;
    private boolean detonated;
    private int snowCd;
    private int paidCost;

    public PuppetSnowGolem(EntityType<? extends SnowGolem> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return SnowGolem.createAttributes();
    }

    // ================= PuppetGolemMob =================

    @Override
    public void puppetSetInput(float zza, float xxa, boolean jumping, boolean shift,
                               boolean left, boolean right, float yRot, float xRot) {
        this.inputZza = zza;
        this.inputXxa = xxa;
        this.inputJump = jumping;
        this.inputShift = shift;
        this.inputRight = right;
        this.inputYRot = yRot;
        this.inputXRot = xRot;
    }

    @Override
    public void puppetBindOwner(ServerPlayer player, int paid) {
        this.ownerId = player.getUUID();
        this.ownerRestPos = player.position();
        this.ownerRestYRot = player.getYRot();
        this.ownerRestXRot = player.getXRot();
        this.paidCost = paid;
        this.prevShift = true; // 防召唤瞬间因仍按住 Shift 而立即自爆
    }

    @Override
    public ServerPlayer puppetOwner() {
        return getOwner();
    }

    @Override
    public int puppetPaidCost() {
        return paidCost;
    }

    @Override
    public void puppetSetPaidCost(int cost) {
        this.paidCost = cost;
    }

    @Override
    public void puppetFinish(boolean exploded) {
        finish(exploded);
    }

    public ServerPlayer getOwner() {
        if (ownerId == null) return null;
        return level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof ServerPlayer sp ? sp : null;
    }

    /** 结束：主人解除定身 + 视角回归 + 实体消失 */
    public void finish(boolean exploded) {
        ServerPlayer owner = getOwner();
        if (owner != null) {
            owner.setNoGravity(false);
            TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner),
                    new PacketBlackBirdCamera(0, false));
        }
        discard();
    }

    // ================= 服务端每 tick 控制 =================

    /** 客户端行走动画同步：记录上一 tick 位置 */
    private double animPrevX;
    private double animPrevZ;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            syncClientAnim();
            return;
        }
        serverControl();
    }

    /** 移动由服务端驱动，客户端不会调用 travel()，这里用每 tick 位移差驱动摆腿（含行走动画同步） */
    private void syncClientAnim() {
        double dx = getX() - animPrevX;
        double dz = getZ() - animPrevZ;
        animPrevX = getX();
        animPrevZ = getZ();
        this.updateWalkAnimation((float) Math.sqrt(dx * dx + dz * dz));
    }

    private void serverControl() {
        ServerPlayer owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            finish(false);
            return;
        }
        if (ownerRestPos != null) {
            owner.setNoGravity(true);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.teleportTo(ownerRestPos.x, ownerRestPos.y, ownerRestPos.z);
            owner.setYRot(ownerRestYRot);
            owner.setXRot(ownerRestXRot);
            owner.yBodyRot = ownerRestYRot;
            owner.yHeadRot = ownerRestYRot;
        }
        setYRot(inputYRot);
        yBodyRot = inputYRot;
        yHeadRot = inputYRot;
        setXRot(inputXRot);
        xRotO = inputXRot;

        if (snowCd > 0) snowCd--;

        if (inputShift && !prevShift && !detonated) {
            detonate();
            return;
        }
        prevShift = inputShift;

        // 左键无效；右键投雪球
        if (inputRight && snowCd <= 0) {
            doShoot(owner);
        }

        Vec3 vel = getDeltaMovement();
        Vec3 motion = Vec3.ZERO;
        if (inputZza != 0 || inputXxa != 0) {
            Vec3 fwd = PuppetUtil.flatDir(inputYRot);
            Vec3 sideLeft = new Vec3(fwd.z, 0, -fwd.x);
            double spd = getAttributeValue(Attributes.MOVEMENT_SPEED);
            motion = fwd.scale(inputZza * spd).add(sideLeft.scale(inputXxa * spd * 0.85));
        }
        double vy = vel.y;
        if (inputJump && onGround()) {
            vy = 0.42;
        }
        if (!onGround()) {
            vy -= 0.08;
            vy *= 0.98;
        }
        if (ownerRestPos != null && distanceToSqr(ownerRestPos.x, ownerRestPos.y, ownerRestPos.z) > 48 * 48) {
            teleportTo(ownerRestPos.x + 0.5, ownerRestPos.y, ownerRestPos.z + 0.5);
            vy = 0;
        }
        setDeltaMovement(motion.x, vy, motion.z);
        move(MoverType.SELF, getDeltaMovement());
        if (onGround()) fallDistance = 0;
        if (getY() < -64) {
            finish(false);
        }
    }

    /** 右键：投雪球（弹道伤害 + 命中短霜冻） */
    private void doShoot(ServerPlayer owner) {
        snowCd = SNOW_CD;
        int output = CursePowerHelper.getCurseOutputLevel(owner);
        int affinity = CursePowerHelper.getCurseAffinity(owner);
        float dmg = Math.round((2.0F + 2.0F * output) * (1.0F + affinity / 100.0F));
        dmg = com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier
                .getSpellAmplification(owner, dmg);
        int frostTicks = 40 + output * 20;
        PuppetSnowball ball = new PuppetSnowball(ModEntities.PUPPET_SNOWBALL.get(), level());
        Vec3 eye = position().add(0, 1.3, 0);
        ball.moveTo(eye.x, eye.y, eye.z, getYRot(), getXRot());
        ball.setOwner(this);
        ball.configure(dmg, frostTicks);
        Vec3 dir = PuppetUtil.viewVec(inputXRot, inputYRot);
        ball.shoot(dir.x, dir.y, dir.z, 1.4F, 1.0F);
        level().addFreshEntity(ball);
        swing(InteractionHand.MAIN_HAND);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** Shift 自爆：半径 3，线性衰减 + 霜冻，不破坏方块 */
    private void detonate() {
        detonated = true;
        ServerPlayer owner = getOwner();
        ServerLevel level = (ServerLevel) this.level();
        int output = owner != null ? CursePowerHelper.getCurseOutputLevel(owner) : 0;
        int affinity = owner != null ? CursePowerHelper.getCurseAffinity(owner) : 0;
        double center = (12.0 + 6.0 * output) * (1.0 + affinity / 100.0);
        if (owner != null) {
            center = com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier
                    .getSpellAmplification(owner, (float) center);
        }
        double radius = 3.0;
        int frostAmp = Math.min(2, output / 2);
        int frostTicks = 60 + output * 30;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), radius * 2, radius * 2, radius * 2),
                e -> e != this && e.isAlive() && !PuppetUtil.isAllyOf(e, owner));
        for (LivingEntity e : victims) {
            double d = e.distanceToSqr(this);
            if (d <= radius * radius) {
                double falloff = 1.0 - Math.sqrt(d) / radius;
                e.hurt(damageSources().explosion(this, owner), (float) (center * falloff));
                if (e.isAlive()) {
                    e.addEffect(new MobEffectInstance(ModEffects.FROST.get(), frostTicks, frostAmp));
                }
            }
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.8, getZ(), 40, 1.4, 1.4, 1.4, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 1.6F, 0.9F);
        if (owner != null) {
            PuppetTechnique.onPuppetSelfDestruct(owner);
        }
        finish(true);
    }

    // ================= 防御性覆写 =================

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            finish(false);
        }
        super.die(source);
    }

    @Override
    protected void registerGoals() {
        // 玩家完全操控，无原版雪傀儡 AI（不自动丢雪球/不近战）
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        // 傀儡不掉落战利品
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) {
        return false;
    }
}
