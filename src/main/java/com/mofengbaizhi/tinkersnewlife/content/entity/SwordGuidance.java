package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.world.phys.Vec3;

/**
 * 飞剑制导 —— 单文件完整实现（由用户提供的 Bukkit 版算法 1:1 移植到 MC 的 {@link Vec3}）。
 *
 * <p>单位说明（MC 每 tick 调用一次）：
 * <ul>
 *   <li>位置 = 格</li>
 *   <li>速度 = 格/tick</li>
 *   <li>加速度 = 格/tick²（每 tick 直接加到速度上，不乘 DT）</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   SwordGuidance g = new SwordGuidance(SwordGuidance.Config.sword());
 *   // 每 tick：
 *   boolean hit = g.hitTest(pos, targetPos);       // 线段扫掠判定（内部记录上一 tick 位置）
 *   Vec3 v = g.guide(pos, vel, targetPos);         // 得到本 tick 的速度
 *   if (hit) g.reset();                            // 命中/换目标后重置状态
 * </pre>
 *
 * <p>三种制导律：
 * <ol>
 *   <li><b>STRAIGHT 直线模式</b>：与目标方向夹角进入死区（带滞后）→ 沿当前方向加速到 vMax，纯冲刺；</li>
 *   <li><b>CHORD 弦长制导</b>：由 "以横向加速度 aLat 画的那段圆弧恰好落点在目标" 反解理想速度
 *       {@code v = sqrt(aLat·L / (2·sinθ))}，并按预估过冲量决定刹车力度 → 这就是"画扇然后冲向目标"；</li>
 *   <li><b>ENDGAME 末端游戏</b>：近距且夹角不大时用 {@code v = sqrt(aLat·L / θ)} 收束，贴脸不再绕圈。</li>
 * </ol>
 * 转向统一走 {@code ω_max = aLat / v}（横向加速度上限决定的角速度上限），
 * 并保持旋转轴符号连续，因此轨迹是圆弧而不是折角，也不会左右抖动。
 */
public final class SwordGuidance {

    // ============================================================
    //  对外字段
    // ============================================================
    private final Config cfg;
    private final State state = new State();
    private final Predictor predictor;
    private final HitDetector hitDetector = new HitDetector();

    // ============================================================
    //  构造
    // ============================================================
    public SwordGuidance(Config cfg) {
        this.cfg = cfg;
        this.predictor = new Predictor(cfg);
    }

    public SwordGuidance() {
        this(Config.sword());
    }

    public Config config() { return cfg; }

    public Mode mode() { return state.mode; }

    public void reset() {
        state.reset();
        predictor.reset();
        hitDetector.reset();
    }

    // ---- 状态存取（供实体做 NBT 持久化，避免读档后转向轴翻转） ----
    public Vec3 getLastAxis() { return state.lastAxis; }
    public void setLastAxis(Vec3 axis) { state.lastAxis = axis == null ? Vec3.ZERO : axis; }
    public boolean isInStraightMode() { return state.inStraightMode; }
    public void setInStraightMode(boolean value) { state.inStraightMode = value; }
    public void setMode(Mode mode) { state.mode = mode; }

    // ============================================================
    //  命中检测
    // ============================================================
    public boolean hitTest(Vec3 currentPos, Vec3 targetPos) {
        return hitDetector.test(currentPos, targetPos, cfg.hitRadius);
    }

    /** 带半径重载：实体可传入按目标体型放大的半径（大体积生物不易漏判） */
    public boolean hitTest(Vec3 currentPos, Vec3 targetPos, double radius) {
        return hitDetector.test(currentPos, targetPos, radius);
    }

    // ============================================================
    //  主导引
    // ============================================================
    public Vec3 guide(Vec3 pos, Vec3 vel, Vec3 targetPos) {

        double s = vel.length();

        // ---- 预测拦截点 ----
        Vec3 aim = predictor.predict(pos, targetPos, s);

        // ---- 零速兜底 ----
        if (s < 1.0E-6) {
            Vec3 toT = aim.subtract(pos);
            if (toT.lengthSqr() < 1.0E-8) return vel;
            state.mode = Mode.BOOST;
            state.lastAxis = Vec3.ZERO;
            state.inStraightMode = false;
            return toT.normalize().scale(cfg.vMin);
        }

        Vec3 d = vel.scale(1.0 / s);
        Vec3 toTarget = aim.subtract(pos);
        double l = toTarget.length();
        if (l < 1.0E-6) return vel;
        Vec3 toTargetDir = toTarget.scale(1.0 / l);

        double cosT = Math.max(-1.0, Math.min(1.0, d.dot(toTargetDir)));
        double theta = Math.acos(cosT);

        // ---- 直线模式（滞后：进容易出困难）----
        if (state.inStraightMode) {
            if (theta > cfg.deadZoneExit()) {
                state.inStraightMode = false;
            } else {
                state.mode = Mode.STRAIGHT;
                keepAxisProjection(d);
                return d.scale(Math.min(cfg.vMax, s + cfg.aAccel));
            }
        }
        if (theta < cfg.deadZone) {
            state.inStraightMode = true;
            state.mode = Mode.STRAIGHT;
            keepAxisProjection(d);
            return d.scale(Math.min(cfg.vMax, s + cfg.aAccel));
        }

        // ---- 末端游戏 ----
        if (l < cfg.endgameL && theta < cfg.endgameAngle) {
            state.mode = Mode.ENDGAME;
            return endgameHoming(d, s, l, theta, toTargetDir);
        }

        // ---- 弦长公式 ----
        state.mode = Mode.CHORD;
        return chordGuidance(d, s, l, theta, toTargetDir);
    }

