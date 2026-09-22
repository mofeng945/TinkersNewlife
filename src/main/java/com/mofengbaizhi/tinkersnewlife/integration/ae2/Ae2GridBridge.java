package com.mofengbaizhi.tinkersnewlife.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.capabilities.Capabilities;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.EeConverterCore;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

/**
 * <b>应用能源 2（AE2）AE → FE</b>（§559）：让本模组的转化器<b>真正入网</b>，
 * 从 AE2 网格的 {@code IEnergyService} 里抽电 ✓。
 *
 * <h2>⚠ 为什么必须"入网"（§557 为什么做不到）</h2>
 * AE2 的电<b>不是</b>暴露在方块实体上的 ✗ —— 它属于<b>网格（grid）</b>，
 * 而"你是不是这个网格的一员"由<b>网格节点（grid node）</b>决定 ✓。
 * §557 只做到"试试看方块实体上有没有 {@code IAEPowerStorage}" ✗ ⇒ 常态读到 0 ✓（当时已写明 ✓）。
 * 现在按 AE2 的正规做法：<b>建一个节点、把该节点暴露给外界</b> ✓
 * ⇒ 它的线缆就会连过来、我们就能从网格里取电 ✓。
 *
 * <h2>核到的真实类名 / 方法名（出处：{@code libs/appliedenergistics2-forge-15.4.10.jar}，逐个 javap ✓）</h2>
 * <pre>
 *   appeng.capabilities.Capabilities
 *       public static Capability&lt;IInWorldGridNodeHost&gt; IN_WORLD_GRID_NODE_HOST   ← 我们暴露这个 ✓
 *   appeng.api.networking.IInWorldGridNodeHost
 *       IGridNode getGridNode(Direction)          ← 线缆靠它拿到我们的节点 ✓
 *       AECableType getCableConnectionType(Direction)   （default ✓）
 *   appeng.api.networking.GridHelper
 *       static &lt;T&gt; IManagedGridNode createManagedNode(T owner, IGridNodeListener&lt;T&gt; listener)
 *       static IInWorldGridNodeHost getNodeHost(Level, BlockPos)
 *       static IGridNode getExposedNode(Level, BlockPos, Direction)
 *   appeng.api.networking.IManagedGridNode
 *       void create(Level, BlockPos)  void destroy()  void loadFromNBT(CompoundTag)  void saveToNBT(CompoundTag)
 *       IManagedGridNode setInWorldNode(boolean) / setExposedOnSides(Set&lt;Direction&gt;)
 *                        / setIdlePowerUsage(double) / setFlags(GridFlags...)
 *       boolean isReady() / isActive() / isOnline() / isPowered()
 *       IGridNode getNode()   IGrid getGrid()（default ✓）
 *   appeng.api.networking.energy.IEnergyService  (implements IEnergySource ✓)
 *       double extractAEPower(double, appeng.api.config.Actionable, appeng.api.config.PowerMultiplier)
 *       boolean isNetworkPowered()   double getStoredPower() / getMaxStoredPower()
 *   appeng.api.config.Actionable  enum { MODULATE, SIMULATE }
 *   appeng.api.config.PowerMultiplier  enum { ONE, CONFIG }  multiply(double) / divide(double)
 *   appeng.api.networking.GridFlags  enum { PREFERRED, ... }
 * </pre>
 *
 * <h2>⚠ 为什么不实现 {@code IGridConnectedBlockEntity}，而是用"能力 + 桥"</h2>
 * 那个接口是给"**方块实体自己**当节点宿主"用的 ✗（它 extends
 * {@code IInWorldGridNodeHost} / {@code IActionHost} / {@code IOwnerAwareBlockEntity}，
 * 且 {@code getExposedNode} 会 {@code instanceof} 检查**方块实体** ✗）⇒
 * 用它就必须让我们的 BE 类直接 implement 一堆 AE2 接口 ✗
 * ⇒ "没装 AE2 的玩家加载那个 BE 类" 就会 {@code NoClassDefFoundError} 崩游戏 ✗✗。
 * <p>而 AE2 自己提供了 {@code Capabilities.IN_WORLD_GRID_NODE_HOST} 这个 <b>capability</b> ✓
 * ⇒ 我们只要在 {@code getCapability} 里返回<b>本桥对象</b>（它 implements
 * {@code IInWorldGridNodeHost} ✓）就等价 ✓ —— 方块实体那边**一个 AE2 类型都不出现** ✓
 * （它只经过 {@code EnergyConverterModBridges} 这个中立分派点 ✓）。
 *
 * <h2>单位与汇率（§560 逐行核对过方向 ✓）</h2>
 * AE2 的能量叫 "AE"，<b>用户口径 {@code 1 AE = 2 FE}</b> ✓（所以 {@code 1 FE = 0.5 AE} ✓、
 * 常量 {@code EnergyUnits.Fe.FE_PER_AE = 2.0} ✓）。
 *
 * <p>⚠ <b>"界面上的 AE 数字"与"AE2 内部能量单位"<u>不是一回事</u></b> ✗ —— 后者是有量纲的
 * 内部单位，官方用 {@code PowerMultiplier.CONFIG} 在两个记数之间换算 ✓。本桥的走法（一进一出）：
 * <pre>
 *   要电时：FE 缺口 ÷ FE_PER_AE  = "要多少 AE"        （64 FE ⇒ 32 AE ✓）
 *           "要多少 AE" ÷ multiplier = 内部单位        ← PowerMultiplier.CONFIG.divide(...) ✓
 *   回来时：内部单位 × multiplier = "实际抽到多少 AE"   ← PowerMultiplier.CONFIG.multiply(...) ✓
 *           "实际 AE" × FE_PER_AE = 到手 FE            （32 AE ⇒ 64 FE ✓）
 * </pre>
 * 中间的 {@code divide} 与 {@code multiply} **正好互逆** ✓ ⇒ 到手的 FE 只由
 * {@link com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits.Fe#FE_PER_AE} 决定 ✓ 自洽 ✓
 * （也就是说：换掉 {@code PowerMultiplier} 那一档只会改变"我们向 AE2 要多少个内部单位"✓
 *  <b>不会</b>改变"1 AE 换几 FE"✗ —— 后者永远由用户口径那一个常量说了算 ✓）。
 * <p>⚠ 所以本桥**不需要**"先按 2 AE = 1 FE 算"这种说法 ✗（那是 §559 汇报里的<u>措辞错误</u> ✗
 * 代码一直是 {@code ÷ FE_PER_AE} = 正确方向 ✓ 见备忘录 §560 ✓）。
 *
 * <h2>隔离（没装 AE2 的玩家为什么不会崩）</h2>
 * <b>本类是唯一 import {@code appeng.*} 的地方</b> ✓ 而且只在
 * {@code EnergyConverterModBridges} 的 {@code isLoaded("ae2")} 分支里被触碰 ✓
 * ⇒ 没装 AE2 时 JVM 永远不会加载本类 ✓。
 */
