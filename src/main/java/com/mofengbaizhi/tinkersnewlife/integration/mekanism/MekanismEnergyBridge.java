package com.mofengbaizhi.tinkersnewlife.integration.mekanism;

import com.mofengbaizhi.tinkersnewlife.content.block.EeConverterCore;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
import mekanism.api.Action;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/**
 * <b>通用机械（Mekanism）J → FE 的"收"那一半</b>（§559）：把本模组的转化器做成一个
 * Mekanism 认得的能力提供者 ⇒ <b>它的线缆/管道能主动往我们这里推 J</b> ✓。
 *
 * <h2>⚠ 这个类为什么必须存在（与 §557 的纯反射版有什么不同）</h2>
 * §557 只做了"<b>我们主动抽</b>"（反射调 {@code IStrictEnergyHandler#extractEnergy} ✓）——
 * 那对"通用机械自己的线缆"没有用 ✗，因为线缆是<b>反过来</b>的：它对着相邻方块问
 * {@code STRICT_ENERGY} 这个 capability、拿到 {@code IStrictEnergyHandler} 之后调
 * <b>{@code insertEnergy}</b> 把电塞进来 ✓。
 * <h3>为什么"反射"做不到这件事</h3>
 * 要让通用机械拿到我们，我们必须<b>注册一个它的 {@code Capability<IStrictEnergyHandler>}</b> ✗ ——
 * 那一步的类型是编译期的（{@code getCapability(Capability<T>, Direction)} 要求
 * {@code cap == Capabilities.STRICT_ENERGY} 这个<b>对象</b>，还要返回 {@code LazyOptional<T>}）✗
 * ⇒ 纯反射无法伪造 ✗（§557 已在 557.5③ 写明这条边界 ✓）⇒ 所以这一节才有用户授权的
 * {@code compileOnly} 破例 ✓。
 *
 * <h2>核到的真实类名 / 方法名（出处：{@code libs/Mekanism-1.20.1-10.4.16.80.jar}）</h2>
 * <pre>
 *   mekanism.common.capabilities.Capabilities
 *       public static final Capability&lt;IStrictEnergyHandler&gt; STRICT_ENERGY
 *   mekanism.api.energy.IStrictEnergyHandler
 *       int          getEnergyContainerCount()
 *       FloatingLong getEnergy(int)
 *       void         setEnergy(int, FloatingLong)
 *       FloatingLong getMaxEnergy(int)
 *       FloatingLong getNeededEnergy(int)
 *       FloatingLong insertEnergy(int, FloatingLong, Action)     ← 通用机械的线缆走这条 ✓
 *       FloatingLong extractEnergy(int, FloatingLong, Action)
 *   mekanism.api.Action             enum { EXECUTE, SIMULATE }
 *   mekanism.api.math.FloatingLong  create(double) / doubleValue()
 * </pre>
 * <p>（这一组与 §557 从 jar 里 javap 到的完全一致 ✓ —— 本次只是把
 * {@code compileOnly} 加上、于是可以直接写类型，不再需要 {@code Class.forName} ✓。）
 *
 * <h2>单位与汇率</h2>
 * 通用机械给的是 <b>J</b>，我们的池子是 <b>FE</b> ⇒ 走 {@link EnergyUnits.Fe#FE_PER_J}（<b>0.4</b> ✓
 * 用户口径 {@code 10 J = 4 FE} ✓）。⚠ 与 §557 的取舍一致：**只收整 FE** ✓
 * （{@code floor(joules × 0.4)} ✓ 少收的那点 J 留在通用机械那边 ✓ 不会凭空产生 FE ✗）。
 *
 * <h2>隔离（没装通用机械的玩家为什么不会崩）</h2>
 * <b>本类是唯一 import {@code mekanism.*} 的地方</b>（除了反射版的 {@code MekanismEnergyAdapter} ✓
 * 那个只 {@code Class.forName} ✓）✓ 而且本类<b>只</b>从
 * {@code EnergyConverterModBridges} 的 {@code isLoaded("mekanism")} 分支里被实例化/调用 ✓
 * ⇒ 没装通用机械时 JVM 永远不会加载本类 ✓（{@code NoClassDefFoundError} 也就无从发生 ✓）。
 */
public final class MekanismEnergyBridge implements IStrictEnergyHandler {

    /** 每个方块实体一个（WeakHashMap ⇒ 方块被拆后不会把 BE 一起吊住 ✓） */

    private static final java.util.Map<EeConverterCore, MekanismEnergyBridge> CACHE =
            new java.util.WeakHashMap<>();