    // ============================================================
    //  弦长制导
    // ============================================================
    private Vec3 chordGuidance(Vec3 d, double s, double l,
                               double theta, Vec3 toTargetDir) {

        double sinT = Math.sin(theta);
        double vIdeal = (sinT < 1.0E-4)
                ? cfg.vMax
                : Math.sqrt(cfg.aLat * l / (2.0 * sinT));
        vIdeal = clamp(vIdeal, cfg.vMin, cfg.vMax);

        double overEst = 0.5 * l * Math.tan(theta * 0.5);
        double aBrakeNow = cfg.aBrake;
        if (overEst > cfg.overUrgent) {
            double u = Math.min(1.0,
                    (overEst - cfg.overUrgent) / (cfg.overMax - cfg.overUrgent));
            aBrakeNow = cfg.aBrake + (cfg.aBrakeMax - cfg.aBrake) * u * u;
        }
        if (overEst > cfg.overMax) {
            vIdeal = Math.max(cfg.vMin, vIdeal * Math.sqrt(cfg.overMax / overEst));
        }

        double sNew = (s > vIdeal)
                ? Math.max(vIdeal, s - aBrakeNow)
                : Math.min(vIdeal, s + cfg.aAccel);
        sNew = Math.max(sNew, cfg.vMin);

        return turn(d, sNew, theta, toTargetDir);
    }

    // ============================================================
    //  末端游戏
    // ============================================================
    private Vec3 endgameHoming(Vec3 d, double s, double l,
                               double theta, Vec3 toTargetDir) {
        double vMaxTurn = (theta < 0.01)
                ? cfg.vMax
                : Math.sqrt(cfg.aLat * l / theta);
        double vT = (vMaxTurn >= s)
                ? Math.min(cfg.vMax, s + cfg.aAccel)
                : vMaxTurn;
        double sNew = (s > vT)
                ? Math.max(vT, s - cfg.aBrake)
                : Math.min(vT, s + cfg.aAccel);
        sNew = Math.max(sNew, cfg.vMin);

        return turn(d, sNew, theta, toTargetDir);
    }

    // ============================================================
    //  统一转向
    // ============================================================
    private Vec3 turn(Vec3 d, double sNew, double theta, Vec3 toTargetDir) {
        double omegaMax = cfg.aLat / sNew;
        double dTheta = Math.min(omegaMax, theta);
        Vec3 axis = chooseAxis(d, toTargetDir);
        return rotateAroundAxis(d, axis, dTheta).scale(sNew);
    }

    private Vec3 chooseAxis(Vec3 d, Vec3 toTargetDir) {
        Vec3 cross = d.cross(toTargetDir);
        if (cross.lengthSqr() > 1.0E-8) {
            Vec3 axis = cross.normalize();
            if (state.lastAxis.lengthSqr() > 1.0E-6
                    && axis.dot(state.lastAxis) < 0) {
                axis = axis.scale(-1.0);
            }
            state.lastAxis = axis;
            return axis;
        }
        // 完全共线（含掉头 180°）：沿用上一次的转轴（投影到与 d 垂直的平面），保证不左右乱翻
        if (state.lastAxis.lengthSqr() > 1.0E-6) {
            Vec3 proj = state.lastAxis.subtract(d.scale(d.dot(state.lastAxis)));
            if (proj.lengthSqr() > 1.0E-8) {
                Vec3 axis = proj.normalize();
                state.lastAxis = axis;
                return axis;
            }
        }
        Vec3 axis = arbitraryPerpendicular(d);
        state.lastAxis = axis;
        return axis;
    }