public final class Ae2GridBridge implements IInWorldGridNodeHost, IGridNodeListener<Ae2GridBridge> {

    /** 每个方块实体一个桥（WeakHashMap ⇒ 方块被拆后不会把 BE 一起吊住 ✓） */
    private static final Map<EeConverterCore, Ae2GridBridge> CACHE = new WeakHashMap<>();

    private final EeConverterCore core;
    private final IManagedGridNode node;
    private final LazyOptional<IInWorldGridNodeHost> holder = LazyOptional.of(() -> this);

    private Ae2GridBridge(EeConverterCore core) {
        this.core = core;
        // GridHelper.createManagedNode(owner, listener) ✓ owner = 本桥（监听回调也会回到本桥 ✓）
        this.node = appeng.api.networking.GridHelper.createManagedNode(this, this);
        // ⚠ 不要 REQUIRE_CHANNEL：目标是"像能量接收器那样**不占频道**"✓（用户口径 ✓）
        //    PREFERRED 只是"优先被选为网络的代表"（AE2 自己的机器常这么设 ✓ 无害 ✓）
        this.node.setFlags(GridFlags.PREFERRED);
        this.node.setInWorldNode(true);
        this.node.setExposedOnSides(EnumSet.allOf(Direction.class));
        this.node.setIdlePowerUsage(0.0D);      // 我们不待机耗电（只从网络里取我们要的 ✓）
    }

