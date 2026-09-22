package com.mofengbaizhi.tinkersnewlife.content.menu;

import com.mofengbaizhi.tinkersnewlife.content.block.EeExtractorBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;

/**
 * <b>EE 抽取方块那一个槽位的 {@code IItemHandler} 视图</b>（§558）。
 *
 * <h2>为什么要它</h2>
 * 本仓库既有的容器一律用 {@code SlotItemHandler}（见 {@code SilentGloveContainer} ✓），
 * 而 {@code SlotItemHandler} 只认 {@link IItemHandler} ✗ —— 我们的"槽位"其实是
 * {@link EeExtractorBlockEntity} 上的一个 {@code ItemStack} 字段（<b>直接存进 BE 的 NBT</b> ✓ 用户要求 ✓）。
 *
 * <p>两条路可选，这里选第一条 ✓：
 * <ol>
 *   <li><b>包一层 handler</b>（本类 ✓）—— BE 仍然是"物品的唯一真相"✓ 菜单只是它的一个视图 ✓
 *       与"台座上的水晶"那张做法同源 ✓；</li>
 *   <li>在 BE 里挂一个 {@code ItemStackHandler} 字段 ✗ —— 那样 NBT 键名会变成
 *       {@code Items/Size/...} 这种 handler 的内部格式 ✓ 与 §557 已有的 {@code EeBuffer} 风格不一致 ✗，
 *       而且以后要读"槽位里那颗水晶现在多少 EE"还得再从 handler 里绕一圈 ✗。</li>
 * </ol>
 *
 * <h2>⚠ 写入时用 {@link #sync()}（我们自己的那一条）</h2>
 * 本类就是给 {@code EeExtractorBlockEntity} 写的专用适配器 ✓ 所以直接调它的 {@code setSlotItem}
 * （内部：复制 + 限量 1 + {@code setChanged} + 直发数据包 ✓）✓
 * —— 不绕 {@link ItemStackHandler} 的 {@code onContentsChanged}（那需要匿名子类 ✓ 代码更长且没有额外好处 ✗）。
 */
public final class EeExtractorSlotHandler implements IItemHandler {

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

    /** 只收<b>有能量的</b>古老者水晶 / 古老者水晶方块 ✓（用户 §558 口径："有能量的"✓） */
    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot != EeExtractorBlockEntity.SLOT_INDEX) return false;
        return EeExtractorBlockEntity.isCrystalItem(stack)
                && EeExtractorBlockEntity.heldEe(stack) > 0;
    }
}
