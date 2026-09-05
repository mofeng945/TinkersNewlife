package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetyAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 模块化魔杖 · 巫法聚晶包界面：
 * 上排 6 格魔杖聚晶包；下方列出背包内聚晶。点击背包聚晶 → 放入包内第一个空位；
 * 点击包内已占位格 → 取回背包；高亮当前装备聚晶。按 R 可循环装备（界面外）。
 */
public class StaffGoetyScreen extends Screen {

    private static final int SLOT = 18;
    private static final int PAD = 4;

    private final int mode;
    private final int idx;
    private final List<ItemStack> foci;
    private final List<ItemStack> invFoci;

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
        if (button == 0) {
            // 点击包内槽位：有聚晶 → 取回
            for (int i = 0; i < foci.size(); i++) {
                int x = pouchX + i * (SLOT + PAD);
                if (inBox(mouseX, mouseY, x, pouchY, SLOT, SLOT)) {
                    if (!foci.get(i).isEmpty()) {
                        TinkersNewlife.CHANNEL.sendToServer(new PacketStaffGoetyAction(3, i));
                        Minecraft_Close();
                    }
                    return true;
                }
            }
            // 点击背包聚晶 → 放入
            int cols = 9;
            for (int i = 0; i < invFoci.size(); i++) {
                int row = i / cols;
                int col = i % cols;
                int x = pouchX + col * (SLOT + PAD);
                int y = invY + row * (SLOT + PAD);
                if (inBox(mouseX, mouseY, x, y, SLOT, SLOT)) {
                    TinkersNewlife.CHANNEL.sendToServer(new PacketStaffGoetyAction(2, i));
                    Minecraft_Close();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void Minecraft_Close() {
        net.minecraft.client.Minecraft.getInstance().setScreen(null);
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
            drawSlot(graphics, x, pouchY, foci.get(i), i == idx);
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

    private void drawSlot(GuiGraphics graphics, int x, int y, ItemStack stack, boolean equipped) {
        graphics.fill(x, y, x + SLOT, y + SLOT, equipped ? 0x99FFAA22 : 0x66000000);
        if (equipped) {
            graphics.fill(x, y, x + SLOT, y + 1, 0xFFFFCC33);
            graphics.fill(x, y, x + 1, y + SLOT, 0xFFFFCC33);
            graphics.fill(x + SLOT - 1, y, x + SLOT, y + SLOT, 0xFFFFCC33);
            graphics.fill(x, y + SLOT - 1, x + SLOT, y + SLOT, 0xFFFFCC33);
        }
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
        }
    }
}
