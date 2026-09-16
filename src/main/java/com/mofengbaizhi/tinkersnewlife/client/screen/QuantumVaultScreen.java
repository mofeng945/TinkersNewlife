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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 6 级量子背包界面：<b>按数量存放</b>（类似 AE / RS）✓
 *
 * <ul>
 *   <li>上方搜索框 ✓（按物品名过滤，客户端本地过滤 ✓）；</li>
 *   <li>中间 9 × 6 = 54 格一页，物品图标 + <b>数量</b>（1.2k / 3.4M 缩写 ✓）；</li>
 *   <li>底部翻页 ◀ ▶ + 页码 + 「存入全部」按钮 ✓；</li>
 *   <li>操作：<b>左键取 1 / 右键取 64 / Shift+左键取光这种</b> ✓；
 *       背包里的物品 <b>Shift 点击</b>即存入（在 {@link QuantumVaultMenu#quickMoveStack} 里 ✓）。</li>
 * </ul>
 *
 * <p>界面不持有任何"真实存储" ✗：数据是服务端发来的快照（{@code PacketVaultSync} ✓），
 * 每次操作都发 {@code PacketVaultAction} 回服务端、由服务端执行并回传新快照 ✓ ——
 * 所以客户端伪造不了物品 ✓。
 */
public class QuantumVaultScreen extends AbstractContainerScreen<QuantumVaultMenu> {

    private static final int COLS = 9;
    private static final int ROWS = 6;
    private static final int PER_PAGE = COLS * ROWS;
    private static final int CELL = 18;

    /** 服务端快照（已按物品名排序 ✓） */
    private static List<PacketVaultSync.Entry> snapshot = new ArrayList<>();
    private static long snapshotTotal = 0L;
    private static int snapshotWindow = -1;

    private final List<PacketVaultSync.Entry> view = new ArrayList<>();
    private final UUID uuid;

    private EditBox search;
    private Button prev;
    private Button next;
    private Button depositAll;
    private int page = 0;

    public QuantumVaultScreen(QuantumVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.uuid = menu.getBagUUID();
        this.imageWidth = 8 + COLS * CELL + 8;              // 178
        this.imageHeight = 22 + ROWS * CELL + 22 + 4 + 3 * 18 + 6 + 9 * 18 + 6;
        this.inventoryLabelY = 22 + ROWS * CELL + 22 + 4 + 4;
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

        int buttonY = y + 22 + ROWS * CELL + 6;
        this.prev = addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            if (this.page > 0) {
                this.page--;
                rebuildView();
            }
        }).bounds(x + 8, buttonY, 20, 16).build());
        this.next = addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            if ((this.page + 1) * PER_PAGE < this.view.size()) {
                this.page++;
                rebuildView();
            }
        }).bounds(x + 30, buttonY, 20, 16).build());
        this.depositAll = addRenderableWidget(Button.builder(
                Component.translatable("gui.tinkersnewlife.quantum_vault.deposit_all"), b -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        TinkersNewlife.CHANNEL.sendToServer(new PacketVaultAction(
                                this.uuid, PacketVaultAction.DEPOSIT_ALL, ItemStack.EMPTY, 0));
                    }
                }).bounds(x + this.imageWidth - 84, buttonY, 76, 16).build());
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        graphics.fill(x, y, x + this.imageWidth, y + 24, 0xFF404040);
        int gridTop = y + 22;
        graphics.fill(x + 6, gridTop - 2, x + this.imageWidth - 6, gridTop + ROWS * CELL + 2, 0xFF8B8B8B);
        // 玩家背包区底色
        int invTop = gridTop + ROWS * CELL + 22;
        graphics.fill(x + 6, invTop - 2, x + this.imageWidth - 6, invTop + 4 + 3 * 18 + 4 + 18 + 2, 0xFF8B8B8B);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int gridLeft = this.leftPos + 8;
        int gridTop = this.topPos + 22;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = this.page * PER_PAGE + i;
            int cx = gridLeft + (i % COLS) * CELL;
            int cy = gridTop + (i / COLS) * CELL;

            if (idx < this.view.size()) {
                PacketVaultSync.Entry e = this.view.get(idx);
                graphics.renderItem(e.stack(), cx, cy);
                graphics.renderItemDecorations(this.font, e.stack(), cx, cy, shortCount(e.amount()));
                if (isHovering(cx - this.leftPos, cy - this.topPos, 16, 16, mouseX, mouseY)) {
                    graphics.renderTooltip(this.font, e.stack(), mouseX, mouseY);
                }
            }
        }

        // 页码 + 总量
        Component info = Component.translatable("gui.tinkersnewlife.quantum_vault.page",
                this.page + 1, Math.max(1, (this.view.size() - 1) / PER_PAGE + 1))
                .append("  ")
                .append(Component.translatable("gui.tinkersnewlife.quantum_vault.total",
                        String.valueOf(snapshotTotal), String.valueOf(QuantumVault.TOTAL_CAPACITY)));
        graphics.drawString(this.font, info, this.leftPos + 56, this.topPos + 22 + ROWS * CELL + 10, 0x404040, false);

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
        int gridTop = this.topPos + 22;
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
                TinkersNewlife.CHANNEL.sendToServer(new PacketVaultAction(this.uuid, action, e.stack(), 0));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
