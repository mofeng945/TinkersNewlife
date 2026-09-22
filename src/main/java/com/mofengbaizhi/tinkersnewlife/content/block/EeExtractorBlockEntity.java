package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeCapabilityBridge;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorage;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorages;
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
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <b>EE 抽取方块</b>（{@code tinkersnewlife:ee_extractor}，§557）的方块实体。
 *
 * <h2>它干什么（一句话）</h2>
 * <b>把相邻方块里的 EE 抽进自己的缓冲，再主动推给相邻方块</b> —— 全程 <b>push 模式</b> ✓
 * （用户口径：「抽出来……然后主动推给相邻方块」✓）。
 *
 * <h2>每 tick 的两步（顺序固定 ✓）</h2>
 * <ol>
 *   <li><b>抽</b>：{@link EeStorages#pullAround} —— 上/下/北/南/西/东 <b>固定顺序</b>找
 *       {@link EeStorage}，合计最多抽 {@link ModConfig#eeExtractorPullPerTick()} 点
 *       （<b>跨邻居合计</b>上限 ✓ 不是每个邻居各一份 ✗ 免得 6 个邻居叠出 6 倍速率）；
 *       抽之前先 simulate 问"两边都同意吗" ⇒ 不会出现"抽出来装不下"的空中蒸发 ✗；</li>
 *   <li><b>推</b>：{@link EeStorages#pushAround} —— 同样是固定顺序，合计最多推
 *       {@link ModConfig#eeExtractorPushPerTick()} 点，并<b>跳过这一步刚抽过的那些方块</b> ✓
 *       （{@link #lastPulled}）—— 否则"从 A 抽出来、又还回 A"就成了每 tick 原地打转的空转 ✗。</li>
 * </ol>
 * <p>⚠ 顺序是"先抽后推"而不是反过来 ✓：这样同一 tick 内"刚进来的电"就能立刻流给下游 ✓
 * 玩家看到的是"接上就通"而不是"先等一拍" ✓。
 *
 * <h2>缓冲与上限</h2>
 * <ul>
 *   <li>缓冲 {@link ModConfig#eeExtractorBuffer()}（默认 <b>4000 EE</b> = 一块水晶方块的量 ✓
 *       —— 恰好"能整块装下一块满的水晶方块" ✓ 也正好是"上游被拆/下游塞满"时的最大积压 ✓）；</li>
 *   <li>抽/推速率各自独立（都是 <b>EE/tick</b> ✓ 默认 256 / 256）—— 抽得快推得慢时缓冲会涨，
 *       涨满后<b>自然停抽</b>（{@link #insertEe} 返回 0 ⇒ 上游不再被扣 ✓）✓ 不会吞电 ✗。</li>
 * </ul>
 *
 * <h2>⚠ 一个有意留下的"不抽"：台座上的那颗水晶物品</h2>
 * 台座（{@code ElderManaPedestalBlockEntity}）对外的 {@code getEe()} <b>只暴露它自己的缓存</b>，
 * <b>不</b>包含"悬浮在台座上方那颗水晶物品里的 EE" ✗ —— 那件东西是玩家的（挖掉台座会原样掉出来 ✓）。
 * 想抽水晶里的电就直接把<b>水晶方块</b>摆在抽取方块旁边 ✓（那条路完全通 ✓）。
 */
public class EeExtractorBlockEntity extends BlockEntity implements EeStorage {

    /** 缓冲的存档键（EE，double ✓ 与台座的 cache 同一口径 ✓） */
    public static final String KEY_BUFFER = "EeBuffer";

    /** 缓冲里的 EE（≤ {@link ModConfig#eeExtractorBuffer()} ✓ 持久化 ✓） */
    private double buffer = 0.0D;

    /**
     * 本 tick 刚"抽过"的那些邻居的方块实体（推的时候要跳过它们 ✓ 见类注释第 2 步）；
     * 每 tick 开头清空 ✓。
     * <p>为什么用一个集合而不是一个坐标：一 tick 内可能从<b>好几个</b>邻居各抽一笔 ✓
     * （例如水晶方块 + 台座缓存）⇒ 只记"最后一个"会让前几个被原路还回去 ✗。
     */
    private final java.util.Set<BlockEntity> pulledFrom =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public EeExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EE_EXTRACTOR.get(), pos, state);
    }

    /** 方块注册用的 ticker（见 {@code EeExtractorBlock#getTicker} ✓ 只在服务端挂 ✓） */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EeExtractorBlockEntity be) {
        if (level instanceof ServerLevel server) be.tick(server, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
        pulledFrom.clear();

        // ① 抽（最多 pull_ee_per_tick 点，跨邻居合计 ✓；sinkFor 顺手记下"这批电是从哪来的" ✓）
        EeStorages.pullAround(level, pos, this, ModConfig.eeExtractorPullPerTick(), this::sinkFrom);

        // ② 推（跳过刚抽过的那些 ✓ 见类注释）
        EeStorages.pushAround(level, pos, this, ModConfig.eeExtractorPushPerTick(),
                pulledFrom::contains);
    }

    /**
     * {@link EeStorages#pullAround} 的回调：返回一个"抽入时顺手记下来源"的包装视图 ✓
     * （真正的搬运仍由 {@code pullAround} 走 {@link #insertEe}/{@link #extractEe} 完成 ✓
     * 这里<b>不</b>再搬一次 ✗）。
     */
    private EeStorage sinkFrom(BlockPos from, EeStorage real) {
        return new SinkView(from, real);
    }

    /** {@link #sinkFrom} 的实体（每次抽新建一个，无状态泄漏 ✓） */
    private final class SinkView implements EeStorage {
        private final BlockPos from;
        private final EeStorage real;

        private SinkView(BlockPos from, EeStorage real) {
            this.from = from;
            this.real = real;
        }

        @Override
        public int getEe() {
            return real.getEe();
        }

        @Override
        public int getCapacity() {
            return real.getCapacity();
        }

        @Override
        public int insertEe(int amount, boolean simulate) {
            int accepted = real.insertEe(amount, simulate);
            if (!simulate && accepted > 0) recordSource();
            return accepted;
        }

        @Override
        public int extractEe(int amount, boolean simulate) {
            return real.extractEe(amount, simulate);
        }

        /** 记下"这一笔是从 {@link #from} 那个邻居抽来的"（推的时候要跳过它 ✓） */
        private void recordSource() {
            BlockEntity be = level == null ? null : level.getBlockEntity(from);
            if (be != null) pulledFrom.add(be);
        }
    }

    // ============================================================
    //  EeStorage
    // ============================================================

    @Override
    public int getEe() {
        return (int) Math.floor(buffer);
    }

    @Override
    public int getCapacity() {
        return ModConfig.eeExtractorBuffer();
    }

    @Override
    public int insertEe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        double room = getCapacity() - buffer;
        if (room <= 0.0D) return 0;
        int accepted = (int) Math.min(Math.floor(room), (double) amount);
        if (accepted <= 0) return 0;
        if (!simulate) {
            buffer += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public int extractEe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int take = (int) Math.min(Math.floor(buffer), (double) amount);
        if (take <= 0) return 0;
        if (!simulate) {
            buffer -= take;
            setChanged();
        }
        return take;
    }

    // ============================================================
    //  Forge 能量能力（只读面 ✓ 让别的模组能"看到"本方块存了多少电）
    // ============================================================

    /**
     * 只读的 FE 视图（{@code 1 FE = 8 EE} 折算 ✓）。
     * <p>⚠ <b>不能抽</b>（见 {@link EeCapabilityBridge} 的类注释 ✓）：外模组想拿这里的电，
     * 请走本模组的 EE 接口（抽取方块 / 转化器 / 以后任何"EE 管道" ✓）。
     */
    private final LazyOptional<net.minecraftforge.energy.IEnergyStorage> feHolder =
            LazyOptional.of(() -> EeCapabilityBridge.readOnly(this, this::setChanged));

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

    // ============================================================
    //  存档
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (buffer > 0.0D) tag.putDouble(KEY_BUFFER, buffer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        double cap = getCapacity();
        buffer = tag.contains(KEY_BUFFER) ? Math.max(0.0D, Math.min(tag.getDouble(KEY_BUFFER), cap)) : 0.0D;
    }

    /** 区块加载时同步（数值本身目前客户端不显示 ✓ 但让数据一致，以后要画就不用再改这条路 ✓） */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (buffer > 0.0D) tag.putDouble(KEY_BUFFER, buffer);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    /**
     * 运行中改动时的同步包（覆写理由与台座 §556 一致：{@code BlockEntity} 默认返回 {@code null}
     * ⇒ 什么都不发 ✗）。本方块目前没有客户端读取者，留着是为了"以后加 HUD 不用再动这条路" ✓。
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
