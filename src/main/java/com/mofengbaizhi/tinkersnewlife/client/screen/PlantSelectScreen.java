package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketPlantSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 草木操术 顺转模式选择界面：
 * 树根（目标脚下长甜浆果丛持续 3 秒）/ 咒种（咒种寄生 debuff）。
 * 点击卡片 → 发送选定包进入蓄力并关闭；随后瞄准敌人再按一次释放键。
 */
public class PlantSelectScreen extends Screen {

    private static final int CARD_W = 210;
    private static final int CARD_H = 110;
    private static final int GAP = 18;

    private final int cost;
    private int leftX;
    private int rightX;
    private int topY;

    public PlantSelectScreen(int cost) {
        super(Component.translatable("screen.tinkersnewlife.plant.title"));
        this.cost = cost;
    }

    @Override
    protected void init() {
        int total = CARD_W * 2 + GAP;
        int startX = (width - total) / 2;
        leftX = startX;
        rightX = startX + CARD_W + GAP;
        topY = Math.max(30, (height - CARD_H) / 2 - 30);
    }

    private boolean in(double mx, double my, int x) {
        return mx >= x && mx <= x + CARD_W && my >= topY && my <= topY + CARD_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (in(mouseX, mouseY, leftX)) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketPlantSelect(0));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            if (in(mouseX, mouseY, rightX)) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketPlantSelect(1));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.plant.title"), topY - 24, 0xFFFFFF);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.plant.foot"), topY + CARD_H + 12, 0x9A9A9A);
        drawCentered(graphics, costLine(), topY + CARD_H + 24, costColor());

        drawCard(graphics, leftX, in(mouseX, mouseY, leftX),
                Component.translatable("screen.tinkersnewlife.plant.roots_name"),
                Component.translatable("screen.tinkersnewlife.plant.roots_desc"));
        drawCard(graphics, rightX, in(mouseX, mouseY, rightX),
                Component.translatable("screen.tinkersnewlife.plant.seed_name"),
                Component.translatable("screen.tinkersnewlife.plant.seed_desc"));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics graphics, int x, boolean hover, Component name, Component desc) {
        graphics.fill(x, topY, x + CARD_W, topY + CARD_H, hover ? 0xAA2E4A2A : 0xAA1E2A1E);
        graphics.fill(x, topY, x + 1, topY + CARD_H, 0xFF5A8A5A);
        graphics.fill(x + CARD_W - 1, topY, x + CARD_W, topY + CARD_H, 0xFF5A8A5A);
        graphics.fill(x, topY, x + CARD_W, topY + 1, 0xFF5A8A5A);
        graphics.fill(x, topY + CARD_H - 1, x + CARD_W, topY + CARD_H, 0xFF5A8A5A);
        int cx = x + CARD_W / 2;
        drawCenteredAt(graphics, name, cx, topY + 12, 0x7CFF7C);
        drawCenteredAt(graphics, desc, cx, topY + 30, 0xC8C8C8);
        drawCenteredAt(graphics, Component.translatable("screen.tinkersnewlife.plant.click_hint"), cx, topY + CARD_H - 14, 0x9A9A9A);
    }

    private Component costLine() {
        if (ClientCurseData.isInfinite()) {
            return Component.translatable("screen.tinkersnewlife.plant.cost_infinite");
        }
        return Component.translatable("screen.tinkersnewlife.plant.cost", cost);
    }

    private int costColor() {
        if (ClientCurseData.isInfinite() || ClientCurseData.getCurse() >= cost) {
            return 0xFFD4924B;
        }
        return 0xFFE05555;
    }

    private void drawCentered(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, text, (width - font.width(text)) / 2, y, color);
    }

    private void drawCenteredAt(GuiGraphics graphics, Component text, int cx, int y, int color) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, color);
    }
}
