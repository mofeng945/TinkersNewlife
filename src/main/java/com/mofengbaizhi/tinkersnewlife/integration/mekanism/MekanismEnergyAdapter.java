package com.mofengbaizhi.tinkersnewlife.integration.mekanism;

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

import javax.annotation.Nullable;

/**
 * <b>通用机械（Mekanism）J → FE 适配器</b>（§557，纯反射软依赖 ✓）。
 *
 * <h2>核到的真实类名 / 方法名（从 {@code Mekanism-1.20.1-10.4.16.80.jar} 的 class 常量池 + javap 逐个确认 ✓）</h2>
 * <pre>
 *   mekanism.api.energy.IStrictEnergyHandler          interface
 *       int             getEnergyContainerCount()
 *       FloatingLong    getEnergy(int)
 *       FloatingLong    extractEnergy(int, FloatingLong, mekanism.api.Action)   ← 取能走它
 *       (还有 insertEnergy / setEnergy / getMaxEnergy / getNeededEnergy)
 *   mekanism.api.math.FloatingLong                    有 doubleValue() / create(double) ✓
 *   mekanism.api.Action                               enum { EXECUTE, SIMULATE } ✓
 *   mekanism.common.capabilities.Capabilities         public static final
 *       Capability&lt;IStrictEnergyHandler&gt; STRICT_ENERGY            ← 能力对象（不是字符串 id ✗）
 *   mekanism.common.util.CapabilityUtils              static getCapability(ICapabilityProvider,
 *                                                          Capability&lt;T&gt;, Direction)
 *   mekanism.common.util.UnitDisplayUtils$EnergyUnit  enum { JOULES, FORGE_ENERGY, ELECTRICAL_UNITS }
 *       —— 同时 implements mekanism.api.energy.IEnergyConversion（有 convertTo/convertFrom/isEnabled）
 *   mekanism.api.energy.IEnergyConversionHelper       INSTANCE → jouleConversion() / feConversion()
 * </pre>
 *
 * <h2>⚠ 与任务书里那组名字的差异（必须记清楚，别再按记忆写 ✗）</h2>
 * 任务书建议的 {@code mekanism.api.energy.Joules} / {@code mekanism.api.energy.IEnergyStorage} /
 * {@code mekanism:energy} 这个 capability id <b>在当前版本（10.4.16.80）里都已经不存在了</b> ✗ ——
 * 全 jar 扫描结果：<b>没有任何</b> {@code Joules} 类 ✗、
 * {@code mekanism/api/energy/} 下只有上面那 6 个类（没有 {@code IEnergyStorage} ✗）、
 * 而且能量能力是<b>用 {@code CapabilityToken} 注册的 {@code Capability} 对象</b>
 * （{@code Capabilities.STRICT_ENERGY}）✓ 不是 {@code new ResourceLocation("mekanism","energy")} ✗。
 * ⇒ 本适配器按<b>实际存在的</b>那套写 ✓（这才是"从 jar 里核实"的意义 ✓）。
 *
 * <h2>汇率怎么来的（重要取舍 ✓）</h2>
 * 用户口径是 {@code 10 J = 4 FE}（{@code 1 J = 0.4 FE} ✓）。通用机械自己的换算倍率
 * <b>是可配置的</b>（默认 {@code 2.5 J = 1 FE} ⇒ 1 J = 0.4 FE —— 恰好与用户口径一致 ✓）。
 * 于是这里<b>先问通用机械自己</b>要倍率 ✓（{@code UnitDisplayUtils.EnergyUnit.JOULES.convertTo(1 FE)}
 * ⇒ "1 FE 值多少 J" ⇒ 取倒数 ✓），问不到才回落到 {@link EnergyUnits.Fe#FE_PER_J}（0.4 ✓）。
 * <p>⚠ 这是<b>我替用户做的取舍</b> ✓：如果玩家在 Mekanism 配置里把倍率改成 5 J = 1 FE，
 * 那通用机械自己的机器与我们的转化器就都会按新倍率算 ✓（不会出现"同一个 J 在两处值不一样"✗）；
 * 想<b>钉死</b>用户口径就把 {@link #USE_MEKANISM_OWN_RATIO} 改成 {@code false} ✓。
 */
