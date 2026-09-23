package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.menu.ElderManaPedestalItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <b>魔力台座上段（悬浮格）的方块实体</b>（§607）。
 *
 * <h2>它干什么</h2>
 * 只做一件事：把 <b>{@code ITEM_HANDLER} 能力转发给正下方那台魔力台座</b> ✓
 * ⇒ 漏斗 / 管道**接到这一格**（而不是接台座本体）时也能塞取水晶 ✓（用户口径 ✓）。
 *
 * <h2>⚠ 转发的是"门控版"handler ✓</h2>
 * 用的是 {@code new ElderManaPedestalItemHandler(be, true)} ✓ —— 与台座本体暴露出去的那一份**同款** ✓
 * ⇒ §606 的规则照样生效：**自动化只有在水晶充满时才取得走** ✓ 手动右键不受限 ✓。
 *
 * <h2>代理是"每次现查"的，不是缓存的 ✓</h2>
 * {@link Proxy} 每次调用都重新看一遍下面那格 ✓ ⇒ 台座被拆/被换/区块没加载时**自然返回空** ✓
 * 不会抱住一个失效的台座 ✗（也就不用管"台座换了一个"这种边界 ✓）。
 *
 * <h2>生命周期</h2>
 * 服务端每 tick 看一次：下面**不是**台座 ⇒ 自己消失 ✓（爆炸/活塞把台座弄没时不留下孤儿 ✓）。
 * 本类**没有需要存档的状态** ✓（save/load 都不用写 ✓）。
 */
public class ElderManaPedestalTopBlockEntity extends BlockEntity {

    /** 物品容器句柄（内容随下面台座走 ✓ 本对象只负责"转" ✓） */
    private LazyOptional<IItemHandler> itemHolder = LazyOptional.of(() -> new Proxy(this));

    public ElderManaPedestalTopBlockEntity(BlockPos pos, BlockState state) {
        super(com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities.ELDER_MANA_PEDESTAL_TOP.get(), pos, state);
    }

    /** 服务端每 tick：下面还是台座就什么都不做 ✓ 不是就自毁 ✓ */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ElderManaPedestalTopBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (!(server.getBlockState(pos.below()).getBlock() instanceof ElderManaPedestalBlock)) {
            server.removeBlock(pos, false);
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        // §607 六面都给 ✓（与台座本体的口径一致 ✓）
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHolder.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHolder.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHolder = LazyOptional.of(() -> new Proxy(this));
    }

    // ============================================================
    //  转发层
    // ============================================================

    /**
     * 把调用原样转给"下方台座那一份**门控版** handler"✓。
     * <p>下面不是台座（或区块还没加载）⇒ {@code getSlots() == 0} ✓、塞进来的原样退回 ✓、取出去的是空 ✓
     * —— 也就是"这一格现在没有容器"✓ 管道那边看到的就是个空容器 ✓ 不会崩不会丢东西 ✓。
     */
    private static final class Proxy implements IItemHandlerModifiable {

        private final ElderManaPedestalTopBlockEntity top;

        Proxy(ElderManaPedestalTopBlockEntity top) {
            this.top = top;
        }

        /** 现查下面的台座 ✓ 没有 ⇒ null ✓ */
        @Nullable
        private ElderManaPedestalItemHandler delegate() {
            final Level level = top.getLevel();
            if (level == null) return null;
            final BlockPos below = top.getBlockPos().below();
            return level.getBlockEntity(below) instanceof ElderManaPedestalBlockEntity pedestal
                    ? new ElderManaPedestalItemHandler(pedestal, true)   // gated=true ✓ 与台座本人一致 ✓
                    : null;
        }

        @Override
        public int getSlots() {
            final ElderManaPedestalItemHandler d = delegate();
            return d == null ? 0 : d.getSlots();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            final ElderManaPedestalItemHandler d = delegate();
            return d == null ? ItemStack.EMPTY : d.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            final ElderManaPedestalItemHandler d = delegate();
            return d == null ? stack : d.insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            final ElderManaPedestalItemHandler d = delegate();
            return d == null ? ItemStack.EMPTY : d.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            final ElderManaPedestalItemHandler d = delegate();
            return d == null ? 0 : d.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            final ElderManaPedestalItemHandler d = delegate();
            return d != null && d.isItemValid(slot, stack);
        }

        /** 界面那条路会用到 ✓（自动化本身不用 ✓）—— 转给台座 ✓ */
        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            final ElderManaPedestalItemHandler d = delegate();
            if (d != null) d.setStackInSlot(slot, stack);
        }
    }
}
