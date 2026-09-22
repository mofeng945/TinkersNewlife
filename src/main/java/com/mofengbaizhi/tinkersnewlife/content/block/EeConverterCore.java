package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorage;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorages;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyConverterContext;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyInputAdapter;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyInputs;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nullable;

/**
 * <b>万用能量转化器的全部状态与逻辑</b>（§557 建 · §558 未动 · §559 抽成"共享核心"）。
 *
 * <h2>⚠ 为什么 §559 要把它从方块实体里抽出来（关键设计）</h2>
 * 「让机械动力的传动杆能接上」在 Create 里<b>只能</b>靠继承
 * {@code com.simibubi.create.content.kinetics.base.KineticBlockEntity} 实现 ✗ ——
 * 而那是<b>可选模组</b>的类 ✗。一旦我们的方块实体直接继承它，
 * "没装 Create 的玩家"加载方块实体类时就会 {@code NoClassDefFoundError} 崩游戏 ✗✗。
 *
 * <p>所以继承结构改成"<b>共享核心 + 两个壳</b>"：
 * <pre>
 *   EeConverterCore                     ← 本类：纯 Java（**不 extends BlockEntity** ✓ **零可选模组 import** ✓）
 *                                         状态 + 每 tick 逻辑 + FE 池 + IEnergyStorage 实现 ✓
 *     ├─（持有）EnergyConverterBlockEntity        extends BlockEntity         ← 没装 Create 时注册这个 ✓
 *     └─（自己就是）CreateEnergyConverterBlockEntity extends KineticBlockEntity ← 装了 Create 时注册这个 ✓
 * </pre>
 * ⚠ 注意"持有"与"是"的区别 ✗：<b>Create 那一支自己就是本体</b>（它 extends
 * {@code KineticBlockEntity}，而那个又 extends {@code BlockEntity} ✓ 所以它一样能提供
 * capability / NBT ✓）；普通那一支则"持有一个核心"✓（它 extends {@code BlockEntity} ✓）。
 * 两条路都只有一份状态与一份逻辑（都在本类 ✓）⇒ **绝不会出现两份会各自漂移的实现** ✗。
 *
 * <h2>它干什么（§557 口径，§558/§559 都没改 ✓）</h2>
 * 单向：各种能量 ⇒ FE（**不做** FE ⇒ EE 的反向 ✗）。每 tick：
 * ① 收（相邻 EE / 相邻 Forge Energy / 四家模组适配器 / 可选模组"真正接上"那两路 / Create 转速）
 * ⇒ ② 一律落进 FE 池 ⇒ ③ 按 {@link ModConfig#converterOutputFePerTick()} 推给相邻方块 ✓。
 */
public final class EeConverterCore implements EeStorage, IEnergyStorage {

    /** FE 池的存档键（int ✓ FE 本来就是整数 ✓） */
    public static final String KEY_FE = "FeBuffer";

    /** EE 那半块的"没凑够 8 EE"余数（double ⇒ 收小数也照样累积 ✓）的存档键 */
    public static final String KEY_EE_IN = "EeInput";

    /**
     * 宿主方块实体（**只用来** setChanged / getLevel / getBlockPos ✓
     * 本类刻意不 extends 它 ✗ —— 见类注释的隔离理由 ✓）。
     */
    private final BlockEntity host;

    /**
     * §559 Create 那一支的转速来源。
     * <p>默认 {@code null} ⇒ 转速按 0 算 ✓；只有
     * {@code CreateEnergyConverterBlockEntity} 会把它设成"读自己的 {@code getSpeed()}"✓
     * ⇒ 本类**不需要**认识任何 Create 的类 ✓（没装 Create 的玩家加载本类时不会碰到可选模组的类型 ✓）。
     */
    @Nullable
    private java.util.function.DoubleSupplier rpmSource;

    /** FE 池（≤ {@link ModConfig#converterBufferFe()} ✓ 持久化 ✓） */
    private int feBuffer = 0;