public final class MekanismEnergyAdapter implements EnergyInputAdapter {

    private MekanismEnergyAdapter() {
    }

    /** 单例（无状态 ✓ 适配器不需要每个方块一份 ✓） */
    public static final MekanismEnergyAdapter INSTANCE = new MekanismEnergyAdapter();

    private static final String C_CAPABILITIES = "mekanism.common.capabilities.Capabilities";
    private static final String C_CAPABILITY_UTILS = "mekanism.common.util.CapabilityUtils";
    private static final String C_FLOATING_LONG = "mekanism.api.math.FloatingLong";
    private static final String C_ACTION = "mekanism.api.Action";
    private static final String C_ENERGY_UNIT = "mekanism.common.util.UnitDisplayUtils$EnergyUnit";

    /** 是否优先用通用机械自己的 J↔FE 倍率（见类注释的取舍 ✓） */
    private static final boolean USE_MEKANISM_OWN_RATIO = true;

    /** 解析状态：0 = 没试过、1 = 成功、-1 = 失败（失败之后不再重试 ✓ 也不刷屏 ✗） */
    private static volatile int state = 0;
    private static String note = "尚未探测";

    private static Object capability;            // Capabilities.STRICT_ENERGY（Capability<IStrictEnergyHandler> ✓）
    private static Method mGetCapability;        // CapabilityUtils.getCapability(ICapabilityProvider, Capability, Direction)
    private static Method mLazyOrElse;           // net.minecraftforge.common.util.LazyOptional#orElse(Object)
    private static Method mGetCount;             // IStrictEnergyHandler#getEnergyContainerCount()
    private static Method mGetEnergy;            // IStrictEnergyHandler#getEnergy(int) -> FloatingLong
    private static Method mExtractEnergy;        // IStrictEnergyHandler#extractEnergy(int, FloatingLong, Action)
    private static Method mDoubleValue;          // FloatingLong#doubleValue()
    private static Method mCreateDouble;         // FloatingLong#create(double)
    private static Object actionExecute;         // Action.EXECUTE
    private static Object actionSimulate;        // Action.SIMULATE

    /** 1 FE 值多少 J（问不到通用机械就回落到用户口径的倒数 ✓） */
    private static volatile double joulesPerFe = -1.0D;

    private static synchronized void resolve() {
        if (state != 0) return;
        try {
            Class<?> cCapabilities = Class.forName(C_CAPABILITIES);
            Class<?> cUtils = Class.forName(C_CAPABILITY_UTILS);
            Class<?> cFL = Class.forName(C_FLOATING_LONG);
            Class<?> cAction = Class.forName(C_ACTION);

            capability = cCapabilities.getField("STRICT_ENERGY").get(null);
            mGetCapability = cUtils.getMethod("getCapability",
                    net.minecraftforge.common.capabilities.ICapabilityProvider.class,
                    net.minecraftforge.common.capabilities.Capability.class,
                    Direction.class);
            // LazyOptional 是 Forge 的类 ⇒ 可以直接按类型取它的 orElse ✓（不需要反射 Forge 自己）
            mLazyOrElse = net.minecraftforge.common.util.LazyOptional.class.getMethod("orElse", Object.class);

            // IStrictEnergyHandler 的方法：按"名字 + 参数类型"逐个取 ✓ 少一个就整路降级 ✓
            Class<?> cHandler = Class.forName("mekanism.api.energy.IStrictEnergyHandler");
            mGetCount = cHandler.getMethod("getEnergyContainerCount");
            mGetEnergy = cHandler.getMethod("getEnergy", int.class);
            mExtractEnergy = cHandler.getMethod("extractEnergy", int.class, cFL, cAction);

            mDoubleValue = cFL.getMethod("doubleValue");
            mCreateDouble = cFL.getMethod("create", double.class);

            actionExecute = Enum.valueOf(cAction.asSubclass(Enum.class), "EXECUTE");
            actionSimulate = Enum.valueOf(cAction.asSubclass(Enum.class), "SIMULATE");

            joulesPerFe = probeJoulesPerFe();
            state = 1;
            note = "已接上：IStrictEnergyHandler + Capabilities.STRICT_ENERGY；1 FE = "
                    + trim(joulesPerFe) + " J（源："
                    + (USE_MEKANISM_OWN_RATIO && joulesPerFe > 0 ? "通用机械自己的倍率" : "用户口径回落值")
                    + "）";
        } catch (Throwable t) {
            state = -1;
            note = "未接上（反射失败：" + t.getClass().getSimpleName() + " " + t.getMessage() + "）";
            capability = null;
            mGetCapability = null;
            mLazyOrElse = null;
            mGetCount = null;
            mGetEnergy = null;
            mExtractEnergy = null;
            mDoubleValue = null;
            mCreateDouble = null;
            actionExecute = null;
            actionSimulate = null;
        }
    }

