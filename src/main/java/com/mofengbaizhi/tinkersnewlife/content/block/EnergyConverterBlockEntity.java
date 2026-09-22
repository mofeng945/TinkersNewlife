package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeCapabilityBridge;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorage;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorages;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyConverterContext;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyInputAdapter;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyInputs;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <b>万用能量转化器</b>（{@code tinkersnewlife:energy_converter}，§557）的方块实体。
 *
 * <h2>单向：各种能量 ⇒ FE（用户明确"单向"✓ 绝不做 FE ⇒ EE 的反向 ✗）</h2>
 * 每 tick 依次做三件事：
 * <ol>
 *   <li><b>收</b>：
 *     <ul>
 *       <li>相邻 EE 容器（{@link EeStorage} ✓ 抽取方块/台座/水晶方块）：抽 EE，
 *           按 {@code 1 EE = 0.125 FE} 折成 FE ✓；</li>
 *       <li>相邻 Forge Energy（{@code ForgeCapabilities.ENERGY}）：抽 FE ✓
 *           —— <b>RF / Tesla / μI / FF 走的就是这一路</b>（1:1 别名 ✓ 用户已确认 ✓）；</li>
 *       <li>四家模组（通用机械 J / Create RPM / IC2 EU / AE2 AE）：交给
 *           {@link EnergyInputs} 里的适配器 ✓ 汇率全在 {@link EnergyUnits.Fe} ✓。</li>
 *     </ul>
 *     收进来的东西一律先落到 {@link #feBuffer}（<b>FE 池</b>）✓ —— 这样"上游一会儿给一会儿不给"
 *     也能平滑地往外送 ✓。</li>
 *   <li><b>转</b>：EE 那条在收的时候就折好了 ✓（不留"半 FE"：不足 8 EE 的部分留在自己的
 *       {@link #eeInput} 里等下一次 ✓ 见 {@link #absorbEe}）；</li>
 *   <li><b>推</b>：把 {@link #feBuffer} 里最多 {@link ModConfig#converterOutputFePerTick()} FE
 *       <b>推给</b>相邻方块的 {@code IEnergyStorage.receiveEnergy} ✓（<b>push 模式</b> ✓ 用户口径）。</li>
 * </ol>
 *
 * <h2>为什么收 FE 用"我们主动抽"</h2>
 * 我们当然也<b>对外暴露</b> {@code IEnergyStorage}（别人可以往我们这里 {@code receiveEnergy} ✓
 * 见 {@code getCapability}）✓ 但"等着别人推"这条路在很多整合包里根本没人走 ✗ ——
 * 线缆、发电机、通用机械的机器都不会主动来找我们 ✓（通用机械自己的线缆就是<b>抽</b>机器的 ✓）。
 * ⇒ 两条路同时留着 ✓ 谁都不漏 ✓（而且我们的 {@code receiveEnergy} 与"主动抽"共用同一个上限，
 * 不会因为两条路并存就变成 2 倍速率 ✗）。
 *
 * <h2>上限（配置见 {@code [ee_network]}）</h2>
 * <ul>
 *   <li>{@code output_fe_per_tick}（默认 64）—— <b>总吞吐闸门</b>：收（电那一半）+ 推都不能超 ✓；</li>
 *   <li>{@code input_fe_per_tick}（默认 64）—— 直接 FE 输入这一条自己的上限 ✓；</li>
 *   <li>{@code input_ee_per_tick}（默认 512 EE/t ⇒ 折 64 FE/t）—— EE 那一条的软上限 ✓；</li>
 *   <li>{@code buffer_fe}（默认 32000）—— FE 池上限 ✓ 满了就不再收（上游自己会停 ✓）✓。</li>
 * </ul>
 * <p>⚠ 「软上限」是什么意思：{@code input_ee_per_tick} 只是"问邻居要多少"的额度 ✓
 * 真正的硬闸门是 {@code output_fe_per_tick} ✓（它同时管收与推）⇒ 三个键怎么配都不会串出
 * 超过 {@code output_fe_per_tick} 的对外功率 ✗（这也是"不会凭空发电"的保证 ✓）。
 */
public class EnergyConverterBlockEntity extends BlockEntity implements EeStorage, IEnergyStorage {

    /** FE 池的存档键（int ✓ FE 本来就是整数 ✓） */
    public static final String KEY_FE = "FeBuffer";

    /** EE 那半块的"没凑够 8 EE"余数（double ⇒ 收小数也照样累积 ✓）的存档键 */
    public static final String KEY_EE_IN = "EeInput";

    /** FE 池（≤ {@link ModConfig#converterBufferFe()} ✓ 持久化 ✓） */
    private int feBuffer = 0;

    /** 从相邻 EE 容器收进来、还没凑够 1 FE 的 EE（<b>小于 8</b> ✓；持久化 ⇒ 重启不丢那几 EE ✓） */
    private double eeInput = 0.0D;

    /**
     * <b>本 tick 已经收了多少 EE</b>（用于把 {@code input_ee_per_tick} 做成真正的"每 tick 额度"✓）。
     * <p>为什么不拿 {@code floor(eeInput)} 当"已用量" ✗：那个余数是<b>跨 tick</b>留下来的（0~7 ✓），
     * 拿它当额度会每 tick 少收几个 EE ✗。这个计数器每 tick 在 {@link #tick} 开头清零 ✓ 不存档 ✓。
     */
    private int eeThisTick = 0;

    /** 对外暴露的 FE 能力句柄（懒加载 ✓ 失效时要 invalidate ✓） */
    private final LazyOptional<IEnergyStorage> feHolder = LazyOptional.of(() -> this);

    public EnergyConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_CONVERTER.get(), pos, state);
    }

    /** 方块注册用的 ticker（见 {@code EnergyConverterBlock#getTicker} ✓ 只在服务端挂 ✓） */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyConverterBlockEntity be) {
        if (level instanceof ServerLevel server) be.tick(server, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
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
        int feBudget = Math.min(outputCap, ModConfig.converterInputFePerTick());
        if (feBudget > 0) {
            pullForgeEnergy(level, pos, feBudget);
        }

        // ①-C 四家模组适配器（J / RPM / EU / AE ⇒ 都折成 FE ✓）
        //      额度用"这一 tick 剩下的吞吐" ⇒ 多条路同时接上也不会串出超过 outputCap 的功率 ✗
        int used = Math.min(feBuffer, outputCap);
        int adapterBudget = Math.max(0, outputCap - used);
        if (adapterBudget > 0) {
            for (EnergyInputAdapter adapter : EnergyInputs.all()) {
                if (adapterBudget <= 0) break;
                int got = adapter.drainFe(ctx, adapterBudget, false);
                if (got <= 0) continue;
                int accepted = insertFe(got, false);
                adapterBudget -= accepted;
            }
        }

        // ③ 推给相邻方块（push 模式 ✓ 最多 output_fe_per_tick ✓）
        pushForgeEnergy(level, pos, outputCap);
    }

    // ============================================================
    //  FE 池（内部）
    // ============================================================

    /** 往 FE 池里放（返回实际接受的 ✓ 满了就拒收 ✓） */
    private int insertFe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int room = getMaxEnergyStored() - feBuffer;
        if (room <= 0) return 0;
        int accepted = Math.min(room, amount);
        if (!simulate) {
            feBuffer += accepted;
            setChanged();
        }
        return accepted;
    }

    /** 从 FE 池里取 */
    private int extractFe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int take = Math.min(feBuffer, amount);
        if (take <= 0) return 0;
        if (!simulate) {
            feBuffer -= take;
            setChanged();
        }
        return take;
    }

    /** 从相邻方块"抽" Forge Energy（我们要多少、它给多少，取小 ✓） */
    private void pullForgeEnergy(Level level, BlockPos pos, int budget) {
        int left = budget;
        for (Direction d : EeStorages.NEIGHBOURS) {
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
            if (left <= 0) break;
            BlockPos at = pos.relative(d);
            BlockEntity be = level.getBlockEntity(at);
            if (be == null || be == this) continue;
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
    private static IEnergyStorage energyAt(BlockEntity be, Direction side) {
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
        setChanged();
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

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        // ⚠ 与"主动抽"共用同一个上限：输出闸门与输入闸门都要看 ✓
        int cap = Math.min(ModConfig.converterOutputFePerTick(), ModConfig.converterInputFePerTick());
        int accepted = Math.min(maxReceive, cap);
        return insertFe(accepted, simulate);
    }

    /** 外人<b>不能</b>从转化器里把电抽回去 ✗（它是"输出端"✓ 抽走就等于把产物拿走了 —— 那由我们推 ✓） */
    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
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
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    // ============================================================
    //  capability / 存档 / 同步
    // ============================================================

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return EeCapabilityBridge.energyOrSuper(cap, side, feHolder, super.getCapability(cap, side));
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        feHolder.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (feBuffer > 0) tag.putInt(KEY_FE, feBuffer);
        if (eeInput > 0.0D) tag.putDouble(KEY_EE_IN, eeInput);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        feBuffer = Math.max(0, Math.min(tag.getInt(KEY_FE), getMaxEnergyStored()));
        eeInput = tag.contains(KEY_EE_IN) ? Math.max(0.0D, tag.getDouble(KEY_EE_IN)) : 0.0D;
    }

    /** 区块加载时同步（数值本身客户端暂不显示 ✓ 让数据一致，以后画数值不用再动这条路 ✓） */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (feBuffer > 0) tag.putInt(KEY_FE, feBuffer);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    /** 运行中改动时的同步包（覆写理由与台座 §556 一致：默认实现返回 null ⇒ 什么都不发 ✗） */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