    /**
     * §571 <b>换算残差池</b>（FE）✗ —— 低转速 / 小额度时"不足 1 FE"的小数原来被 `floor` 直接丢掉 ✗
     * （例：1 RPM = 0.703 FE/tick ⇒ floor 永远是 0 ⇒ **一点电都发不出来** ✗）。现在小数攒在这里 ✓
     * 攒够 1 FE 才吐出去 ✓（<b>绝不凭空发电</b> ✗ 只是把本该给的补上 ✓）。
     */
    private double feResidual = 0.0D;

    /** 从相邻 EE 容器收进来、还没凑够 1 FE 的 EE（<b>小于 8</b> ✓；持久化 ⇒ 重启不丢那几 EE ✓） */
    private double eeInput = 0.0D;

    /** 本 tick 已经收了多少 EE（把 {@code input_ee_per_tick} 做成真正的"每 tick 额度"✓ 不存档 ✓） */
    private int eeThisTick = 0;

    public EeConverterCore(BlockEntity host) {
        this.host = host;
    }

    // ============================================================
    //  给"可选模组的桥"用的最小访问面（⚠ 刻意**不**暴露 BlockEntity 本体 ✗
    //  —— 那样桥那边就会 import 原版的 BlockEntity，虽然无害，但本类想保持"只给需要的"✓）
    // ============================================================

    /** 宿主世界（可能为 null：方块实体还没进世界 ✓） */
    @Nullable
    public net.minecraft.world.level.Level getLevel() {
        return host.getLevel();
    }

    /** 宿主坐标 = 本转化器的坐标 ✓ */
    public BlockPos getBlockPos() {
        return host.getBlockPos();
    }

    /** 宿主标脏（存盘 ✓ 桥在"AE2 要求存盘"时用它 ✓） */
    public void markChanged() {
        host.setChanged();
    }

    /** §559 Create 那一支注册转速来源（普通那一支永远不调 ⇒ 转速恒 0 ✓） */
    public void setRpmSource(@Nullable java.util.function.DoubleSupplier source) {
        this.rpmSource = source;
    }

