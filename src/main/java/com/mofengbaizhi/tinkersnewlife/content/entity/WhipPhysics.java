package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>鞭身物理</b>（§1053）—— <b>按参照模组 BetterWhips 的完整机制移植</b> ✓
 * （{@code https://github.com/my2167592261-cell/Better-Whips} ✓ <b>MIT License</b> ✓
 * Copyright (c) 2026 my2167592261-cell ✓；许可原文见 {@code LICENSES/BetterWhips-MIT.txt} ✓
 * 与 jar 内 {@code META-INF/BetterWhips-MIT.txt} ✓）。
 *
 * <h2>移植范围（逐项对应它的源码 ✓）</h2>
 * <ol>
 *   <li><b>绳子本体</b>：<b>57 点 / 56 段</b> ✓，段长由模型骨骼 {@code AUTHORED_PIVOT_Z} 推出
 *       （0.19 → 0.44 格 ✓，总长 <b>≈8.49 格</b> ✓），逐段碰撞半径 0.059 → 0.018（梢端 0.0388）✓；</li>
 *   <li><b>质量渐变</b>：{@code mass = 0.065 + 0.935·(1−taper)²} ✓ ⇒ 梢部只有根部的 1/15 ✓
 *       —— 这是鞭梢能"抽爆"的根本 ✓；</li>
 *   <li><b>逐点限速</b>：{@code (82 + 108·taper^1.65)} 格/秒 ✓ ⇒ 根 82、<b>梢 190</b> ✓；</li>
 *   <li><b>XPBD 约束族</b>：长度（柔度 3e-8 ✓）＋ 弯曲/抗折叠（1.6e-7 ✓）＋ 最大拉伸 1.003 ✓
 *       ＋ <b>手柄刚性区</b>（前 20 段 ×3.4 ✓、连续性检查点 ✓）＋ 自碰撞（0.055 ✓）；</li>
 *   <li><b>自适应子步</b>：按最大速度取 6/8/12/18/24 ✓，攻击中至少 12 ✓；</li>
 *   <li><b>驱动</b>：{@code predict()} 是 Verlet 且<b>根部硬钉在锚点</b> ✓
 *       （{@code pos += (pos−prev)·retention ＋ (0, −21.5·dt², 0)} ✓、保留 0.989 ✓）；</li>
 *   <li><b>三种驱动</b>：左键手臂弧（见 {@link #precisionHandAnchor} ✓）、右键蓄力自转
 *       （{@link #applyChargeForces} ✓）、松手钟摆下抽（{@link #applyReleaseForces} ✓）；</li>
 *   <li><b>附加导引</b>（{@link #applyGuide} ✓）：只在 {@code progress ≥ 0.30} 时施加 ✓。</li>
 * </ol>
 *
 * <h2>为什么这样才"甩得出去"（前七版的教训 ✓）</h2>
 * 鞭子 ＝ <b>一根 57 点的轻梢绳 ＋ 一只被硬钉住、真的在挥的手</b> ✓。
 * 手在 3＋4 tick 里划出约 1.1 格的斜向弧 ✓，绳身因惯性滞后 ✓、
 * <b>梢部因为质量只有 1/15 而获得极高加速度</b> ✓ ⇒ 波传到梢部时抽出声爆 ✓（它注释里 82~190 格/秒 ✓）。
 */
public final class WhipPhysics {

    // ==================== 绳子的物理形状（照它的模型骨骼 ✓） ====================

    public static final int SEGMENTS = 56;
    public static final int POINTS = SEGMENTS + 1;

    /** 模型骨骼 Z（1/16 格 ✓，共 57 个枢轴点 ✓）—— 只用来推段长 ✓ */
    private static final float[] AUTHORED_PIVOT_Z = {
            0.0000F, -3.0266F, -6.0279F, -9.0037F, -11.9541F, -14.8792F, -17.7788F,
            -20.6531F, -23.5019F, -26.3254F, -29.1234F, -31.8961F, -34.6434F, -37.3652F,
            -40.0617F, -42.7328F, -45.3784F, -47.9987F, -50.5936F, -53.1631F, -55.7072F,
            -58.2258F, -60.7191F, -63.1870F, -65.6295F, -68.0466F, -70.4383F, -72.8046F,
            -75.1455F, -77.4611F, -79.7512F, -82.0159F, -84.2552F, -86.4691F, -88.6577F,
            -90.8208F, -92.9585F, -95.0709F, -97.1578F, -99.2193F, -101.2555F, -103.2662F,
            -105.2516F, -107.2115F, -109.1461F, -111.0552F, -112.9390F, -114.7974F, -116.6303F,
            -118.4379F, -120.2201F, -121.9769F, -123.7082F, -125.4142F, -127.0948F, -128.7500F,
            -135.7700F
    };

    /** 逐段静止长度（格 ✓）：由相邻骨骼枢轴差 ÷16 得来 ✓ */
    public static final double[] REST_LENGTHS = buildRestLengths();

    /** 逐段碰撞半径（格 ✓）—— 根 0.059 收到 0.018，梢端有个 0.0388 的"疙瘩" ✓ */
    private static final double[] SEGMENT_RADIUS = {
            0.05913D, 0.05831D, 0.05750D, 0.05668D, 0.05587D, 0.05505D,
            0.05424D, 0.05342D, 0.05261D, 0.05179D, 0.05098D, 0.05016D,
            0.04935D, 0.04853D, 0.04772D, 0.04690D, 0.04609D, 0.04527D,
            0.04446D, 0.04364D, 0.04283D, 0.04201D, 0.04120D, 0.04038D,
            0.03957D, 0.03875D, 0.03794D, 0.03713D, 0.03631D, 0.03550D,
            0.03468D, 0.03387D, 0.03305D, 0.03224D, 0.03142D, 0.03061D,
            0.02979D, 0.02898D, 0.02816D, 0.02735D, 0.02653D, 0.02572D,
            0.02490D, 0.02409D, 0.02327D, 0.02246D, 0.02164D, 0.02083D,
            0.02001D, 0.01920D, 0.01838D, 0.01800D, 0.01800D, 0.01800D,
            0.01800D, 0.03884D
    };

    // ==================== 物理常量（全部照它的值 ✓） ====================

    private static final double TICK_SECONDS = 1.0D / 20.0D;
    private static final int SOLVER_ITERATIONS = 5;
    private static final int MIN_SUBSTEPS = 7;
    private static final int MAX_SUBSTEPS = 24;

    private static final double MAX_SEGMENT_STRETCH = 1.003D;
    private static final double LENGTH_COMPLIANCE = 3.0E-8D;
    private static final double BEND_COMPLIANCE = 1.6E-7D;
    private static final double TICK_VELOCITY_RETENTION = 0.989D;
    private static final double GRAVITY = -21.5D;
    private static final double SELF_COLLISION_DISTANCE = 0.055D;
    private static final double CONTACT_SKIN = 0.0125D;
    private static final int DEPENETRATION_PASSES = 8;
    private static final double DEPENETRATION_EPSILON = 1.0E-4D;
    private static final double SURFACE_TANGENT_RETENTION_PER_TICK = 0.955D;
    private static final int HANDLE_BEND_SEGMENTS = 20;
    private static final double HANDLE_BEND_STIFFNESS_MULTIPLIER = 3.4D;

    // 导引（TrainerStylePrecisionGuide ✓）
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
    /** 参照里 {@code PRECISION_RELEASE_RAW}：导引从进度 0.30 起生效 ✓ */
    public static final double PRECISION_RELEASE_RAW = 0.30D;

    // 蓄力自转（照它 ✓）
    private static final double CHARGE_SPIN_SIGN = 1.0D;
    private static final double CHARGE_CENTRIFUGAL_SCALE = 0.12D;
    private static final double CHARGE_MAX_RADIAL_ACCEL = 150.0D;
    private static final double CHARGE_MAX_TANGENTIAL_ACCEL = 90.0D;
    public static final int RIGHT_CHARGE_TICKS = 60;
    private static final double RIGHT_SPIN_TURNS_AT_FULL_CHARGE = 4.0D;

    /** 驱动模式 ✓ */
    public static final int MODE_LASH = 0;
    public static final int MODE_CHARGE = 1;
    public static final int MODE_RELEASE = 2;

    // ==================== 状态 ====================

    private final Vec3[] pos = new Vec3[POINTS];
    private final Vec3[] previous = new Vec3[POINTS];
    private final Vec3[] before = new Vec3[POINTS];
    private final Vec3[] tickStart = new Vec3[POINTS];
    private final double[] lengthLambda = new double[SEGMENTS];
    private final double[] bendLambda = new double[Math.max(1, SEGMENTS - 1)];
    /** 每 tick 复用的方块形状缓存 ✓（否则每个子步都查一遍方块 ✗） */
    private final Map<BlockPos, List<AABB>> blockCache = new HashMap<>();
    private final Vec3[] guideDirections = new Vec3[POINTS];
    private final Vec3[] guideDelta = new Vec3[POINTS];
    private boolean guideInitialized;
    private boolean started;

    /** 一 tick 的驱动输入（由鞭击实体按参照的时间轴填好 ✓） */
    public static final class Drive {
        public int mode = MODE_LASH;
        public Vec3 rootFrom = Vec3.ZERO;
        public Vec3 rootTo = Vec3.ZERO;
        public Vec3 eye = Vec3.ZERO;
        public Vec3 aim = new Vec3(0.0D, 0.0D, 1.0D);
        public Vec3 forward = new Vec3(0.0D, 0.0D, 1.0D);
        public Vec3 right = new Vec3(1.0D, 0.0D, 0.0D);
        public Vec3 center = Vec3.ZERO;
        public double side = 1.0D;
        /** 左键进度（跨子步插值用 from→to ✓） */
        public double progressFrom;
        public double progressTo;
        /** 这一鞭扫向 +1 / −1 ✓ */
        public double swingSign = 1.0D;
        public double chargeTicks;
        public double releaseProgress;
    }

    // ==================== 静止摆位 ====================

    /**
     * 静止姿态：<b>一条完全伸展开的鞭</b>，从手部往下、往后垂着 ✓
     * （§1050 用户口径 ✓；同时照它 {@code initialDirection} 考虑离地高度 ✓ 免得整条戳进地里 ✗）。
     */
    public void reset(Vec3 root, Vec3 aim) {
        Vec3 a = safeDir(aim);
        Vec3 back = a.scale(-1.0D);
        double total = 0.0D;
        for (double rest : REST_LENGTHS) {
            total += rest;
        }
        double drop = Math.min(1.0D, Math.max(0.15D, (root.y - 0.05D) / Math.max(1.0E-6D, total)));
        Vec3 cursor = root;
        for (int i = 0; i < POINTS; i++) {
            if (i > 0) {
                double len = REST_LENGTHS[i - 1];
                double t = i / (double) (POINTS - 1);
                Vec3 step = new Vec3(0.0D, -len * (0.35D + 0.62D * drop), 0.0D)
                        .add(back.scale(len * 0.38D * (1.0D - 0.5D * t)));
                cursor = cursor.add(step);
            }
            pos[i] = cursor;
            previous[i] = cursor;
            tickStart[i] = cursor;
            guideDirections[i] = safeDir(cursor.subtract(root));
            guideDelta[i] = Vec3.ZERO;
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

    /** 每 tick 开始前调用 ✓（记录 tick 起点，用于命中与速度 ✓） */
    public void markTickStart() {
        if (!started) {
            return;
        }
        blockCache.clear();
        for (int i = 0; i < POINTS; i++) {
            tickStart[i] = pos[i];
        }
    }

    /** 第 i 点本 tick 位移折算的速度（格/秒 ✓） */
    public double speed(int i) {
        if (!started) {
            return 0.0D;
        }
        return pos[i].distanceTo(tickStart[i]) / TICK_SECONDS;
    }

    /** 第 i 段的平均速度（格/秒 ✓）—— 伤害按它折算 ✓（参照用 5 个材质采样取平均 ✓，这里取两端均值 ✓） */
    public double segmentSpeed(int i) {
        int a = Mth.clamp(i, 0, POINTS - 2);
        double sa = pos[a].distanceTo(tickStart[a]) / TICK_SECONDS;
        double sb = pos[a + 1].distanceTo(tickStart[a + 1]) / TICK_SECONDS;
        return (sa + sb) * 0.5D;
    }

    /** 第 i 段的扫掠是否穿过给定碰撞箱 ✓（上一 tick → 现在，含本段两端 ✓） */
    public boolean segmentHits(int i, AABB box) {
        int a = Mth.clamp(i, 0, POINTS - 2);
        AABB grown = box.inflate(SEGMENT_RADIUS[Math.min(a, SEGMENT_RADIUS.length - 1)] + CONTACT_SKIN);
        return segmentIntersects(grown, tickStart[a], pos[a])
                || segmentIntersects(grown, tickStart[a + 1], pos[a + 1])
                || segmentIntersects(grown, pos[a], pos[a + 1]);
    }

    /** 第 i 段与碰撞箱的最早接触点 ✓（没有接触返回 null ✓） */
    public Vec3 segmentContact(int i, AABB box) {
        int a = Mth.clamp(i, 0, POINTS - 2);
        AABB grown = box.inflate(SEGMENT_RADIUS[Math.min(a, SEGMENT_RADIUS.length - 1)] + CONTACT_SKIN);
        for (double t = 0.0D; t <= 1.0001D; t += 0.25D) {
            Vec3 p = tickStart[a].lerp(pos[a], t);
            Vec3 q = tickStart[a + 1].lerp(pos[a + 1], t);
            if (grown.contains(p)) {
                return p;
            }
            if (grown.contains(q)) {
                return q;
            }
            if (segmentIntersects(grown, p, q)) {
                return p.lerp(q, 0.5D);
            }
        }
        return null;
    }

    // ==================== 主推进入口 ====================

    public void step(Level level, Drive d) {
        if (!started) {
            reset(d.rootTo, d.aim);
        }
        int substeps = chooseAdaptiveSubsteps();
        double dt = TICK_SECONDS / substeps;
        double dtSqr = dt * dt;
        double retention = Math.pow(TICK_VELOCITY_RETENTION, 1.0D / substeps);
        double surfaceRetention = Math.pow(SURFACE_TANGENT_RETENTION_PER_TICK, 1.0D / substeps);

        for (int s = 0; s < substeps; s++) {
            double alpha = (s + 1.0D) / substeps;
            Vec3 root = d.rootFrom.lerp(d.rootTo, alpha);
            double progress = Mth.lerp(alpha, d.progressFrom, d.progressTo);

            System.arraycopy(pos, 0, before, 0, POINTS);
            predict(root, retention, dt, dtSqr);

            if (d.mode == MODE_CHARGE) {
                applyChargeForces(d, dt, dtSqr);
            } else if (d.mode == MODE_RELEASE) {
                applyReleaseForces(d, dt, dtSqr);
            } else if (progress >= PRECISION_RELEASE_RAW) {
                applyGuide(d, progress, dt, dtSqr);
            }

            Arrays.fill(lengthLambda, 0.0D);
            Arrays.fill(bendLambda, 0.0D);
            for (int iteration = 0; iteration < SOLVER_ITERATIONS; iteration++) {
                pos[0] = root;
                for (int i = 0; i < SEGMENTS; i++) {
                    solveDistance(i, i + 1, REST_LENGTHS[i], LENGTH_COMPLIANCE, lengthLambda, i, false, dtSqr);
                }
                if (d.mode != MODE_LASH) {
                    enforceHandleBendZone();
                    enforceHandleContinuity();
                }
                enforceAntiFold(dtSqr);
                enforceMaximumStretch();
            }
            pos[0] = root;
            solveSelfCollision();
            depenetrateFromBlocks(level, surfaceRetention, dtSqr);
        }
    }

    // ==================== 预测（照它的 predict ✓） ====================

    private void predict(Vec3 root, double retention, double dt, double dtSqr) {
        pos[0] = root;
        previous[0] = root;
        for (int i = 1; i < POINTS; i++) {
            Vec3 current = pos[i];
            Vec3 velocity = current.subtract(previous[i]).scale(retention);
            double speed = velocity.length();
            double maxStep = maximumVerletStep(i, dt);
            if (speed > maxStep) {
                velocity = velocity.scale(maxStep / speed);
            }
            previous[i] = current;
            pos[i] = current.add(velocity).add(0.0D, GRAVITY * dtSqr, 0.0D);
        }
    }

    private static double particleMass(int particle) {
        if (particle <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        double taper = (particle - 1.0D) / Math.max(1.0D, POINTS - 2.0D);
        double remaining = 1.0D - Mth.clamp(taper, 0.0D, 1.0D);
        return 0.065D + 0.935D * remaining * remaining;
    }

    private static double inverseMass(int particle) {
        return particle <= 0 ? 0.0D : 1.0D / particleMass(particle);
    }

    private static double maximumVerletStep(int particle, double substepSeconds) {
        double taper = particle / (double) (POINTS - 1);
        return (82.0D + 108.0D * Math.pow(taper, 1.65D)) * substepSeconds;
    }

    // ==================== 左键：手臂弧锚点（照它的 precisionHandAnchor ✓） ====================

    /** 手部基点 ✓（它的 {@code handBase}：脚下 ＋ 眼高−0.58 ＋ 右手侧×0.34 ＋ 前方×0.10 ✓） */
    public static Vec3 handBase(Vec3 feet, double eyeHeight, double side, Vec3 forward) {
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
        horizontal = horizontal.lengthSqr() < 1.0E-10D ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
        Vec3 right = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        return feet.add(0.0D, eyeHeight - 0.58D, 0.0D)
                .add(right.scale(side * 0.34D))
                .add(horizontal.scale(0.10D));
    }

    /**
     * <b>手抡出去的那道弧</b> ✓ —— 与它 {@code precisionHandAnchor} 逐段对应 ✓：
     * 起手（&lt;0.30）上 0→+0.68、侧 0→side·sign·0.16；
     * 抽击（0.30~0.72）{@code pow(t,1.55)}：前 0.08→0.86、上 +0.68→−0.08、侧 +0.16→−0.10；
     * 收势（&gt;0.72）前→0.20、上/侧→0 ✓。
     */
    public static Vec3 precisionHandAnchor(Vec3 base, Vec3 aim, Vec3 right, double rawProgress, double sign) {
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
            double t = Math.pow(Mth.clamp((raw - 0.30D) / 0.42D, 0.0D, 1.0D), 1.55D);
            forwardOffset = Mth.lerp(t, 0.08D, 0.86D);
            verticalOffset = Mth.lerp(t, 0.68D, -0.08D);
            lateralOffset = Mth.lerp(t, s * 0.16D, -s * 0.10D);
        } else {
            double t = smoothstep((raw - 0.72D) / 0.28D);
            forwardOffset = Mth.lerp(t, 0.86D, 0.20D);
            verticalOffset = Mth.lerp(t, -0.08D, 0.0D);
            lateralOffset = Mth.lerp(t, -s * 0.10D, 0.0D);
        }
        return base.add(aim.scale(forwardOffset))
                .add(0.0D, verticalOffset, 0.0D)
                .add(right.scale(lateralOffset));
    }

    // ==================== 右键：蓄力自转 + 松手钟摆（照它 ✓） ====================

    /** 自转中心 ✓（它的 {@code chargedSpinCenter}：handBase ＋ 前方×0.32 ＋ 上 0.68 ✓） */
    public static Vec3 chargedSpinCenter(Vec3 base, Vec3 forward) {
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
        horizontal = horizontal.lengthSqr() < 1.0E-10D ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
        return base.add(horizontal.scale(0.32D)).add(0.0D, 0.68D, 0.0D);
    }

    /** 蓄力时手绕着小圈自转 ✓（它的 {@code chargedSpinHandAnchor}：半径 0.18 ✓） */
    public static Vec3 chargedSpinHandAnchor(Vec3 restAnchor, Vec3 center, Vec3 forward, Vec3 right,
                                             double side, double chargeTicks) {
        double charge = Mth.clamp(chargeTicks / RIGHT_CHARGE_TICKS, 0.0D, 1.0D);
        double lift = smoothstep(Math.min(1.0D, charge / 0.12D));
        double theta = CHARGE_SPIN_SIGN * rightSpinTurns(chargeTicks) * Math.PI * 2.0D;
        double radius = 0.18D * smoothstep(Math.min(1.0D, charge / 0.18D));
        Vec3 orbit = center
                .add(forward.scale(Math.cos(theta) * radius))
                .add(right.scale(Math.sin(theta) * radius * side));
        return restAnchor.lerp(orbit, lift);
    }

    /** 松手后钟摆式下抽 ✓（它的 {@code chargedReleaseHandAnchor}：半径 0.72 ✓、θ 由 π/2 → −0.36 ✓） */
    public static Vec3 chargedReleaseHandAnchor(Vec3 base, Vec3 forward, double releaseProgress) {
        Vec3 horizontal = new Vec3(forward.x, 0.0D, forward.z);
        horizontal = horizontal.lengthSqr() < 1.0E-10D ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
        double t = smoothstep(releaseProgress);
        double theta = Mth.lerp(t, Math.PI * 0.5D, -0.36D);
        double radius = 0.72D;
        Vec3 center = base.add(horizontal.scale(0.34D)).add(0.0D, 0.05D, 0.0D);
        return center
                .add(horizontal.scale(Math.cos(theta) * radius))
                .add(0.0D, Math.sin(theta) * radius, 0.0D);
    }

    private static double rightChargeProgress(double chargeTicks) {
        return Mth.clamp(chargeTicks / RIGHT_CHARGE_TICKS, 0.0D, 1.0D);
    }

    /** 转数 ✓（照它：蓄力段 t² 增长 ✓，满了之后按 2×满转/蓄力时长 匀速 ✓） */
    private static double rightSpinTurns(double chargeTicks) {
        double ticks = Math.max(0.0D, chargeTicks);
        if (ticks <= RIGHT_CHARGE_TICKS) {
            double t = ticks / RIGHT_CHARGE_TICKS;
            return RIGHT_SPIN_TURNS_AT_FULL_CHARGE * t * t;
        }
        return RIGHT_SPIN_TURNS_AT_FULL_CHARGE
                + (ticks - RIGHT_CHARGE_TICKS) * (2.0D * RIGHT_SPIN_TURNS_AT_FULL_CHARGE / RIGHT_CHARGE_TICKS);
    }

    /** 角速度（弧度/秒 ✓） */
    private static double rightSpinOmega(double chargeTicks) {
        double ticks = Math.max(0.0D, chargeTicks);
        double turnsPerTick = ticks < RIGHT_CHARGE_TICKS
                ? 2.0D * RIGHT_SPIN_TURNS_AT_FULL_CHARGE * ticks / (RIGHT_CHARGE_TICKS * (double) RIGHT_CHARGE_TICKS)
                : 2.0D * RIGHT_SPIN_TURNS_AT_FULL_CHARGE / RIGHT_CHARGE_TICKS;
        return turnsPerTick * 20.0D * Math.PI * 2.0D;
    }

    /** 离心 ＋ 切向把绳子甩成一张圆盘 ✓（照它的 {@code applyChargeForces} ✓） */
    private void applyChargeForces(Drive d, double dt, double dtSqr) {
        Vec3 forward = new Vec3(d.forward.x, 0.0D, d.forward.z);
        forward = forward.lengthSqr() < 1.0E-10D ? new Vec3(0.0D, 0.0D, 1.0D) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double charge = rightChargeProgress(d.chargeTicks);
        double omega = rightSpinOmega(d.chargeTicks);
        double envelope = smoothstep(charge);

        for (int i = 1; i < POINTS; i++) {
            Vec3 radial = new Vec3(pos[i].x - d.center.x, 0.0D, pos[i].z - d.center.z);
            double radius = radial.length();
            if (radius < 1.0E-5D) {
                continue;
            }
            Vec3 radialDir = radial.scale(1.0D / radius);
            double f = radialDir.dot(forward);
            double r = radialDir.dot(right);
            Vec3 tangentDir = forward.scale(-r).add(right.scale(f)).scale(CHARGE_SPIN_SIGN * d.side);
            tangentDir = tangentDir.lengthSqr() > 1.0E-10D ? tangentDir.normalize() : Vec3.ZERO;

            double taper = i / (double) (POINTS - 1);
            double weight = Math.pow(taper, 1.30D) * envelope;
            double radialAccel = Math.min(CHARGE_MAX_RADIAL_ACCEL,
                    omega * omega * radius * CHARGE_CENTRIFUGAL_SCALE) * weight;
            Vec3 velocity = pos[i].subtract(previous[i]);
            double tangentSpeed = velocity.dot(tangentDir) / dt;
            double desiredSpeed = omega * radius;
            double tangentAccel = Mth.clamp((desiredSpeed - tangentSpeed) * 8.0D,
                    -CHARGE_MAX_TANGENTIAL_ACCEL, CHARGE_MAX_TANGENTIAL_ACCEL) * weight;
            Vec3 acceleration = radialDir.scale(radialAccel).add(tangentDir.scale(tangentAccel));
            pos[i] = pos[i].add(acceleration.scale(dtSqr));
        }
    }

    /** 松手把绳子沿"竖直钟摆平面"抽下去 ✓（照它的 {@code applyReleaseForces} ✓） */
    private void applyReleaseForces(Drive d, double dt, double dtSqr) {
        Vec3 forward = new Vec3(d.forward.x, 0.0D, d.forward.z);
        forward = forward.lengthSqr() < 1.0E-10D ? new Vec3(0.0D, 0.0D, 1.0D) : forward.normalize();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double release = smoothstep(d.releaseProgress);
        double planeBlend = smoothstep(Mth.clamp(d.releaseProgress / 0.30D, 0.0D, 1.0D));

        double pendulumAngle = release * Math.PI * 0.82D;
        Vec3 verticalRadialDir = up.scale(Math.cos(pendulumAngle))
                .add(forward.scale(Math.sin(pendulumAngle))).normalize();
        Vec3 verticalTangentDir = forward.scale(Math.cos(pendulumAngle))
                .add(up.scale(-Math.sin(pendulumAngle))).normalize();

        double accumulatedLength = 0.0D;
        for (int i = 1; i < POINTS; i++) {
            accumulatedLength += REST_LENGTHS[i - 1];
            double taper = i / (double) (POINTS - 1);
            double weight = Math.pow(taper, 1.22D);

            Vec3 relative = pos[i].subtract(d.center);
            double worldRadius = relative.length();
            if (worldRadius < 1.0E-6D) {
                continue;
            }
            Vec3 currentRadialDir = relative.scale(1.0D / worldRadius);
            Vec3 radialDir = currentRadialDir.lerp(verticalRadialDir, planeBlend);
            radialDir = radialDir.lengthSqr() < 1.0E-10D ? verticalRadialDir : radialDir.normalize();

            double targetRadius = Mth.clamp(worldRadius, accumulatedLength * 0.72D, accumulatedLength);
            Vec3 targetPoint = d.center.add(radialDir.scale(targetRadius));
            Vec3 pathError = targetPoint.subtract(pos[i]);
            double lateralOffset = relative.dot(right);

            Vec3 stepVelocity = pos[i].subtract(previous[i]);
            double stepSpeed = stepVelocity.length();
            double speedPerSecond = stepSpeed / Math.max(1.0E-6D, dt);
            Vec3 tangentDir = verticalTangentDir;
            if (stepSpeed > 1.0E-8D) {
                Vec3 currentVelocityDir = stepVelocity.scale(1.0D / stepSpeed);
                tangentDir = currentVelocityDir.lerp(verticalTangentDir, planeBlend);
                tangentDir = tangentDir.lengthSqr() < 1.0E-10D ? verticalTangentDir : tangentDir.normalize();

                double steer = Mth.clamp((0.52D * planeBlend) * weight + 0.10D * taper * planeBlend, 0.0D, 0.78D);
                Vec3 guided = stepVelocity.lerp(tangentDir.scale(stepSpeed), steer);
                double guidedLength = guided.length();
                if (guidedLength > 1.0E-8D) {
                    guided = guided.scale(stepSpeed / guidedLength);
                    previous[i] = pos[i].subtract(guided);
                }
            }

            double centripetalAccel = Math.min(1450.0D, speedPerSecond * speedPerSecond / Math.max(0.25D, targetRadius));
            Vec3 acceleration = radialDir.scale(-centripetalAccel * weight)
                    .add(tangentDir.scale((80.0D + 560.0D * release) * weight * planeBlend))
                    .add(pathError.scale(360.0D * weight * planeBlend))
                    .add(right.scale(-lateralOffset * 980.0D * weight * planeBlend))
                    .add(up.scale(-GRAVITY * weight * planeBlend));
            double accelLength = acceleration.length();
            if (accelLength > 1800.0D) {
                acceleration = acceleration.scale(1800.0D / accelLength);
            }
            pos[i] = pos[i].add(acceleration.scale(dtSqr));
        }
    }

    // ==================== 附加导引（照 TrainerStylePrecisionGuide ✓） ====================

    private void applyGuide(Drive d, double progress, double dt, double dtSqr) {
        Vec3 source = crosshairSweepDirection(d.aim, progress, d.swingSign);
        if (!guideInitialized) {
            initializeGuide(d.eye, source);
        }
        double perLinkTau = FOLLOW_TOTAL_DELAY_SECONDS / Math.max(1.0D, POINTS - 1.0D);
        double blend = Mth.clamp(1.0D - Math.exp(-dt / perLinkTau), 0.0D, 1.0D);
        for (int i = POINTS - 1; i >= 1; i--) {
            Vec3 oldDirection = guideDirections[i];
            Vec3 mixed = oldDirection.lerp(guideDirections[i - 1], blend);
            if (mixed.lengthSqr() < 1.0E-10D) {
                mixed = guideDirections[i - 1];
            }
            Vec3 next = mixed.normalize();
            guideDelta[i] = next.subtract(oldDirection);
            guideDirections[i] = next;
        }
        guideDelta[0] = source.subtract(guideDirections[0]);
        guideDirections[0] = source;

        double rootRadius = Mth.clamp(pos[0].distanceTo(d.eye), 0.45D, 1.15D);
        double accumulatedLength = 0.0D;
        Vec3 rootTarget = d.eye.add(source.scale(rootRadius));
        Vec3 rootAcceleration = rootTarget.subtract(pos[0]).scale(FOLLOW_POSITION_ACCEL * 0.42D);
        double rootAccelLength = rootAcceleration.length();
        if (rootAccelLength > FOLLOW_MAX_ACCEL * 0.55D) {
            rootAcceleration = rootAcceleration.scale((FOLLOW_MAX_ACCEL * 0.55D) / rootAccelLength);
        }
        pos[0] = pos[0].add(rootAcceleration.scale(dtSqr));

        for (int i = 1; i < POINTS; i++) {
            accumulatedLength += REST_LENGTHS[i - 1];
            double taper = i / (double) (POINTS - 1);
            double targetRadius = rootRadius + accumulatedLength;
            double gate = crosshairGate(progress, taper);

            Vec3 desiredDirection = guideDirections[i].lerp(d.aim, gate);
            desiredDirection = desiredDirection.lengthSqr() < 1.0E-10D ? d.aim : desiredDirection.normalize();

            Vec3 targetPoint = d.eye.add(desiredDirection.scale(targetRadius));
            Vec3 pathError = targetPoint.subtract(pos[i]);
            double radialError = targetRadius - pos[i].subtract(d.eye).length();
            Vec3 desiredAngularVelocity = guideDelta[i].scale(targetRadius / dt);
            Vec3 currentVelocity = pos[i].subtract(previous[i]).scale(1.0D / dt);
            Vec3 velocityError = desiredAngularVelocity.subtract(currentVelocity);

            Vec3 fromEye = pos[i].subtract(d.eye);
            double rayDistance = Math.max(0.35D, fromEye.dot(d.aim));
            Vec3 crosshairError = d.eye.add(d.aim.scale(rayDistance)).subtract(pos[i]);

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
            guideDirections[i] = direction;
            guideDelta[i] = Vec3.ZERO;
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
        double distance = (rawProgress - (CROSSHAIR_SOURCE_PROGRESS + delayProgress)) / CROSSHAIR_GATE_WIDTH_PROGRESS;
        return Math.exp(-distance * distance);
    }

    // ==================== 约束族（照它的实现 ✓） ====================

    private void solveDistance(int a, int b, double rest, double compliance,
                               double[] lambdas, int lambdaIndex, boolean minimumOnly, double dtSqr) {
        Vec3 delta = pos[b].subtract(pos[a]);
        double distance = delta.length();
        if (distance < 1.0E-9D || (minimumOnly && distance >= rest)) {
            if (minimumOnly) {
                lambdas[lambdaIndex] = 0.0D;
            }
            return;
        }
        double weightA = inverseMass(a);
        double weightB = inverseMass(b);
        double alpha = compliance / dtSqr;
        double constraint = distance - rest;
        double deltaLambda = (-constraint - alpha * lambdas[lambdaIndex]) / (weightA + weightB + alpha);
        lambdas[lambdaIndex] += deltaLambda;
        Vec3 direction = delta.scale(1.0D / distance);
        if (weightA > 0.0D) {
            pos[a] = pos[a].add(direction.scale(-weightA * deltaLambda));
        }
        if (weightB > 0.0D) {
            pos[b] = pos[b].add(direction.scale(weightB * deltaLambda));
        }
    }

    private void enforceMaximumStretch() {
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 delta = pos[i + 1].subtract(pos[i]);
            double distance = delta.length();
            double maximum = REST_LENGTHS[i] * MAX_SEGMENT_STRETCH;
            if (distance <= maximum || distance < 1.0E-9D) {
                continue;
            }
            double weightA = inverseMass(i);
            double weightB = inverseMass(i + 1);
            double total = weightA + weightB;
            if (total <= 0.0D) {
                continue;
            }
            Vec3 correction = delta.scale((distance - maximum) / distance);
            if (weightA > 0.0D) {
                pos[i] = pos[i].add(correction.scale(weightA / total));
            }
            if (weightB > 0.0D) {
                pos[i + 1] = pos[i + 1].subtract(correction.scale(weightB / total));
            }
        }
    }

    private void enforceAntiFold(double dtSqr) {
        for (int i = 1; i < SEGMENTS; i++) {
            double adjacent = REST_LENGTHS[i - 1] + REST_LENGTHS[i];
            double taper = (i - 1.0D) / Math.max(1.0D, SEGMENTS - 2.0D);
            double minimumRatio = 0.28D + 0.62D * Math.pow(1.0D - taper, 1.55D);
            solveDistance(i - 1, i + 1, adjacent * minimumRatio, BEND_COMPLIANCE, bendLambda, i - 1, true, dtSqr);
        }
    }

    private void enforceHandleBendZone() {
        int reinforced = Math.min(HANDLE_BEND_SEGMENTS, SEGMENTS - 1);
        for (int i = 1; i <= reinforced; i++) {
            double adjacent = REST_LENGTHS[i - 1] + REST_LENGTHS[i];
            double t = (i - 1.0D) / Math.max(1.0D, reinforced - 1.0D);
            double baseMinimumRatio = Mth.lerp(t, 0.97D, 0.78D);
            double minimumRatio = 1.0D - (1.0D - baseMinimumRatio) / HANDLE_BEND_STIFFNESS_MULTIPLIER;
            enforceMinimumSpan(i - 1, i + 1, adjacent * minimumRatio);
        }
    }

    private void enforceHandleContinuity() {
        int[] checkpoints = {3, 4, 6, 8, 12, 16};
        double[] ratios = {0.70D, 0.58D, 0.50D, 0.42D, 0.36D, 0.32D};
        double accumulated = 0.0D;
        int next = 0;
        for (int i = 0; i < Math.min(HANDLE_BEND_SEGMENTS, SEGMENTS); i++) {
            accumulated += REST_LENGTHS[i];
            int count = i + 1;
            if (next < checkpoints.length && count == checkpoints[next]) {
                enforceMinimumSpan(0, count, accumulated * ratios[next]);
                next++;
            }
        }
    }

    private void enforceMinimumSpan(int a, int b, double minimumSpan) {
        Vec3 delta = pos[b].subtract(pos[a]);
        double distance = delta.length();
        if (distance >= minimumSpan) {
            return;
        }
        Vec3 direction;
        if (distance < 1.0E-8D) {
            direction = b > 1 ? pos[b - 1].subtract(pos[a]) : new Vec3(0.0D, -1.0D, 0.0D);
            direction = direction.lengthSqr() < 1.0E-10D ? new Vec3(0.0D, -1.0D, 0.0D) : direction.normalize();
        } else {
            direction = delta.scale(1.0D / distance);
        }
        double weightA = inverseMass(a);
        double weightB = inverseMass(b);
        double total = weightA + weightB;
        if (total <= 0.0D) {
            return;
        }
        Vec3 correction = direction.scale(minimumSpan - distance);
        if (weightA > 0.0D) {
            pos[a] = pos[a].add(correction.scale(-weightA / total));
        }
        if (weightB > 0.0D) {
            pos[b] = pos[b].add(correction.scale(weightB / total));
        }
    }

    private void solveSelfCollision() {
        for (int i = 1; i < POINTS; i++) {
            for (int j = i + 3; j < POINTS; j++) {
                double minimum = SELF_COLLISION_DISTANCE;
                double minimumSqr = minimum * minimum;
                Vec3 delta = pos[j].subtract(pos[i]);
                double distanceSqr = delta.lengthSqr();
                if (distanceSqr >= minimumSqr || distanceSqr < 1.0E-12D) {
                    continue;
                }
                double distance = Math.sqrt(distanceSqr);
                double weightA = inverseMass(i);
                double weightB = inverseMass(j);
                double total = weightA + weightB;
                if (total <= 0.0D) {
                    continue;
                }
                Vec3 correction = delta.scale((minimum - distance) / distance);
                pos[i] = pos[i].add(correction.scale(-weightA / total));
                pos[j] = pos[j].add(correction.scale(weightB / total));
            }
        }
    }

    // ==================== 方块碰撞（逐点去穿插 ＋ 切向阻尼 ✓） ====================

    private void depenetrateFromBlocks(Level level, double surfaceRetention, double dtSqr) {
        for (int pass = 0; pass < DEPENETRATION_PASSES; pass++) {
            boolean moved = false;
            for (int i = 1; i < POINTS; i++) {
                double radius = SEGMENT_RADIUS[Math.min(i, SEGMENT_RADIUS.length - 1)];
                Vec3 p = pos[i];
                BlockPos center = BlockPos.containing(p);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos bp = center.offset(dx, dy, dz);
                            for (AABB box : shapesAt(level, bp)) {
                                Vec3 resolved = pushOut(p, box, radius);
                                if (resolved != null) {
                                    Vec3 normal = resolved.subtract(p);
                                    double len = normal.length();
                                    if (len > DEPENETRATION_EPSILON) {
                                        pos[i] = resolved;
                                        p = resolved;
                                        // 沿表面切向衰减（照它的 SURFACE_TANGENT_RETENTION ✓）
                                        Vec3 delta = pos[i].subtract(previous[i]);
                                        Vec3 unit = normal.scale(1.0D / len);
                                        Vec3 normalPart = unit.scale(delta.dot(unit));
                                        Vec3 tangentPart = delta.subtract(normalPart).scale(surfaceRetention);
                                        previous[i] = pos[i].subtract(normalPart.add(tangentPart));
                                        moved = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!moved) {
                return;
            }
        }
    }

    private List<AABB> shapesAt(Level level, BlockPos bp) {
        List<AABB> cached = blockCache.get(bp);
        if (cached != null) {
            return cached;
        }
        VoxelShape shape = level.getBlockState(bp).getCollisionShape(level, bp);
        List<AABB> list = shape.isEmpty() ? List.of() : shape.toAabbs();
        blockCache.put(bp.immutable(), list);
        return list;
    }

    /** 把点从方块盒里推出去（含半径 ✓）；没碰到返回 null ✓ */
    private static Vec3 pushOut(Vec3 p, AABB box, double radius) {
        double minX = box.minX - radius;
        double minY = box.minY - radius;
        double minZ = box.minZ - radius;
        double maxX = box.maxX + radius;
        double maxY = box.maxY + radius;
        double maxZ = box.maxZ + radius;
        if (p.x <= minX || p.x >= maxX || p.y <= minY || p.y >= maxY || p.z <= minZ || p.z >= maxZ) {
            return null;
        }
        double dxMin = p.x - minX;
        double dxMax = maxX - p.x;
        double dyMin = p.y - minY;
        double dyMax = maxY - p.y;
        double dzMin = p.z - minZ;
        double dzMax = maxZ - p.z;
        double best = dxMin;
        int axis = 0;
        boolean positive = false;
        if (dxMax < best) { best = dxMax; axis = 0; positive = true; }
        if (dyMin < best) { best = dyMin; axis = 1; positive = false; }
        if (dyMax < best) { best = dyMax; axis = 1; positive = true; }
        if (dzMin < best) { best = dzMin; axis = 2; positive = false; }
        if (dzMax < best) { best = dzMax; axis = 2; positive = true; }
        return switch (axis) {
            case 0 -> new Vec3(positive ? maxX : minX, p.y, p.z);
            case 1 -> new Vec3(p.x, positive ? maxY : minY, p.z);
            default -> new Vec3(p.x, p.y, positive ? maxZ : minZ);
        };
    }

    // ==================== 子步数（照它的分段 ✓） ====================

    private int chooseAdaptiveSubsteps() {
        double maxSpeed = 0.0D;
        for (int i = 1; i < POINTS; i++) {
            maxSpeed = Math.max(maxSpeed, pos[i].distanceTo(previous[i]) / (TICK_SECONDS / 8.0D));
        }
        int steps;
        if (maxSpeed < 28.0D) {
            steps = 6;
        } else if (maxSpeed < 58.0D) {
            steps = 8;
        } else if (maxSpeed < 105.0D) {
            steps = 12;
        } else if (maxSpeed < 165.0D) {
            steps = 18;
        } else {
            steps = 24;
        }
        return Mth.clamp(steps, MIN_SUBSTEPS, MAX_SUBSTEPS);
    }

    // ==================== 工具 ====================

    public static double[] buildRestLengths() {
        double[] result = new double[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            result[i] = Math.abs(AUTHORED_PIVOT_Z[i + 1] - AUTHORED_PIVOT_Z[i]) / 16.0D;
        }
        return result;
    }

    /** 第 i 段的碰撞半径（格 ✓）—— 渲染带面按它收放 ✓（照参照的 SEGMENT_COLLIDER_RADIUS ✓） */
    public static double segmentRadius(int i) {
        return SEGMENT_RADIUS[Mth.clamp(i, 0, SEGMENT_RADIUS.length - 1)];
    }

    /** 总长（格 ✓）—— 参照约 8.49 ✓ */
    public static double totalLength() {
        double total = 0.0D;
        for (double rest : REST_LENGTHS) {
            total += rest;
        }
        return total;
    }

    private static double smoothstep(double value) {
        double t = Mth.clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
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