    // ============================================================
    //  capability 分派 / 生命周期（都给 EnergyConverterModBridges 调 ✓）
    // ============================================================

    @Nullable
    public static <T> LazyOptional<T> capability(EeConverterCore core, Capability<T> cap, @Nullable Direction side) {
        if (cap != Capabilities.IN_WORLD_GRID_NODE_HOST) return null;
        return bridgeOf(core).holder.cast();
    }

    private static Ae2GridBridge bridgeOf(EeConverterCore core) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(core, Ae2GridBridge::new);
        }
    }

    /** 区块加载 ⇒ 建节点（AE2 的机器也是在这个时机 create ✓） */
    public static void onLoad(EeConverterCore core) {
        try {
            bridgeOf(core).node.create(core.getLevel(), core.getBlockPos());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[§559] AE2 网格节点 create 失败: {}", t.toString());
        }
    }
    /** 区块卸载 / 方块被拆 ⇒ 毁节点（漏了这一步会让线缆"连着一个不存在的方块" ✗） */
    public static void onUnload(EeConverterCore core) {
        synchronized (CACHE) {
            Ae2GridBridge b = CACHE.get(core);
            if (b == null) return;
            try {
                b.node.destroy();
            } catch (Throwable ignored) {
            }
            b.holder.invalidate();
        }
    }

    /** 能力句柄作废（invalidateCaps ✓ 与 onUnload 的区别：这里**不**从缓存里摘掉，节点还活着 ✓） */
    public static void invalidate(EeConverterCore core) {
        synchronized (CACHE) {
            Ae2GridBridge b = CACHE.get(core);
            if (b != null) b.holder.invalidate();
        }
    }

    /** 每 tick：从网格取电（这是"真正生效"的那一步 ✓） */
    public static void tick(EeConverterCore core) {
        Ae2GridBridge b = null;
        synchronized (CACHE) {
            b = CACHE.get(core);
        }
        if (b == null) return;
        b.pullFromGrid();
    }

    private void pullFromGrid() {
        // ① 节点必须还在、还连着、且网络有电 ✓（没连上就什么都不做 ⇒ 不刷日志 ✗）
        if (!this.node.isReady() || !this.node.isActive() || !this.node.isPowered()) return;
        IGrid grid = this.node.getGrid();
        if (grid == null) return;
        IEnergyService energy = grid.getEnergyService();
        if (energy == null) return;

        int outputCap = com.mofengbaizhi.tinkersnewlife.config.ModConfig.converterOutputFePerTick();
        if (outputCap <= 0) return;
        int room = Math.max(0, core.getMaxEnergyStored() - core.getEnergyStored());
        int wantFe = Math.min(outputCap, room);
        if (wantFe <= 0) return;

        // ② 用户口径：**1 AE = 2 FE**（所以 1 FE = 0.5 AE）—— 常量 EnergyUnits.Fe.FE_PER_AE = 2.0 ✓
        //    ⇒ 要 wantFe 点 FE，就得抽 wantFe / 2 个 "AE" ✓
        //      例：想补 64 FE ⇒ 抽 32 AE ⇒ 折回来正好 2 × 32 = 64 FE ✓（§560 已逐行核对方向 ✓）
        //    ⚠ 方向别写反 ✗：这里必须是 **÷ FE_PER_AE**（= ÷2）✗ 不是 ×2 ✗
        //      （×2 会让 AE 那条路的实际功率变成正确的 1/4 ✗ —— §560 就是来钉死这一点的 ✓）
        double wantAeUnits = EnergyUnits.Fe.feToAe(wantFe);
        // ③ "界面上的 AE 数字" → **AE2 内部能量单位**（这两个不是一回事 ✗ 见类注释"单位与汇率"✓）
        //    AE2 自己的 `PowerMultiplier` 语义（javap 核实 ✓）：
        //      `multiply(internal)` = internal × multiplier ⇒ **内部单位 → 界面数字** ✓
        //      `divide(display)`    = display ÷ multiplier   ⇒ **界面数字 → 内部单位** ✓
        //    ⇒ 去"要"的时候用 divide（本行 ✓）、回来"数"的时候用 multiply（下面第 ⑤ 步 ✓）
        //      —— 一进一出正好抵消 ✓ 所以到我们手上的 FE 只由 `FE_PER_AE` 决定 ✓ 自洽 ✓
        double internal = PowerMultiplier.CONFIG.divide(wantAeUnits);
        if (!(internal > 0.0D)) return;

        // ④ 先模拟问一次（拿不到就不打扰网络 ✓），再真取 ✓
        double canGetInternal = energy.extractAEPower(internal, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (!(canGetInternal > 0.0D)) return;
        double gotInternal = energy.extractAEPower(canGetInternal, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (!(gotInternal > 0.0D)) return;

        // ⑤ 内部单位 → "界面 AE 数字" → FE ✓（**向下取整**：只收整 FE ✓ 少收的零头不补 ⇒ 不凭空发电 ✗）
        //    ⚠ 方向核对（§560）：`multiply` 是"内部 → 界面"✓ 与第 ③ 步的 `divide` 正好互逆 ✓
        //      ⇒ 抽到 N AE ⇒ 这里 `gotAeUnits ≈ N` ⇒ `gotFe = floor(N × 2)` ✓
        //        完全符合用户口径 `1 AE = 2 FE` ✓（例：抽 16 AE ⇒ 得 32 FE ✓）
        double gotAeUnits = PowerMultiplier.CONFIG.multiply(gotInternal);
        int gotFe = (int) Math.floor(EnergyUnits.Fe.aeToFe(gotAeUnits));
        if (gotFe <= 0) return;
        int accepted = core.insertFe(Math.min(gotFe, wantFe), false);
        if (accepted <= 0) return;
        // ⑥ 万一池子比我们算的小（配置在运行中被改），多取的这一小点就留在池子里 ✓ 不退回也不丢 ✓
    }

    // ============================================================
    //  IInWorldGridNodeHost（线缆靠这两个方法认识我们 ✓）
    // ============================================================

    @Override
    public IGridNode getGridNode(Direction dir) {
        return this.node.getNode();
    }

    // ============================================================
    //  IGridNodeListener（AE2 的生命周期回调 ✓ 我们只需要"存盘"那一条 ✓）
    // ============================================================

    /** AE2 要求宿主在"需要存盘"时保存自己 —— 让宿主 BE 标脏即可 ✓ */
    @Override
    public void onSaveChanges(Ae2GridBridge owner, IGridNode node) {
        core.markChanged();
    }

    // ============================================================
    //  ⚠ 下面两个是给"AE2 主动找宿主"用的可选钩子（当前版本不一定会调 ✓ 留着是稳妥的）
    // ============================================================

    /** 让 AE2 也能把我们当"动作宿主"看待（返回节点 ✓ 无害 ✓） */
    public IGridNode getActionableNode() {
        return this.node.getNode();
    }

    /** 主人是谁（我们的转化器是方块、没有主人 ⇒ 空实现 ✓ 只要方法在，AE2 就不会因为找不到而报错 ✓） */
    public void setOwner(Player player) {
        // 有意留空 ✓
    }

    /** 我们的"暴露面"（全 6 面 ✓ 与构造里的 setExposedOnSides 一致 ✓） */
    public java.util.Set<Direction> getGridConnectableSides() {
        return EnumSet.allOf(Direction.class);
    }

    /** 节点坐标（调试用 ✓） */
    public BlockPos nodePos() {
        return core.getBlockPos();
    }}
