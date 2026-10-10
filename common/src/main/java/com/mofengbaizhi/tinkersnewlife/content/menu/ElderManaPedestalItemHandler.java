package com.mofengbaizhi.tinkersnewlife.content.menu;

import com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;

/**
 * <b>台座那一个"水晶位"的 {@code IItemHandler} 视图</b>（§601 自动化）。
 *
 * <h2>为什么要它</h2>
 * 台座上的水晶其实只是 {@link ElderManaPedestalBlockEntity} 上的一个 {@code ItemStack} 字段
 * （直接进 BE 的 NBT ✓ 用户要求 ✓），<b>不是</b>任何 {@code Container} ✗ ⇒ 漏斗 / 管道 /
 * 别的模组的"自动化"默认看不见它 ✗。这个类就是那件东西的<b>标准物品容器视图</b> ✓
 * —— 方块实体把它作为 {@code ForgeCapabilities.ITEM_HANDLER} 暴露出去（六面都通 ✓）✓。
 *
 * <h2>⚠ §562 的老教训（这个类必须抄对 ✗）</h2>
 * {@code SlotItemHandler#set} 内部是<b>无条件强转</b> {@code (IItemHandlerModifiable) handler} ✗
 * ⇒ 手写的 handler 只要漏了这个接口，一碰槽位就 {@code ClassCastException} ✗。
 * 所以本类和 {@link EeExtractorSlotHandler} 一样，<b>一律 implements {@link IItemHandlerModifiable}</b> ✓
 * （同包的两个 handler 语义也保持一致：一格一件 ✓ 三个写入口都只走 BE 的那<b>一个</b> setter ✓）。
 *
 * <h2>收什么 / 吐什么</h2>
 * <ul>
 *   <li><b>收</b>：古老者水晶 与 古老者水晶方块 ✓（{@link ElderManaPedestalBlockEntity#heldCrystal} ✓ 同一份判断 ✓）。
 *       <p>⚠ 与抽取器不同：这里<b>空水晶也收</b> ✓ —— 台座的本职就是"把它充满"✓
 *       （抽取器那边只收"有电的"✓ 因为它的本职是"把电抽出来"✓ 见 {@code EeExtractorSlotHandler#isItemValid} ✓）。</li>
 *   <li><b>吐</b>：台上那一件原样给出 ✓（可以是充好的 ✓ 也可以是还没充的 ✓）。</li>
 * </ul>
 */
public final class ElderManaPedestalItemHandler implements IItemHandlerModifiable {

    /** 只有一个槽 ✓ */
    public static final int SLOT = 0;

    private final ElderManaPedestalBlockEntity be;

    /**
     * §606 这份 handler 是不是"给自动化用的"那一份 ✓。
     * <ul>
     *   <li>{@code false}（默认 ✓）：**不门控** —— 谁都能随时取走台上那件 ✓
     *       （用户口径：手动/界面取得自由 ✓ 目前台座没有界面 ✓ 保留给将来 ✓）；</li>
     *   <li>{@code true}：只有台上那颗**充满**时才让取走 ✓（{@link ElderManaPedestalBlockEntity#heldCrystalFull()} ✓）
     *       —— 方块实体把<b>这一份</b>挂到 {@code ITEM_HANDLER} 能力上 ✓ ⇒ 漏斗/管道会**等它充满**再来搬 ✓
     *       这样"台座 → 抽取器 → 台座"的闭环节奏才是对的 ✓（不然水晶会一路弹跳 ✗ 见 §606 用户提问 ✓）。</li>
     * </ul>
     */
    private final boolean gateForAutomation;

    public ElderManaPedestalItemHandler(ElderManaPedestalBlockEntity be) {
        this(be, false);
    }

    public ElderManaPedestalItemHandler(ElderManaPedestalBlockEntity be, boolean gateForAutomation) {
        this.be = be;
        this.gateForAutomation = gateForAutomation;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == SLOT ? be.getCrystal() : ItemStack.EMPTY;
    }

    /** §562 必须有它（{@code SlotItemHandler#set} 会强转进来 ✓）；写回统一走 BE 的 setter ✓ */
    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (slot != SLOT) return;
        be.setCrystal(stack);      // 内部：规范化空栈 / 只留 1 件 / 标脏 / 同步 ✓
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot != SLOT) return stack;
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        if (!be.getCrystal().isEmpty()) return stack;               // 台上已有一件 ⇒ 拒收（一格一件 ✓）
        if (simulate) {
            ItemStack one = stack.copy();
            one.setCount(1);
            return one;                                            // 只放得下 1 个 ⇒ 剩下的如实退回 ✓
        }
        ItemStack one = stack.copy();
        one.setCount(1);
        be.setCrystal(one);
        ItemStack rest = stack.copy();
        rest.shrink(1);
        return rest;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot != SLOT || amount <= 0) return ItemStack.EMPTY;
        // §606 自动化门控：只有**充满**才让搬走 ✓（手动那份 handler 不门控 ✓）
        if (gateForAutomation && !be.heldCrystalFull()) return ItemStack.EMPTY;
        ItemStack cur = be.getCrystal();
        if (cur.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = cur.copy();                                // ⚠ 拷贝 ⇒ 自动化那边改不到 BE 的字段 ✓
        out.setCount(Math.min(amount, out.getCount()));
        if (!simulate) be.setCrystal(ItemStack.EMPTY);
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;                                                  // 一格一件 ✓
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return slot == SLOT && ElderManaPedestalBlockEntity.heldCrystal(stack);
    }
}
