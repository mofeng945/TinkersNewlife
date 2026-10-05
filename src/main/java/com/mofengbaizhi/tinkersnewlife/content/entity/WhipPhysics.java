package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>鞭身物理</b>（§1047～§1052）。
 *
 * <h2>⚠ 来源与许可</h2>
 * 抽击驱动<b>移植自 Better Whips</b>
 * （{@code https://github.com/my2167592261-cell/Better-Whips} ✓ <b>MIT License</b> ✓
 * Copyright (c) 2026 my2167592261-cell ✓）—— 完整许可见 {@code LICENSES/BetterWhips-MIT.txt} ✓
 * 与 jar 内 {@code META-INF/BetterWhips-MIT.txt} ✓。
 * 移植对象：{@code LeatherWhipPhysics.predict()}（Verlet ＋ <b>根部硬钉在锚点</b> ✓）、
 * {@code precisionHandAnchor()}（<b>手抡出去的那道弧</b> ✓ —— §1052 才补上 ✓）、
 * {@code physics/TrainerStylePrecisionGuide}（附加导引 ✓）。
 *
 * <h2>终于拼齐的机制（试了六版 ✗）</h2>
 * <ol>
 *   <li><b>`predict`：Verlet 绳 ＋ 根部硬钉</b> ✓ ——
 *       {@code points[0] = anchor} ✓（<b>不是弹簧</b> ✗）、其余点用
 *       {@code pos += (pos - prev)·retention + (0, GRAVITY·dt², 0)} ✓；</li>
 *   <li><b>`precisionHandAnchor`：手真的抡出去</b> ✓ ——
 *       起手（&lt;0.30）抬手：上 → <b>+0.68</b>、侧 → ±0.16；
 *       抽击（0.30~0.72）用 {@code pow(t,1.55)} 加速：前 0.08 → <b>0.86</b>、上 +0.68 → <b>−0.08</b>、
 *       侧 +0.16 → <b>−0.10</b>（下劈 ＋ 横扫 ✓）；
 *       收势（&gt;0.72）回到前 0.20 ✓ —— <b>§1047~§1051 我一直没接这一段</b> ✗，
 *       所以根部只是在原地转方向 ✗（用户原话：
 *       「<b>根部开始向绳子尾部传递一个向左或向右的波，传递很慢…完全不是扫动的效果</b>」✓）；</li>
 *   <li><b>`TrainerStylePrecisionGuide`：附加导引</b> ✓（只在进度 ≥ 0.30 后施加 ✓，
 *       照它 {@code PRECISION_RELEASE_RAW} 的门槛 ✓）—— 它是<b>锦上添花</b> ✓，不是本体 ✗。</li>
 * </ol>
 * <p>结论：<b>鞭子 ＝ 一根 Verlet 绳 ＋ 一只被抡圆的手</b> ✓。手划出 1 米多的弧 ✓，
 * 绳身因惯性滞后 ✓，波沿链传到梢部 ✓ ⇒ 甩出圆弧 ✓。
 *
 * <h2>非抽击时</h2>
 * 同一根 Verlet 绳 ＋ 重力 ＋ 地面碰撞 ✓，根部挂在手边 ✓ ⇒
 * 平时就是"从手里垂着／拖在身后的一条<b>完全伸展开</b>的鞭" ✓（§1050 用户口径 ✓）。
 */
public final class WhipPhysics {

    public static final int POINTS = 25;

    private static final int SUBSTEPS = 8;
    private static final int SOLVER_ITERATIONS = 5;

    private static final double TICK_SECONDS = 0.05D;
    /** 段长（格 ✓）：24 × 0.36 ≈ 8.6 格总长 ✓ */
    private static final double SEGMENT_LENGTH = 0.36D;
    /** 重力（格/秒² ✓）：它的 {@code GRAVITY = −21.5} ✓ */
    private static final double GRAVITY = -21.5D;
    /** 每 tick 速度保留 ✓：它的 {@code TICK_VELOCITY_RETENTION = 0.989} ✓ */
    private static final double TICK_VELOCITY_RETENTION = 0.989D;
    private static final double MAX_STRETCH = 1.003D;
    private static final double MAX_STEP_PER_SUBSTEP = 0.55D;

    /** 一次抽击时长（tick ✓）：照它的 {@code ATTACK_SECONDS = 0.5s} ✓ */
    public static final double ATTACK_TICKS = 10.0D;
    /** 抽击驱动延续到进度多少为止 ✓（之后交回"垂着"的静止态 ✓，避免突然抽搐 ✗） */
    private static final double LASH_DRIVE_END = 1.30D;
    /** 它施加附加导引的门槛 ✓：{@code PRECISION_RELEASE_RAW = 0.30} ✓ */
    private static final double PRECISION_RELEASE_RAW = 0.30D;

    // ---------------- 以下常量照 precisionHandAnchor / TrainerStylePrecisionGuide ✓ ----------------
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
    /** 上一<b>子步</b>位置 ✓（它的 {@code previous} ✓ —— Verlet 与速度项都要用 ✓） */
    private final Vec3[] previous = new Vec3[POINTS];
    /** 本 tick 开始时的位置 ✓（扫掠命中与"接触点速度"用 ✓） */
    private final Vec3[] tickStart = new Vec3[POINTS];
    /** 附加导引的状态：每点方向 ＋ 方向增量 ＋ 是否已初始化 ✓ */
    private final Vec3[] directions = new Vec3[POINTS];
    private final Vec3[] directionDelta = new Vec3[POINTS];
    private boolean guideInitialized;
    private boolean started;

    /** 静止摆位：从手边往下、往后垂着一条<b>完全伸展开</b>的鞭 ✓（§1050 用户口径 ✓） */
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
     * @param eye      玩家眼睛位置 ✓（附加导引的球心 ✓）
     * @param handBase 手部基点 ✓（它的 {@code handBase}：脚下 ＋ 眼高−0.58 ＋ 右手侧×0.34 ＋ 前方×0.10 ✓）
     * @param aim      准星方向 ✓
     * @param right    水平右手侧 ✓
     * @param progress 抽击进度 ✓（= 已过 tick / {@link #ATTACK_TICKS} ✓）
     * @param sign     ±1：这一鞭往哪边扫 ✓
     * @param slam     砸地相位 ✓
     */
    public void tick(Level level, Vec3 eye, Vec3 handBase, Vec3 aim, Vec3 right,
                     double progress, double sign, boolean slam) {
        Vec3 a = safeDir(aim);
        Vec3 r = safeDir(right);
        if (!started) {
            reset(eye, a);
        }

        boolean driving = !slam && progress < LASH_DRIVE_END;
        double dt = TICK_SECONDS / SUBSTEPS;
        double progressFrom = Math.max(0.0D, progress - 1.0D / ATTACK_TICKS);

        for (int s = 0; s < SUBSTEPS; s++) {
            double alpha = (s + 1.0D) / SUBSTEPS;
            double subProgress = driving ? Mth.lerp(alpha, progressFrom, progress) : progress;

            // ① 锚点：手抡出去的那道弧 ✓（非抽击时就是手边 ✓ —— 平滑过渡 ✓ 不会突然抽搐 ✓）
            Vec3 anchor;
            if (slam) {
                anchor = handBase.add(new Vec3(a.x, -0.85D, a.z).normalize().scale(1.6D));
            } else if (driving) {
                anchor = precisionHandAnchor(handBase, a, r, subProgress, sign);
            } else {
                anchor = handBase;
            }

            // ② Verlet ＋ 根部硬钉在锚点上 ✓（照它的 predict ✓）
            predict(anchor, dt);

            // ③ 附加导引 ✓（只在进度 ≥ 0.30 后 ✓ —— 照它的门槛 ✓）
            if (driving && subProgress >= PRECISION_RELEASE_RAW) {
                applyGuide(eye, a, subProgress, sign, dt);
            }

            // ④ 距离约束（只拉不推 ✓）＋ 地面碰撞 ✓
            solveDistance();
            collide(level);
        }
    }

    // ==================================================================
    //  ① 手部锚点（移植自 LeatherWhipPhysics.precisionHandAnchor ✓ MIT ✓）
    // ==================================================================

    /**
     * <b>手抡出去的那道弧</b> ✓ —— 与它 {@code precisionHandAnchor} 逐段对应 ✓：
     * <ul>
     *   <li>{@code raw < 0.30}（起手 3 tick）：前 0→0.08、<b>上 0→+0.68</b>（抬手 ✓）、侧 0→side·sign·0.16 ✓；</li>
     *   <li>{@code 0.30~0.72}（抽击 4 tick）：{@code pow(t,1.55)} 加速：前 0.08→<b>0.86</b>、
     *       上 +0.68→<b>−0.08</b>（下劈 ✓）、侧 +0.16→<b>−0.10</b>（横扫 ✓）；</li>
     *   <li>{@code > 0.72}（收势）：前 0.86→0.20、上 →0、侧 →0 ✓。</li>
     * </ul>
     */
    public static Vec3 precisionHandAnchor(Vec3 handBase, Vec3 aim, Vec3 right,
                                           double rawProgress, double sign) {
        double s = sign >= 0.0D ? 1.0D : -1.0D;
        double raw = Mth.clamp(rawProgress, 0.0D, 1.0D);
        double forwardOffset;
        double verticalOffset;
        double lateralOffset;
        if (raw < 0.30D) {
            double t = smoothstep(raw / 0.30D);
            forwardOffset = Mth.lerp(t, 0.0D, 0.08D);
            verticalOffset = Mth.lerp(t, 0.0D, 0.68D);
            lateralOffset = Mth.lerp(t, 0.0D, s * 0.16D);
        } else if (raw < 0.72D) {
            double accelerated = Math.pow(Mth.clamp((raw - 0.30D) / 0.42D, 0.0D, 1.0D), 1.55D);
            forwardOffset = Mth.lerp(accelerated, 0.08D, 0.86D);
            verticalOffset = Mth.lerp(accelerated, 0.68D, -0.08D);
            lateralOffset = Mth.lerp(accelerated, s * 0.16D, -s * 0.10D);
        } else {
            double t = smoothstep((raw - 0.72D) / 0.28D);
            forwardOffset = Mth.lerp(t, 0.86D, 0.20D);
            verticalOffset = Mth.lerp(t, -0.08D, 0.0D);
            lateralOffset = Mth.lerp(t, -s * 0.10D, 0.0D);
        }
        return handBase
                .add(aim.scale(forwardOffset))
                .add(0.0D, verticalOffset, 0.0D)
                .add(right.scale(lateralOffset));
    }

    // ==================================================================
    //  ② Verlet ＋ 根部硬钉（移植自它的 predict ✓）
    // ==================================================================

    private void predict(Vec3 anchor, double dt) {
        pos[0] = anchor;
        previous[0] = anchor;
        double retention = Math.pow(TICK_VELOCITY_RETENTION, 1.0D / SUBSTEPS);
        for (int i = 1; i < POINTS; i++) {
            Vec3 current = pos[i];
            Vec3 velocity = current.subtract(previous[i]).scale(retention);
            double speed = velocity.length();
            if (speed > MAX_STEP_PER_SUBSTEP) {
                velocity = velocity.scale(MAX_STEP_PER_SUBSTEP / speed);
            }
            previous[i] = current;
            pos[i] = current.add(velocity).add(0.0D, GRAVITY * dt * dt, 0.0D);
        }
    }

    // ==================================================================
    //  ③ 附加导引（移植自 TrainerStylePrecisionGuide ✓ —— 锦上添花 ✓ 非本体 ✓）
    // ==================================================================

    private void applyGuide(Vec3 eye, Vec3 aim, double progress, double sign, double dt) {
        Vec3 source = crosshairSweepDirection(aim, progress, sign);
        if (!guideInitialized) {
            initializeGuide(eye, source);
        }
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

        double rootRadius = Mth.clamp(pos[0].distanceTo(eye), 0.45D, 1.15D);
        double accumulatedLength = 0.0D;
        Vec3 rootTarget = eye.add(source.scale(rootRadius));
        Vec3 rootAcceleration = rootTarget.subtract(pos[0]).scale(FOLLOW_POSITION_ACCEL * 0.42D);
        double rootAccelLength = rootAcceleration.length();
        if (rootAccelLength > FOLLOW_MAX_ACCEL * 0.55D) {
            rootAcceleration = rootAcceleration.scale((FOLLOW_MAX_ACCEL * 0.55D) / rootAccelLength);
        }
        pos[0] = pos[0].add(rootAcceleration.scale(dt * dt));

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
            Vec3 crosshairError = eye.add(aim.scale(rayDistance)).subtract(pos[i]);

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

    private static double crosshairGate(double rawProgress, double chainTaper) {
        double delayProgress = (FOLLOW_TOTAL_DELAY_SECONDS / ATTACK_SECONDS) * Mth.clamp(chainTaper, 0.0D, 1.0D);
        double distance = (rawProgress - (CROSSHAIR_SOURCE_PROGRESS + delayProgress))
                / CROSSHAIR_GATE_WIDTH_PROGRESS;
        return Math.exp(-distance * distance);
    }

    // ==================================================================
    //  ④ 约束 ＋ 地面
    // ==================================================================

    private void solveDistance() {
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
    }

    private void collide(Level level) {
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

    private static double smoothstep(double x) {
        double c = Mth.clamp(x, 0.0D, 1.0D);
        return c * c * (3.0D - 2.0D * c);
    }

    private static Vec3 safeDir(Vec3 dir) {
        if (dir == null || dir.lengthSqr() < 1.0E-8D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return dir.normalize();
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
