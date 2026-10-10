package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * <b>伟大白色空间传送门</b>（§660）的方块实体：只存一件事 —— <b>这扇门通向哪里</b>。
 *
 * <p>为什么必须用方块实体：门位在世界上是「一扇固定方块」，但目的地是<b>任意</b>的
 * （主世界某坐标 / 白色空间某坐标 / GUI 里手填的任意维度坐标），方块状态放不下 ⇒ 只能存 NBT ✓。
 *
 * <p>只存 4 个标量（维度 id 字符串 + x/y/z 三个 int）：
 * <ul>
 *   <li>不用 {@code BlockPos.asLong()} 压缩 —— y 的编码范围只有 ±2048，
 *       万一以后想开一扇通往 y=3000 的门就会静默错位 ✗；三个 int 没有这个上限 ✓。</li>
 *   <li>{@code getUpdateTag} 把它同步给客户端 —— 现在客户端不读它，但以后要做
 *       「门上飘一行目的地文字」之类的渲染就不必再加通道 ✓ 数据量极小 ✓。</li>
 * </ul>
 */
public class WhiteSpacePortalBlockEntity extends BlockEntity {

    private static final String KEY_DIM = "TargetDim";
    private static final String KEY_X = "TargetX";
    private static final String KEY_Y = "TargetY";
    private static final String KEY_Z = "TargetZ";

    /** 目的地维度；未设置（旧存档 / 异常）时为 null ⇒ 这扇门不传送 */
    @Nullable
    private ResourceKey<Level> targetDimension;

    /**
     * 目的地坐标；未设置为 {@code null}。
     * <p>⚠ 刻意用 {@code null} 表示"没设过"，<b>不用</b> {@code BlockPos.ZERO} 当哨兵 ✗
     * —— {@code (0, 0, 0)} 是合法坐标，用 ZERO 当哨兵会让"通往原点的门"变成一扇死门 ✗。
     */
    @Nullable
    private BlockPos targetPos;

    public WhiteSpacePortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WHITE_SPACE_PORTAL.get(), pos, state);
    }

    // ============================================================
    //  目的地
    // ============================================================

    public void setDestination(ResourceKey<Level> dimension, BlockPos pos) {
        this.targetDimension = dimension;
        this.targetPos = pos.immutable();
        setChanged();
    }

    @Nullable
    public ResourceKey<Level> getDestinationDimension() {
        return targetDimension;
    }

    /** 目的地坐标（没设过就是 {@code (0,0,0)}；调用方应先问 {@link #hasDestination()}） */
    public BlockPos getDestinationPos() {
        return targetPos == null ? BlockPos.ZERO : targetPos;
    }

    /** 这扇门是不是真的能用（目的地已设置） */
    public boolean hasDestination() {
        return targetDimension != null && targetPos != null;
    }

    // ============================================================
    //  存档 / 同步
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (targetDimension != null && targetPos != null) {
            tag.putString(KEY_DIM, targetDimension.location().toString());
            tag.putInt(KEY_X, targetPos.getX());
            tag.putInt(KEY_Y, targetPos.getY());
            tag.putInt(KEY_Z, targetPos.getZ());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ResourceLocation id = tag.contains(KEY_DIM) ? ResourceLocation.tryParse(tag.getString(KEY_DIM)) : null;
        if (id == null) {
            targetDimension = null;
            targetPos = null;
            return;
        }
        targetDimension = ResourceKey.create(Registries.DIMENSION, id);
        targetPos = new BlockPos(tag.getInt(KEY_X), tag.getInt(KEY_Y), tag.getInt(KEY_Z));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    /**
     * ⚠ <b>必须覆写</b>：默认实现返回 {@code null} ⇒ 方块被放置／目的地被改写时，
     * 服务端<b>不会</b>把方块实体数据发给客户端 ✗（新放下的门要等下一次区块重发才有目的地）。
     * <p>发出去的路径是 {@code ServerLevel#sendBlockUpdated} → {@code ChunkHolder#blockChanged}
     * → {@code broadcastBlockEntityIfNeeded} → {@code getUpdatePacket()}（告示牌/旗帜都是这一套 ✓）；
     * 客户端收到后走 Forge 的 {@code IForgeBlockEntity#onDataPacket} → {@link #handleUpdateTag} → {@link #load} ✓。
     */
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
    getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}
