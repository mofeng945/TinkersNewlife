package com.mofengbaizhi.tinkersnewlife.integration.ic2;

import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyConverterContext;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyInputAdapter;

/**
 * <b>工业时代 2（IC2）EU → FE 适配器</b>（§557）：<b>本整合包里整套跳过</b> ✗。
 *
 * <h2>为什么跳过（两条，都是硬事实）</h2>
 * <ol>
 *   <li><b>模组不在场</b>：{@code G:\tex\.minecraft\versions\1.20.1-Forge_47.4.22\mods\}
 *       <b>没有</b> IC2（industrialcraft / ic2）的 jar ✗（我逐个列过 mods 目录 ✓）
 *       ⇒ 没有任何 jar 可以让我"javap 核实类名" ⚠ —— 而任务书明确要求
 *       <b>不许凭记忆写类名</b> ✗ ⇒ 于是我<b>不写</b>那一串猜测的类名 ✗；</li>
 *   <li><b>就算写上也没用</b>：IC2 的能量方块<b>不是</b>靠 Forge capability 暴露的 ✗ ——
 *       它要求方块实体在 {@code EnergyTileLoadEvent}（入网）/ {@code EnergyTileUnloadEvent}（退网）
 *       里<b>主动登记</b>自己，然后由能源网络推 {@code injectEnergy(...)} ✓。
 *       要真接上就必须实现 {@code IEnergySink} 并处理这两个事件 —— 那是<b>编译期依赖</b>
 *       （接口方法做不到纯反射 ✗：IC2 的网络会直接 {@code instanceof IEnergySink} 后强转调用 ✓）
 *       ⇒ 与本模组"可选模组一律纯反射软依赖、绝不动 build.gradle"的铁律冲突 ✗（用户零容忍 ✗）。</li>
 * </ol>
 *
 * <h2>所以这个类是干什么的</h2>
 * 它是一个<b>诚实的占位</b>：{@link #isAvailable()} 恒 false ✓ {@link #drainFe} 恒 0 ✓
 * 转化器的启动日志里会明说"IC2(EU)：未接上（模组不在场，且需要入网事件 ⇒ 见 §557）" ✓
 * ⇒ 玩家一眼能看出"这条没做" ✓ 而不是"默默没反应" ✗。
 *
 * <p>⚠ 汇率常量 {@code 1 EU = 4 FE} <b>已经</b>按用户口径写进
 * {@code EnergyUnits.Fe.FE_PER_EU} ✓ —— 将来真要接 IC2，只需要 ① 把接口方法反射化到
 * 一个包装类，② 在 {@code build.gradle} 里加一次 compileOnly（或改写成事件注册）✓
 * <b>换算部分一行都不用改</b> ✓。
 */
public final class Ic2EnergyAdapter implements EnergyInputAdapter {

    private Ic2EnergyAdapter() {
    }

    /** 单例（无状态 ✓ 也永远不会真的工作 ✓） */
    public static final Ic2EnergyAdapter INSTANCE = new Ic2EnergyAdapter();

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public int drainFe(EnergyConverterContext ctx, int maxFe, boolean simulate) {
        return 0;
    }

    @Override
    public String probeNote() {
        return "IC2(EU)：未接上（本整合包没有 IC2 的 jar ⇒ 无法核实类名；且 IC2 需要"
                + " EnergyTileLoad/Unload 入网事件 ⇒ 纯反射做不到，见 §557 诚实项）";
    }
}
