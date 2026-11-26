package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetIronGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * 傀儡操术 召唤选择界面：
 * 左右两张卡片分别以 3D 渲染铁傀儡 / 雪傀儡实体（复用原版渲染器），
 * 显示召唤咒力消耗与操作说明，点击卡片 → 发送选定包召唤并关闭。
 * 3D 实体渲染由 {@link GuiEntityViewer} 提供。
 */
public class PuppetSelectScreen extends Screen {

    private static final int CARD_W = 172;
    private static final int CARD_H = 244;
    private static final int GAP = 22;

    private final int ironCost;
    private final int snowCost;

    private PuppetIronGolem ironDummy;
    private PuppetSnowGolem snowDummy;
    private int leftX;
    private int rightX;
    private int topY;

    public PuppetSelectScreen(int ironCost, int snowCost) {
        super(Component.translatable("screen.tinkersnewlife.puppet.title"));
        this.ironCost = ironCost;
        this.snowCost = snowCost;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (ironDummy == null && mc.level != null) {
            ironDummy = ModEntities.PUPPET_IRON_GOLEM.get().create(mc.level);
            snowDummy = ModEntities.PUPPET_SNOW_GOLEM.get().create(mc.level);
        }
        int total = CARD_W * 2 + GAP;
        int startX = (width - total) / 2;
        leftX = startX;
        rightX = startX + CARD_W + GAP;
        topY = Math.max(24, (height - CARD_H) / 2 - 10);
    }

    private boolean inIron(double mx, double my) {
        return mx >= leftX && mx <= leftX + CARD_W && my >= topY && my <= topY + CARD_H;
    }

    private boolean inSnow(double mx, double my) {
        return mx >= rightX && mx <= rightX + CARD_W && my >= topY && my <= topY + CARD_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inIron(mouseX, mouseY)) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketPuppetSelect(0));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            if (inSnow(mouseX, mouseY)) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketPuppetSelect(1));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.puppet.title"),
                topY - 22, 0xFFFFFF);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.puppet.foot"),
                topY + CARD_H + 12, 0x9A9A9A);

        drawCard(graphics, leftX, inIron(mouseX, mouseY), true, mouseX, mouseY);
        drawCard(graphics, rightX, inSnow(mouseX, mouseY), false, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics graphics, int x, boolean hover, boolean iron,
                          double mouseX, double mouseY) {
        graphics.fill(x, topY, x + CARD_W, topY + CARD_H, hover ? 0xAA33333C : 0xAA1E1E24);
        graphics.fill(x, topY, x + 1, topY + CARD_H, 0xFF5A5A5A);
        graphics.fill(x + CARD_W - 1, topY, x + CARD_W, topY + CARD_H, 0xFF5A5A5A);
        graphics.fill(x, topY, x + CARD_W, topY + 1, 0xFF5A5A5A);
        graphics.fill(x, topY + CARD_H - 1, x + CARD_W, topY + CARD_H, 0xFF5A5A5A);

        int cx = x + CARD_W / 2;
        int feetY = topY + 150;
        if (iron) {
            if (ironDummy != null) {
                GuiEntityViewer.render(graphics, ironDummy, cx, feetY, 34, mouseX, mouseY);
            }
            drawCenteredAt(graphics, Component.translatable("entity.tinkersnewlife.puppet_iron_golem"),
                    cx, topY + 16, 0xFFFFFF);
            drawCenteredAt(graphics, costLine(ironCost), cx, topY + 172, costColor(ironCost));
            drawCenteredAt(graphics, Component.translatable("screen.tinkersnewlife.puppet.hint_iron"),
                    cx, topY + 186, 0xA0A0A0);
            drawCenteredAt(graphics, Component.translatable("screen.tinkersnewlife.puppet.hint_common"),
                    cx, topY + 200, 0x7A7A7A);
        } else {
            if (snowDummy != null) {
                GuiEntityViewer.render(graphics, snowDummy, cx, feetY, 24, mouseX, mouseY);
            }
            drawCenteredAt(graphics, Component.translatable("entity.tinkersnewlife.puppet_snow_golem"),
                    cx, topY + 16, 0xFFFFFF);
            drawCenteredAt(graphics, costLine(snowCost), cx, topY + 172, costColor(snowCost));
            drawCenteredAt(graphics, Component.translatable("screen.tinkersnewlife.puppet.hint_snow"),
                    cx, topY + 186, 0xA0A0A0);
            drawCenteredAt(graphics, Component.translatable("screen.tinkersnewlife.puppet.hint_common"),
                    cx, topY + 200, 0x7A7A7A);
        }
    }

    private Component costLine(int cost) {
        if (ClientCurseData.isInfinite()) {
            return Component.translatable("screen.tinkersnewlife.puppet.cost_infinite");
        }
        return Component.translatable("screen.tinkersnewlife.puppet.cost", cost);
    }

    private int costColor(int cost) {
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