    /**
     * 问通用机械"1 FE 值多少 J"（{@code EnergyUnit.JOULES.convertTo(1 FE)} ✓）。
     * <p>问不到 ⇒ 返回 {@code -1}（调用方回落用户口径 ✓）。
     */
    private static double probeJoulesPerFe() {
        try {
            Class<?> cUnit = Class.forName(C_ENERGY_UNIT);
            Object joules = Enum.valueOf(cUnit.asSubclass(Enum.class), "JOULES");
            Method convertTo = cUnit.getMethod("convertTo", Class.forName(C_FLOATING_LONG));
            Object oneFe = mCreateDouble.invoke(null, 1.0D);       // FloatingLong.create(1.0) = 1 FE
            Object perFe = convertTo.invoke(joules, oneFe);        // = 该 1 FE 对应的 J
            double v = (Double) mDoubleValue.invoke(perFe);
            if (v > 0.0D) return v;
        } catch (Throwable ignored) {
            // 问不到就走回落值 ✓ 绝不因为这一问失败而让整路失效 ✗
        }
        return -1.0D;
    }

    /** 1 J = 多少 FE（问到了就用通用机械的倍率，否则用用户口径 0.4 ✓） */
    private static double fePerJoule() {
        resolve();
        if (USE_MEKANISM_OWN_RATIO && joulesPerFe > 0.0D) return 1.0D / joulesPerFe;
        return EnergyUnits.Fe.FE_PER_J;
    }

    /** 1 FE 值多少 J（真正拿去请求扣除的量 ✓） */
    private static double joulesPerFeNow() {
        resolve();
        if (USE_MEKANISM_OWN_RATIO && joulesPerFe > 0.0D) return joulesPerFe;
        return 1.0D / EnergyUnits.Fe.FE_PER_J;
    }

    @Override
    public boolean isAvailable() {
        resolve();
        return state == 1;
    }

    @Override
    public String probeNote() {
        resolve();
        return "Mekanism(J)：" + note;
    }

    /**
     * 逐个方向找 Mekanism 的能量容器，按 J→FE 折成 FE 并<b>真的扣掉</b>。
     * <p>一次最多取 {@code maxFe} 点 FE（跨 6 个邻居合计 ✓ 与抽取方块同一口径 ✓）。
     */
    @Override
    public int drainFe(EnergyConverterContext ctx, int maxFe, boolean simulate) {
        if (maxFe <= 0 || ctx == null || !ctx.jouleEnabled()) return 0;
        resolve();
        if (state != 1 || capability == null) return 0;
        Level level = ctx.level();
        if (level == null) return 0;

        int leftFe = maxFe;
        int totalFe = 0;
        for (Direction d : EeStorages.NEIGHBOURS) {
            if (leftFe <= 0) break;
            BlockPos at = ctx.pos().relative(d);
            BlockEntity be = level.getBlockEntity(at);
            if (be == null) continue;
            Object handler = handlerOf(be, d);
            if (handler == null) continue;
            int gotFe = drainOne(handler, leftFe, simulate);
            if (gotFe <= 0) continue;
            totalFe += gotFe;
            leftFe -= gotFe;
        }
        return totalFe;
    }

