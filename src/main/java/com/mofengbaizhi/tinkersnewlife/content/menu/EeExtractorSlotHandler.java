package com.mofengbaizhi.tinkersnewlife.content.menu;

import com.mofengbaizhi.tinkersnewlife.content.block.EeExtractorBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;

/**
 * <b>EE 抽取方块那一个槽位的 {@code IItemHandler} 视图</b>（§558 建 · §562 修）。
 *
 * <h2>为什么要它</h2>
 * 本仓库既有的容器一律用 {@code SlotItemHandler}（见 {@code SilentGloveContainer} ✓），
 * 而 {@code SlotItemHandler} 只认 {@link IItemHandlerModifiable} ✗ —— 我们的"槽位"其实是
 * {@link EeExtractorBlockEntity} 上的一个 {@code ItemStack} 字段（<b>直接存进 BE 的 NBT</b> ✓ 用户要求 ✓）。
 *
 * <h2>⚠⚠ §562 崩溃教训（必须记住 ✗）</h2>
 * 第一版我只写了 {@code implements IItemHandler} ✗ ⇒ **一碰槽位就崩客户端** ✗：
 * <pre>
 * java.lang.ClassCastException: EeExtractorSlotHandler cannot be cast to IItemHandlerModifiable
 *     at net.minecraftforge.items.SlotItemHandler.m_5852_(SlotItemHandler.java:47)  ← set() 内部强转
 *     at ...EeExtractorMenu.m_7648_(EeExtractorMenu.java:160)                       ← clicked()
 * </pre>
 * <b>原因</b>：Forge 的 {@code SlotItemHandler#set} 内部是<b>无条件强转</b> ✗：
 * <pre>
 * public void set(ItemStack stack) { ((IItemHandlerModifiable) this.handler).setStackInSlot(slot, stack); }
 * </pre>
 * —— 它**不看**你实现了哪个接口 ✗ ⇒ 只要走到"放入 / 取出 / Shift 快速移动 / 数字键交换 / 丢弃键"
 * 这些会调 {@code Slot#set} 的路径，就一定 {@code ClassCastException} ✗。
 *
 * <p>⇒ <b>凡是喂给 {@code SlotItemHandler} 的 handler，必须是 {@code IItemHandlerModifiable}</b> ✓
 * （{@code ItemStackHandler} 就是它的实现 ✓ —— 所以仓库里另外两处
 * {@code StorageManager.BigStackHandler} / {@code SilentGloveHandler} 天生没问题 ✓；
 * <b>§562 审计下来只有本节这一处是手写的、漏了</b> ✗）。
 *
 * <p>⚠ 三个写入口（{@link #setStackInSlot} / {@link #insertItem} / {@link #extractItem}）现在
 * <b>全部只走</b> {@link EeExtractorBlockEntity#setSlotItem} 一个出口 ✓
 * ⇒ "槽位永远 ≤1 件 + 一定标脏 + 一定同步客户端"这三条规则只有一份实现 ✗。
 */
public final class EeExtractorSlotHandler implements IItemHandlerModifiable {

    private final EeExtractorBlockEntity be;

    public EeExtractorSlotHandler(EeExtractorBlockEntity be) {
        this.be = be;
    }

    @Override
    public int getSlots() {
        return 1;                                  // 就一个槽 ✓
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == EeExtractorBlockEntity.SLOT_INDEX ? be.getSlotItem() : ItemStack.EMPTY;
    }

    /**
     * <b>§562 补上的那一个方法</b> ✓ —— {@code SlotItemHandler#set} 内部强转之后调的就是它 ✓。
     * <p>⚠ 必须容忍<b>空栈</b> ✓（"取出来"的时候 Forge 就是拿 {@code ItemStack.EMPTY} 来写的 ✓，
     * {@code EeExtractorBlockEntity#setSlotItem} 已经把空栈规范成 {@code EMPTY} ✓）。
     * <p>⚠ 这里**不**套 {@link #insertItem} 那套"只收一件 + 校验能不能放"✗ ——
     * Forge 调它是为了"把这一格<b>变成</b>这个栈"（可能是空 ✓ 也可能是从光标上放下的东西 ✓），
     * <b>是否允许放</b>由菜单那一侧 {@code SlotItemHandler#mayPlace} 先行把关 ✓
     * （{@code mayPlace} 与 {@link #isItemValid} 用的是同一份判断 ✓）⇒ 这里如实写回即可 ✓。
     */
    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (slot != EeExtractorBlockEntity.SLOT_INDEX) return;
        be.setSlotItem(stack);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot != EeExtractorBlockEntity.SLOT_INDEX) return stack;
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        if (!be.getSlotItem().isEmpty()) return stack;             // 槽位已占 ⇒ 拒收（一次只放一件 ✓）
        if (simulate) {
            ItemStack one = stack.copy();
            one.setCount(1);
            return one;                                            // 只放得下 1 个 ⇒ 剩下的如实退回 ✓
        }
        ItemStack one = stack.copy();
        one.setCount(1);
        be.setSlotItem(one);
        ItemStack rest = stack.copy();
        rest.shrink(1);
        return rest;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != EeExtractorBlockEntity.SLOT_INDEX || amount <= 0) return ItemStack.EMPTY;
        ItemStack cur = be.getSlotItem();
        if (cur.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = cur.copy();
        out.setCount(Math.min(amount, out.getCount()));
        if (!simulate) be.setSlotItem(ItemStack.EMPTY);
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;                                                  // 一格一件 ✓
    }

    /**
     * 只收<b>有能量的</b>古老者水晶 / 古老者水晶方块 ✓（用户 §558 口径："有能量的"✓）。
     * <p>⚠ 这只管 {@code insertItem} 那条路 ✓；从光标<b>手动</b>放进来走的是
     * {@link #setStackInSlot} ✓，那边由菜单的 {@code mayPlace} 把关 ✓（同一份判断 ✓）。
     */
    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot != EeExtractorBlockEntity.SLOT_INDEX) return false;
        return EeExtractorBlockEntity.isCrystalItem(stack)
                && EeExtractorBlockEntity.heldEe(stack) > 0;
    }
}
