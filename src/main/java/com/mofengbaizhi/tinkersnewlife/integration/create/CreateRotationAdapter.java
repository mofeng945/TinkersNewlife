package com.mofengbaizhi.tinkersnewlife.integration.create;

import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyConverterContext;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyInputAdapter;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

/**
 * <b>Create 转速（RPM）→ FE 适配器</b>（§557，纯反射软依赖 ✓）。
 *
 * <h2>核到的真实类名 / 方法名</h2>
 * <pre>
 *   com.simibubi.create.content.kinetics.base.KineticBlockEntity#getSpeed() : float   ← RPM
 * </pre>
 * <p>⚠ <b>诚实项</b>：Create（或它的前置 {@code flywheel}）<b>不在本整合包的 mods 目录里</b> ✗
 * ⇒ 我<b>无法</b>像 Mekanism 那样从 jar 里 javap 核实这一条 ✗。这里写的就是任务书给的名字 ✓
 * 并且做了两件事来兜住"名字不对"的风险：
 * <ol>
 *   <li>反射用 {@code Class.forName(...)} + {@code getMethod("getSpeed")} ✓ 找不到就整路降级 ✓
 *       （不会 {@code NoClassDefFoundError}、也不会崩 ✗）；</li>
 *   <li>{@code getSpeed()} 的返回类型允许是 {@code float} / {@code double} / {@code int}
 *       （都按数值取 ✓）—— 万一某个版本改了返回类型，也还能正常读 ✓。</li>
 * </ol>
 * <p>⇒ 装上 Create 之后这条要么正好工作 ✓、要么在日志里留下一行"未接上（反射失败：…）" ✓
 * 不会静默做错事 ✗。
 *
 * <h2>汇率</h2>
 * 用户口径：{@code FE/t = (45 × RPM) / 64} ⇒ 1 RPM = 0.703125 FE/t ✓
 * （常量在 {@link EnergyUnits.Fe} ✓ 本类不写数字 ✗）。
 *
 * <h2>⚠ "输入"到底是谁给谁</h2>
 * Create 的传动轴<b>不会</b>把旋转"付给"我们 ✗ —— 转速是<b>读</b>出来的信息（旋转本身不会被扣掉 ✓
 * 这与 Create 自己的应力/转速模型一致：轴上的转速是所有连着的机器共享的一个量 ✓）。
 * ⇒ 本适配器每 tick <b>读一次相邻所有动力方块的转速并相加</b> ✓ 折成 FE/t ✓
 * 也就是说：<b>16 RPM 的轴贴着转化器就能持续产出 ≈11.25 FE/t</b> ✓（再受
 * {@code output_fe_per_tick} 与配置上限的约束 ✓）。
 * <p>⚠ 这是我的口径取舍 ✓：好处是"接了就有电、不消耗玩家的转速"（Create 里也确实没有"消耗转速"这个概念 ✓）；
 * 坏处是"一根飞轮轴可以同时喂很多台转化器" ✗。用户口径只说"Create 转速"是输入之一 ✓
 * 没说要不要按台数摊掉 ⇒ 我按"读转速"实现 ✓ 觉得太松就调小
 * {@code create_rotation_fe_per_rpm} 或直接关掉 {@code create_rotation_enabled} ✓。
 */
public final class CreateRotationAdapter implements EnergyInputAdapter {

    private CreateRotationAdapter() {
    }

    /** 单例（无状态 ✓） */
    public static final CreateRotationAdapter INSTANCE = new CreateRotationAdapter();

    private static final String C_KINETIC_BE = "com.simibubi.create.content.kinetics.base.KineticBlockEntity";

    private static volatile int state = 0;         // 0 未试 / 1 成功 / -1 失败
    private static String note = "尚未探测";
    private static Class<?> cKinetic;
    private static Method mGetSpeed;

    private static synchronized void resolve() {
        if (state != 0) return;
        try {
            cKinetic = Class.forName(C_KINETIC_BE);
            mGetSpeed = cKinetic.getMethod("getSpeed");        // 公开方法 ✓ 返回 float ✓
            state = 1;
            note = "已接上：" + C_KINETIC_BE + "#getSpeed()；1 RPM = " + trim(EnergyUnits.Fe.fePerRpm())
                    + " FE/t（读转速，不消耗转速）";
        } catch (Throwable t) {
            state = -1;
            note = "未接上（反射失败：" + t.getClass().getSimpleName() + " " + t.getMessage()
                    + "；Create 未安装也是这个结果 ✓）";
            cKinetic = null;
            mGetSpeed = null;
        }
    }

    @Override
    public boolean isAvailable() {
        resolve();
        return state == 1;
    }

    @Override
    public String probeNote() {
        resolve();
        return "Create(RPM)：" + note;
    }

    /**
     * 读相邻所有 Create 动力方块（{@code KineticBlockEntity}）的转速并求和，折成 FE/t。
     * <p>{@code simulate} 对这条路<b>没有区别</b> ✓ —— 我们不扣任何东西（见类注释"输入是谁给谁"✓）。
     */
    @Override
    public int drainFe(EnergyConverterContext ctx, int maxFe, boolean simulate) {
        if (maxFe <= 0 || ctx == null || !ctx.rpmEnabled()) return 0;
        resolve();
        if (state != 1 || mGetSpeed == null) return 0;
        Level level = ctx.level();
        if (level == null) return 0;

        double rpm = 0.0D;
        for (Direction d : EeStorages.NEIGHBOURS) {
            BlockPos at = ctx.pos().relative(d);
            BlockEntity be = level.getBlockEntity(at);
            if (be == null || !cKinetic.isInstance(be)) continue;
            rpm += speedOf(be);
        }
        if (!(rpm > 0.0D)) return 0;
        int fe = (int) Math.floor(EnergyUnits.Fe.rpmToFePerTick(rpm));
        return Math.max(0, Math.min(fe, maxFe));
    }

    /** 单个动力方块的转速（读失败 = 0 ✓ 只影响这一格 ✓） */
    private static double speedOf(BlockEntity be) {
        try {
            Object v = mGetSpeed.invoke(be);
            return v instanceof Number n ? Math.abs(n.doubleValue()) : 0.0D;   // 反向也照样发电（取绝对值 ✓）
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    private static String trim(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }
}
