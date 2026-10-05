package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>鞭身物理</b>（§1047～§1051）。
 *
 * <h2>⚠ 来源与许可</h2>
 * 抽击算法<b>移植自 Better Whips</b>
 * （{@code https://github.com/my2167592261-cell/Better-Whips} ✓ <b>MIT License</b> ✓
 * Copyright (c) 2026 my2167592261-cell ✓）—— 完整许可见 {@code LICENSES/BetterWhips-MIT.txt} ✓
 * 与 jar 内 {@code META-INF/BetterWhips-MIT.txt} ✓。
 * 移植对象：{@code physics/TrainerStylePrecisionGuide}（见 {@link #applyGuide} ✓ 逐行对应 ✓）
 * 与 {@code LeatherWhipPhysics} 的手臂姿势角度 ✓。
 *
 * <h2>为什么前四版都"甩不出去"✗（用户实测反馈 ＋ 我第四次的错因 ✓）</h2>
 * 我一直把它当成"<b>距离约束的绳子</b>" ✗（Verlet/PBD ＋ 只驱动根部 ✗）——
 * 那种模型里手只动 1 米 ✗，8.6 米长的绳身只会<b>原地垂着</b> ✗（用户原话：
 * 「<b>左键后鞭子原地垂下了，没有甩出去</b>」✓）。
 * <p><b>它真正的模型是"以玩家眼睛为球心的方向波"</b> ✓（读完源码才明白 ✓）：
 * <ol>
 *   <li>每点存一个<b>方向</b> {@code directions[i]} ✓ 与到眼睛的<b>半径</b>
 *       （{@code rootRadius + 累计段长} ✓）⇒ 形状由"方向 ＋ 半径"决定 ✓、天然不可伸长 ✓；</li>
 *   <li>根部方向 = <b>准星绕竖轴扫过 ±68°</b> 的方向 ✓（{@link #crosshairSweepDirection} ✓）；</li>
 *   <li><b>从梢到根</b>逐链混合：{@code perLinkTau = 0.24/(N-1)} ✓、
 *       {@code blend = 1-exp(-dt/tau)} ✓ ⇒ <b>一道延迟波从手一路传到梢</b> ✓
 *       —— 这才是"甩"的本质 ✓（总延迟 0.24 秒 ✓）；</li>
 *   <li>每点被加速度拉向 {@code eye + desiredDirection × targetRadius} ✓：
 *       位置 640 ＋ 径向 430 ＋ 速度 28 ＋ "过准星门" 5200（高斯门 ✓ 让鞭子<b>抽过准星线</b> ✓）
 *       ⇒ 乘 tipGain（梢部 1.18 ✓）⇒ 上限 4400 ✓ —— 数值全部照它 ✓。</li>
 * </ol>
 * ⇒ 鞭梢到眼睛的距离可达 1.15＋8.64 ≈ <b>9.8 格</b> ✓，方向波扫过 136° 时梢部自然甩出大圆弧 ✓
 *（它注释里的梢速 82~190 格/秒 ✓ 就是这么来的 ✓）。
 *
 * <h2>非抽击时</h2>
 * 用本仓自己的 Verlet/PBD 绳 ＋ 重力 ＋ 地面碰撞 ✓ ⇒ 平时就是"从手里垂着／拖在身后的一条鞭" ✓
 *（§1050 用户口径：静止时必须是<b>完全伸展开</b>的一条整鞭 ✓）。
 */
public final class WhipPhysics {

    public static final int POINTS = 25;

    private static final int SUBSTEPS = 8;
    private static final int SOLVER_ITERATIONS = 5;

    private static final double TICK_SECONDS = 0.05D;
    /** 段长（格 ✓）：24 × 0.36 ≈ 8.6 格总长 ✓ */
    private static final double SEGMENT_LENGTH = 0.36D;
    /** 平时（非抽击）的重力与速度保留 ✓：−21.5 与 0.989 都取自它的常量 ✓ */
    private static final double GRAVITY = -21.5D;
    private static final double VELOCITY_RETENTION = 0.989D;
    private static final double MAX_STRETCH = 1.003D;
    private static final double IDLE_MAX_SPEED = 60.0D;

    /** 一次抽击时长（tick ✓）：照它的 {@code ATTACK_SECONDS = 0.5s} ✓ */
    public static final double ATTACK_TICKS = 10.0D;

    // ---------------- 以下常量全部照 TrainerStylePrecisionGuide ✓ ----------------
    private static final double FOLLOW_TOTAL_DELAY_SECONDS = 0.24D;
    private static final double FOLLOW_POSITION_ACCEL = 640.0D;
    private static final double FOLLOW_VELOCITY_ACCEL = 28.0D;
    private static final double FOLLOW_RADIAL_ACCEL = 430.0D;
    private static final double FOLLOW_MAX_ACCEL = 4400.0D;
    private static final double FOLLOW_TIP_GAIN = 1.18D;
    private static final double CROSSHAIR_SOURCE_PROGRESS = 0.30D;
    private static final double CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS = 0.34D;
    private static final double CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS = Math.toRadians(68.0D);
    private static final double ATTACK_SECONDS = 0.50D;
    private static final double CROSSHAIR_GATE_WIDTH_PROGRESS = 0.095D;
    private static final double CROSSHAIR_GATE_ACCEL = 5200.0D;

    private static final double COLLISION_RADIUS = 0.06D;
    private static final double MAX_DEPENETRATION = 0.25D;

    private final Vec3[] pos = new Vec3[POINTS];
    /** 上一<b>子步</b>的位置 ✓（它的 {@code previous} ✓ —— 速度项要用 ✓） */
    private final Vec3[] previous = new Vec3[POINTS];
    /** 本 tick 开始时的位置 ✓（扫掠命中与"接触点速度"用 ✓） */
    private final Vec3[] tickStart = new Vec3[POINTS];
    /** 它的 {@code State}：每点的方向 ＋ 方向增量 ＋ 是否已初始化 ✓ */
    private final Vec3[] directions = new Vec3[POINTS];
    private final Vec3[] directionDelta = new Vec3[POINTS];
    private boolean guideInitialized;
    private boolean started;

    /** 平时摆位：从手里往下、往后垂着一条<b>完全伸展开</b>的鞭 ✓（§1050 用户口径 ✓） */
    public void reset(Vec3 eye, Vec3 aim) {
        Vec3 a = safeDir(aim);
        Vec3 back = a.scale(-1.0D);
        Vec3 root = eye.add(a.scale(0.9D));
        for (int i = 0; i < POINTS; i++) {
            double t = i;
            Vec3 p = root
                    .add(new Vec3(0.0D, -SEGMENT_LENGTH * t * 0.92D, 0.0D))
                    .add(back.scale(SEGMENT_LENGTH * t * 0.38D));
            pos[i] = p;
            previous[i] = p;
            tickStart[i] = p;
            directions[i] = safeDir(p.subtract(eye));
            directionDelta[i] = Vec3.ZERO;
        }
        guideInitialized = false;
        started = true;
    }

    public boolean isStarted() {
        return started;
    }

    public Vec3 point(int i) {
        return pos[Mth.clamp(i, 0, POINTS - 1)];
    }

    public void markTickStart() {
        if (!started) {
            return;
        }
        for (int i = 0; i < POINTS; i++) {
            tickStart[i] = pos[i];
        }
    }

    public double speed(int i) {
        if (!started) {
            return 0.0D;
        }
        return pos[i].distanceTo(tickStart[i]) / TICK_SECONDS;
    }

    /**
     * 推进一 tick ✓。
     *
     * @param eye      玩家<b>眼睛</b>位置 ✓ —— 抽击时整条鞭就是绕它的一个球面扇形 ✓（照它 ✓）
     * @param aim      准星方向（单位向量 ✓）
     * @param progress 抽击进度 0..1 ✓（= 已过 tick / {@link #ATTACK_TICKS} ✓）
     * @param sign     ±1：这一鞭往哪边扫 ✓
     * @param slam     砸地相位 ✓（不走方向波，改为往下狠抽 ✓）
     */
    public void tick(Level level, Vec3 eye, Vec3 aim, double progress, double sign, boolean slam) {
        Vec3 a = safeDir(aim);
        if (!started) {
            reset(eye, a);
        }
        // 抽击期间（进度 < 1.25）走"方向波" ✓；之后交回普通绳 ✓（收势自然垂落 ✓）
        boolean guiding = !slam && progress < 1.25D;
        double dt = TICK_SECONDS / SUBSTEPS;

        for (int s = 0; s < SUBSTEPS; s++) {
            for (int i = 0; i < POINTS; i++) {
                previous[i] = pos[i];
            }
            if (guiding) {
                applyGuide(eye, a, progress, sign, dt);
            } else {
                stepIdle(level, eye, a, dt, slam);
            }
        }
    }

    // ==================================================================
    //  抽击：方向波导引（移植自 BetterWhips 的 TrainerStylePrecisionGuide ✓ MIT ✓）
    // ==================================================================

    private void applyGuide(Vec3 eye, Vec3 aim, double progress, double sign, double dt) {
        Vec3 source = crosshairSweepDirection(aim, progress, sign);
        if (!guideInitialized) {
            initializeGuide(eye, source);
        }

        // ① 方向波：从梢到根逐链混合 ⇒ 总延迟 0.24 秒 ✓（照它 ✓）
        double perLinkTau = FOLLOW_TOTAL_DELAY_SECONDS / Math.max(1.0D, POINTS - 1.0D);
        double blend = Mth.clamp(1.0D - Math.exp(-dt / perLinkTau), 0.0D, 1.0D);
        for (int i = POINTS - 1; i >= 1; i--) {
            Vec3 oldDirection = directions[i];
            Vec3 leaderDirection = directions[i - 1];
            Vec3 mixed = oldDirection.lerp(leaderDirection, blend);
            if (mixed.lengthSqr() < 1.0E-10D) {
                mixed = leaderDirection;
            }
            Vec3 nextDirection = mixed.normalize();
            directionDelta[i] = nextDirection.subtract(oldDirection);
            directions[i] = nextDirection;
        }
        directionDelta[0] = source.subtract(directions[0]);
        directions[0] = source;

        // ② 根部：维持"离眼睛 rootRadius" ✓（它把 0.45~1.15 夹住 ✓）
        double rootRadius = Mth.clamp(pos[0].distanceTo(eye), 0.45D, 1.15D);
        double accumulatedLength = 0.0D;
        Vec3 rootTarget = eye.add(source.scale(rootRadius));
        Vec3 rootAcceleration = rootTarget.subtract(pos[0]).scale(FOLLOW_POSITION_ACCEL * 0.42D);
        double rootAccelLength = rootAcceleration.length();
        if (rootAccelLength > FOLLOW_MAX_ACCEL * 0.55D) {
            rootAcceleration = rootAcceleration.scale((FOLLOW_MAX_ACCEL * 0.55D) / rootAccelLength);
        }
        pos[0] = pos[0].add(rootAcceleration.scale(dt * dt));

        // ③ 其余各点：拉向"自己那一圈球面上的目标点" ✓
        double dtSqr = dt * dt;
        for (int i = 1; i < POINTS; i++) {
            accumulatedLength += SEGMENT_LENGTH;
            double taper = i / (double) (POINTS - 1);
            double targetRadius = rootRadius + accumulatedLength;
            double gate = crosshairGate(progress, taper);

            Vec3 followedDirection = directions[i];
            Vec3 desiredDirection = followedDirection.lerp(aim, gate);
            desiredDirection = desiredDirection.lengthSqr() < 1.0E-10D ? aim : desiredDirection.normalize();

            Vec3 targetPoint = eye.add(desiredDirection.scale(targetRadius));
            Vec3 pathError = targetPoint.subtract(pos[i]);
            double radialError = targetRadius - pos[i].subtract(eye).length();
            Vec3 desiredAngularVelocity = directionDelta[i].scale(targetRadius / dt);
            Vec3 currentVelocity = pos[i].subtract(previous[i]).scale(1.0D / dt);
            Vec3 velocityError = desiredAngularVelocity.subtract(currentVelocity);

            Vec3 fromEye = pos[i].subtract(eye);
            double rayDistance = Math.max(0.35D, fromEye.dot(aim));
            Vec3 crosshairPoint = eye.add(aim.scale(rayDistance));
            Vec3 crosshairError = crosshairPoint.subtract(pos[i]);

            double tipGain = Mth.lerp(taper, 1.0D, FOLLOW_TIP_GAIN);
            Vec3 acceleration = pathError.scale(FOLLOW_POSITION_ACCEL)
                    .add(desiredDirection.scale(radialError * FOLLOW_RADIAL_ACCEL))
                    .add(velocityError.scale(FOLLOW_VELOCITY_ACCEL))
                    .add(crosshairError.scale(CROSSHAIR_GATE_ACCEL * gate))
                    .scale(tipGain);
            double accelLength = acceleration.length();
            if (accelLength > FOLLOW_MAX_ACCEL) {
                acceleration = acceleration.scale(FOLLOW_MAX_ACCEL / accelLength);
            }
            pos[i] = pos[i].add(acceleration.scale(dtSqr));
        }
    }

    private void initializeGuide(Vec3 eye, Vec3 fallbackDirection) {
        Vec3 last = fallbackDirection;
        for (int i = 0; i < POINTS; i++) {
            Vec3 relative = pos[i].subtract(eye);
            Vec3 direction = relative.lengthSqr() > 1.0E-10D ? relative.normalize() : last;
            directions[i] = direction;
            directionDelta[i] = Vec3.ZERO;
            last = direction;
        }
        guideInitialized = true;
    }

    /** 根部方向的"横扫"：准星绕竖轴扫过 ±68° ✓（照它的 {@code crosshairSweepDirection} ✓） */
    private static Vec3 crosshairSweepDirection(Vec3 aim, double rawProgress, double swingSign) {
        Vec3 referenceUp = Math.abs(aim.y) < 0.94D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 side = referenceUp.cross(aim);
        side = side.lengthSqr() < 1.0E-10D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        double phase = Mth.clamp(
                (rawProgress - CROSSHAIR_SOURCE_PROGRESS) / CROSSHAIR_SWEEP_HALF_SPAN_PROGRESS, -1.0D, 1.0D);
        double signedAngle = phase * CROSSHAIR_SWEEP_HALF_ANGLE_RADIANS * (swingSign >= 0.0D ? 1.0D : -1.0D);
        Vec3 direction = aim.scale(Math.cos(signedAngle)).add(side.scale(Math.sin(signedAngle)));
        return direction.lengthSqr() > 1.0E-10D ? direction.normalize() : aim;
    }

    /** "过准星门"：以 0.30＋延迟 为中心的高斯 ✓ ⇒ 让鞭子真的<b>抽过准星线</b> ✓（照它 ✓） */
    private static double crosshairGate(double rawProgress, double chainTaper) {
        double delayProgress = (FOLLOW_TOTAL_DELAY_SECONDS / ATTACK_SECONDS) * Mth.clamp(chainTaper, 0.0D, 1.0D);
        double crossingProgress = CROSSHAIR_SOURCE_PROGRESS + delayProgress;
        double distance = (rawProgress - crossingProgress) / CROSSHAIR_GATE_WIDTH_PROGRESS;
        return Math.exp(-distance * distance);
    }

    // ==================================================================
    //  非抽击：本仓自己的 Verlet/PBD 绳（重力 ＋ 距离约束 ＋ 地面 ✓）
    // ==================================================================

    private void stepIdle(Level level, Vec3 eye, Vec3 aim, double dt, boolean slam) {
        Vec3 rootTarget = slam
                ? eye.add(new Vec3(aim.x, -0.85D, aim.z).normalize().scale(1.6D))
                : eye.add(aim.scale(0.9D));

        for (int i = 0; i < POINTS; i++) {
            Vec3 v = pos[i].subtract(previous[i]).scale(1.0D / dt).scale(VELOCITY_RETENTION);
            v = clampSpeed(v, IDLE_MAX_SPEED).add(0.0D, GRAVITY * dt, 0.0D);
            previous[i] = pos[i];
            pos[i] = pos[i].add(v.scale(dt));
        }
        pos[0] = pos[0].lerp(rootTarget, 0.35D);

        for (int it = 0; it < SOLVER_ITERATIONS; it++) {
            for (int i = 1; i < POINTS; i++) {
                Vec3 a = pos[i - 1];
                Vec3 b = pos[i];
                Vec3 delta = b.subtract(a);
                double len = delta.length();
                if (len < 1.0E-6D || len <= SEGMENT_LENGTH * MAX_STRETCH) {
                    continue;
                }
                pos[i] = b.subtract(delta.scale((len - SEGMENT_LENGTH) / len));
            }
        }

        for (int i = 0; i < POINTS; i++) {
            Vec3 p = pos[i];
            BlockPos bp = BlockPos.containing(p.x, p.y - COLLISION_RADIUS, p.z);
            var shape = level.getBlockState(bp).getCollisionShape(level, bp);
            if (shape.isEmpty()) {
                continue;
            }
            double top = bp.getY() + shape.max(Direction.Axis.Y);
            if (p.y < top + COLLISION_RADIUS) {
                pos[i] = new Vec3(p.x, p.y + Math.min(MAX_DEPENETRATION, top + COLLISION_RADIUS - p.y), p.z);
            }
        }
    }

    /** 第 i 段的扫掠是否穿过给定碰撞箱 ✓（本 tick 起点 → 当前位置 ＋ 本段 ✓） */
    public boolean segmentHits(int i, AABB box) {
        int a = Mth.clamp(i, 0, POINTS - 2);
        AABB grown = box.inflate(COLLISION_RADIUS);
        return segmentIntersects(grown, tickStart[a], pos[a])
                || segmentIntersects(grown, pos[a], pos[a + 1]);
    }

    private static Vec3 safeDir(Vec3 dir) {
        if (dir == null || dir.lengthSqr() < 1.0E-8D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return dir.normalize();
    }

    private static Vec3 clampSpeed(Vec3 v, double max) {
        double len = v.length();
        if (len > max) {
            return v.scale(max / len);
        }
        return v;
    }

    private static boolean segmentIntersects(AABB box, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double tMin = 0.0D;
        double tMax = 1.0D;
        double[][] slabs = {
                {from.x, dx, box.minX, box.maxX},
                {from.y, dy, box.minY, box.maxY},
                {from.z, dz, box.minZ, box.maxZ}
        };
        for (double[] s : slabs) {
            double start = s[0];
            double delta = s[1];
            double lo = s[2];
            double hi = s[3];
            if (Math.abs(delta) < 1.0E-9D) {
                if (start < lo || start > hi) {
                    return false;
                }
                continue;
            }
            double t1 = (lo - start) / delta;
            double t2 = (hi - start) / delta;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return false;
            }
        }
        return true;
    }
}
