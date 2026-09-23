package com.mofengbaizhi.tinkersnewlife.content.block;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
 * <b>万用能量转化器</b>的方块实体 —— <b>装了机械动力（Create）时注册的那一支</b>（§559）。
 *
 * <h2>它为什么必须继承 {@code KineticBlockEntity}</h2>
 * Create 的传动杆只对**相邻的动能方块**建立连接 ✗ —— 判定方式是"那格的方块实体是不是
 * {@code KineticBlockEntity}"（外加方块要实现 {@code IRotate} ✓ 见
 * {@link CreateEnergyConverterBlock}）✓。
 * §557 那种"只读相邻转速"的做法（那个适配器类已随 §598 整体删除 ✗）**不会**让传动杆真的接过来 ✓
 * ⇒ 必须让本方块<b>自己就是动能方块</b> ✓ —— 这正是用户口径「传动杆要能接上」的意思 ✓。
 *
 * <h2>核到的真实类名 / 方法名（出处：{@code libs/create-1.20.1-6.0.8.jar}，逐个 javap ✓）</h2>
 * <pre>
 *   com.simibubi.create.content.kinetics.base.KineticBlockEntity
 *       KineticBlockEntity(BlockEntityType&lt;?&gt;, BlockPos, BlockState)      ← 构造签名 ✓
 *       public static void tick(Level, BlockPos, BlockState, KineticBlockEntity)  ← 它自己的 tick ✓
 *       float getSpeed()             ← 本格**当前**转速（RPM ✓）  ← 我们读它 ✓
 *       float getTheoreticalSpeed()  ← 目标转速（平滑用 ✓）
 *       int   getRotationAngleOffset(Direction.Axis)
 *   com.simibubi.create.content.kinetics.base.KineticBlock  implements IRotate
 *       KineticBlock(BlockBehaviour.Properties)
 *       boolean hasShaftTowards(LevelReader, BlockPos, BlockState, Direction)   ← 默认 true ✓
 *   com.simibubi.create.content.kinetics.base.IRotate
 *       boolean hasShaftTowards(LevelReader, BlockPos, BlockState, Direction)
 *       Direction.Axis getRotationAxis(BlockState)
 * </pre>
 * <p>⚠ <b>为什么不覆写 {@code getGeneratedSpeed()}</b> ✗：那个是"我**产生**多少转速"
 * （风车/发电机才该覆写 ✓）。我们是<b>被动接收方</b> ⇒ 覆写它会让 Create 以为我们是动力源、
 * 去驱动别的机器 ✗。我们读 {@code getSpeed()}（被传动杆带成多少转 ✓）就对了 ✓。
 *
 * <h2>换算与取舍</h2>
 * {@code FE/t = (45 × RPM) / 64} ✓（用户口径 ✓ 常量在 {@code EnergyUnits.Fe} ✓）；
 * <b>仍然"读转速、不扣转速"</b> ✓（Create 的动能系统里没有"消耗转速"这个概念 ✓
 * ⇒ 一根轴可以同时喂多台转化器 ✓ —— 这是 §557 就写明的取舍 ✓ 未变 ✓ 要改成扣应力请说一声 ✓）。
 *
 * <h2>隔离（没装 Create 的玩家为什么不会崩）</h2>
 * <b>本类与 {@link CreateEnergyConverterBlock} 是唯一 import {@code com.simibubi.create.*} 的地方</b> ✓
 * 而且它们<b>只</b>在 {@code ModList.isLoaded("create")} 为真时被注册/实例化 ✓
 * （见 {@code ModBlocks} / {@code ModBlockEntities} 的分派 ✓）
 * ⇒ 没装 Create 时这两个类永不被 JVM 加载 ✓（{@code NoClassDefFoundError} 无从发生 ✓）。
 */
public class CreateEnergyConverterBlockEntity extends KineticBlockEntity implements ConverterCoreHolder {

    /**
     * §595 <b>应力影响</b>（Create 口径 ✓）：本方块靠传动杆驱动 ⇒ 像机器一样**吃应力** ✓
     * （总应力 = 影响 × 转速 ✓ ⇒ 转得越快、吃得越多 ✓ 一根轴喂多台就得多出力 ✓）。
     * <p>API 由 javap 实核 ✓：{@code KineticBlockEntity#calculateStressApplied():float} ✓
     * （基类默认从 {@code BlockStressValues} 取 ✓ 我们没在那边登记 ⇒ 必须自己覆写 ✓ 否则恒 0 = 白嫖动力 ✗）。
     * <p>取值：**4.0**（与机械压力机同量级 ✓ 想调只改这一个数 ✓）。
     */
    @Override
    public float calculateStressApplied() {
        return 4.0F;
    }

    /** 共享核心（状态 + 逻辑 ✓ 宿主就是本对象 ✓） */
    private final EeConverterCore core = new EeConverterCore(this);

    /** 对外暴露的 FE 能力句柄（与普通那一支同一口径 ✓） */
    private final LazyOptional<IEnergyStorage> feHolder = LazyOptional.of(() -> core);

