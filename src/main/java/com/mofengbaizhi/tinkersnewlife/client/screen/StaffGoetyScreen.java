package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetyAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 模块化魔杖 · 巫法聚晶包界面（常驻，J/ESC 才关）：
 * <ul>
 *   <li>左键点包内聚晶：无选中 → 选中（金框）；再点另一格 → 交换排序；点空格 → 移入；点自身 → 取消选中。</li>
 *   <li>右键点包内聚晶 → 取回背包；左键点下方背包聚晶 → 放入包内第一个空位。</li>
 *   <li>放入/取出/交换后界面保持打开，服务端同步回来会自动刷新内容。</li>
 *   <li>装备位高亮橙框；再次按 J 或按 ESC 关闭。</li>
 * </ul>
 */
public class StaffGoetyScreen extends Screen {

    private static final int SLOT = 18;
    private static final int PAD = 4;

    private final int mode;
    private final int idx;
    private final List<ItemStack> foci;
    private final List<ItemStack> invFoci;

    /** 当前选中（待交换）的包内槽位，-1 = 无 */
    private int selectedSlot = -1;

    private int pouchX;
    private int pouchY;
    private int invY;

    public StaffGoetyScreen(int mode, int idx, List<ItemStack> foci, List<ItemStack> invFoci) {
        super(Component.translatable("screen.tinkersnewlife.staff.goety_title"));
        this.mode = mode;
        this.idx = idx;
        this.foci = foci;
        this.invFoci = invFoci;
    }

    @Override
    protected void init() {
        int totalW = 6 * SLOT + 5 * PAD;
        pouchX = (width - totalW) / 2;
        pouchY = 60;
        invY = 130;
    }

    private boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ===== 包内槽位 =====
        int pouchSlot = slotAtPouch(mouseX, mouseY);
        if (pouchSlot >= 0) {
            boolean has = pouchSlot < foci.size() && !foci.get(pouchSlot).isEmpty();
            if (button == 1) {
                // 右键：取回背包（界面保持打开）
                if (has) {
                    send(3, pouchSlot);
                }
                return true;
            }
            if (button == 0) {
                if (selectedSlot < 0) {
                    if (has) selectedSlot = pouchSlot; // 选中待交换
                } else if (selectedSlot == pouchSlot) {
                    selectedSlot = -1;                  // 取消选中
                } else {
                    send(4, selectedSlot * 10 + pouchSlot); // 交换/移入空格
                    selectedSlot = -1;
                }
                return true;
            }
            return true;
        }
        // ===== 下方背包聚晶：左键放入第一个空位（界面保持打开） =====
        if (button == 0) {
            int cols = 9;
            for (int i = 0; i < invFoci.size(); i++) {
                int row = i / cols;
                int col = i % cols;
                int x = pouchX + col * (SLOT + PAD);
                int y = invY + row * (SLOT + PAD);
                if (inBox(mouseX, mouseY, x, y, SLOT, SLOT)) {
                    send(2, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 命中包内第几格，未命中返回 -1 */
    private int slotAtPouch(double mx, double my) {
        for (int i = 0; i < 6; i++) {
            int x = pouchX + i * (SLOT + PAD);
            if (inBox(mx, my, x, pouchY, SLOT, SLOT)) return i;
        }
        return -1;
    }

    private void send(int action, int arg) {
        TinkersNewlife.CHANNEL.sendToServer(new PacketStaffGoetyAction(action, arg));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        Component title = Component.translatable("screen.tinkersnewlife.staff.goety_title");
        graphics.drawString(font, title, (width - font.width(title)) / 2, 28, 0xFFFFFF);
        Component hint = Component.translatable("screen.tinkersnewlife.staff.goety_hint");
        graphics.drawString(font, hint, (width - font.width(hint)) / 2, 42, 0x9A9A9A);
        // 包内 6 格
        for (int i = 0; i < foci.size(); i++) {
            int x = pouchX + i * (SLOT + PAD);
            boolean equipped = i == idx;
            boolean selected = i == selectedSlot;
            drawSlot(graphics, x, pouchY, foci.get(i), equipped, selected);
        }
        // 背包聚晶
        Component invTitle = Component.translatable("screen.tinkersnewlife.staff.inv_foci");
        graphics.drawString(font, invTitle, pouchX, invY - 14, 0xC8C8C8);
        int cols = 9;
        for (int i = 0; i < invFoci.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            int x = pouchX + col * (SLOT + PAD);
            int y = invY + row * (SLOT + PAD);
            graphics.fill(x, y, x + SLOT, y + SLOT, 0x66000000);
            graphics.renderItem(invFoci.get(i), x + 1, y + 1);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y, ItemStack stack, boolean equipped, boolean selected) {
        graphics.fill(x, y, x + SLOT, y + SLOT, equipped ? 0x99FFAA22 : 0x66000000);
        if (equipped) {
            graphics.fill(x, y, x + SLOT, y + 1, 0xFFFFCC33);
            graphics.fill(x, y, x + 1, y + SLOT, 0xFFFFCC33);
            graphics.fill(x + SLOT - 1, y, x + SLOT, y + SLOT, 0xFFFFCC33);
            graphics.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, 0xFFFFCC33);
        }
        if (selected) {
            // 选中待交换：内缩一圈亮金/白框
            graphics.fill(x + 2, y + 2, x + SLOT - 2, y + 3, 0xFFE0E0E0);
            graphics.fill(x + 2, y + 2, x + 3, y + SLOT - 2, 0xFFE0E0E0);
            graphics.fill(x + SLOT - 3, y + 2, x + SLOT - 2, y + SLOT - 2, 0xFFE0E0E0);
            graphics.fill(x + 2, y + SLOT - 3, x + SLOT - 2, y + SLOT - 2, 0xFFE0E0E0);
        }
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
        }
    }
}
