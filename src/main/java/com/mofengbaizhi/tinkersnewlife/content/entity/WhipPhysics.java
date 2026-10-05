package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>鞭身物理</b>（§1047／§1048）。
 *
 * <h2>⚠ 来源与许可（重要 ✓）</h2>
 * 「<b>抽击 = 横扫</b>」这套驱动口径**移植自 BetterWhips** ✓（仓库
 * {@code https://github.com/my2167592261-cell/Better-Whips} ✓，<b>MIT License</b> ✓，
 * Copyright (c) 2026 my2167592261-cell ✓ —— 完整许可原文见仓库根目录
 * {@code LICENSES/BetterWhips-MIT.txt} ✓）。
 * 具体参考它 {@code TrainerStylePrecisionGuide} 里的扫动口径 ✓：
 * 扫动半角 <b>68°</b>、进度区间 <b>0.30 起 / 0.34 跨度</b>、一次抽击 <b>0.5 秒（10 tick）</b>、
 * 绳身跟随有<b>逐段延迟</b>（总共 0.24 秒）✓；绳子的积分/约束仍是本仓自研 ✓。
 *
 * <h2>为什么必须"扫"而不是"射"（用户实测反馈 ✓）</h2>
 * 上一版我把根部**朝准星方向弹出去** ✗ ⇒ 看起来是把绳子当箭射出去 ✗（用户原话：
 * 「<b>你现在鞭子做出来的效果是把绳子朝准星发射出去</b>」✓）。
 * 正确做法是：<b>驱动方向本身在准星周围横扫</b> ✓ —— 起手甩到一侧、抽击时快速扫到另一侧 ✓
 * ⇒ 梢部被甩出一个巨大的圆弧 ✓，速度自然飙到 100+ 格/秒 ✓ ⇒ 这才是鞭子 ✓
 * （它的注释也印证：梢速上限约 82~190 格/秒 ✓）。
 *
 * <h2>实现</h2>
 * <ol>
 *   <li><b>扫动方向</b>：{@link #sweepAngle} 把进度映射成 ±68° 的扫动角 ✓
 *       （起手段不动 ✓、抽击段用 {@code pow(u, 1.55)} 加速 ✓ 收势段回位 ✓ —— 曲线照它 ✓）；</li>
 *   <li><b>只驱动根部</b> ✓：根部弹簧的目标 = 「手 ＋ <b>扫过的方向</b> × 伸展」✓，
 *       其余点靠距离约束把运动一节节传到梢部 ✓；</li>
 *   <li><b>只拉不推</b> ✓、PBD 反推速度 ✓、地面碰撞 ✓、子步 8 ✓、求解 5 次 ✓；</li>
 *   <li><b>服务端/客户端同一套</b> ✓（输入相同 ✓）⇒ 服务端结算伤害 ✓、客户端画鞭身 ✓。</li>
 * </ol>
 */
public final class WhipPhysics {

    /** 绳上的点数（段数 = POINTS - 1 ✓） */
    public static final int POINTS = 25;

    private static final int SUBSTEPS = 8;
    private static final int SOLVER_ITERATIONS = 5;

    private static final double TICK_SECONDS = 0.05D;
    /** 段长（格 ✓）：24 × 0.36 ≈ 8.6 格总长 ✓（BetterWhips 皮革鞭约 8.5 格 ✓） */
    private static final double SEGMENT_LENGTH = 0.36D;
    /** 重力（格/秒² ✓）：BetterWhips 用 −21.5 ✓（比现实大得多，鞭子才"脆" ✓） */
    private static final double GRAVITY = -21.5D;
    /** 速度保留（空气阻力 ✓）：BetterWhips 用 0.989 ✓ */
    private static final double VELOCITY_RETENTION = 0.989D;
    /** 段长最大拉伸 ✓：BetterWhips 用 1.003 ✓ */
    private static final double MAX_STRETCH = 1.003D;

    /**
     * §1048 一次抽击的总时长（tick ✓）：照 BetterWhips 的 {@code ATTACK_SECONDS = 0.50} ✓ = 10 tick ✓。
     * <p>它的 {@code LEFT_DAMAGE_WINDOW_TICKS} 也是 10 ✓（伤害窗口 = 整个抽击 ✓）。
     */
    public static final double ATTACK_TICKS = 10.0D;
    /**
     * §1048 <b>扫动半角</b>（弧度 ✓）：照它的 {@code CROSSHAIR_SWEEP_HALF_ANGLE = 68°} ✓
     * ⇒ 一次抽击横扫 <b>136°</b> ✓ —— 这就是"鞭子扫过去"的关键 ✓（不是朝准星射 ✗）。
     */
    private static final double SWEEP_HALF_ANGLE = Math.toRadians(68.0D);
    /** §1048 扫动在进度上的起点 ✓ / 跨度 ✓：照它的 0.30 与 0.34 ✓ */
    private static final double SWEEP_START = 0.30D;
    private static final double SWEEP_SPAN = 0.34D;
    /** §1048 抽击段的加速指数 ✓：照它的 {@code pow(t, 1.55)} ✓（"抽"出去那一下很脆 ✓） */
    private static final double SWEEP_ACCEL_POW = 1.55D;

    /** 根部弹簧刚度 ✓ / 加速度上限 ✓（格/秒²）—— 决定"甩得多凶" ✓ */
    private static final double ROOT_STIFFNESS = 190.0D;
    private static final double ROOT_MAX_ACCEL = 1900.0D;
    /** 根部朝扫动方向的最大伸展（格 ✓） */
    private static final double ROOT_REACH = 1.15D;
    /** 单点速度上限（格/秒 ✓）：它实测梢速能到 82~190 ✓，这里给 160 留余量 ✓ */
    private static final double MAX_SPEED = 160.0D;

    private static final double COLLISION_RADIUS = 0.06D;
    private static final double MAX_DEPENETRATION = 0.25D;

    private final Vec3[] pos = new Vec3[POINTS];
    private final Vec3[] vel = new Vec3[POINTS];
    private final Vec3[] tickStart = new Vec3[POINTS];
    private boolean started;

    /**
     * §1048 <b>进度 ⇒ 扫动角</b>（弧度 ✓，带左右符号 ✓）—— 照 BetterWhips 的曲线口径 ✓。
     *
     * @param progress 0..1（一次抽击的进度 ✓ = 已过 tick / {@link #ATTACK_TICKS} ✓）
     * @param sign     +1 / −1 ⇒ 这一鞭从左往右扫还是从右往左扫 ✓（连续两次会交替 ✓）
     * @return 从 −68° 扫到 +68° 的角度 ✓（乘以 sign 决定方向 ✓）
     */
    public static double sweepAngle(double progress, double sign) {
        double p = Mth.clamp(progress, 0.0D, 1.0D);
        double t;
        if (p <= SWEEP_START) {
            t = 0.0D;                                                   // 起手：鞭子先甩到一侧 ✓
        } else if (p < SWEEP_START + SWEEP_SPAN) {
            double u = (p - SWEEP_START) / SWEEP_SPAN;                   // 0..1
            t = Math.pow(Mth.clamp(u, 0.0D, 1.0D), SWEEP_ACCEL_POW);     // 加速抽出 ✓
        } else {
            double span = Math.max(1.0E-6D, 1.0D - SWEEP_START - SWEEP_SPAN);
            double u = Mth.clamp((p - SWEEP_START - SWEEP_SPAN) / span, 0.0D, 1.0D);
            t = 1.0D - Math.pow(u, 1.6D);                                // 收势回位 ✓
        }
        return (2.0D * t - 1.0D) * SWEEP_HALF_ANGLE * (sign >= 0.0D ? 1.0D : -1.0D);
    }

    /** 摆到指定位置（生成瞬间 ✓） */
    public void reset(Vec3 root, Vec3 dir) {
        Vec3 d = safeDir(dir);
        for (int i = 0; i < POINTS; i++) {
            Vec3 p = root.add(d.scale(SEGMENT_LENGTH * i));
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

    /** 第 i 点当前速度（格/秒 ✓）—— 伤害按它折算 ✓ */
    public double speed(int i) {
        if (!started) {
            return 0.0D;
        }
        return pos[i].distanceTo(tickStart[i]) / TICK_SECONDS;
    }

    /**
     * 推进一 tick ✓。
     *
     * @param hand     手部基点（根部弹簧的基点 ✓ —— 通常取「眼睛高度 − 0.58 ＋ 右手侧 0.34」✓ 同它 ✓）
     * @param aim      准星方向（单位向量 ✓）
     * @param right    准星的"右手侧"水平单位向量 ✓（横扫的平面就是它和 aim 张成的平面 ✓）
     * @param progress 抽击进度 0..1 ✓（{@link #sweepAngle} 用它 ✓）
     * @param sign     扫动方向 ±1 ✓
     * @param slam     true = 砸地相位 ✓（不用横扫，改为往斜下方狠抽 ✓）
     */
    public void tick(Level level, Vec3 hand, Vec3 aim, Vec3 right, double progress, double sign, boolean slam) {
        if (!started) {
            reset(hand, aim);
        }
        Vec3 base = safeDir(aim);
        Vec3 side = safeDir(right);
        // §1048 关键：驱动方向 = 准星方向绕"竖直轴 + 右手侧"扫动后的方向 ✓
        double angle = slam ? 0.0D : sweepAngle(progress, sign);
        Vec3 driveDir;
        if (slam) {
            driveDir = new Vec3(base.x, -0.85D, base.z).normalize();      // 砸地：往斜下方抽 ✓
        } else {
            // 用右手侧向量做绕竖轴的旋转：dir = base·cos(a) + side·sin(a) ✓（横扫 ✓）
            driveDir = base.scale(Math.cos(angle)).add(side.scale(Math.sin(angle))).normalize();
        }
        Vec3 rootTarget = hand.add(driveDir.scale(ROOT_REACH * (slam ? 1.6D : 1.0D)));

        double sub = TICK_SECONDS / SUBSTEPS;
        for (int s = 0; s < SUBSTEPS; s++) {
            for (int i = 0; i < POINTS; i++) {
                vel[i] = clampSpeed(vel[i].add(0.0D, GRAVITY * sub, 0.0D));
                pos[i] = pos[i].add(vel[i].scale(sub));
            }

            Vec3 toTarget = rootTarget.subtract(pos[0]);
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

            for (int i = 0; i < POINTS; i++) {
                vel[i] = clampSpeed(pos[i].subtract(before[i]).scale(1.0D / sub));
            }
        }

        for (int i = 0; i < POINTS; i++) {
            vel[i] = clampSpeed(vel[i].scale(VELOCITY_RETENTION));
        }
    }

    /** 第 i 段的"扫掠"是否穿过给定碰撞箱 ✓（上一 tick 位置 → 当前位置 ＋ 本段 ✓） */
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

    /** 线段 vs AABB 的平板法判定 ✓ */
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
