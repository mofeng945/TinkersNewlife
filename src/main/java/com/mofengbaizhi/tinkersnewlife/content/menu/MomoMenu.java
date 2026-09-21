package com.mofengbaizhi.tinkersnewlife.content.menu;

import com.mofengbaizhi.tinkersnewlife.content.ModMenus;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 墨默的**三选项菜单**（对话 / 交易 / 雇佣 ✓ 用户口径 §455 A ✓）。
 *
 * <p>实现说明：
 * <ul>
 *   <li>没有槽位 ✓ 只借"容器菜单"这条现成通道来**打开自定义界面**并把按钮点回服务端 ✓
 *       （客户端点按钮走原版 {@code handleInventoryButtonClick} ✓ 服务端落到
 *       {@link #clickMenuButton} ✓ **不用自己造网络包** ✓）；</li>
 *   <li>打开时把**墨默 entityId** 与**好感度**塞进 `data` 一起同步 ✓
 *       （好感度客户端也要用 ✓ 决定"交易/雇佣"是否变灰 ✓ 而持久数据不走同步 ✗）；</li>
 *   <li>回退 = 按钮 {@link #BTN_BACK} ⇒ 关掉界面 ✓（用户要求"每个界面都要有回退按钮" ✓）。</li>
 * </ul>
 *
 * <p>⚠ 批 1 只做到"菜单能开、四个按钮能点" ✓；**交易 / 雇佣 / 对话**分别在批 2/3/4 接到这里 ✓（下面留有 TODO ✓）。
 */
public class MomoMenu extends AbstractContainerMenu {

    public static final int BTN_TALK = 0;
    public static final int BTN_TRADE = 1;
    public static final int BTN_HIRE = 2;
    public static final int BTN_BACK = 3;

    private final int momoId;
    private final int favor;

    /** 服务端：直接拿实体 ✓（同时把好感度快照进去 ✓ 与发给客户端的那份一致 ✓） */
    public MomoMenu(int windowId, Inventory inv, Entity momo) {
        this(windowId, inv, momo == null ? -1 : momo.getId(), MomoFavor.get(inv.player));
    }

    /** 服务端 / 客户端共用：显式给 id 与好感度 ✓ */
    public MomoMenu(int windowId, Inventory inv, int momoId, int favor) {
        super(ModMenus.MOMO_MENU.get(), windowId);
        this.momoId = momoId;
        this.favor = favor;
    }

    /** 客户端：从 `data` 读（顺序必须与服务端 openScreen 的写入顺序一致 ✗ 别对调 ✓） */
    public MomoMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        this(windowId, inv, data.readInt(), data.readInt());
    }

    public int momoId() { return momoId; }
    public int favor() { return favor; }
    public boolean canTalk() { return favor >= 0; }
    public boolean canHire() { return favor >= 0; }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp)) return false;
        switch (id) {
            case BTN_TALK -> {
                // TODO 批 4：打开对话树（负数不能对话 ⇒ 这里也要挡一道 ✓）
                if (MomoFavor.canTalk(sp)) {
                    // ⭐ 用户口径：**首次与墨默对话 ⇒ 获得「新生神秘学编年史」** ✓（持久标记 ⇒ 只送一次 ✓）
                    if (!sp.getPersistentData().getBoolean("tn_momo_chronicle_given")) {
                        sp.getPersistentData().putBoolean("tn_momo_chronicle_given", true);
                        ItemStack book = new ItemStack(
                                com.mofengbaizhi.tinkersnewlife.content.ModItems.GUIDE_BOOK.get());
                        if (!sp.getInventory().add(book)) sp.drop(book, false);
                        sp.displayClientMessage(
                                Component.translatable("menu.tinkersnewlife.momo.chronicle"), false);
                    }
                    sp.displayClientMessage(
                            Component.translatable("menu.tinkersnewlife.momo.todo_talk"), true);
                }
            }
            case BTN_TRADE -> {
                // 批 2 第一步：**接回墨默原有的交易界面** ✓（`PacketMomoOpen.sendTo` 就是它原本右键打开那条路 ✓
                //   批 1 的右键拦截把那条路切断了 ✗ ⇒ 这里补回来 ✓）
                if (sp.level().getEntity(momoId) instanceof com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant momo) {
                    sp.closeContainer();
                    com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoOpen.sendTo(sp, momo);
                }
            }
            case BTN_HIRE -> {
                // TODO 批 3：雇佣（天数 + 多货币优先级）；负数不能雇佣 ⇒ 挡一道 ✓
                if (MomoFavor.canHire(sp)) sp.displayClientMessage(
                        Component.translatable("menu.tinkersnewlife.momo.todo_hire"), true);
                else sp.displayClientMessage(Component.translatable("menu.tinkersnewlife.momo.no_hire"), true);
            }
            case BTN_BACK -> sp.closeContainer();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
