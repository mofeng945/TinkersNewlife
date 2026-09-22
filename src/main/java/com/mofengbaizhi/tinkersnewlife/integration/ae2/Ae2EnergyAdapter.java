package com.mofengbaizhi.tinkersnewlife.integration.ae2;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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
 * <b>应用能源 2（AE2）AE → FE 适配器</b>（§557，纯反射软依赖 ✓，<b>本整合包未接上</b> ✗）。
 *
 * <h2>核到的真实类名 / 方法名</h2>
 * <pre>
 *   appeng.api.networking.energy.IAEPowerStorage
 *       boolean isAEPublicPowerStorage()
 *       double  getAECurrentPower()
 *       double  getAEMaxPower()
 *       double  extractAEPower(double amt, appeng.api.config.Actionable how)
 *   appeng.api.config.Actionable            enum { MODULATE, SIMULATE } ✓
 * </pre>
 * <p>⚠ <b>诚实项（最重要的一条）</b>：本整合包的 mods 目录里<b>没有 AE2 的 jar</b> ✗
 * ⇒ 上面这组名字<b>我无法从实际 jar 里核实</b> ⚠（Mekanism 那一路是逐个 javap 过的 ✓，
 * 这一路不是 ✗）。它们来自 AE2 长期稳定的公开 API，但我<b>不宣称</b>已核实。
 * 所以这里做了"最保守"的处理：<b>默认直接放弃</b>（见下）。
 *
 * <h2>为什么默认放弃而不是"试着反射一下"</h2>
 * AE2 的能量方块能力<b>挂在网格节点（{@code IGridNode}）上</b> ✗，不在方块实体上：
 * <pre>
 *   IGridNode node = ((IGridHost) blockEntity).getGridNode(side);
 *   if (node != null &amp;&amp; node.isActive()) node.getGrid().getEnergyService() ...
 * </pre>
 * 也就是说要真接上，至少得摸到 {@code appeng.api.networking.IGridHost#getGridNode(Direction)}、
 * {@code IGridNode#isActive()/getGrid()}、{@code IGrid#getEnergyService()}、
 * {@code IEnergyService} 这几层 ✓ —— 每一层的名字我都<b>没法核实</b> ✗，
 * 而且"我们这种外来方块<b>得先入网</b>"（{@code IInWorldGridNodeHost} / 需要 {@code IManagedGridNode} ✓）
 * 这条与 IC2 是同一个坑：AE2 的能量服务只会管<b>自己网格里</b>的机器 ✗
 * ⇒ 我们站在旁边"读一读"通常拿到的是 0 ✓（因为那个方块根本不在我们的网格上下文里 ✓）。
 *
 * <p>⇒ 本类只保留 {@code IAEPowerStorage} 这一层（<b>带 try/catch 的尝试性实现</b> ✓）：
 * 万一某个版本/某个方块（例如 {@code isAEPublicPowerStorage() == true} 的公共储能方块）
 * 真的把该能力暴露在方块实体上，这里就能顺手取到 ✓；取不到就安静返回 0 ✓ 不报错、不刷屏 ✗。
 * 用户要的是"AE 也是输入之一" —— 本轮的口径是：<b>结构留好、默认关闭、并如实说明未接上</b> ✓。
 */
public final class Ae2EnergyAdapter implements EnergyInputAdapter {

    private Ae2EnergyAdapter() {
    }

    /** 单例（无状态 ✓） */
    public static final Ae2EnergyAdapter INSTANCE = new Ae2EnergyAdapter();

    private static final String C_POWER_STORAGE = "appeng.api.networking.energy.IAEPowerStorage";
    private static final String C_ACTIONABLE = "appeng.api.config.Actionable";

    private static volatile int state = 0;             // 0 未试 / 1 类在 / -1 类不在
    private static String note = "尚未探测";
    private static Class<?> cStorage;
    private static Method mPublic;
    private static Method mCurrent;
    private static Method mMax;
    private static Method mExtract;
    private static Object actionableModulate;
    private static Object actionableSimulate;

    private static synchronized void resolve() {
        if (state != 0) return;
        try {
            cStorage = Class.forName(C_POWER_STORAGE);
            mPublic = cStorage.getMethod("isAEPublicPowerStorage");
            mCurrent = cStorage.getMethod("getAECurrentPower");
            mMax = cStorage.getMethod("getAEMaxPower");
            Class<?> cActionable = Class.forName(C_ACTIONABLE);
            mExtract = cStorage.getMethod("extractAEPower", double.class, cActionable);
            actionableModulate = Enum.valueOf(cActionable.asSubclass(Enum.class), "MODULATE");
            actionableSimulate = Enum.valueOf(cActionable.asSubclass(Enum.class), "SIMULATE");
            state = 1;
            note = "类在（IAEPowerStorage ✓）——但只认\"方块实体直接暴露该能力\"的情况；"
                    + "AE2 的能量其实挂在网格节点上 ⇒ 常态下读不到（见 §557 诚实项）";
        } catch (Throwable t) {
            state = -1;
            note = "未接上（" + t.getClass().getSimpleName() + " " + t.getMessage()
                    + "；本整合包没有 AE2 的 jar，类名未经 javap 核实 ✓ 见 §557）";
            cStorage = null;
            mPublic = null;
            mCurrent = null;
            mMax = null;
            mExtract = null;
            actionableModulate = null;
            actionableSimulate = null;
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
        return "AE2(AE)：" + note;
    }

    /**
     * 尝试从相邻方块实体上读 {@code IAEPowerStorage} 并抽 AE，折成 FE（1 AE = 2 FE ✓）。
     * <p>只认 {@code isAEPublicPowerStorage() == true} 的那些 ✓ —— 否则会去抽别人<b>私人</b>的
     * 储能缓冲 ✗（那属于"偷电"，不是这次的需求 ✓）。
     */
    @Override
    public int drainFe(EnergyConverterContext ctx, int maxFe, boolean simulate) {
        if (maxFe <= 0 || ctx == null || !ctx.aeEnabled()) return 0;
        resolve();
        if (state != 1 || cStorage == null) return 0;
        Level level = ctx.level();
        if (level == null) return 0;

        int leftFe = maxFe;
        int totalFe = 0;
        for (Direction d : EeStorages.NEIGHBOURS) {
            if (leftFe <= 0) break;
            BlockPos at = ctx.pos().relative(d);
            BlockEntity be = level.getBlockEntity(at);
            if (be == null || !cStorage.isInstance(be)) continue;
            int gotFe = drainOne(be, leftFe, simulate);
            if (gotFe <= 0) continue;
            totalFe += gotFe;
            leftFe -= gotFe;
        }
        return totalFe;
    }

    private static int drainOne(Object storage, int maxFe, boolean simulate) {
        try {
            Object open = mPublic.invoke(storage);
            if (!(open instanceof Boolean b) || !b) return 0;      // 非公共储能 ⇒ 不碰 ✓
            Object cur = mCurrent.invoke(storage);
            if (!(cur instanceof Number have) || have.doubleValue() <= 0.0D) return 0;

            double wantAe = maxFe / EnergyUnits.Fe.FE_PER_AE;       // 2 AE = 1 FE ⇒ 想要 maxFe FE 就要 2×maxFe AE
            double askAe = Math.min(wantAe, have.doubleValue());
            if (!(askAe > 0.0D)) return 0;

            Object action = simulate ? actionableSimulate : actionableModulate;
            Object got = mExtract.invoke(storage, askAe, action);
            if (!(got instanceof Number taken) || taken.doubleValue() <= 0.0D) return 0;
            int fe = (int) Math.floor(EnergyUnits.Fe.aeToFe(taken.doubleValue()));
            return Math.max(0, Math.min(fe, maxFe));
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.debug("[§557] AE2 取能失败（已跳过这一格）: {}", t.toString());
            return 0;
        }
    }
}