    /** 本 tick 的转速（RPM）—— 没接 Create 时恒 0 ✓ */
    private double rpm() {
        java.util.function.DoubleSupplier s = this.rpmSource;
        if (s == null) return 0.0D;
        try {
            double v = s.getAsDouble();
            return (v > 0.0D && !Double.isNaN(v)) ? v : 0.0D;
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    // ============================================================
    //  每 tick（两个壳都调这里 ✓）
    // ============================================================

    public void tick(ServerLevel level, BlockPos pos) {
        eeThisTick = 0;                                              // §557 "每 tick 额度"从这里重新开始 ✓
        final int outputCap = ModConfig.converterOutputFePerTick();
        if (outputCap <= 0) return;                                  // 总闸门关着 ⇒ 一整 tick 什么都不做 ✓
        if (feBuffer >= getMaxEnergyStored()) return;                // 池子满了 ⇒ 不收不推（等下游来拿 ✓）

        EnergyConverterContext ctx = EnergyConverterContext.of(level, pos);

        // ①-A 相邻 EE 容器（单位 EE ⇒ 折成 FE ✓）
        int eeBudget = ModConfig.converterInputEePerTick();
        if (eeBudget > 0) {
            EeStorages.pullAround(level, pos, this, eeBudget, EeStorages.DIRECT);
        }

        // ①-B 相邻 Forge Energy（FE ✓ 直接进池子）
        int feBudget = ModConfig.converterInputFePerTick();   // §571 闸门拆分：输入只看输入闸门 ✓
        if (feBudget > 0) {
            pullForgeEnergy(level, pos, feBudget);
        }

        // ①-C 四家模组适配器（J / RPM / EU / AE ⇒ 都折成 FE ✓）
        //      额度用"这一 tick 剩下的吞吐" ⇒ 多条路同时接上也不会串出超过 outputCap 的功率 ✗
        // §577 **停用 §557 的"反射版适配器"** ✗ —— 真因就是它 ✓：
        //   用户实测「侧面接上传动杆照样产出 FE」✗ ⇒ 因为那套里有 **Create(RPM) 适配器**，
        //   它读的是"**相邻**动能方块的转速"✗，**完全不管方向**✗ ⇒ 任何一面有杆都被算成输入 ✓。
        //   §559 起：Mek(J) 走 `STRICT_ENERGY` capability ✓、AE 走网格节点 ✓、
        //   Create(RPM) 走"本方块自己就是动能方块"（且**正面把关** ✓ §575/§576 ✓）⇒ 这套适配器**全部多余** ✗。
        //   ⚠ 适配器类（`EnergyInputs` / `Ae2EnergyAdapter` 等）暂留为死代码 ✓ 下次清理 ✗（本次只断调用 ✓ 零编译风险 ✓）。
        //
        //   （原代码：遍历 `EnergyInputs.all()` 逐个 `drainFe(...)` 并 `insertFe(...)` ✓）        // ①-D §559 可选模组的"真正接上"那两路（Mekanism = 收它推来的 J ✓ AE2 = 从网格取电 ✓）
        //      ⚠ 都藏在 EnergyConverterModBridges 后面 ⇒ 没装那家时连它们的类都不会被加载 ✓
        EnergyConverterModBridges.tick(host);

        // ①-E §559 机械动力（Create）：本方块就是动能方块 ⇒ 直接读自己的转速 ✓
        //      ⚠ "读转速、不扣转速"的取舍与 §557 一致 ✓（Create 里没有"消耗转速"这个概念 ✓）
        if (ctx.rpmEnabled()) {
            double rpm = rpm();
            if (rpm > 0.0D) {
                int fromRpm = withResidual(EnergyUnits.Fe.rpmToFePerTick(rpm));   // §571 残差：低转速不再恒 0 ✓
                int room = Math.max(0, getMaxEnergyStored() - feBuffer);
                if (fromRpm > 0 && room > 0) {
                    insertFe(Math.min(fromRpm, room), false);
                }
            }
        }

        // ③ 推给相邻方块（push 模式 ✓ 最多 output_fe_per_tick ✓）
        pushForgeEnergy(level, pos, outputCap);
    }

    // ============================================================
    //  FE 池（内部）
    // ============================================================

    /**
     * §571 把"带小数的 FE 产量"攒进残差池并吐出凑整的部分 ✓（不足 1 FE 不丢 ✓ 攒够再出 ✓）。
     *
     * @return 这一次真正可以入池的整数 FE（可能为 0 ⇒ 说明还在攒 ✓）
     */
    public int withResidual(double fe) {
        if (!(fe > 0.0D)) return 0;
        feResidual += fe;
        int whole = (int) Math.floor(feResidual);
        if (whole > 0) feResidual -= whole;
        return whole;
    }

    /** §571 给外部桥用的入口（J→FE / AE→FE 这类换算带小数时走它 ✓ 免得每 tick 都被 floor 丢一点 ✗） */
    public int residualFloor(double fe) {
        return withResidual(fe);
    }

    /** §578 宿主方块的状态（给六面角色判定用 ✓ 桥与核心都靠它 ✓ 可能为 null ⇒ 判定里已容忍 ✓） */
    public net.minecraft.world.level.block.state.BlockState hostState() {
        try {
            return host.getBlockState();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 往 FE 池里放（返回实际接受的 ✓ 满了就拒收 ✓；§559 起对"外部桥"可见 ✓） */
    public int insertFe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int room = getMaxEnergyStored() - feBuffer;
        if (room <= 0) return 0;
        int accepted = Math.min(room, amount);
        if (!simulate) {
            feBuffer += accepted;
            host.setChanged();
        }
        return accepted;
    }

    /** 从 FE 池里取 */
    public int extractFe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int take = Math.min(feBuffer, amount);
        if (take <= 0) return 0;
        if (!simulate) {
            feBuffer -= take;
            host.setChanged();
        }
        return take;
    }

    /** 从相邻方块"抽" Forge Energy（我们要多少、它给多少，取小 ✓） */
    private void pullForgeEnergy(Level level, BlockPos pos, int budget) {
        int left = budget;
        for (Direction d : EeStorages.NEIGHBOURS) {
            // §578 六面角色：FE 只在**背面出口 + 底面万用**上取/送 ✓（原来六面都抽都推 ✗）
            if (!EnergyConverterFaces.allowsFeOut(hostState(), d)) continue;   // §580 主动推只从**背面** ✓
            if (left <= 0) break;
            BlockPos at = pos.relative(d);
            BlockEntity be = level.getBlockEntity(at);
            if (be == null) continue;
            IEnergyStorage handler = energyAt(be, d);
            if (handler == null || !handler.canExtract()) continue;
            int room = getMaxEnergyStored() - feBuffer;
            if (room <= 0) break;
            int want = Math.min(left, room);
            int offered = handler.extractEnergy(want, true);
            if (offered <= 0) continue;
            int accepted = insertFe(offered, true);
            if (accepted <= 0) continue;
            int got = handler.extractEnergy(accepted, false);
            if (got <= 0) continue;
            int really = insertFe(got, false);
            if (really < got) {
                // 理论到不了这里（上面 simulate 过）⇒ 真发生了就把多出来的灌回去 ✓ 绝不吞 ✗
                handler.receiveEnergy(got - really, false);
            }
            left -= really;
        }
    }

    /** 把 FE 池里的电"推"给相邻方块的 {@code IEnergyStorage}（push 模式 ✓ 固定方向顺序 ✓） */
    private void pushForgeEnergy(Level level, BlockPos pos, int budget) {
        int left = Math.min(budget, feBuffer);
        for (Direction d : EeStorages.NEIGHBOURS) {
            // §578 六面角色：FE 只在**背面出口 + 底面万用**上取/送 ✓（原来六面都抽都推 ✗）
            if (!EnergyConverterFaces.allowsFeOut(hostState(), d)) continue;   // §580 主动推只从**背面** ✓
            if (left <= 0) break;
            BlockPos at = pos.relative(d);
            BlockEntity be = level.getBlockEntity(at);
            if (be == null || be == host) continue;
            IEnergyStorage handler = energyAt(be, d);
            if (handler == null || !handler.canReceive()) continue;
            int want = Math.min(left, extractFe(left, true));
            if (want <= 0) break;
            int accepted = handler.receiveEnergy(want, true);
            if (accepted <= 0) continue;
            int got = extractFe(accepted, false);
            if (got <= 0) continue;
            int really = handler.receiveEnergy(got, false);
            if (really < got) insertFe(got - really, false);   // 还回池子 ✓
            left -= really;
        }
    }

    /** 某格方块实体在某个面上的 Forge Energy（没有 ⇒ null ✓ 全程不抛 ✓） */
    @Nullable
    public static IEnergyStorage energyAt(BlockEntity be, Direction side) {
        try {
            return be.getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ============================================================
    //  EeStorage（单位：EE）：转化器"存 EE 的容量"其实就是那点"还没凑成 FE 的余数"
    // ============================================================

    /** 转化器只把 EE 当"过路货"⇒ 存量就是那个还没凑够 1 FE 的余数（0~7 ✓） */
    @Override
    public int getEe() {
        return (int) Math.floor(eeInput);
    }

    /**
     * EE 的"容量" = 一块水晶方块（4000 EE ✓）。
     * <p>⚠ 它不是真的能囤 4000 EE ✗ —— 转化器是<b>过路</b>的（进来的 EE 立刻折成 FE ✓）。
     * 这个数字只是给"抽取方块"一个合理的上限语义 ✓ 免得它一 tick 想把 65536 EE 全塞过来 ✗
     * （真正的限速仍然是 {@code input_ee_per_tick} 与 {@code output_fe_per_tick} ✓）。
     */
    @Override
    public int getCapacity() {
        return 4000;
    }

    /** 收 EE：立刻按 {@code 1 FE = 8 EE} 折成 FE 进池子 ✓ 不满 1 FE 的余数留在 {@link #eeInput} ✓ */
    @Override
    public int insertEe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int eeBudget = ModConfig.converterInputEePerTick();
        if (eeBudget <= 0) return 0;
        // 额度 = 「本 tick 的 EE 总额度 − 本 tick 已经用掉的」与「FE 池剩下的空间折成 EE」两者取小 ✓
        int roomByTick = Math.max(0, eeBudget - eeThisTick);
        int roomByPool = (getMaxEnergyStored() - feBuffer) * (int) EnergyUnits.FE_PER_EE_FACTOR;
        int take = Math.min(amount, Math.min(roomByTick, roomByPool));
        if (take <= 0) return 0;
        if (simulate) return take;                        // 只报数、不改状态 ✓
        return depositEe(take);
    }

    /**
     * 真正把 EE 攒进 {@link #eeInput}，凑够 1 FE 就折进 FE 池 ✓。
     *
     * @return 真的收下的 EE（= 入参 ✓ 调用方已经按额度算过了）
     */
    private int depositEe(int ee) {
        if (ee <= 0) return 0;
        eeThisTick += ee;                                 // 记进"本 tick 已收"✓（额度判定用它 ✓）
        eeInput += ee;
        int fe = EnergyUnits.eeToFe(eeInput);
        if (fe > 0) {
            int really = insertFe(fe, false);
            eeInput -= really * EnergyUnits.FE_PER_EE_FACTOR;
            if (eeInput < 0.0D) eeInput = 0.0D;           // 防浮点负数 ✓
        }
        host.setChanged();
        return ee;
    }

    /** 转化器<b>不</b>把 EE 给任何人 ✗（单向：只进不出 ✓ 用户明确"不要反向"✓） */
    @Override
    public int extractEe(int amount, boolean simulate) {
        return 0;
    }

    // ============================================================
    //  IEnergyStorage（对外就是"FE 池"本身 ✓）
    // ============================================================
    /** §580 **只收不放**的 FE 面（右面线缆口 / 底面万用口 ✓） */
    public final net.minecraftforge.energy.IEnergyStorage feInput = new net.minecraftforge.energy.IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean sim) { return EeConverterCore.this.receiveEnergy(max, sim); }
        @Override public int extractEnergy(int max, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return feBuffer; }
        @Override public int getMaxEnergyStored() { return EeConverterCore.this.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    /** §580 **只放不收**的 FE 面（**背面 = 唯一输出口** ✓） */
    public final net.minecraftforge.energy.IEnergyStorage feOutput = new net.minecraftforge.energy.IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean sim) { return 0; }
        @Override public int extractEnergy(int max, boolean sim) { return EeConverterCore.this.extractFe(max, sim); }
        @Override public int getEnergyStored() { return feBuffer; }
        @Override public int getMaxEnergyStored() { return EeConverterCore.this.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        // ⚠ 与"主动抽"共用同一个上限：输出闸门与输入闸门都要看 ✓
        int cap = ModConfig.converterInputFePerTick();   // §571 闸门拆分：外部推进来只看输入闸门 ✓
        int accepted = Math.min(maxReceive, cap);
        return insertFe(accepted, simulate);
    }

    /** 外人<b>不能</b>从转化器里把电抽回去 ✗（它是"输出端"✓ 抽走就等于把产物拿走了 —— 那由我们推 ✓） */
    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return extractFe(maxExtract, simulate);   // §563 允许被拉（Mek 线缆主动拉 ✓）
    }

    @Override
    public int getEnergyStored() {
        return feBuffer;
    }

    @Override
    public int getMaxEnergyStored() {
        return ModConfig.converterBufferFe();
    }

    @Override
    public boolean canExtract() {
        return true;      // §563
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    // ============================================================
    //  存档（两个壳都转发到这里 ✓）
    // ============================================================

    public void save(CompoundTag tag) {
        if (feBuffer > 0) tag.putInt(KEY_FE, feBuffer);
        if (eeInput > 0.0D) tag.putDouble(KEY_EE_IN, eeInput);
    }

    public void load(CompoundTag tag) {
        feBuffer = Math.max(0, Math.min(tag.getInt(KEY_FE), getMaxEnergyStored()));
        eeInput = tag.contains(KEY_EE_IN) ? Math.max(0.0D, tag.getDouble(KEY_EE_IN)) : 0.0D;
    }

    /** 区块加载时同步用的快照（让客户端数据一致 ✓ 以后画数值不用再动这条路 ✓） */
    public void writeSyncTag(CompoundTag tag) {
        if (feBuffer > 0) tag.putInt(KEY_FE, feBuffer);
    }
}
