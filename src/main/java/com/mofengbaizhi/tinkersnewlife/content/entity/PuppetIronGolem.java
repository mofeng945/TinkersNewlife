package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * 傀儡操术 · 铁傀儡（玩家视角转移操控）。
 * <p>
 * 本体定身：主人留在原地（可见可被打）；玩家输入驱动移动/转身。
 * 左键攻击：前方 3 格内非豁免目标，伤害 = round((4 + 输出×4) × (1 + 亲和/100))，12 tick 冷却；
 * 右键击飞：前方 2.5 格 60° 扇形内目标被击退（无直接伤害），8 tick 冷却；
 * Shift：自爆，中心伤害 = (30 + 输出×10) × (1 + 亲和/100)，半径 5 格线性衰减，不破坏方块。
 * 傀儡死亡/自爆 → 视角回归、主人恢复；自爆后重召冷却 max(200, 1200 - 输出×40) tick。
 */
public class PuppetIronGolem extends IronGolem implements PuppetGolemMob {

    private static final double MELEE_RANGE = 3.0;      // 左击命中距离
    private static final double KNOCK_RANGE = 2.5;      // 右键击飞距离
    private static final double KNOCK_CONE = 0.866;     // cos(30°) 60° 扇形
    private static final int MELEE_CD = 12;
    private static final int KNOCK_CD = 8;

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
    private boolean inputLeft;
    private boolean inputRight;
    private boolean prevShift;
    private boolean detonated;
    private int meleeCd;
    private int knockCd;
    private int paidCost;

    public PuppetIronGolem(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return IronGolem.createAttributes();
    }

    // ================= PuppetGolemMob =================

    @Override
    public void puppetSetInput(float zza, float xxa, boolean jumping, boolean shift,
                               boolean left, boolean right, float yRot, float xRot) {
        this.inputZza = zza;
        this.inputXxa = xxa;
        this.inputJump = jumping;
        this.inputShift = shift;
        this.inputLeft = left;
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
        // 主人定身：留在原地、不转身
        if (ownerRestPos != null) {
            owner.setNoGravity(true);
            owner.setDeltaMovement(Vec3.ZERO);
            owner.teleportTo(ownerRestPos.x, ownerRestPos.y, ownerRestPos.z);
            owner.setYRot(ownerRestYRot);
            owner.setXRot(ownerRestXRot);
            owner.yBodyRot = ownerRestYRot;
            owner.yHeadRot = ownerRestYRot;
        }
        // 朝向 = 玩家视角
        setYRot(inputYRot);
        yBodyRot = inputYRot;
        yHeadRot = inputYRot;
        setXRot(inputXRot);
        xRotO = inputXRot;

        if (meleeCd > 0) meleeCd--;
        if (knockCd > 0) knockCd--;

        // Shift（边缘触发）→ 自爆
        if (inputShift && !prevShift && !detonated) {
            detonate();
            return;
        }
        prevShift = inputShift;

        if (inputLeft && meleeCd <= 0) {
            doMelee(owner);
        }
        if (inputRight && knockCd <= 0) {
            doKnock(owner);
        }

        // 移动：W/A/S/D 相对朝向（地面行走 + 重力 + 跳跃）
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
        // 超出操控范围自动拉回（防走失）
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

    /** 左键：原版式近战命中 */
    private void doMelee(ServerPlayer owner) {
        meleeCd = MELEE_CD;
        Vec3 fwd = PuppetUtil.flatDir(inputYRot);
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().expandTowards(fwd.scale(MELEE_RANGE)).inflate(1.2),
                e -> e != this && e.isAlive() && !PuppetUtil.isAllyOf(e, owner));
        LivingEntity best = null;
        double bestD = MELEE_RANGE * MELEE_RANGE;
        for (LivingEntity e : list) {
            Vec3 to = e.position().subtract(position());
            double horizSq = to.x * to.x + to.z * to.z;
            if (horizSq > bestD) continue;
            if (Math.abs(to.y) > 2.6) continue;
            if (horizSq > 0.01) {
                Vec3 h = new Vec3(to.x, 0, to.z).normalize();
                if (h.dot(fwd) < 0.45) continue;
            }
            double d2 = distanceToSqr(e);
            if (d2 < bestD) {
                bestD = d2;
                best = e;
            }
        }
        if (best != null) {
            int[] stat = stats(owner);
            float dmg = Math.round((4.0F + 4.0F * stat[0]) * (1.0F + stat[1] / 100.0F));
            dmg = com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier
                    .getSpellAmplification(owner, dmg);
            best.hurt(damageSources().mobAttack(this), dmg);
            swing(InteractionHand.MAIN_HAND);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_ATTACK,
                    SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /** 右键：击飞（无直接伤害，前方 60° 扇形） */
    private void doKnock(ServerPlayer owner) {
        knockCd = KNOCK_CD;
        Vec3 fwd = PuppetUtil.flatDir(inputYRot);
        int output = CursePowerHelper.getCurseOutputLevel(owner);
        double launch = (1.6 + 0.2 * output) * 0.2; // 击飞速度 V=1.6+输出×0.2 m/s → 脉冲速度
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(KNOCK_RANGE + 1.5),
                e -> e != this && e.isAlive() && !PuppetUtil.isAllyOf(e, owner));
        boolean hit = false;
        for (LivingEntity e : list) {
            Vec3 to = e.position().subtract(position());
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            if (horiz > KNOCK_RANGE) continue;
            if (Math.abs(to.y) > 2.4) continue;
            if (horiz > 0.01) {
                Vec3 h = new Vec3(to.x, 0, to.z).normalize();
                if (h.dot(fwd) < KNOCK_CONE) continue;
            }
            Vec3 away = horiz > 0.01 ? new Vec3(to.x, 0, to.z).normalize() : fwd;
            Vec3 push = away.scale(launch);
            e.setDeltaMovement(push.x, 0.32 + launch * 0.35, push.z);
            e.hurtMarked = true;
            hit = true;
        }
        if (hit) {
            swing(InteractionHand.MAIN_HAND);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_ATTACK,
                    SoundSource.HOSTILE, 1.0F, 1.2F);
        }
    }

    /** Shift 自爆：半径 5，线性衰减，不破坏方块 */
    private void detonate() {
        detonated = true;
        ServerPlayer owner = getOwner();
        ServerLevel level = (ServerLevel) this.level();
        int[] stat = owner != null ? stats(owner) : new int[]{0, 0};
        double center = (30.0 + 10.0 * stat[0]) * (1.0 + stat[1] / 100.0);
        if (owner != null) {
            center = com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier
                    .getSpellAmplification(owner, (float) center);
        }
        double radius = 5.0;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(position(), radius * 2, radius * 2, radius * 2),
                e -> e != this && e.isAlive() && !PuppetUtil.isAllyOf(e, owner));
        for (LivingEntity e : victims) {
            double d = e.distanceToSqr(this);
            if (d <= radius * radius) {
                double falloff = 1.0 - Math.sqrt(d) / radius;
                e.hurt(damageSources().explosion(this, owner), (float) (center * falloff));
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1.0, getZ(), 32, 1.6, 1.6, 1.6, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 2.0F, 0.7F);
        if (owner != null) {
            PuppetTechnique.onPuppetSelfDestruct(owner);
        }
        finish(true);
    }

    private int[] stats(ServerPlayer owner) {
        return new int[]{CursePowerHelper.getCurseOutputLevel(owner),
                CursePowerHelper.getCurseAffinity(owner)};
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
        // 玩家完全操控，无原版铁傀儡 AI
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
