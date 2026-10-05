package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * <b>鞭身物理</b>（§1047）—— 自研的 XPBD 绳 ✓（<b>没有抄 BetterWhips 的代码</b> ✗，
 * 只按它 tooltip／常量透露的"思路与口径"自己实现 ✓）。
 *
 * <h2>手感三要素（都来自 BetterWhips 的设计 ✓）</h2>
 * <ol>
 *   <li><b>只驱动根部</b> ✓：每 tick 只有第 0 点被<b>弹簧</b>拉向「手部 + 准星方向 × 伸展长度」✓，
 *       其余点只靠<b>距离约束</b>把运动一节一节传到梢部 ✓ ⇒ 自然甩出鞭花、梢速远大于手速 ✓
 *       （它原文：「only the root is driven toward the crosshair and its trajectory propagates
 *       progressively to the tip」✓）；</li>
 *   <li><b>只拉不推</b> ✓：距离约束只在超过段长时修正 ✓（绳子不能被推 ✓）；</li>
 *   <li><b>速度决定伤害</b> ✓：命中按接触点当前速度（格/秒）折算 ✓（它的 tooltip 口径：
 *       「每满 10 格/秒 造成一个系数的伤害」✓）。</li>
 * </ol>
 *
 * <h2>参数口径（照它反编译里读到的值 ✓）</h2>
 * 重力 {@code -21.5} 格/秒² ✓、速度保留 {@code 0.989} ✓、段长拉伸上限 {@code 1.003} ✓、
 * 求解 5 次迭代 ✓、总长 ≈ <b>8.6 格</b>（它皮革鞭写"约 8.5 格" ✓）。
 * 点数我们从它的 57 降到 <b>25</b> ✓（观感差别很小 ✓ 开销降到 1/2 以下 ✓），子步固定 8 ✓。
 *
 * <h2>服务端 / 客户端同一套</h2>
 * 输入相同（手部位置 ＋ 准星方向 ＋ 蓄力进度 ✓）⇒ 两边各自跑一份 ✓：
 * 服务端那份结算伤害 ✓，客户端那份画鞭身 ✓ ⇒ <b>不需要每 tick 同步 25 个点</b> ✓。
 */
public final class WhipPhysics {

    /** 绳上的点数（段数 = POINTS - 1 ✓） */
    public static final int POINTS = 25;

    private static final int SUBSTEPS = 8;
    private static final int SOLVER_ITERATIONS = 5;

    private static final double TICK_SECONDS = 0.05D;
    /** 段长（格 ✓）：24 × 0.36 ≈ 8.6 格总长 ✓ */
    private static final double SEGMENT_LENGTH = 0.36D;
    /** 重力（格/秒² ✓ 同它的 -21.5 ✓ —— 比现实大得多，鞭子才"脆" ✓） */
    private static final double GRAVITY = -21.5D;
    /** 每 tick 速度保留（空气阻力 ✓ 同它的 0.989 ✓） */
    private static final double VELOCITY_RETENTION = 0.989D;
    /** 段长允许的最大拉伸 ✓（同它的 1.003 ✓） */
    private static final double MAX_STRETCH = 1.003D;

    /** 根部弹簧刚度（1/秒² ✓）与加速度上限（格/秒² ✓）—— 决定"手甩得多快" ✓ */
    private static final double ROOT_STIFFNESS = 150.0D;
    private static final double ROOT_MAX_ACCEL = 240.0D;
    /** 根部朝准星方向的最大伸展（格 ✓）：手往前一送、鞭身被"带"出去 ✓ */
    private static final double ROOT_REACH = 1.15D;
    /** 单点速度上限（格/秒 ✓）防爆 ✓（它的梢速上限 82~190 ✓，我们更保守 ✓） */
    private static final double MAX_SPEED = 130.0D;

    /** 碰撞半径（格 ✓）与单次推出上限（格/子步 ✓） */
    private static final double COLLISION_RADIUS = 0.06D;
    private static final double MAX_DEPENETRATION = 0.25D;