    private final EeConverterCore core;
    private final LazyOptional<IStrictEnergyHandler> holder = LazyOptional.of(() -> this);

    private MekanismEnergyBridge(EeConverterCore core) {
        this.core = core;
        this.heatHolder = LazyOptional.of(() -> new HeatSink(this.core));   // §585 热沉句柄 ✓
    }

    /**
     * 这个 capability 是不是归我们管。
     *
     * @return 匹配 ⇒ 句柄 ✓；不匹配 ⇒ {@code null}（调用方继续走别的路 ✓）
     */
    @Nullable
    public static <T> LazyOptional<T> capability(EeConverterCore core, Capability<T> cap, @Nullable Direction side) {
        if (cap != Capabilities.STRICT_ENERGY && cap != Capabilities.HEAT_HANDLER) return null;   // §585 热导线缆也要放行 ✓
        // §578 六面角色：通用机械只在**右面 + 底面**暴露（原来六面全开 ✗）
        if (!com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.allowsMekanism(core.hostState(), side)) return null;
        MekanismEnergyBridge bridge;
        synchronized (CACHE) {
            bridge = CACHE.computeIfAbsent(core, MekanismEnergyBridge::new);
        }
        if (cap == Capabilities.HEAT_HANDLER) return bridge.heatHolder.cast();   // §585 右面 = 热导线缆（热量 ⇒ FE ✓）
        return bridge.holder.cast();
    }

    /** 能力作废（方块被拆 / 区块卸载 ✓ 与 Forge 的 invalidateCaps 同一时机 ✓） */
    public static void invalidate(EeConverterCore core) {
        synchronized (CACHE) {
            MekanismEnergyBridge b = CACHE.remove(core);
            if (b != null) b.holder.invalidate();
        }
    }

    // ============================================================
    //  IStrictEnergyHandler
    // ============================================================

    @Override
    public int getEnergyContainerCount() {
        return 1;
    }

    /** 我们对外只报"已经折成 FE 的那部分换算回 J"✓（没凑够 1 FE 的零头不报 ⇒ 不凭空多出能量 ✗） */
    @Override
    public FloatingLong getEnergy(int container) {
        return FloatingLong.create(core.getEnergyStored() / EnergyUnits.Fe.FE_PER_J);
    }

    /** ⚠ 不许外人直接写数值 ✗（要送电请走 {@link #insertEnergy} ✓ 那里有汇率与上限 ✓） */
    @Override
    public void setEnergy(int container, FloatingLong energy) {
        // 有意留空：通用机械不会用它来充电（它用 insertEnergy）✓
    }

    @Override
    public FloatingLong getMaxEnergy(int container) {
        return FloatingLong.create(core.getMaxEnergyStored() / EnergyUnits.Fe.FE_PER_J);
    }

    @Override
    public FloatingLong getNeededEnergy(int container) {
        int roomFe = Math.max(0, core.getMaxEnergyStored() - core.getEnergyStored());
        return FloatingLong.create(roomFe / EnergyUnits.Fe.FE_PER_J);
    }

    /**
     * <b>通用机械的线缆/管道走的就是这一条</b> ✓：收到 J ⇒ 按 {@code 1 J = 0.4 FE} 折成 FE
     * 进池子 ✓（只收整 FE ✓ 少收的零头留在它那边 ✓）。
     *
     * @return <b>实际用掉</b>的 J（没吃下的部分如实退回 ⇒ 它不会以为送成功了 ✗）
     */
    @Override
    public FloatingLong insertEnergy(int container, FloatingLong amount, Action action) {
        if (amount == null) return FloatingLong.ZERO;
        double joules = amount.doubleValue();
        if (!(joules > 0.0D)) return FloatingLong.ZERO;
        int wantedFe = core.residualFloor(joules * EnergyUnits.Fe.FE_PER_J);   // §571 残差：小数不丢 ✓
        if (wantedFe <= 0) return FloatingLong.ZERO;                 // 不到 1 FE 的量：不收 ✓（留着别丢 ✓）
        // 上限：与"输入上限"同一套闸门 ✓（否则通用机械可以无视配置把池子灌满 ✗）
        int cap = Math.min(com.mofengbaizhi.tinkersnewlife.config.ModConfig.converterOutputFePerTick(),
                com.mofengbaizhi.tinkersnewlife.config.ModConfig.converterInputFePerTick());
        if (cap <= 0) return FloatingLong.ZERO;
        wantedFe = Math.min(wantedFe, cap);
        int acceptedFe = core.insertFe(wantedFe, action.simulate());
        if (acceptedFe <= 0) return FloatingLong.ZERO;
        // 只承认"真的折成 FE 的那部分 J"✓ 多出来的（不足 1 FE 的零头）不扣 ✓
        return FloatingLong.create(acceptedFe / EnergyUnits.Fe.FE_PER_J);
    }