    /** 某一格方块实体在某个面上的 {@code IStrictEnergyHandler}（没有 ⇒ null ✓） */
    @Nullable
    private static Object handlerOf(BlockEntity be, Direction side) {
        try {
            // 泛型在运行期被擦除 ⇒ 直接按原始类型反射调用即可 ✓（javap 的签名是
            // <T> LazyOptional<T> getCapability(ICapabilityProvider, Capability<T>, Direction) ✓）
            Object lazy = mGetCapability.invoke(null, be, capability, side);
            if (lazy == null) return null;
            return mLazyOrElse.invoke(lazy, (Object) null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 从一个 {@code IStrictEnergyHandler} 里取出最多 {@code maxFe} 点 FE（真扣 ✓） */
    private static int drainOne(Object handler, int maxFe, boolean simulate) {
        try {
            int slots = (Integer) mGetCount.invoke(handler);
            if (slots <= 0) return 0;
            double perFeJ = joulesPerFeNow();
            double fePerJ = fePerJoule();
            int gotFe = 0;
            for (int i = 0; i < slots && gotFe < maxFe; i++) {
                Object storedJ = mGetEnergy.invoke(handler, i);
                if (storedJ == null) continue;
                double haveJ = (Double) mDoubleValue.invoke(storedJ);
                if (!(haveJ > 0.0D)) continue;
                // 这一 tick 还想要多少 J（= 还差多少 FE 折成 J ✓）
                double wantJ = (maxFe - gotFe) * perFeJ;
                // ⚠ 关键一步：**只请求"恰好能折出整 FE"的那部分 J** ✓
                //    否则"扣掉 1 J、却因为 J→FE 向下取整而一个 FE 都没换出来"就是凭空丢电 ✗
                //    钳到 （差多少 FE）× 每 FE 多少 J 的整数倍 ⇒ 取出来的 J 一定正好折成整 FE ✓
                double unitJ = perFeJ;                                        // 1 FE 值多少 J
                double roomJForWholeFe = Math.floor(haveJ / unitJ) * unitJ;    // 这些 J 一定能折成整 FE
                double askJ = Math.min(wantJ, Math.min(haveJ, roomJForWholeFe));
                if (!(askJ > 0.0D)) continue;
                Object ask = mCreateDouble.invoke(null, askJ);
                Object action = simulate ? actionSimulate : actionExecute;
                Object takenJ = mExtractEnergy.invoke(handler, i, ask, action);
                if (takenJ == null) continue;
                double gotJ = (Double) mDoubleValue.invoke(takenJ);
                if (!(gotJ > 0.0D)) continue;
                int fe = (int) Math.floor(gotJ * fePerJ);
                if (fe <= 0) {
                    // 极端情况（浮点/对方返回了不足 1 FE 的量）⇒ 立刻把这点 J 还回去 ✓ 绝不吃掉 ✗
                    if (!simulate) {
                        // IStrictEnergyHandler 没有"退还"接口 ⇒ 用 insertEnergy 塞回去 ✓（多一个方法名，
                        // 反射不到就退化成"这一点点 J 丢了" ✓ 只影响这一格这一 tick ✓）
                        refund(handler, i, gotJ);
                    }
                    continue;
                }
                gotFe += fe;
            }
            return Math.min(gotFe, maxFe);
        } catch (Throwable t) {
            // 任何一步不兼容 ⇒ 这一格这一 tick 拿不到电 ✓ 但不影响别的方块与别家适配器 ✓
            TinkersNewlife.LOGGER.debug("[§557] Mekanism 取能失败（已跳过这一格）: {}", t.toString());
            return 0;
        }
    }

    /**
     * 把一点点 J 塞回对方的容器（只在"取出来了却一个 FE 都换不出"的边界上用到 ✓）。
     * <p>反射不到 {@code insertEnergy} 就静默放弃 ✓（损失只有不到 1 FE 的量 ✓ 不值得为它崩 ✗）。
     */
    private static void refund(Object handler, int slot, double joules) {
        try {
            Method insert = handler.getClass().getMethod("insertEnergy", int.class,
                    Class.forName(C_FLOATING_LONG), Class.forName(C_ACTION));
            insert.invoke(handler, slot, mCreateDouble.invoke(null, joules), actionExecute);
        } catch (Throwable ignored) {
            // 见注释：放弃 ✓
        }
    }

    private static String trim(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }
}