    private void keepAxisProjection(Vec3 d) {
        if (state.lastAxis.lengthSqr() > 1.0E-6) {
            Vec3 proj = state.lastAxis.subtract(d.scale(d.dot(state.lastAxis)));
            if (proj.lengthSqr() > 1.0E-8) {
                state.lastAxis = proj.normalize();
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ============================================================
    //  ██ 内部类 ██
    // ============================================================

    /** 制导模式 */
    public enum Mode {
        IDLE, BOOST, STRAIGHT, CHORD, ENDGAME
    }

    /** 参数（默认值即用户算法中的 sword() 预设） */
    public static class Config {

        // ---- 速度（格/tick）----
        public double vMax = 1.20;
        public double vMin = 0.35;

        // ---- 加速度（格/tick²）----
        public double aAccel = 0.08;
        public double aBrake = 0.40;
        public double aBrakeMax = 1.20;
        public double aLat = 0.25;

        // ---- 角度 ----
        public double deadZone = 0.01;

        // ---- 过冲约束（格）----
        public double overMax = 12.0;
        public double overUrgent = 4.0;

        // ---- 末端游戏 ----
        public double endgameL = 5.0;
        public double endgameAngle = Math.PI / 4.0;

        // ---- 目标预测 ----
        public int predictIter = 3;
        public double predictMaxTicks = 20.0;
        public double predictScale = 1.4;
        public double predictSmoothNew = 0.25;

        // ---- 命中 ----
        public double hitRadius = 0.5;

        // ---- 派生 ----
        public double deadZoneExit() { return deadZone * 5; }

        // ---- 预设 ----
        public static Config sword() { return new Config(); }

        public static Config missile() {
            Config c = new Config();
            c.aLat = 0.5; c.vMax = 1.0; c.deadZone = 0.02;
            return c;
        }

        public static Config elegant() {
            Config c = new Config();
            c.aLat = 0.18; c.vMax = 1.4; c.predictScale = 1.8;
            return c;
        }
    }

    /** 状态（可序列化到 NBT） */
    public static class State {
        public Vec3 lastAxis = Vec3.ZERO;
        public boolean inStraightMode = false;
        public Mode mode = Mode.IDLE;

        public void reset() {
            lastAxis = Vec3.ZERO;
            inStraightMode = false;
            mode = Mode.IDLE;
        }
    }

    /** 目标预测：用平滑后的目标速度外推拦截点（迭代 3 次收敛） */
    private static final class Predictor {
        private final Config cfg;
        private Vec3 lastTargetPos = null;
        private Vec3 smoothedVel = Vec3.ZERO;

        Predictor(Config cfg) { this.cfg = cfg; }

        void reset() {
            lastTargetPos = null;
            smoothedVel = Vec3.ZERO;
        }

        Vec3 predict(Vec3 pos, Vec3 targetPos, double currentSpeed) {
            if (lastTargetPos != null) {
                Vec3 raw = targetPos.subtract(lastTargetPos);
                smoothedVel = smoothedVel.scale(1 - cfg.predictSmoothNew)
                        .add(raw.scale(cfg.predictSmoothNew));
            }
            lastTargetPos = targetPos;

            Vec3 aim = targetPos;
            double sGuess = Math.max(currentSpeed, cfg.vMin);
            for (int i = 0; i < cfg.predictIter; i++) {
                double d = aim.distanceTo(pos);
                if (d < 1.0E-6) break;
                double tGo = Math.min(cfg.predictMaxTicks, d / sGuess * cfg.predictScale);
                aim = targetPos.add(smoothedVel.scale(tGo));
            }
            return aim;
        }
    }

    /** 命中检测：上一 tick 位置 → 当前位置 的线段到目标的最近距离 < 半径 */
    private static final class HitDetector {
        private Vec3 lastPos = null;

        void reset() { lastPos = null; }

        boolean test(Vec3 currentPos, Vec3 target, double radius) {
            if (lastPos == null) {
                lastPos = currentPos;
                return false;
            }
            Vec3 ab = currentPos.subtract(lastPos);
            double abLenSq = ab.lengthSqr();

            boolean hit;
            if (abLenSq < 1.0E-10) {
                hit = currentPos.distanceTo(target) < radius;
            } else {
                double t = -lastPos.subtract(target).dot(ab) / abLenSq;
                t = Math.max(0.0, Math.min(1.0, t));
                Vec3 closest = lastPos.add(ab.scale(t));
                hit = closest.distanceTo(target) < radius;
            }
            lastPos = currentPos;
            return hit;
        }
    }

    /** 向量工具（罗德里格旋转公式 / 任意垂直向量） */
    static Vec3 rotateAroundAxis(Vec3 v, Vec3 axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double d = v.dot(axis);
        return axis.scale(d * (1 - c))
                .add(v.scale(c))
                .add(axis.cross(v).scale(s));
    }

    static Vec3 arbitraryPerpendicular(Vec3 d) {
        Vec3 ref = Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p = d.cross(ref);
        if (p.lengthSqr() < 1.0E-8) {
            p = d.cross(new Vec3(0, 0, 1));
        }
        return p.normalize();
    }
}
