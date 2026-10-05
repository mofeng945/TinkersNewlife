package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.item.WhipItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>鞭击</b>（§1053）—— 一次"左键抽击"或"右键蓄力→砸地"的载体 ✓
 * （纯逻辑 ＋ 视觉 ✓ 不移动、不碰撞、不存档 ✓）。
 *
 * <h2>时间轴完全照参照模组 BetterWhips 的 {@code ArmMotor}（MIT ✓）</h2>
 * <ul>
 *   <li><b>左键 PRECISION</b> ✓：{@code windup = clamp(min(3, period−2),1,3)} ✓、
 *       {@code stroke = clamp(period−windup−1,1,4)} ✓（{@code period = ceil(攻击冷却)} ✓）
 *       ⇒ 进度 0→0.30 落在起手段 ✓、0.30→1.0 落在抽击段 ✓；
 *       <b>驱动一结束（{@code driveTick ≥ windup+stroke}）根部立刻回到手上</b> ✓
 *       ⇒ 此后绳子<b>自由飞</b> ✓（这是"甩出去"的关键 ✓，也是它注释里 82~190 格/秒的来源 ✓）；</li>
 *   <li><b>伤害窗口</b>：{@code ageTicks > windup && ≤ windup + 10} ✓ ⇒ <b>飞行段照样打人</b> ✓；</li>
 *   <li><b>右键蓄力</b> ✓：最长 {@link WhipPhysics#RIGHT_CHARGE_TICKS} tick 绕手自转 ✓
 *       （离心力把绳子甩成一张盘 ✓）⇒ 松手后 {@code 14} tick 钟摆式下抽 ✓，
 *       梢部碰到方块或实体即触发<b>冲击波</b> ✓；</li>
 *   <li><b>伤害口径</b>（照它 ✓）：{@code floor(段速度/10) × 0.2} ✓，
 *       并按本鞭已命中目标数<b>逐次减半</b> ✓（{@code base / 2^prior} ✓）。</li>
 * </ul>
 */
public class WhipLashEntity extends Entity {

    public static final int PHASE_LASH = 0;
    public static final int PHASE_CHARGE = 1;
    public static final int PHASE_RELEASE = 2;

    private static final EntityDataAccessor<String> OWNER_UUID =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SWING_SIGN =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> RELEASE_TICK =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE_TICKS =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);

    /** 左键：伤害窗口 = 起手段之后 10 tick ✓（照它的 {@code LEFT_DAMAGE_WINDOW_TICKS} ✓） */
    private static final int LEFT_DAMAGE_WINDOW_TICKS = 10;
    /** 抽击驱动结束后，绳子还要自由飞这么多 tick ✓ 让波传完 ✓（照它实体活 32 tick 的量级 ✓） */
    private static final int LASH_FREE_FLIGHT_TICKS = 25;
    /** 砸地：松手后的钟摆段 tick 数 ✓（照它的 {@code RIGHT_SLAM_TICKS = 14} ✓） */
    private static final int RIGHT_SLAM_TICKS = 14;
    /** 砸地结束后绳子自由飞多久 ✓ */
    private static final int SLAM_FREE_FLIGHT_TICKS = 18;
    /** 砸地冲击波半径（格 ✓）与击退 ✓ */
    private static final double SHOCKWAVE_RADIUS = 3.5D;
    private static final double SHOCKWAVE_KNOCKBACK = 0.9D;
    /** 一根鞭同时只有一个活动实体（蓄力段要能被松手打断 ✓） */
    private static final Map<UUID, WhipLashEntity> ACTIVE_CHARGES = new HashMap<>();
    /** 每个玩家"上次开始抽击"的 tick ✓ —— 挥击包与命中包可能同 tick 都来 ✓ 只允许甩一次 ✓ */
    private static final Map<UUID, Integer> LAST_LASH_TICK = new HashMap<>();

    private final WhipPhysics physics = new WhipPhysics();
    private final WhipPhysics.Drive drive = new WhipPhysics.Drive();
    /** 本鞭已结算过的目标 ⇒ 伤害按 2^prior 递减 ✓（照它的 {@code WhipMultiHitDamage} ✓） */
    private final Set<UUID> contactedTargets = new HashSet<>();
    private boolean shockwaveTriggered;
    private int windupTicks = 3;
    private int strokeTicks = 4;

    public WhipLashEntity(EntityType<? extends WhipLashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    // ==================== 生成入口（服务端调用 ✓） ====================

    /** 左键：一次抽击 ✓（同一玩家同一时刻只甩一次 ✓ 见 {@link #LAST_LASH_TICK}） */
    public static void startLash(Player player) {
        int now = player.tickCount;
        Integer last = LAST_LASH_TICK.get(player.getUUID());
        if (last != null && now - last < 2) {
            return;
        }
        LAST_LASH_TICK.put(player.getUUID(), now);
        WhipLashEntity lash = new WhipLashEntity(player.level(), player, PHASE_LASH);
        player.level().addFreshEntity(lash);
    }

    /** 右键按下：开始蓄力自转 ✓（同一玩家只保留一个 ✓） */
    public static void startCharge(Player player) {
        WhipLashEntity old = ACTIVE_CHARGES.remove(player.getUUID());
        if (old != null && old.isAlive()) {
            old.discard();
        }
        WhipLashEntity lash = new WhipLashEntity(player.level(), player, PHASE_CHARGE);
        player.level().addFreshEntity(lash);
        ACTIVE_CHARGES.put(player.getUUID(), lash);
    }

    /** 右键松手：切换到"砸地"释放段 ✓（没在蓄力就什么都不做 ✓） */
    public static void releaseCharge(Player player) {
        WhipLashEntity lash = ACTIVE_CHARGES.remove(player.getUUID());
        if (lash == null || !lash.isAlive()) {
            return;
        }
        lash.setPhase(PHASE_RELEASE);
        lash.setReleaseTick(0);
    }

    public static void cancelCharge(Player player) {
        WhipLashEntity lash = ACTIVE_CHARGES.remove(player.getUUID());
        if (lash != null && lash.isAlive()) {
            lash.discard();
        }
    }

    private WhipLashEntity(Level level, Player owner, int phase) {
        this(ModEntities.WHIP_LASH.get(), level);
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        this.setOwnerUuid(owner.getUUID().toString());
        this.setPhase(phase);
        this.setSwingSignPositive(owner.getRandom().nextBoolean());
        this.setReleaseTick(-1);
        this.setChargeTicks(0);
        Vec3 aim = owner.getViewVector(1.0F);
        Vec3 forward = new Vec3(aim.x, 0.0D, aim.z);
        forward = forward.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : forward.normalize();
        this.physics.reset(WhipPhysics.handBase(owner.position(), owner.getEyeHeight(),
                side(owner), forward), aim);
    }

    // ==================== 同步字段 ====================

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(OWNER_UUID, "");
        this.getEntityData().define(PHASE, PHASE_LASH);
        this.getEntityData().define(SWING_SIGN, true);
        this.getEntityData().define(RELEASE_TICK, -1);
        this.getEntityData().define(CHARGE_TICKS, 0);
    }

    public String getOwnerUuid() { return this.getEntityData().get(OWNER_UUID); }
    public void setOwnerUuid(String v) { this.getEntityData().set(OWNER_UUID, v); }
    public int getPhase() { return this.getEntityData().get(PHASE); }
    public void setPhase(int v) { this.getEntityData().set(PHASE, v); }
    public boolean isSwingSignPositive() { return this.getEntityData().get(SWING_SIGN); }
    public void setSwingSignPositive(boolean v) { this.getEntityData().set(SWING_SIGN, v); }
    public int getReleaseTick() { return this.getEntityData().get(RELEASE_TICK); }
    public void setReleaseTick(int v) { this.getEntityData().set(RELEASE_TICK, v); }
    public int getChargeTicks() { return this.getEntityData().get(CHARGE_TICKS); }
    public void setChargeTicks(int v) { this.getEntityData().set(CHARGE_TICKS, v); }

    public WhipPhysics physics() {
        return physics;
    }

    private static double side(LivingEntity owner) {
        return owner.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -1.0D : 1.0D;
    }

    /** 它的 {@code fallbackAnchor}：眼位 ＋ 前方×0.22 ＋ 右手侧×side×0.34 ＋ 下 0.52 ✓ */
    private static Vec3 fallbackAnchor(Player owner, Vec3 forward, double side) {
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
        horizontal = horizontal.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        return owner.getEyePosition()
                .add(horizontal.scale(0.22D))
                .add(right.scale(side * 0.34D))
                .add(0.0D, -0.52D, 0.0D);
    }

    // ==================== 主循环 ====================

    @Override
    public void tick() {
        super.tick();

        Player owner = resolveOwner();
        if (owner == null || !owner.isAlive()) {
            if (!this.level().isClientSide) {
                forgetCharge();
                this.discard();
            }
            return;
        }

        Vec3 aim = owner.getViewVector(1.0F);
        Vec3 forward = new Vec3(aim.x, 0.0D, aim.z);
        forward = forward.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double side = side(owner);
        Vec3 base = WhipPhysics.handBase(owner.position(), owner.getEyeHeight(), side, forward);

        int phase = getPhase();
        boolean server = !this.level().isClientSide;

        if (phase == PHASE_CHARGE) {
            int charge = Math.min(getChargeTicks() + 1, WhipPhysics.RIGHT_CHARGE_TICKS);
            if (server) {
                setChargeTicks(charge);
            }
            Vec3 restAnchor = fallbackAnchor(owner, forward, side);
            Vec3 center = WhipPhysics.chargedSpinCenter(base, forward);
            Vec3 anchor = WhipPhysics.chargedSpinHandAnchor(restAnchor, center, forward, right, side, charge);
            drive.mode = WhipPhysics.MODE_CHARGE;
            drive.center = center;
            drive.forward = forward;
            drive.right = right;
            drive.side = side;
            drive.chargeTicks = charge;
            drive.rootFrom = anchor;
            drive.rootTo = anchor;
            drive.progressFrom = 0.0D;
            drive.progressTo = 0.0D;
        } else if (phase == PHASE_RELEASE) {
            int release = Math.max(0, getReleaseTick());
            if (server) {
                setReleaseTick(release + 1);
            }
            double from = Mth.clamp(release / (double) RIGHT_SLAM_TICKS, 0.0D, 1.0D);
            double to = Mth.clamp((release + 1.0D) / RIGHT_SLAM_TICKS, 0.0D, 1.0D);
            drive.mode = WhipPhysics.MODE_RELEASE;
            drive.center = WhipPhysics.chargedSpinCenter(base, forward);
            drive.forward = forward;
            drive.right = right;
            drive.side = side;
            drive.releaseProgress = to;
            drive.rootFrom = WhipPhysics.chargedReleaseHandAnchor(base, forward, from);
            drive.rootTo = WhipPhysics.chargedReleaseHandAnchor(base, forward, to);
            drive.progressFrom = 0.0D;
            drive.progressTo = 0.0D;
        } else {
            // §1055 攻速决定"挥动速度"的方式 = 参照的原公式 ✓（⚠ §1054 我改错了方向 ✗）：
            //   攻速<b>快</b> ⇒ 周期短 ⇒ 起手/抽击更短 ⇒ 抽得又快又脆 ✓；
            //   攻速<b>慢</b> ⇒ 周期长，但<b>抽击段被硬封在 4 tick</b> ✓ ⇒ 手依旧很快、鞭子照样甩得远 ✓
            //   ⇒ 慢攻速只体现在<b>冷却更长</b>（每秒能抽的次数更少 ✓）。
            // ⚠ 关键教训：驱动 tick 数一旦被拉长 ✗，手在同一段弧上就更慢 ✗ ⇒ 绳子甩不出去 ✗
            //   （用户原话：「这样改了之后鞭子挥不远了」✓ 就是这个原因 ✓）。
            int period = WhipItem.attackPeriodTicks(owner);
            windupTicks = Mth.clamp(Math.min(3, Math.max(1, period - 2)), 1, 3);
            strokeTicks = Mth.clamp(Math.max(1, period - windupTicks - 1), 1, 4);
            int total = windupTicks + strokeTicks;
            int driveTick = this.tickCount - 1;
            boolean driving = driveTick >= 0 && driveTick < total;
            double progressFrom;
            double progressTo;
            if (driveTick < 0) {
                progressFrom = 0.0D;
                progressTo = 0.0D;
            } else if (driveTick < windupTicks) {
                progressFrom = WhipPhysics.PRECISION_RELEASE_RAW
                        * Mth.clamp(driveTick / (double) windupTicks, 0.0D, 1.0D);
                progressTo = WhipPhysics.PRECISION_RELEASE_RAW
                        * Mth.clamp((driveTick + 1.0D) / windupTicks, 0.0D, 1.0D);
            } else {
                double lashTick = driveTick - windupTicks;
                progressFrom = Mth.lerp(Mth.clamp(lashTick / (double) strokeTicks, 0.0D, 1.0D),
                        WhipPhysics.PRECISION_RELEASE_RAW, 1.0D);
                progressTo = Mth.lerp(Mth.clamp((lashTick + 1.0D) / strokeTicks, 0.0D, 1.0D),
                        WhipPhysics.PRECISION_RELEASE_RAW, 1.0D);
            }
            drive.mode = WhipPhysics.MODE_LASH;
            drive.eye = owner.getEyePosition();
            drive.aim = aim.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : aim.normalize();
            drive.forward = forward;
            drive.right = right;
            drive.side = side;
            drive.swingSign = isSwingSignPositive() ? 1.0D : -1.0D;
            if (driving) {
                drive.rootFrom = driveTick <= 0 ? base
                        : WhipPhysics.precisionHandAnchor(base, drive.aim, right, progressFrom, drive.swingSign);
                drive.rootTo = WhipPhysics.precisionHandAnchor(base, drive.aim, right, progressTo, drive.swingSign);
                drive.progressFrom = progressFrom;
                drive.progressTo = progressTo;
            } else {
                // 驱动结束 ⇒ 根部回到手上 ✓ 绳子自由飞 ✓（导引同时关闭：进度归 0 ✓）
                drive.rootFrom = base;
                drive.rootTo = base;
                drive.progressFrom = 0.0D;
                drive.progressTo = 0.0D;
            }
        }

        physics.markTickStart();
        physics.step(this.level(), drive);

        if (server) {
            if (phase == PHASE_RELEASE) {
                tickShockwave(owner);
            } else if (phase == PHASE_LASH) {
                tickLashDamage(owner);
            }
            tickLifetime(phase);
        } else {
            tickClientFx();
        }
    }

    /** 左键：逐段扫掠结算 ✓（照它的 damageForSpeed ＋ 减半 ✓） */
    private void tickLashDamage(Player owner) {
        int age = this.tickCount;
        if (age <= windupTicks || age > windupTicks + LEFT_DAMAGE_WINDOW_TICKS) {
            return;
        }
        AABB search = owner.getBoundingBox().inflate(WhipPhysics.totalLength() + 2.0D);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, search,
                e -> isValidTarget(owner, e));
        if (targets.isEmpty()) {
            return;
        }
        // §1054 用户口径：伤害由【攻击力属性】决定 ✓（基准面板 3.5 ⇒ 与原参照口径一致 ✓）
        float panel = WhipItem.attackPanel(owner.getMainHandItem());
        for (int seg = 0; seg < WhipPhysics.SEGMENTS; seg++) {
            double speed = physics.segmentSpeed(seg);
            float base = WhipItem.damageForSpeed(speed, panel);
            if (base <= 0.0F) {
                continue;
            }
            for (LivingEntity target : targets) {
                if (contactedTargets.contains(target.getUUID())) {
                    continue;
                }
                Vec3 contact = physics.segmentContact(seg, target.getBoundingBox());
                if (contact == null) {
                    continue;
                }
                float damage = (float) (base / Math.pow(2.0D, contactedTargets.size()));
                contactedTargets.add(target.getUUID());
                if (hurt(owner, target, contact, damage)) {
                    this.level().playSound(null, contact.x, contact.y, contact.z,
                            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.85F,
                            0.96F + this.random.nextFloat() * 0.08F);
                }
            }
        }
    }

    /** 砸地：梢部触到方块或实体 ⇒ 冲击波 ✓（照它 tipBlockContact / tipLivingHit ⇒ shockwave ✓） */
    private void tickShockwave(Player owner) {
        if (shockwaveTriggered || getReleaseTick() > RIGHT_SLAM_TICKS) {
            return;
        }
        Vec3 tip = physics.point(WhipPhysics.POINTS - 1);
        net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(tip);
        boolean hitBlock = !this.level().getBlockState(bp).getCollisionShape(this.level(), bp).isEmpty();
        double tipSpeed = physics.speed(WhipPhysics.POINTS - 1);
        Vec3 impact = null;
        if (hitBlock) {
            impact = tip;
        } else {
            List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(tip, tip).inflate(0.6D), e -> isValidTarget(owner, e));
            if (!nearby.isEmpty()) {
                impact = nearby.get(0).position();
            }
        }
        if (impact == null) {
            return;
        }
        shockwaveTriggered = true;

        float base = WhipItem.damageForSpeed(tipSpeed, WhipItem.attackPanel(owner.getMainHandItem()));
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(SHOCKWAVE_RADIUS), e -> isValidTarget(owner, e));
        for (LivingEntity target : targets) {
            Vec3 push = target.position().subtract(impact);
            Vec3 dir = push.lengthSqr() < 1.0E-6D ? owner.getViewVector(1.0F) : push.normalize();
            hurt(owner, target, target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), base);
            target.push(dir.x * SHOCKWAVE_KNOCKBACK, 0.55D * SHOCKWAVE_KNOCKBACK, dir.z * SHOCKWAVE_KNOCKBACK);
            target.hurtMarked = true;
        }

        if (this.level() instanceof ServerLevel server) {
            for (int i = 0; i < 48; i++) {
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                double r = this.random.nextDouble() * SHOCKWAVE_RADIUS;
                server.sendParticles(ParticleTypes.CLOUD,
                        impact.x + Math.cos(a) * r, impact.y + 0.1D, impact.z + Math.sin(a) * r,
                        1, 0.0D, 0.06D, 0.0D, 0.02D);
            }
            server.playSound(null, impact.x, impact.y, impact.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }

    private boolean hurt(Player owner, LivingEntity target, Vec3 at, float amount) {
        if (amount <= 0.0F) {
            return false;
        }
        boolean damaged = target.hurt(this.damageSources().playerAttack(owner), amount);
        if (!damaged) {
            return false;
        }
        target.invulnerableTime = 0;
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.14D, 0.14D, 0.14D, 0.1D);
        }
        return true;
    }

    /** 客户端：梢部拖粒子 ✓ */
    private void tickClientFx() {
        if (this.tickCount % 2 != 0) {
            return;
        }
        Vec3 tip = physics.point(WhipPhysics.POINTS - 1);
        this.level().addParticle(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 0.0D, 0.0D, 0.0D);
    }

    /** 生命周期 ✓（左键：起手＋抽击＋自由飞 ✓；砸地：钟摆＋自由飞 ✓；蓄力：松手或超时 ✓） */
    private void tickLifetime(int phase) {
        if (phase == PHASE_CHARGE) {
            if (getChargeTicks() >= WhipPhysics.RIGHT_CHARGE_TICKS + 40) {
                forgetCharge();
                this.discard();
            }
            return;
        }
        if (phase == PHASE_RELEASE) {
            if (getReleaseTick() > RIGHT_SLAM_TICKS + SLAM_FREE_FLIGHT_TICKS) {
                this.discard();
            }
            return;
        }
        if (this.tickCount > windupTicks + strokeTicks + LASH_FREE_FLIGHT_TICKS) {
            this.discard();
        }
    }

    private void forgetCharge() {
        String uuid = getOwnerUuid();
        if (!uuid.isEmpty()) {
            try {
                ACTIVE_CHARGES.remove(UUID.fromString(uuid));
            } catch (Throwable ignored) {
                // uuid 不合法就算了 ✓
            }
        }
    }

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
            return this.level().getPlayerByUUID(uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ==================== 杂项 ====================

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(WhipPhysics.totalLength() + 2.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 256.0D * 256.0D;
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
