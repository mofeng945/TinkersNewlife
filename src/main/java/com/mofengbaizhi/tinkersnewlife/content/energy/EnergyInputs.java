package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;

import java.util.List;

/**
 * <b>「别家模组能量 → FE」四路适配器的登记处</b>（§557）。
 *
 * <h2>四路（用户口径）</h2>
 * <table border="1">
 *   <caption>适配器一览</caption>
 *   <tr><th>路</th><th>类</th><th>本轮状态</th></tr>
 *   <tr><td>通用机械 Mekanism（J）</td>
 *       <td>{@code integration.mekanism.MekanismEnergyAdapter}</td>
 *       <td><b>已接上</b>（类名逐个 javap 核实 ✓ 模组在本包里 ✓）</td></tr>
 *   <tr><td>Create 转速（RPM）</td>
 *       <td>{@code integration.create.CreateRotationAdapter}</td>
 *       <td>结构写好 ✓ <b>未实机验证</b>（Create 不在本包里 ✗ 类名未经 javap ⚠）</td></tr>
 *   <tr><td>工业时代 IC2（EU）</td>
 *       <td>{@code integration.ic2.Ic2EnergyAdapter}</td>
 *       <td><b>整套跳过</b> ✗（模组不在场 + 需要入网事件 ⇒ 纯反射做不到 ✓ 见该类注释）</td></tr>
 *   <tr><td>应用能源 AE2（AE）</td>
 *       <td>{@code integration.ae2.Ae2EnergyAdapter}</td>
 *       <td>只做了"方块实体直接暴露 {@code IAEPowerStorage}"这一层 ✓ 常态读不到 ✓
 *           <b>未接上</b>（模组不在场 + 能量挂在网格节点上 ✓ 见该类注释）</td></tr>
 * </table>
 *
 * <p>⚠ <b>RF / Tesla / μI / FF 不在这里</b> ✗ —— 它们是 FE 的别名（1:1 ✓ 用户已确认 ✓）
 * ⇒ 走标准 {@code ForgeCapabilities.ENERGY} 那一路 ✓（就是"相邻方块通过 Forge Energy 给的能量"✓）。
 *
 * <h2>为什么要有 probeAll()</h2>
 * 四条路里三条"可能悄悄不工作" ✗ ⇒ 启动时把它们各自的结论打成<b>一行</b> INFO ✓
 * 让日志自己说清"哪条接上了、哪条为什么没有" ✓（比玩家游戏里发现"AE 不出电"再来问要省事得多 ✓）。
 */
public final class EnergyInputs {

    private EnergyInputs() {
    }

    /**
     * 四路适配器（顺序 = 日志顺序 ✓ 与用户给的清单一致 ✓）。
     * <p>⚠ 刻意声明在 {@link #probeAll()} <b>之前</b>（Java 的静态初始化按源码顺序走）：
     * 这个字段只是"摸一下四家的 INSTANCE 单例" ✓ 而四家适配器的真正反射解析都是<b>惰性</b>的
     * （第一次被调用时才做 ✓）⇒ 这里既不会提前触发 {@code Class.forName}，也不可能读到 null ✓。
     */
    private static final List<EnergyInputAdapter> ADAPTERS = List.of(
            com.mofengbaizhi.tinkersnewlife.integration.mekanism.MekanismEnergyAdapter.INSTANCE,
            com.mofengbaizhi.tinkersnewlife.integration.create.CreateRotationAdapter.INSTANCE,
            com.mofengbaizhi.tinkersnewlife.integration.ic2.Ic2EnergyAdapter.INSTANCE,
            com.mofengbaizhi.tinkersnewlife.integration.ae2.Ae2EnergyAdapter.INSTANCE
    );

    /** 只打一次日志 ✓（转化器每 tick 都会问适配器，绝不能每 tick 打日志 ✗） */
    private static volatile boolean probed = false;

    /** 全部适配器（只读 ✓） */
    public static List<EnergyInputAdapter> all() {
        return ADAPTERS;
    }

    /**
     * 逐个探测并<b>一次性</b>记下结论（启动时调 ✓ 见 {@code TinkersNewlife} 构造）。
     * <p>全程 try/catch ⇒ 任何一家把日志搞崩都不行 ✗。
     */
    public static void probeAll() {
        if (probed) return;
        probed = true;
        try {
            StringBuilder sb = new StringBuilder("[§557] 万用能量转化器的外部输入已探测：");
            for (EnergyInputAdapter a : ADAPTERS) {
                sb.append('\n').append("    - ").append(a.probeNote());
            }
            TinkersNewlife.LOGGER.info(sb.toString());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[§557] 外部输入探测日志打印失败（不影响功能）: {}", t.toString());
        }
    }
}
