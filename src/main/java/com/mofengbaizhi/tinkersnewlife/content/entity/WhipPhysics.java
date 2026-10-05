package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>鞭身物理</b>（§1047～§1049）。
 *
 * <h2>⚠ 来源与许可</h2>
 * 「<b>盘绕 + 抡圆抽击</b>」这套手感移植自 <b>Better Whips</b>
 * （{@code https://github.com/my2167592261-cell/Better-Whips} ✓ <b>MIT License</b> ✓
 * Copyright (c) 2026 my2167592261-cell ✓）—— 许可原文见 {@code LICENSES/BetterWhips-MIT.txt} ✓
 * 与 jar 内 {@code META-INF/BetterWhips-MIT.txt} ✓。取用到的口径：
 * 扫动半角 68°、进度窗口 0.30／0.34、一次抽击 0.5 秒（10 tick）、绳身跟随延迟 0.24 秒、
 * 手部基点公式、抽击加速曲线 {@code pow(t,1.55)}、手臂姿势 pitch 1.52 → −0.58、
 * <b>盘绕常量</b>（{@code COIL_TURNS / COIL_LAYER_PITCH / COIL_POSITION_GAIN /
 * COIL_MAX_PULL_PER_SUBSTEP / COIL_VELOCITY_DAMPING}）✓；
 * 绳子的积分/约束/命中仍是本仓自研 ✓。
 *
 * <h2>为什么前两版看起来都是"把绳子射出去" ✗（用户两次实测反馈 ✓）</h2>
 * <ol>
 *   <li>§1047：根部弹簧的目标点放在**准星方向上** ✗ ⇒ 等于把绳子朝前拽直 ✗；</li>
 *   <li>§1048：虽然加了 ±68° 扫动角 ✗，但 <b>{@code reset()} 仍然把 25 个点"沿准星一次性铺开 8.6 格"</b> ✗
 *       —— 生成那一瞬间绳子就已经是<b>一条伸直的鞭</b> ✗，之后根部只在 1.15 格里晃 ✗
 *       ⇒ 玩家看到的永远是"已经射出去的绳子" ✗。</li>
 * </ol>
 * <b>真正的做法</b>（照 BetterWhips ✓）：
 * <ol>
 *   <li><b>平时盘在手里</b> ✓：非抽击相位时，每个点都被拉向<b>手心的一卷螺旋</b>
 *       （{@link #coilTarget} ✓）⇒ 生成瞬间就是"一卷鞭子" ✓<b>而不是一条伸直的鞭</b> ✗；</li>
 *   <li><b>抽击时手抡一个大圆弧</b> ✓：{@link #swingHand} 把它的 {@code precisionArmPose} 口径
 *       （pitch 1.52 → −0.58、yaw 左右侧摆 ✓）折算成<b>手部锚点的运动</b> ✓
 *       ⇒ 手从侧上方抡到前下方 ✓，绳卷因惯性滞后 ✓、被一节节甩开 ✓ ⇒ <b>梢部甩出圆弧</b> ✓
 *       （它注释里的梢速 82~190 格/秒 ✓ 就是这么来的 ✓）；</li>
 *   <li><b>收势再盘回去</b> ✓。</li>
 * </ol>
 */
public final class WhipPhysics {

    public static final int POINTS = 25;

    private static final int SUBSTEPS = 8;
    private static final int SOLVER_ITERATIONS = 5;

    private static final double TICK_SECONDS = 0.05D;
    /** 段长（格 ✓）：24 × 0.36 ≈ 8.6 格总长 ✓ */
    private static final double SEGMENT_LENGTH = 0.36D;
    /** 重力（格/秒² ✓）：BetterWhips 用 −21.5 ✓ */
    private static final double GRAVITY = -21.5D;
    private static final double VELOCITY_RETENTION = 0.989D;
    private static final double MAX_STRETCH = 1.003D;

    /** 一次抽击（tick ✓）：照它的 {@code ATTACK_SECONDS = 0.5s} ✓ */
    public static final double ATTACK_TICKS = 10.0D;
    /** 扫动在进度上的起点／跨度 ✓：照它的 0.30／0.34 ✓ */
    private static final double SWEEP_START = 0.30D;
    private static final double SWEEP_SPAN = 0.34D;
    /** 抽击段加速指数 ✓：照它的 {@code pow(t,1.55)} ✓ */
    private static final double SWEEP_ACCEL_POW = 1.55D;

    // ---------------- §1049 盘绕（照它的 COIL_* ✓） ----------------
    /** 盘绕圈数 ✓（它 5 圈 ✓；我们绳长 8.64 格 ⇒ 8 圈时每圈 1.08 格、半径才 ≈0.17 格 ✓ 更贴合约束 ✓） */
    private static final double COIL_TURNS = 8.0D;
    /** 盘绕半径（格 ✓）与轴向长度（格 ✓）—— 让"螺旋长度 ≈ 绳长"⇒ 约束不与盘绕打架 ✓ */
    private static final double COIL_RADIUS = 0.19D;
    private static final double COIL_LENGTH = 0.10D;
    /** 盘绕拉力增益 ✓／每个子步的最大位移 ✓／速度阻尼 ✓：前两个照它 ✓ */
    private static final double COIL_PULL_GAIN = 0.17D;
    private static final double COIL_MAX_PULL_PER_SUBSTEP = 0.105D;
    private static final double COIL_VELOCITY_DAMPING = 0.50D;

    /**
     * §1050 手部锚点抡圆时的半径（格 ✓）—— 决定"手甩得有多开" ✓。
     * <p>§1049 用 0.95 时用户反馈「<b>绳子没有完全甩出去</b>」✗：手划的弧太短 ⇒
     * 8.6 格的鞭来不及整条甩开 ✗ ⇒ 调到 1.25 ✓。
     */
    private static final double SWING_HAND_RADIUS = 1.25D;
    /** 根部弹簧刚度 ✓ / 加速度上限 ✓（格/秒²）—— 手抡起来时根部要被"拽着走"✓ */
    private static final double ROOT_STIFFNESS = 320.0D;
    private static final double ROOT_MAX_ACCEL = 3200.0D;
    /** 单点速度上限（格/秒 ✓）：它实测梢速 82~190 ✓ */
    private static final double MAX_SPEED = 170.0D;

    private static final double COLLISION_RADIUS = 0.06D;
    private static final double MAX_DEPENETRATION = 0.25D;

    private final Vec3[] pos = new Vec3[POINTS];
    private final Vec3[] vel = new Vec3[POINTS];
    private final Vec3[] tickStart = new Vec3[POINTS];
    private boolean started;

    /**
     * §1049 <b>盘绕权重</b>（⚠ §1050 起<b>已停用</b> ✗ —— 抽击期间不再盘绕 ✓，
     * 保留此处是为了以后做"闲置 5 秒后慢慢盘起来"的花活时能直接用 ✓）。
     */
    public static double coilWeight(double progress, boolean slam) {
        if (slam) {
            return 0.0D;                                   // 砸地：不盘，直接往下抽 ✓
        }
        double p = Mth.clamp(progress, 0.0D, 1.0D);
        if (p <= SWEEP_START) {
            return 1.0D;                                   // 起手：整卷还在手里 ✓
        }
        if (p < SWEEP_START + 0.06D) {
            return 1.0D - (p - SWEEP_START) / 0.06D;       // 抽出瞬间松手 ✓
        }
        if (p < 0.78D) {
            return 0.0D;                                   // 甩击段：完全放开 ✓
        }
        double u = Mth.clamp((p - 0.78D) / 0.22D, 0.0D, 1.0D);
        return u * u;                                      // 收势：盘回去 ✓
    }

    /**
     * §1050 静止姿态：<b>一条完全伸展开的鞭，从手里往下、往后垂着</b> ✓。
     *
     * <p>⚠ §1049 我把它盘成了 8 圈螺旋 ✗ ⇒ 用户实测「<b>绳子没有完全甩出去</b>」✗ ——
     * 原因很清楚：抽击只有 3~4 tick（0.15~0.2 秒）✗，**根本来不及把 8.6 格的卷展开** ✗。
     * 真实鞭子握在手里本来就是**垂着／拖着**的 ✓；
     * BetterWhips 的 {@code COIL_*} 是"<b>闲置 100 tick（5 秒）之后</b>才慢慢盘起来"的花活 ✓
     * （{@code COIL_IDLE_DELAY_TICKS = 100}／{@code COIL_DURATION_TICKS = 36} ✓），
     * **不是**抽击的前置动作 ✗ —— 这一点我上一轮理解错了 ✓。
     */
    public void reset(Vec3 hand, Vec3 axis, Vec3 side) {
        Vec3 back = safeDir(axis).scale(-1.0D);
        for (int i = 0; i < POINTS; i++) {
            double t = i;
            Vec3 p = hand
                    .add(new Vec3(0.0D, -SEGMENT_LENGTH * t * 0.92D, 0.0D))
                    .add(back.scale(SEGMENT_LENGTH * t * 0.38D));
            pos[i] = p;
            tickStart[i] = p;
            vel[i] = Vec3.ZERO;
        }
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
     * @param hand     手部基点 ✓
     * @param aim      准星方向 ✓（既是盘绕轴 ✓，也是抡圆时的"前方" ✓）
     * @param right    水平右手侧 ✓（盘绕的参考轴之一 ✓）
     * @param progress 抽击进度 0..1 ✓
     * @param sign     ±1：这一鞭从左往右还是从右往左 ✓
     * @param slam     砸地相位 ✓
     */
    public void tick(Level level, Vec3 hand, Vec3 aim, Vec3 right, double progress, double sign, boolean slam) {
        Vec3 axis = safeDir(aim);
        Vec3 side = safeDir(right);
        if (!started) {
            reset(hand, axis, side);
        }

        Vec3 anchor = slam
                ? hand.add(new Vec3(axis.x, -0.85D, axis.z).normalize().scale(1.6D))
                : swingHand(hand, axis, side, progress, sign);

        double sub = TICK_SECONDS / SUBSTEPS;
        for (int s = 0; s < SUBSTEPS; s++) {
            for (int i = 0; i < POINTS; i++) {
                vel[i] = clampSpeed(vel[i].add(0.0D, GRAVITY * sub, 0.0D));
                pos[i] = pos[i].add(vel[i].scale(sub));
            }

            // ① 根部：弹簧朝"手部锚点"（这个锚点是被抡圆的那只手 ✓）
            Vec3 toTarget = anchor.subtract(pos[0]);
            Vec3 acc = toTarget.scale(ROOT_STIFFNESS);
            if (acc.length() > ROOT_MAX_ACCEL) {
                acc = acc.normalize().scale(ROOT_MAX_ACCEL);
            }
            vel[0] = clampSpeed(vel[0].add(acc.scale(sub)));
            pos[0] = pos[0].add(vel[0].scale(sub));


            Vec3[] before = new Vec3[POINTS];
            for (int i = 0; i < POINTS; i++) {
                before[i] = pos[i];
            }

            // ③ 距离约束：只拉不推 ✓
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

            // ④ 地面碰撞 ✓
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

            // ⑤ PBD：由位置差反推速度 ✓
            for (int i = 0; i < POINTS; i++) {
                vel[i] = clampSpeed(pos[i].subtract(before[i]).scale(1.0D / sub));
            }
        }

        for (int i = 0; i < POINTS; i++) {
            vel[i] = clampSpeed(vel[i].scale(VELOCITY_RETENTION));
        }
    }

    /**
     * §1049 <b>手部锚点随挥击抡圆</b> —— 把 BetterWhips 的 {@code precisionArmPose} 口径
     * （起手 pitch → 1.52、抽击段用 {@code pow(t,1.55)} 甩到 −0.58、yaw 带 {@code sign} 侧摆 ✓）
     * 折算成"手在空间里划的那道弧" ✓：侧上方 → 前下方横扫 ✓（半径 {@link #SWING_HAND_RADIUS} ✓）。
     */
    private static Vec3 swingHand(Vec3 hand, Vec3 aim, Vec3 right, double progress, double sign) {
        double p = Mth.clamp(progress, 0.0D, 1.0D);
        double s = sign >= 0.0D ? 1.0D : -1.0D;
        double pitch;
        double yaw;
        if (p < SWEEP_START) {
            double t = smoothstep(p / SWEEP_START);
            pitch = Mth.lerp(t, 0.30D, 1.52D);                  // 起手：把手抬起来 ✓
            yaw = Mth.lerp(t, 0.0D, -0.24D) * s;
        } else if (p < SWEEP_START + SWEEP_SPAN) {
            double t = Math.pow(Mth.clamp((p - SWEEP_START) / SWEEP_SPAN, 0.0D, 1.0D), SWEEP_ACCEL_POW);
            pitch = Mth.lerp(t, 1.52D, -0.58D);                 // 抽击：加速甩下去 ✓
            yaw = Mth.lerp(t, -0.24D, 0.26D) * s;               // 同时横扫到另一侧 ✓
        } else {
            double span = Math.max(1.0E-6D, 1.0D - SWEEP_START - SWEEP_SPAN);
            double t = smoothstep(Mth.clamp((p - SWEEP_START - SWEEP_SPAN) / span, 0.0D, 1.0D));
            pitch = Mth.lerp(t, -0.58D, 0.30D);                 // 收势回位 ✓
            yaw = Mth.lerp(t, 0.26D, 0.0D) * s;
        }
        double r = SWING_HAND_RADIUS;
        return hand
                .add(aim.scale(Math.cos(pitch) * Math.cos(yaw) * r))
                .add(new Vec3(0.0D, 1.0D, 0.0D).scale(Math.sin(pitch) * r))
                .add(right.scale(Math.sin(yaw) * r));
    }

    /** §1049 第 i 个点"盘在手里"时应该在的位置 ✓（⚠ §1050 起同样已停用 ✓ 见上 ✓） */
    private static Vec3 coilTarget(int i, Vec3 hand, Vec3 axis, Vec3 side) {
        double t = (double) i / (POINTS - 1);
        double ang = t * COIL_TURNS * Math.PI * 2.0D;
        double radius = COIL_RADIUS * (1.0D - 0.45D * t);

        Vec3 a = safeDir(axis);
        Vec3 u = side.subtract(a.scale(side.dot(a)));
        if (u.lengthSqr() < 1.0E-8D) {
            u = new Vec3(0.0D, 1.0D, 0.0D).subtract(a.scale(a.y));
        }
        if (u.lengthSqr() < 1.0E-8D) {
            u = new Vec3(1.0D, 0.0D, 0.0D);
        }
        u = u.normalize();
        Vec3 v = a.cross(u).normalize();

        return hand
                .add(u.scale(Math.cos(ang) * radius))
                .add(v.scale(Math.sin(ang) * radius))
                .add(a.scale(t * COIL_LENGTH * POINTS));
    }

    private static double smoothstep(double v) {
        double x = Mth.clamp(v, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    /** 第 i 段的扫掠是否穿过给定碰撞箱 ✓ */
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

    private static Vec3 clampSpeed(Vec3 v) {
        double len = v.length();
        if (len > MAX_SPEED) {
            return v.scale(MAX_SPEED / len);
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
