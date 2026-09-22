package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * <b>古老者水晶方块</b>的方块实体：存 {@code EE}（晶能），容量
 * {@link ElderCrystalStorage#BLOCK_CAPACITY} = 4000 EE。
 *
 * <h2>NBT（键 {@code EE}，与水晶物品同一个键 ✓）</h2>
 * 方块实体只存这一个数字 ✓ —— 因为原版方块物品的通用约定就是
 * "挖掉 → 战利品表 {@code copy_nbt(source=block_entity)} → 物品的 {@code BlockEntityTag}；
 * 放下 → {@code BlockItem#updateCustomBlockEntityTag} → 方块实体"
 * （潜影盒同款 ✓），所以<b>挖掉掉自己、EE 一起带走</b>不需要我们写任何搬运代码 ✓。
 *
 * <h2>⭐ 本轮（P1）只留接口（用户口径）</h2>
 * 「魔力台座 / 能量转化系统」<b>后续再做</b> ⇒ 这里只把两个入口留好、<b>不做</b>任何自动充放能：
 * <ul>
 *   <li>{@link #absorb(int)} —— 往方块里灌 EE，返回<b>实际接受</b>的量（装不下就退回 ✓）；</li>
 *   <li>{@link #query()} / {@link #getEe()} / {@link #getCapacity()} —— 查询接口。</li>
 * </ul>
 * 将来做台座时，只要对着这两个方法写调用方即可 ✓ 不用再动方块/方块实体本体。
 */
public class ElderCrystalBlockEntity extends BlockEntity {

    /** 当前存储的 EE（0 ~ {@link ElderCrystalStorage#BLOCK_CAPACITY}） */
    private int ee = 0;

    public ElderCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELDER_CRYSTAL_BLOCK.get(), pos, state);
    }

    // ============================================================
    //  查询接口
    // ============================================================

    /** 当前存量 */
    public int getEe() {
        return ee;
    }

    /** 容量（4000） */
    public int getCapacity() {
        return ElderCrystalStorage.BLOCK_CAPACITY;
    }

    /** 剩余可存入量 */
    public int getSpace() {
        return Math.max(0, getCapacity() - ee);
    }

    public boolean isEmpty() {
        return ee <= 0;
    }

    /** 存量占比 0~1（将来渲染/光照/台座用） */
    public float fillRatio() {
        return getCapacity() <= 0 ? 0.0F : (float) ee / (float) getCapacity();
    }

    /**
     * <b>Query 接口</b>：一次性把存量/容量读出来（将来台座/玉/JEI 之类的查询方用它 ✓
     * 免得各自去摸字段 ✗）。
     */
    public Query query() {
        return new Query(ee, getCapacity());
    }

    /** 查询结果（不可变 ✓ 与 EE 单位无关，就是原始数字 ✓） */
    public record Query(int stored, int capacity) {
        public int space() {
            return Math.max(0, capacity - stored);
        }

        public float ratio() {
            return capacity <= 0 ? 0.0F : (float) stored / (float) capacity;
        }

        public boolean full() {
            return stored >= capacity;
        }
    }

    // ============================================================
    //  写入接口
    // ============================================================

    /**
     * <b>absorb 接口</b>：把 {@code amount} 点 EE 灌进方块。
     *
     * @return 实际接受的量（0 = 满了/参数非法；<b>装不下的部分不会被吞掉</b> ✓ 由调用方处置）
     */
    public int absorb(int amount) {
        if (amount <= 0) return 0;
        int accept = Math.min(amount, getSpace());
        if (accept <= 0) return 0;
        setEe(ee + accept);
        return accept;
    }

    /** 直接写存量（自动夹在 0~容量之间 ✓ 会 setChanged ✓） */
    public void setEe(int value) {
        int next = Math.max(0, Math.min(value, getCapacity()));
        if (next == ee) return;
        ee = next;
        setChanged();
    }

    // ============================================================
    //  存档
    // ============================================================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // ⚠ 键名必须与物品侧完全一致（ElderCrystalStorage.KEY_EE）——
        //   战利品表的 copy_nbt 是"整包搬运方块实体 NBT"，键名对不上就等于丢电 ✗
        if (ee > 0) tag.putInt(ElderCrystalStorage.KEY_EE, ee);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ee = Math.max(0, Math.min(tag.getInt(ElderCrystalStorage.KEY_EE), getCapacity()));
    }

    /** 同步包（客户端渲染器/玉将来要读存量时不必再加通道 ✓ 数据量极小 ✓） */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (ee > 0) tag.putInt(ElderCrystalStorage.KEY_EE, ee);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}