    private final Vec3[] pos = new Vec3[POINTS];
    private final Vec3[] vel = new Vec3[POINTS];
    /** 本 tick 开始时的位置 ✓ —— 用于扫掠命中与"接触点速度" ✓ */
    private final Vec3[] tickStart = new Vec3[POINTS];
    private boolean started;

    /** 摆到指定位置（生成瞬间 ✓），避免留下"没初始化"的零点 ✗ */
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

    /** 记录本 tick 起点 ✓（在 {@link #tick} 之前调用 ✓） */
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
     * @param hand  手部位置（根部弹簧的目标基点 ✓）
     * @param aim   准星方向（单位向量 ✓）
     * @param drive 驱动强度 ✓：1 = 正常挥击 ✓；0 = 不驱动（自然垂落 ✓）；&gt;1 = 蓄力砸地（更狠 ✓）
     */
    public void tick(Level level, Vec3 hand, Vec3 aim, double drive) {
        if (!started) {
            reset(hand, aim);
        }
        Vec3 d = safeDir(aim);
        Vec3 rootTarget = hand.add(d.scale(ROOT_REACH * Mth.clamp(drive, 0.0D, 2.0D)));
        double sub = TICK_SECONDS / SUBSTEPS;

        for (int s = 0; s < SUBSTEPS; s++) {
            // ① 积分：重力 + 速度
            for (int i = 0; i < POINTS; i++) {
                Vec3 v = clampSpeed(vel[i].add(0.0D, GRAVITY * sub, 0.0D));
                vel[i] = v;
                pos[i] = pos[i].add(v.scale(sub));
            }

            // ② 根部：弹簧拉向「手 + 准星 × 伸展」✓（只有它被驱动 ✓）
            Vec3 toTarget = rootTarget.subtract(pos[0]);
            Vec3 acc = toTarget.scale(ROOT_STIFFNESS * Math.max(0.0D, drive));
            if (acc.length() > ROOT_MAX_ACCEL) {
                acc = acc.normalize().scale(ROOT_MAX_ACCEL);
            }
            vel[0] = clampSpeed(vel[0].add(acc.scale(sub)));
            pos[0] = pos[0].add(vel[0].scale(sub));

            // ③ 记录求解前的位置 ⇒ 求解后用位置差反推速度（PBD 标准做法 ✓ 保留动量 ✓）
            Vec3[] before = new Vec3[POINTS];
            for (int i = 0; i < POINTS; i++) {
                before[i] = pos[i];
            }

            // ④ 距离约束：只拉不推 ✓（绳子 ✓）
            for (int it = 0; it < SOLVER_ITERATIONS; it++) {
                for (int i = 1; i < POINTS; i++) {
                    Vec3 a = pos[i - 1];
                    Vec3 b = pos[i];
                    Vec3 delta = b.subtract(a);
                    double len = delta.length();
                    if (len < 1.0E-6D) {
                        continue;
                    }
                    if (len <= SEGMENT_LENGTH * MAX_STRETCH) {
                        continue;
                    }
                    double correction = (len - SEGMENT_LENGTH) / len;
                    pos[i] = b.subtract(delta.scale(correction));
                }
            }

            // ⑤ 地面碰撞：把点从方块里顶出来 ✓
            for (int i = 0; i < POINTS; i++) {
                Vec3 p = pos[i];
                BlockPos bp = BlockPos.containing(p.x, p.y - COLLISION_RADIUS, p.z);
                var shape = level.getBlockState(bp).getCollisionShape(level, bp);
                if (shape.isEmpty()) {
                    continue;
                }
                double top = bp.getY() + shape.max(Direction.Axis.Y);
                if (p.y < top + COLLISION_RADIUS) {
                    double push = Math.min(MAX_DEPENETRATION, top + COLLISION_RADIUS - p.y);
                    pos[i] = new Vec3(p.x, p.y + push, p.z);
                }
            }

            // ⑥ 速度 = (求解后 - 求解前) / 子步时长 ✓
            for (int i = 0; i < POINTS; i++) {
                vel[i] = clampSpeed(pos[i].subtract(before[i]).scale(1.0D / sub));
            }
        }

        // ⑦ tick 末：空气阻力 ✓
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

    /** 线段 vs AABB 的平板法判定 ✓（够用且便宜 ✓） */
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
