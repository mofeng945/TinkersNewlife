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
    /** §1058 收回段 ✓：右键"收回没有收回的鞭身" ✓ —— 绳子被拉回手心后散场 ✓ */
    public static final int PHASE_RETRACT = 3;

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
    /** §1058 收回段已经走了多少 tick ✓ */
    private static final EntityDataAccessor<Integer> RETRACT_TICK =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);

    /** 左键：伤害窗口 = 起手段之后 10 tick ✓（照它的 {@code LEFT_DAMAGE_WINDOW_TICKS} ✓） */
    private static final int LEFT_DAMAGE_WINDOW_TICKS = 14;
    /** 抽击驱动结束后，绳子还要自由飞这么多 tick ✓ 让波传完 ✓（照它实体活 32 tick 的量级 ✓） */
    private static final int LASH_FREE_FLIGHT_TICKS = 32;
    /** 砸地：松手后的钟摆段 tick 数 ✓（照它的 {@code RIGHT_SLAM_TICKS = 14} ✓） */
    private static final int RIGHT_SLAM_TICKS = 14;
    /** 砸地结束后绳子自由飞多久 ✓ */
    private static final int SLAM_FREE_FLIGHT_TICKS = 18;
    /** §1058 收回段持续多少 tick ✓（鞭身回到手里就散场 ✓） */
    private static final int RETRACT_TICKS = 8;
    /** §1058 每个玩家"当前那一条鞭" ✓ —— 右键要能找到它才能把鞭身收回来 ✓ */
    private static final Map<UUID, WhipLashEntity> ACTIVE_LASHES = new HashMap<>();
    /** 砸地冲击波半径（格 ✓）与击退 ✓ */
    private static final double SHOCKWAVE_RADIUS = 3.5D;
    private static final double SHOCKWAVE_KNOCKBACK = 0.9D;
    /** 一根鞭同时只有一个活动实体（蓄力段要能被松手打断 ✓） */
    private static final Map<UUID, WhipLashEntity> ACTIVE_CHARGES = new HashMap<>();
    /**
     * §1057 每个玩家"<b>下一次允许抽击</b>"的 tick ✓ ——
     * 间隔 ＝ {@link WhipItem#attackPeriodTicks} ✓ ⇒ <b>攻速属性只决定每秒能抽几次</b> ✓
     * （用户口径：「<b>攻速只影响冷却</b>」✓ —— 不影响力度/射程 ✓，驱动长度恒为 3＋4 ✓）；
     * 同时兼作"挥击包与命中包同 tick 都来、只甩一次"的闸门 ✓。
     */
    private static final Map<UUID, Integer> NEXT_LASH_TICK = new HashMap<>();

    private final WhipPhysics physics = new WhipPhysics();
    private final WhipPhysics.Drive drive = new WhipPhysics.Drive();
    /** 本鞭已结算过的目标 ⇒ 伤害按 2^prior 递减 ✓（照它的 {@code WhipMultiHitDamage} ✓） */
    private final Set<UUID> contactedTargets = new HashSet<>();
    private boolean shockwaveTriggered;
    /**
     * §1057 驱动长度<b>固定</b> ✓（照参照的 {@code ArmMotor}：起手 3 ＋ 抽击 4 ＝ 7 tick ✓）
     * —— <b>与攻速无关</b> ✓。
     * <p>用户口径（2026-10-05）：「<b>让攻速只影响冷却速度吧，别影响力度了</b>」✓
     * ⇒ 手永远以同样的速度划完同样的弧 ⇒ 射程与力度恒定 ✓；
     * 攻速只通过 {@link WhipItem#attackPeriodTicks}（冷却）决定"每秒能抽几次" ✓。
     */
    private static final int WINDUP_TICKS = 3;
    private static final int STROKE_TICKS = 4;
    private int windupTicks = WINDUP_TICKS;
    private int strokeTicks = STROKE_TICKS;
    /**
     * §1056 <b>本实体"真正开始模拟"的年龄</b> ✓ —— 只在 owner 解析成功后才 +1 ✓。
     * <p>⚠ 为什么不能用 {@code tickCount} ✗：客户端实体要靠**同步过来的 owner uuid** 才能模拟 ✓，
     * uuid 还没到的前 1~2 tick 我原来直接 {@code return} ✗（不模拟，但 {@code tickCount} 照样在涨 ✗）
     * ⇒ <b>短驱动会被整段吃掉</b> ✗：攻速快的鞭子驱动只有 2 tick ✗ ⇒ 客户端一帧都没画 ⇒
     * 用户实测「<b>攻速快的甩不出去，攻速慢的还能甩 5 格</b>」✓（慢的 7 tick、吃掉 1~2 tick 还剩 5 ✓）。
     */
    private int age;

    public WhipLashEntity(EntityType<? extends WhipLashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    // ==================== 生成入口（服务端调用 ✓） ====================

    /** 冷却是否已好 ✓ —— 攻速越快 ⇒ 间隔越短 ⇒ 抽得越频繁 ✓（与力度/射程无关 ✓） */
    public static boolean isLashReady(Player player) {
        Integer next = NEXT_LASH_TICK.get(player.getUUID());
        return next == null || player.tickCount >= next;
    }

    /** 左键：一次抽击 ✓（<b>冷却</b>按攻速算 ✓；抽击动作本身恒为 3＋4 tick ✓） */
    public static void startLash(Player player) {
        if (!isLashReady(player)) {
            return;
        }
        NEXT_LASH_TICK.put(player.getUUID(), player.tickCount + WhipItem.attackPeriodTicks(player));
        // 照参照 beginPrecisionNow ✓：重置攻击冷却 ⇒ 准星上的攻击指示器与鞭子冷却同步 ✓
        player.resetAttackStrengthTicker();
        spawn(player, PHASE_LASH);
    }

    /** §1058 <b>不受抽击冷却限制</b>地挥一鞭 ✓ —— 完美格挡/反射时用 ✓（视觉与判定都要立刻出现 ✓） */
    public static void startLashNow(Player player) {
        spawn(player, PHASE_LASH);
    }

    private static void spawn(Player player, int phase) {
        WhipLashEntity lash = new WhipLashEntity(player.level(), player, phase);
        player.level().addFreshEntity(lash);
        if (phase == PHASE_LASH) {
            ACTIVE_LASHES.put(player.getUUID(), lash);
        }
    }

    /**
     * §1058 用户口径：右键「<b>收回没有收回的鞭身</b>」✓ ——
     * 把玩家当前那条还在飞／还在抽的鞭切成<b>收回段</b> ✓，绳身会被拉回手心 ✓（{@code MODE_RETRACT} ✓）。
     */
    public static void retract(Player player) {
        WhipLashEntity lash = ACTIVE_LASHES.get(player.getUUID());
        if (lash == null || !lash.isAlive()) {
            ACTIVE_LASHES.remove(player.getUUID());
            return;
        }
        lash.setPhase(PHASE_RETRACT);
        lash.setRetractTick(0);
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
        this.getEntityData().define(RETRACT_TICK, -1);
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
    public int getRetractTick() { return this.getEntityData().get(RETRACT_TICK); }
    public void setRetractTick(int v) { this.getEntityData().set(RETRACT_TICK, v); }

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
            // ⚠ 客户端这里**不能**推进 age ✓ ⇒ 时间轴会等 owner 就绪之后才开始 ✓
            // 否则攻速快的短驱动会在客户端被整段吃掉 ✗（§1056 的 bug ✓）
            return;
        }
        this.age++;

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
        } else if (phase == PHASE_RETRACT) {
            // §1058 收回：根部回到手上 ✓ 且每个点被拉向手心 ✓（WhipPhysics.MODE_RETRACT ✓）
            int retract = Math.max(0, getRetractTick());
            if (server) {
                setRetractTick(retract + 1);
            }
            drive.mode = WhipPhysics.MODE_RETRACT;
            drive.aim = aim.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : aim.normalize();
            drive.forward = forward;
            drive.right = right;
            drive.rootFrom = base;
            drive.rootTo = base;
            drive.progressFrom = 0.0D;
            drive.progressTo = 0.0D;
        } else {
            // §1055 攻速决定"挥动速度"的方式 = 参照的原公式 ✓（⚠ §1054 我改错了方向 ✗）：
            //   攻速<b>快</b> ⇒ 周期短 ⇒ 起手/抽击更短 ⇒ 抽得又快又脆 ✓；
            //   攻速<b>慢</b> ⇒ 周期长，但<b>抽击段被硬封在 4 tick</b> ✓ ⇒ 手依旧很快、鞭子照样甩得远 ✓
            //   ⇒ 慢攻速只体现在<b>冷却更长</b>（每秒能抽的次数更少 ✓）。
            // ⚠ 关键教训：驱动 tick 数一旦被拉长 ✗，手在同一段弧上就更慢 ✗ ⇒ 绳子甩不出去 ✗
            //   （用户原话：「这样改了之后鞭子挥不远了」✓ 就是这个原因 ✓）。
            // §1057 攻速**只**影响冷却 ✓ —— 驱动长度恒定 ✓
            //（手始终以同样速度划完同样的 1.1 格弧 ✓ ⇒ 射程/力度不再随攻速变化 ✓）
            windupTicks = WINDUP_TICKS;
            strokeTicks = STROKE_TICKS;
            int total = windupTicks + strokeTicks;
            int driveTick = this.age - 1;
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
        if (this.age <= windupTicks || this.age > windupTicks + LEFT_DAMAGE_WINDOW_TICKS) {
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
        if (phase == PHASE_RETRACT) {
            if (getRetractTick() > RETRACT_TICKS) {
                this.discard();
            }
            return;
        }
        if (this.age > windupTicks + strokeTicks + LASH_FREE_FLIGHT_TICKS || this.tickCount > 400) {
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