    public CreateEnergyConverterBlockEntity(BlockPos pos, BlockState state) {
        // §561：现在**只有一个** BE 类型常量 ✓（选哪一支收在
        // EnergyConverterModBridges#createBlockEntityType 里 ✓）
        super(com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities.ENERGY_CONVERTER.get(), pos, state);
        // §559：把"读自己的转速"接给核心 ⇒ 核心那边就不必认识 Create 的类 ✓
        this.// §576 只认"正面贴着动能方块"的转速 ✗ —— 不再依赖 Create 的 hasShaftTowards（实测"哪面都能驱动" ✗）
        core.setRpmSource(() -> {
            net.minecraft.world.level.block.state.BlockState st = getBlockState();
            net.minecraft.core.Direction front =
                    st.getValue(com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterBlock.FACING);
            if (getLevel() == null) return 0.0F;
            if (!(getLevel().getBlockEntity(getBlockPos().relative(front))
                    instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity)) {
                return 0.0F;   // 正面没有动能方块（传动杆/机器）⇒ 视为"没接杆" ✓
            }
            return getSpeed();
        });
    }

    @Override
    public EeConverterCore converterCore() {
        return core;
    }

    // ============================================================
    //  服务端 tick
    // ============================================================

    /**
     * <b>服务端 tick 的入口 —— 直接覆写 {@code KineticBlockEntity#tick()}</b> ✓（§559 踩坑后的正解）。
     *
     * <h3>⚠ 为什么不是"在方块类里挂 ticker"</h3>
     * 因为 {@code KineticBlock} 的父类是 {@code net.minecraft.world.level.block.Block} ✗
     * （**不是** {@code BaseEntityBlock} ✗）⇒ 它没有 {@code getTicker} 可覆写 ✗
     * （我第一版写了，javac 报"方法不会覆盖或实现超类型的方法" ✗）。
     * <p>而 {@code KineticBlockEntity.tick()} 是 <b>public 实例方法</b>（javap 核实 ✓）、
     * 而且 Create 自己会在服务端每 tick 调它 ✓ ⇒ 我们只要覆写它、先调 {@code super.tick()}
     * 再跑转化器那一段就行 ✓ —— 比"自己挂 ticker"还省事 ✓ 也不用额外注册 ✓。
     */

    private int syncTimer = 0;
    private int lastSyncedFe = -1;

    /** §593 每 10 tick 查一次 FE 池 ⇒ 变了就 `sendData()` ✓（Create 的 SyncedBlockEntity 那条路 ✓） */
    void syncIfChanged() {
        if (++syncTimer < 10) return;
        syncTimer = 0;
        int now = core.getEnergyStored();
        if (now == lastSyncedFe) return;
        lastSyncedFe = now;
        if (level != null && !level.isClientSide) sendData();
    }

    @Override
    public void tick() {
        super.tick();                                          // ① Create 的动能逻辑（转速传播/平滑 ✓）
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            core.tick(server, worldPosition);                  // ② 转化器本体（收 / 折 / 推 ✓）
            syncIfChanged();   // §593 FE 池变化时同步给客户端 ✓（Create 的 sendData ✓）
        }
    }

    // ============================================================
    //  capability / 生命周期（与普通那一支**逐字同一套** ✓）
    // ============================================================

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> modBoost = EnergyConverterModBridges.capability(core, cap, side);
        if (modBoost != null) return modBoost;
        if (cap == ForgeCapabilities.ENERGY) {                                    // §580 六面角色（FE 单向）
            net.minecraft.world.level.block.state.BlockState st = getBlockState();
            if (com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.isBack(st, side)) {
                return LazyOptional.of(() -> (IEnergyStorage) core.feOutput).cast();      // 背面 = 唯一输出（只放不收 ✓）
            }
            if (com.mofengbaizhi.tinkersnewlife.content.block.EnergyConverterFaces.allowsFeIn(st, side)) {
                return LazyOptional.of(() -> (IEnergyStorage) core.feInput).cast();       // 右/底 = 输入（只收不放 ✓）
            }
            return LazyOptional.empty();                                                 // 其余面不暴露 FE ✗
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        feHolder.invalidate();
        EnergyConverterModBridges.invalidate(core);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        EnergyConverterModBridges.onLoad(core);
    }

    /**
     * ⚠ <b>不能覆写 {@code setRemoved()}</b> ✗ —— Create 在这个版本里把它标成了
     * {@code final}（javac 实测："被覆盖的方法为 final" ✗）⇒
     * 拆方块时的"毁掉 AE2 节点"改挂 {@link #onChunkUnloaded()} 与
     * {@code invalidateCaps()}（两处都调 ✓ 覆盖"区块卸载"与"能力作废"两条路 ✓）。
     */
    @Override
    public void onChunkUnloaded() {
        EnergyConverterModBridges.onUnload(core);
        super.onChunkUnloaded();
    }

    // ============================================================
    //  存档 / 同步（全转发给核心 ✓）
    // ============================================================

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);            // Create 自己的动能数据（转速缓存等 ✓）
        core.save(tag);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        core.load(tag);
    }

    /**
     * ⚠ <b>不覆写 {@code getUpdatePacket()}</b> ✗ —— Create 的父类
     * {@code SyncedBlockEntity} 已经把它实现成返回 {@code ClientboundBlockEntityDataPacket}（更具体的类型 ✓），
     * 我再按原版签名覆写会"返回类型不兼容"✗（javac 实测 ✓）⇒ 直接用 Create 那一份 ✓
     * （两边的 {@code write/read} 已经在上面接好了 ✓ 所以包里的内容是对的 ✓）。
     */
}
