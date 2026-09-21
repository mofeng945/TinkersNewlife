package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 墨默三选项菜单 —— **纯客户端 Screen**（用户口径 §455 A/E ✓）。
 *
 * <p>⚠ 改版原因（用户实测 ✓）：早先做成"无槽位容器菜单 + MenuScreens"那套时**三个按钮连回退都点不动** ✗
 * ⇒ 说明点击根本没进 `mouseClicked` ✓ ⇒ 现在**彻底不碰容器菜单** ✓ 只由服务端的
 * {@code PacketMomoMenuOpen} 把屏打开 ✓ 按钮点击直接发 {@link PacketMomoMenuAction} ✓。
 *
 * <p>另外加了**键盘兜底**（1/2/3 = 对话/交易/雇佣 ✓ ESC = 关 ✓）：万一鼠标那条路在某些环境下还是不灵 ✓ 也能用 ✓。
 */
public class MomoMenuScreen extends Screen {

    private static final int PANEL_W = 190;
    private static final int PANEL_H = 158;
    private static final int BTN_H = 22;
    private static final int GAP = 8;

    private final int momoId;
    private final int favor;
    private int hovered = -1;
    private int panelX, panelY;

    public MomoMenuScreen(int momoId, int favor) {
        super(Component.translatable("menu.tinkersnewlife.momo"));
        this.momoId = momoId;
        this.favor = favor;
    }

    /** 面板靠**左侧可**用区域居中（右侧给立绘让位 ✓ 用户口径：「进入菜单界面也显示立绘」） */
    private int availW() {
        return Math.max(PANEL_W + 8, this.width - MomoArt.portraitWidth(this.width, this.height) - 24);
    }

    private int left() { return (availW() - PANEL_W) / 2; }
    private int top() { return (this.height - PANEL_H) / 2; }
    private int btnX() { return left() + 16; }
    private int btnY(int i) { return top() + 42 + i * (BTN_H + GAP); }
    private int btnW() { return PANEL_W - 32; }
    private int backX() { return left() + PANEL_W - 56; }
    private int backY() { return top() + PANEL_H - 26; }

    private boolean canTalk() { return favor >= 0; }
    private boolean canHire() { return favor >= 0; }

    private boolean enabled(int i) {
        if (i == 0) return canTalk();
        if (i == 2) return canHire();
        return true;                       // 交易：负好感也能点（只是更贵 ✓ 用户口径）
    }

    private static String label(int i) {
        return switch (i) {
            case 0 -> "对话";
            case 1 -> "交易";
            case 2 -> "雇佣";
            default -> "回退";
        };
    }

    private boolean hovering(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(0, 0, this.width, this.height, 0x99000000);          // 压暗背景（和对话界面一致 ✓）
        MomoArt.portrait(graphics, MomoArt.exprForFavor(favor), this.width, this.height);   // 立绘随好感变脸 ✓

        int x = left();
        int y = top();
        MomoArt.nine(graphics, MomoArt.BUBBLE, x, y, PANEL_W, PANEL_H);     // 面板 = 气泡九宫格 ✓
        graphics.drawString(this.font, "墨默", x + 14, y + 12, 0x202020, false);
        graphics.drawString(this.font, "好感度 " + favor, x + 14, y + 26, 0x505050, false);
        graphics.fill(x + 12, y + 38, x + PANEL_W - 12, y + 39, 0x558C7F63);

        hovered = -1;
        for (int i = 0; i <= 2; i++) {
            boolean on = enabled(i);
            boolean hov = on && hovering(btnX(), btnY(i), btnW(), BTN_H, mouseX, mouseY);
            if (hov) hovered = i;
            MomoArt.nine(graphics, MomoArt.OPTION, btnX(), btnY(i), btnW(), BTN_H);
            if (!on) {
                graphics.fill(btnX() + 2, btnY(i) + 2, btnX() + btnW() - 2, btnY(i) + BTN_H - 2, 0x55202020);
            } else if (hov) {
                graphics.fill(btnX() + 2, btnY(i) + 2, btnX() + btnW() - 2, btnY(i) + BTN_H - 2, 0x33FFFFFF);
            }
            String s = label(i);
            graphics.drawString(this.font, s, btnX() + (btnW() - this.font.width(s)) / 2, btnY(i) + 7,
                    on ? 0x202020 : 0xFF6A6A6A, false);
        }
        boolean backHov = hovering(backX(), backY(), 44, 18, mouseX, mouseY);
        MomoArt.nine(graphics, MomoArt.OPTION, backX(), backY(), 44, 18);
        if (backHov) graphics.fill(backX() + 2, backY() + 2, backX() + 42, backY() + 16, 0x33FFFFFF);
        graphics.drawString(this.font, "回退", backX() + 22 - this.font.width("回退") / 2, backY() + 5, 0x202020, false);
    }

    /** 真正干活的地方（鼠标 / 键盘共用 ✓ 只此一处 ✓ 便于确认到底有没有被调用 ✓） */
    private void click(int action) {
        if (action == 3) {
            this.onClose();
            return;
        }
        if (!enabled(action)) return;
        TinkersNewlife.CHANNEL.sendToServer(new PacketMomoMenuAction(momoId, action));
        if (action == 0) {
            String name = this.minecraft != null && this.minecraft.player != null
                    ? this.minecraft.player.getGameProfile().getName() : "";
            this.setScreenCompat(new MomoTalkScreen(momoId, favor, name));
        } else {
            this.onClose();      // 交易/雇佣：交给服务端开交易界面 ✓ 先把菜单关掉 ✓
        }
    }

    private void setScreenCompat(Screen screen) {
        if (this.minecraft != null) this.minecraft.setScreen(screen);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hovering(backX(), backY(), 44, 18, mouseX, mouseY)) {
                click(3);
                return true;
            }
            for (int i = 0; i <= 2; i++) {
                if (hovering(btnX(), btnY(i), btnW(), BTN_H, mouseX, mouseY)) {
                    click(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case 49 -> { click(0); return true; }     // 1
            case 50 -> { click(1); return true; }     // 2
            case 51 -> { click(2); return true; }     // 3
            case 256 -> { this.onClose(); return true; }   // ESC
            default -> { }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
