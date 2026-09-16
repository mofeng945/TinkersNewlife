package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.storage.QuantumVault;
import com.mofengbaizhi.tinkersnewlife.content.storage.QuantumVaultMenu;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultAction;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultSync;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 6 级量子背包界面：<b>按数量存放</b>（类似 AE / RS）✓
 *
 * <h2>布局（别再"算"常量 ✗）</h2>
 * 第一版把 {@code imageHeight} 算成 384、而菜单里的槽位却写死在 140/198 ✗ —— 结果整体偏上、
 * 槽位跑到画面外 ✗（用户实测反馈 ✓）。现在所有 Y 都用下面这组常量推导，菜单里的槽位坐标也按同一组写 ✓：
 * <pre>
 *   y+0   .. 20   搜索框
 *   y+22  .. 130  存储格 9×6（每格 18）
 *   y+132 .. 148  翻页 / 页数 / 存入全部
 *   y+152 .. 206  玩家背包 3×9
 *   y+210 .. 228  快捷栏
 *   imageHeight = 234
 * </pre>
 *
 * <p>槽位方框是**自己画的** ✓ —— 原版容器靠贴图提供边框，我们没贴图 ✗，
 * 所以对"存储格"和"菜单里的玩家槽位"统一画 18×18 凹槽 ✓。
 */
public class QuantumVaultScreen extends AbstractContainerScreen<QuantumVaultMenu> {

    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int PER_PAGE = COLS * ROWS;
    private static final int CELL = 18;

    private static final int GRID_TOP = 22;
    private static final int BAR_TOP = GRID_TOP + ROWS * CELL + 2;      // 132
    private static final int INV_TOP = BAR_TOP + 20;                    // 152
    private static final int HOTBAR_TOP = INV_TOP + 3 * CELL + 4;       // 210
    private static final int HEIGHT = HOTBAR_TOP + CELL + 6;            // 234
    private static final int WIDTH = 8 + COLS * CELL + 8;               // 178

    /** 服务端快照（已按物品名排序 ✓） */
    private static List<PacketVaultSync.Entry> snapshot = new ArrayList<>();
    private static long snapshotTotal = 0L;
    private static int snapshotWindow = -1;

    private final List<PacketVaultSync.Entry> view = new ArrayList<>();
    private final UUID uuid;

    private EditBox search;
    private int page = 0;

    public QuantumVaultScreen(QuantumVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.uuid = menu.getBagUUID();
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = INV_TOP - 11;
    }

    /** 服务端快照到达（{@code PacketVaultSync} 的处理器调用 ✓） */
    public void acceptSync(int windowId, long total, List<PacketVaultSync.Entry> entries) {
        if (windowId != this.menu.containerId) return;
        snapshot = new ArrayList<>(entries);
        snapshot.sort((a, b) -> a.stack().getHoverName().getString()
                .compareToIgnoreCase(b.stack().getHoverName().getString()));
        snapshotTotal = total;
        snapshotWindow = windowId;
        rebuildView();
    }

