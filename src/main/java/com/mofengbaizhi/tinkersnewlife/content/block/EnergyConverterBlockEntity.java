package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <b>万用能量转化器</b>（{@code tinkersnewlife:energy_converter}）的方块实体 ——
 * <b>没装机械动力（Create）时注册的那一支</b>（§559 起）。
 *
 * <h2>本类只有"壳"的职责</h2>
 * 状态与逻辑全在 {@link EeConverterCore}（一个纯 Java 类 ✓）里 ✓
 * 本类只做四件事：① 持有那个核心 ✓ ② 把 capability 转发给它 / 给两个可选模组的桥 ✓
 * ③ 把 NBT 转发给它 ✓ ④ 把 ticker 接到它 ✓。
 *
 * <h2>⚠ 为什么不能直接把核心继承进来（§559 的关键取舍 ✗）</h2>
 * 因为 Create 那一支<b>必须</b>继承 {@code KineticBlockEntity} ✗（否则传动杆不会连过来 ✓），
 * 而 Java 不能多继承 ✗ ⇒ "普通支"与"Create 支"只能是<b>两个都 extends 了不同父类的壳</b> ✓
 * ⇒ 共享的东西（状态/逻辑）就只能放在"被持有"的核心类里 ✓。
 * <p>两个壳的办法不一样（这是有意的 ✓）：
 * <ul>
 *   <li><b>本类</b>：{@code extends BlockEntity} + <b>持有一个</b> {@link EeConverterCore} ✓；</li>
 *   <li><b>Create 那一支</b>：{@code extends KineticBlockEntity} + 同样<b>持有一个</b>
 *       {@link EeConverterCore} ✓（它自己就是方块实体 ⇒ 把 {@code this} 交给核心当宿主 ✓）。</li>
 * </ul>
 *
 * <h2>三种输入怎么接上（§559 的核心）</h2>
 * <ol>
 *   <li><b>通用机械（J）</b>：暴露 {@code Capabilities.STRICT_ENERGY} ⇒ <b>它的线缆能主动推进来</b> ✓；</li>
 *   <li><b>应用能源（AE）</b>：暴露 {@code appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST}
 *       ⇒ <b>AE2 的线缆会与我们建网格连接</b>，我们就能从网格取电 ✓；</li>
 *   <li><b>机械动力（RPM）</b>：换成 {@code CreateEnergyConverterBlockEntity}（动能方块）⇒ 传动杆能接上 ✓。</li>
 * </ol>
 * ⚠ 前两条走 {@link EnergyConverterModBridges}（中立分派点 ✓ 只有它在
 * {@code ModList.isLoaded(...)} 之后才碰那两家的类 ✓）⇒ 没装那家的玩家不会崩 ✓。
 *
 * <h2>设计（§557 定的，§558/§559 一个字没改 ✓）</h2>
 * 单向：各种能量 ⇒ FE（**不做** FE ⇒ EE 的反向 ✗）；FE 池 + {@code converter_output_fe_per_tick}
 * 单一硬闸门；推给邻居用 {@code IEnergyStorage#receiveEnergy} ✓。
 */
public class EnergyConverterBlockEntity extends BlockEntity implements ConverterCoreHolder {

    /** 共享核心（状态 + 逻辑 ✓ 见类注释） */
    private final EeConverterCore core = new EeConverterCore(this);

    /** 对外暴露的 FE 能力句柄（懒加载 ✓ 失效时 invalidate ✓） */
    private final LazyOptional<IEnergyStorage> feHolder = LazyOptional.of(() -> core);

    public EnergyConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENERGY_CONVERTER.get(), pos, state);
    }

    /** 共享核心（两个可选模组的桥要从它读/写 FE 池 ✓；中立分派点也通过本接口取它 ✓） */
    @Override
    public EeConverterCore converterCore() {
        return core;
    }

    /** 方块注册用的 ticker（见 {@code EnergyConverterBlock#getTicker} ✓ 只在服务端挂 ✓） */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EnergyConverterBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            be.core.tick(server, pos);
        }
    }

    // ============================================================
    //  capability / 生命周期
    // ============================================================

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> modBoost = EnergyConverterModBridges.capability(core, cap, side);
        if (modBoost != null) return modBoost;
        if (cap == ForgeCapabilities.ENERGY) return feHolder.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        feHolder.invalidate();
        EnergyConverterModBridges.invalidate(core);
    }

    /** 区块加载 ⇒ AE2 的网格节点在这里建（没装 AE2 ⇒ 分派点直接返回 ✓） */
    @Override
    public void onLoad() {
        super.onLoad();
        EnergyConverterModBridges.onLoad(core);
    }

    /** 区块卸载 / 方块被拆 ⇒ 毁掉节点 ✓ */
    @Override
    public void setRemoved() {
        EnergyConverterModBridges.onUnload(core);
        super.setRemoved();
    }

    // ============================================================
    //  存档 / 同步（全转发给核心 ✓）
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        core.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        core.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        core.writeSyncTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
