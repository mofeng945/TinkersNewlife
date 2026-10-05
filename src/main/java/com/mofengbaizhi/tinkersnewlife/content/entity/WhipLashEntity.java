package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <b>鞭击</b>（§1047）—— 一次挥鞭／一次砸地的载体 ✓（纯逻辑 + 视觉 ✓ 不移动、不碰撞、不存档 ✓）。
 *
 * <h2>为什么做成实体</h2>
 * 鞭身是一条 25 点的绳 ✓，要画给<b>所有人</b>看 ✓、还要在<b>服务端</b>逐段扫掠判定 ✓ ——
 * 做成实体最省事 ✓（同 {@code SoldierSlashEntity} 的思路 ✓）：
 * <ul>
 *   <li><b>服务端</b>：跑 {@link WhipPhysics} ✓ ⇒ 逐段扫掠 ✓ ⇒ 按"接触点速度"结算伤害 ✓
 *       （多目标衰减 100% → 50% → 25% → 再减半 ✓ 同 BetterWhips 的 tooltip 口径 ✓）；</li>
 *   <li><b>客户端</b>：跑<b>同一套</b>物理（输入相同 ✓）⇒ 只负责把绳画出来 ✓
 *       ⇒ <b>不需要每 tick 同步 25 个点</b> ✓（只同步主人 uuid／相位／驱动／寿命这几个标量 ✓）。</li>
 * </ul>
 *
 * <h2>两种相位</h2>
 * <ul>
 *   <li>{@link #PHASE_LASH}：左键挥击 ✓ —— 根部被弹簧朝准星方向甩出去 ✓ ⇒ 梢部自然加速 ✓；</li>
 *   <li>{@link #PHASE_SLAM}：右键蓄力 2 秒后松手 ✓ —— 驱动更狠 ✓，并在 {@link #SLAM_IMPACT_TICK}
 *       tick 时对周围敌人来一次<b>冲击波</b>（AoE 伤害 ＋ 强击退 ＋ 粒子）✓。</li>
 * </ul>
 */
public class WhipLashEntity extends Entity {

    public static final int PHASE_LASH = 0;
    public static final int PHASE_SLAM = 1;

    private static final EntityDataAccessor<String> OWNER_UUID =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE_TICKS =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PANEL_DAMAGE =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.FLOAT);
    /** §1048 这一鞭从左往右扫还是从右往左扫 ✓（连续两次随机交替 ⇒ 像他们的左右交替 ✓） */
    private static final EntityDataAccessor<Boolean> SWING_SIGN =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.BOOLEAN);

    /** §1047 伤害口径：每满 <b>10 格/秒</b> 的接触速度 = 面板的该倍数 ✓（同 BetterWhips 的 tooltip ✓） */
    private static final double SPEED_UNIT = 10.0D;
    /** 一次接触的伤害上限（面板倍数 ✓）⇒ 再快也不会一刀秒 ✓ */
    private static final double MAX_DAMAGE_MULTIPLIER = 3.0D;
    /** 触发伤害的最低接触速度（格/秒 ✓）⇒ 轻轻蹭到不算 ✓ */
    private static final double MIN_HIT_SPEED = 6.0D;
    /** 击退强度 ✓（BetterWhips 皮革鞭偏轻 ✓ 链条 0.1 / 重型 0.6 ✓ 我们取中间 ✓） */
    private static final double KNOCKBACK = 0.35D;
    /** 砸地冲击波：半径 ✓ / 伤害倍数 ✓ / 击退 ✓ */
    private static final double SLAM_RADIUS = 3.5D;
    private static final double SLAM_DAMAGE_MULTIPLIER = 1.5D;
    private static final double SLAM_KNOCKBACK = 0.9D;
    /** 砸地的"落地"tick ✓（蓄力松手后第几 tick 打出冲击波 ✓） */
    private static final int SLAM_IMPACT_TICK = 6;

    private final WhipPhysics physics = new WhipPhysics();
    /** 这一鞭已经打过的目标 ⇒ 每个目标每鞭只结算一次 ✓ 并按命中顺序衰减 ✓ */
    private final Map<UUID, Integer> hitOrder = new HashMap<>();
    private int nextFalloffIndex;
    private boolean slamDone;

    public WhipLashEntity(EntityType<? extends WhipLashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;          // 纯逻辑/视觉 ✓ 不参与碰撞 ✓
        this.noCulling = true;
    }

    /** 生成一次鞭击 ✓（服务端调用 ✓） */
    public WhipLashEntity(Level level, Player owner, int phase, int lifeTicks, float panelDamage) {
        this(ModEntities.WHIP_LASH.get(), level);
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        this.setOwnerUuid(owner.getUUID().toString());
        this.setPhase(phase);
        this.setLifeTicks(lifeTicks);
        this.setPanelDamage(panelDamage);
        this.setSwingSignPositive(owner.getRandom().nextBoolean());
        this.physics.reset(handPos(owner, owner.getViewVector(1.0F)), owner.getViewVector(1.0F));
    }

    // ==================== 同步字段 ====================
    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(OWNER_UUID, "");
        this.getEntityData().define(PHASE, PHASE_LASH);
        this.getEntityData().define(LIFE_TICKS, 12);
        this.getEntityData().define(PANEL_DAMAGE, 3.0F);
        this.getEntityData().define(SWING_SIGN, true);
    }

    public void setOwnerUuid(String v) { this.getEntityData().set(OWNER_UUID, v); }
    public String getOwnerUuid() { return this.getEntityData().get(OWNER_UUID); }
    public void setPhase(int v) { this.getEntityData().set(PHASE, v); }
    public int getPhase() { return this.getEntityData().get(PHASE); }
    public void setLifeTicks(int v) { this.getEntityData().set(LIFE_TICKS, v); }
    public int getLifeTicks() { return this.getEntityData().get(LIFE_TICKS); }
    public void setPanelDamage(float v) { this.getEntityData().set(PANEL_DAMAGE, v); }
    public float getPanelDamage() { return this.getEntityData().get(PANEL_DAMAGE); }
    public void setSwingSignPositive(boolean v) { this.getEntityData().set(SWING_SIGN, v); }
    public boolean isSwingSignPositive() { return this.getEntityData().get(SWING_SIGN); }

    /** 客户端渲染要用它 ✓；服务端也要 ✓ */
    public WhipPhysics physics() {
        return physics;
    }

    /**
     * §1048 手部基点 —— 口径照 BetterWhips（MIT）的 {@code handBase} ✓：
     * 「脚下位置 ＋ (0, 眼睛高度 − 0.58, 0) ＋ 右手侧 × 0.34 ＋ 前方 × 0.10」✓。
     */
    private static Vec3 handPos(LivingEntity owner, Vec3 horizontal) {
        Vec3 h = horizontal;
        if (h.lengthSqr() < 1.0E-8D) {
            Vec3 view = owner.getViewVector(1.0F);
            h = new Vec3(view.x, 0.0D, view.z);
        }
        if (h.lengthSqr() < 1.0E-8D) {
            h = new Vec3(0.0D, 0.0D, 1.0D);
        }
        h = h.normalize();
        double side = owner.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -1.0D : 1.0D;
        return owner.position()
                .add(0.0D, owner.getEyeHeight() - 0.58D, 0.0D)
                .add(rightOf(h).scale(side * 0.34D))
                .add(h.scale(0.10D));
    }

    /** §1048 水平"右手侧"单位向量 ✓ —— 横扫所在的平面由它与准星张成 ✓ */
    private static Vec3 rightOf(Vec3 horizontal) {
        return new Vec3(-horizontal.z, 0.0D, horizontal.x);
    }

    @Override
    public void tick() {
        super.tick();

        Player owner = resolveOwner();
        if (owner == null || !owner.isAlive()) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return;
        }

        Vec3 aim = owner.getViewVector(1.0F);
        boolean slam = getPhase() == PHASE_SLAM;
        // §1048 横扫：进度 = 已过 tick / 一次抽击 10 tick ✓（照 BetterWhips 的 ATTACK_SECONDS=0.5s ✓）
        double progress = slam ? this.tickCount / 14.0D : this.tickCount / WhipPhysics.ATTACK_TICKS;

        Vec3 horizontal = new Vec3(aim.x, 0.0D, aim.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            horizontal = new Vec3(0.0D, 0.0D, 1.0D);
        }
        horizontal = horizontal.normalize();
        Vec3 hand = handPos(owner, horizontal);

        physics.markTickStart();
        physics.tick(this.level(), hand, aim, rightOf(horizontal), progress,
                isSwingSignPositive() ? 1.0D : -1.0D, slam);

        if (!this.level().isClientSide) {
            if (slam) {
                tickSlam(owner);
            } else {
                tickLash(owner);
            }
        } else {
            tickClientFx(owner);
        }

        if (this.tickCount > getLifeTicks() + 4) {
            this.discard();
        }
    }

    /** 客户端：梢部拖一点粒子 ✓（成本很低、观感提升明显 ✓） */
    private void tickClientFx(Player owner) {
        if (this.tickCount % 2 != 0) {
            return;
        }
        Vec3 tip = physics.point(WhipPhysics.POINTS - 1);
        this.level().addParticle(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 0.0D, 0.0D, 0.0D);
    }

    /** 服务端：逐段扫掠 → 按接触点速度结算 ✓ */
    private void tickLash(Player owner) {
        AABB search = owner.getBoundingBox().inflate(11.0D);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, search,
                e -> isValidTarget(owner, e));
        if (targets.isEmpty()) {
            return;
        }
        for (int seg = 0; seg < WhipPhysics.POINTS - 1; seg++) {
            double speed = physics.speed(seg);
            if (speed < MIN_HIT_SPEED) {
                continue;
            }
            for (LivingEntity target : targets) {
                if (hitOrder.containsKey(target.getUUID())) {
                    continue;                                  // 每鞭每目标一次 ✓
                }
                if (!physics.segmentHits(seg, target.getBoundingBox())) {
                    continue;
                }
                damage(owner, target, speed, 1.0D, KNOCKBACK);
            }
        }
    }

    /** 服务端：砸地 ⇒ 一次冲击波 ✓（只打一次 ✓） */
    private void tickSlam(Player owner) {
        if (slamDone || this.tickCount < SLAM_IMPACT_TICK) {
            return;
        }
        slamDone = true;

        Vec3 center = owner.position();
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(SLAM_RADIUS),
                e -> isValidTarget(owner, e));
        for (LivingEntity target : targets) {
            damage(owner, target, 0.0D, SLAM_DAMAGE_MULTIPLIER, SLAM_KNOCKBACK);
            Vec3 push = target.position().subtract(center);
            Vec3 dir = push.lengthSqr() < 1.0E-6D ? owner.getViewVector(1.0F) : push.normalize();
            target.push(dir.x * SLAM_KNOCKBACK, 0.55D * SLAM_KNOCKBACK, dir.z * SLAM_KNOCKBACK);
            target.hurtMarked = true;
        }

        if (this.level() instanceof ServerLevel server) {
            for (int i = 0; i < 40; i++) {
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                double r = this.random.nextDouble() * SLAM_RADIUS;
                server.sendParticles(ParticleTypes.CLOUD,
                        center.x + Math.cos(a) * r, center.y + 0.1D, center.z + Math.sin(a) * r,
                        1, 0.0D, 0.05D, 0.0D, 0.02D);
            }
            server.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }

    /** 结算一次命中 ✓：伤害 = 面板 × (速度/10 或给定倍数) ✓，并按命中顺序衰减 100/50/25/… ✓ */
    private void damage(Player owner, LivingEntity target, double speed, double multiplier, double knockback) {
        hitOrder.put(target.getUUID(), nextFalloffIndex);
        double falloff = Math.pow(0.5D, nextFalloffIndex);
        nextFalloffIndex++;

        double speedMultiplier = speed <= 0.0D ? multiplier : Math.min(MAX_DAMAGE_MULTIPLIER, speed / SPEED_UNIT);
        float amount = (float) Math.max(0.5D, getPanelDamage() * speedMultiplier * multiplier * falloff);

        boolean hurt = target.hurt(this.damageSources().playerAttack(owner), amount);
        if (!hurt) {
            return;
        }
        target.invulnerableTime = 0;                        // 一鞭多段不被无敌帧吞掉 ✓

        Vec3 push = target.position().subtract(owner.position());
        Vec3 dir = push.lengthSqr() < 1.0E-6D ? owner.getViewVector(1.0F) : push.normalize();
        target.push(dir.x * knockback, 0.12D * knockback, dir.z * knockback);
        target.hurtMarked = true;

        if (this.level() instanceof ServerLevel server) {
            Vec3 at = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.15D, 0.15D, 0.15D, 0.1D);
            server.playSound(null, at.x, at.y, at.z,
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F,
                    1.1F + this.random.nextFloat() * 0.2F);
        }
    }

    /** 能不能打：不是主人 ✓ 不是同伴（同队玩家／主人的宠物 ✓）✓ 活着 ✓ */
    private boolean isValidTarget(Player owner, LivingEntity candidate) {
        if (candidate == owner || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (candidate instanceof Player other) {
            return !other.isAlliedTo(owner);
        }
        if (candidate instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()) {
            return tame.getOwnerUUID() == null || !tame.getOwnerUUID().equals(owner.getUUID());
        }
        return true;
    }

    private Player resolveOwner() {
        try {
            UUID uuid = UUID.fromString(getOwnerUuid());
            Player p = this.level().getPlayerByUUID(uuid);
            return p;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ==================== 杂项 ====================
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(10.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 192.0D * 192.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