    @Override
    protected void init() {
        super.init();
        if (snapshotWindow == this.menu.containerId) rebuildView();

        int x = this.leftPos;
        int y = this.topPos;

        this.search = new EditBox(this.font, x + 8, y + 6, this.imageWidth - 16, 14,
                Component.translatable("gui.tinkersnewlife.quantum_vault.search"));
        this.search.setHint(Component.translatable("gui.tinkersnewlife.quantum_vault.search"));
        this.search.setResponder(s -> {
            this.page = 0;
            rebuildView();
        });
        addRenderableWidget(this.search);

        addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (this.page > 0) {
                this.page--;
                rebuildView();
            }
        }).bounds(x + 8, y + BAR_TOP + 2, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if ((this.page + 1) * PER_PAGE < this.view.size()) {
                this.page++;
                rebuildView();
            }
        }).bounds(x + 30, y + BAR_TOP + 2, 20, 16).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.tinkersnewlife.quantum_vault.deposit_all"), b ->
                        send(PacketVaultAction.DEPOSIT_ALL, ItemStack.EMPTY))
                .bounds(x + this.imageWidth - 84, y + BAR_TOP + 2, 76, 16).build());

        // 刚打开时先向服务端要一份快照 ✓
        // 服务端在 openScreen 之后已经主动同步过一次快照（包按序到达 ✓），这里不用再请求 ✓
    }

    /** 搜索框获得焦点时，别让 'E' 之类的按键把界面关掉 ✓ */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.search != null && this.search.isFocused()) {
            if (this.search.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (keyCode == 256) {                       // Esc：先退出输入框 ✓
                this.search.setFocused(false);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(byte action, ItemStack template) {
        TinkersNewlife.CHANNEL.sendToServer(new PacketVaultAction(this.uuid, action, template, 0));
    }

    private void rebuildView() {
        List<PacketVaultSync.Entry> out = new ArrayList<>();
        String q = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        for (PacketVaultSync.Entry e : snapshot) {
            if (q.isEmpty() || e.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        view.clear();
        view.addAll(out);
        int maxPage = Math.max(0, (view.size() - 1) / PER_PAGE);
        if (this.page > maxPage) this.page = maxPage;
    }

    // ============================================================
    //  绘制
    // ============================================================

    /** 画一个 18×18 凹槽（原版靠贴图，我们自绘 ✓） */
    private void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x, y, x + 16, y + 16, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 1, 0xFF000000);
        graphics.fill(x, y, x + 1, y + 16, 0xFF000000);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        graphics.fill(x, y, x + this.imageWidth, y + 21, 0xFF404040);

        // ① 存储格 9×6
        for (int i = 0; i < PER_PAGE; i++) {
            drawSlotFrame(graphics, x + 8 + (i % COLS) * CELL, y + GRID_TOP + (i / COLS) * CELL);
        }
        // ② 玩家背包 + 快捷栏（槽位来自菜单 ✓，坐标与 QuantumVaultMenu 保持一致 ✓）
        for (Slot slot : this.menu.slots) {
            drawSlotFrame(graphics, x + slot.x, y + slot.y);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int gridLeft = this.leftPos + 8;
        int gridTop = this.topPos + GRID_TOP;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = this.page * PER_PAGE + i;
            if (idx >= this.view.size()) break;
            PacketVaultSync.Entry e = this.view.get(idx);
            int cx = gridLeft + (i % COLS) * CELL;
            int cy = gridTop + (i / COLS) * CELL;
            graphics.renderItem(e.stack(), cx, cy);
            graphics.renderItemDecorations(this.font, e.stack(), cx, cy, shortCount(e.amount()));
            if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
                graphics.renderTooltip(this.font, e.stack(), mouseX, mouseY);
            }
        }

        Component info = Component.translatable("gui.tinkersnewlife.quantum_vault.page",
                        this.page + 1, Math.max(1, (this.view.size() - 1) / PER_PAGE + 1))
                .append("   ")
                .append(Component.translatable("gui.tinkersnewlife.quantum_vault.total",
                        String.valueOf(snapshotTotal), String.valueOf(QuantumVault.TOTAL_CAPACITY)));
        graphics.drawString(this.font, info, this.leftPos + 56, this.topPos + BAR_TOP + 6, 0x404040, false);

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /** 数量缩写：1234 → 1.2k，1234567 → 1.2M ✓ */
    private static String shortCount(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
    }

    // ============================================================
    //  操作
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int gridLeft = this.leftPos + 8;
        int gridTop = this.topPos + GRID_TOP;
        for (int i = 0; i < PER_PAGE; i++) {
            int cx = gridLeft + (i % COLS) * CELL;
            int cy = gridTop + (i / COLS) * CELL;
            if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
                int idx = this.page * PER_PAGE + i;
                if (idx >= this.view.size()) break;
                PacketVaultSync.Entry e = this.view.get(idx);
                byte action;
                if (button == 1) {
                    action = PacketVaultAction.WITHDRAW_STACK;
                } else if (hasShiftDown()) {
                    action = PacketVaultAction.WITHDRAW_ALL;
                } else if (button == 0) {
                    action = PacketVaultAction.WITHDRAW_ONE;
                } else {
                    return true;
                }
                send(action, e.stack());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