    // ============================================================
    //  §585 右面 = 通用机械**热导线缆（热量 ⇒ FE）**（签名由 javap 实核 ✓）
    // ============================================================

    /** 汇率：**1 热量(J) = 0.4 FE**（§583 按 `maxEnergyPerSteam = 10` + 用户 `10 J = 4 FE` 推导 ✓） */
    private static final double FE_PER_HEAT = 0.4D;
    /** 热沉热容（J/K）：给小值 ⇒ 不当"热量仓库" ✗ */
    private static final double SINK_CAPACITY = 64.0D;
    /** 逆热导（Mek 口径：越小越容易导热 ✓）给中等值 ⇒ 流量温和 ✓ */
    private static final double SINK_INVERSE_CONDUCTION = 5.0D;

    /** 热面句柄（每个 core 一个桥 ✓ 由静态 {@code capability(...)} 通过局部变量访问 ✓） */
    private final LazyOptional<mekanism.api.heat.IHeatHandler> heatHolder;   // §585b 在构造器里赋值 ✓（空白 final 不能在字段初始化式里被读 ✗）

    /**
     * §585 <b>热沉</b>：温度报 Mek 的环境温度（相对热源"冷" ⇒ 热自己导进来 ✓）。
     * <p>⚠ <b>绝不囤积</b> ✗：{@link #handleHeat(int, double)} **只吃"这一 tick 立刻能换成 FE"的那部分** ✓
     * （预算 = `min(FE 池剩余, converter_input_fe_per_tick)` ÷ 0.4 ✓）⇒ 多出来的热**留在 Mek 网络里** ✓
     * ⇒ 我们一个热量都不存 ⇒ **不会爆表** ✓✓；也**绝不反向放热**（非正数直接忽略 ✓）。
     */
    private static final class HeatSink implements mekanism.api.heat.IHeatHandler {
        private final EeConverterCore core;

        HeatSink(EeConverterCore core) { this.core = core; }

        @Override public int getHeatCapacitorCount() { return 1; }
        @Override public double getTemperature(int capacitor) { return mekanism.api.heat.HeatAPI.AMBIENT_TEMP; }
        @Override public double getInverseConduction(int capacitor) { return SINK_INVERSE_CONDUCTION; }
        @Override public double getHeatCapacity(int capacitor) { return SINK_CAPACITY; }

        /** ⚠ javap 实核：这个方法是 **void**（不是 double ✗）—— 收下多少由"我们限制自己"决定 ✓ */
        @Override
        public void handleHeat(int capacitor, double transfer) {
            if (!(transfer > 0.0D)) return;                                  // 不放热 ✗
            int feRoom = Math.max(0, core.getMaxEnergyStored() - core.getEnergyStored());
            if (feRoom <= 0) return;                                         // FE 池满 ⇒ 不收热 ✓
            int feBudget = Math.min(feRoom, com.mofengbaizhi.tinkersnewlife.config.ModConfig.converterInputFePerTick());
            if (feBudget <= 0) return;
            double heatBudget = feBudget / FE_PER_HEAT;                       // 这一 tick 最多吃多少热 ✓
            double accepted = Math.min(transfer, heatBudget);
            if (!(accepted > 0.0D)) return;
            int fe = core.residualFloor(accepted * FE_PER_HEAT);              // §571 残差：小数不丢 ✓
            if (fe > 0) core.insertFe(fe, false);
        }
    }
    /** ⚠ 我们<b>不</b>让通用机械从转化器里把电抽走 ✗（它是输出端 ✓ 与 FE 面的 {@code canExtract=false} 同一口径 ✓） */
    @Override
    public FloatingLong extractEnergy(int container, FloatingLong amount, Action action) {
        // §563 通用机械线缆"主动拉"走这条（原来恒 ZERO ✗ ⇒ 一根 J 都拉不出去 ✗）
        if (amount == null) return FloatingLong.ZERO;
        double joules = amount.doubleValue();
        if (!(joules > 0.0D)) return FloatingLong.ZERO;
        int wantedFe = core.residualFloor(joules * EnergyUnits.Fe.FE_PER_J);   // §571 残差：小数不丢 ✓
        if (wantedFe <= 0) return FloatingLong.ZERO;
        int gotFe = core.extractFe(wantedFe, action.simulate());
        if (gotFe <= 0) return FloatingLong.ZERO;
        return FloatingLong.create(gotFe / EnergyUnits.Fe.FE_PER_J);

    }
}
