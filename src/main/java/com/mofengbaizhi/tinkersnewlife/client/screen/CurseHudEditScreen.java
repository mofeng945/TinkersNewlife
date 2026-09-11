package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.client.hud.CurseHudRenderer;
import com.mofengbaizhi.tinkersnewlife.client.hud.CurseHudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 咒术 HUD 位置调整界面：<b>拖动进度条</b>即可改位置（滚轮调宽度），关闭时自动写入配置。
 *
 * <p>背景只压一层半透明黑，不画模糊背景，方便对着实际游戏画面调位置；界面不暂停世界
 * （{@link #isPauseScreen()} = false），因为 HUD 要在正常游戏状态下才看得到真实效果。
 */
public class CurseHudEditScreen extends Screen {

    private int hudX;
    private int hudY;
    private int hudW;

    private boolean dragging = false;
    private double grabOffsetX;
    private double grabOffsetY;

    public CurseHudEditScreen() {
        super(Component.translatable("screen.tinkersnewlife.curse_hud.title"));
        this.hudX = CurseHudConfig.x;
        this.hudY = CurseHudConfig.y;
        this.hudW = CurseHudConfig.width;
    }

    public int getEditX() { return hudX; }
    public int getEditY() { return hudY; }
    public int getEditWidth() { return hudW; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 不画模糊/暗化背景，直接看游戏画面 */
    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x55000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // 顶部说明
        Component title = Component.translatable("screen.tinkersnewlife.curse_hud.title");
        graphics.drawCenteredString(this.font, title, this.width / 2, 12, 0xFFFFFF);
        Component hint = Component.translatable("hud.tinkersnewlife.curse_hud.hint");
        graphics.drawCenteredString(this.font, hint, this.width / 2, 26, 0xA0E8FF);
        Component pos = Component.literal("x=" + hudX + "  y=" + hudY + "  w=" + hudW);
        graphics.drawCenteredString(this.font, pos, this.width / 2, 40, 0x9A9A9A);

        // 预览（与实际 HUD 同一套绘制代码）+ 可拖动虚框
        int h = CurseHudRenderer.totalHeight();
        CurseHudRenderer.drawBar(graphics, this.font, hudX, hudY, hudW);
        CurseHudRenderer.drawEditFrame(graphics, hudX, hudY, hudW, h);
        CurseHudRenderer.drawPreviewExtraLines(graphics, this.font, hudX, hudY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inside(mouseX, mouseY)) {
            dragging = true;
            grabOffsetX = mouseX - hudX;
            grabOffsetY = mouseY - hudY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            hudX = clampX((int) Math.round(mouseX - grabOffsetX));
            hudY = clampY((int) Math.round(mouseY - grabOffsetY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        hudW = (int) Mth.clamp(hudW + delta * 8, 40, 600);
        return true;
    }

    /** 关闭即保存 */
    @Override
    public void onClose() {
        CurseHudConfig.x = hudX;
        CurseHudConfig.y = hudY;
        CurseHudConfig.width = hudW;
        CurseHudConfig.save();
        super.onClose();
    }

    private boolean inside(double mouseX, double mouseY) {
        int h = Math.max(CurseHudRenderer.BAR_H, CurseHudRenderer.totalHeight());
        return mouseX >= hudX - 3 && mouseX <= hudX + hudW + 3
                && mouseY >= hudY - 3 && mouseY <= hudY + h + 3;
    }

    private int clampX(int v) {
        return (int) Mth.clamp(v, -hudW + 8, this.width - 8);
    }

    private int clampY(int v) {
        return (int) Mth.clamp(v, 0, this.height - CurseHudRenderer.BAR_H);
    }
}
