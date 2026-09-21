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

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 160;
    private static final int BTN_H = 22;

    private final int momoId;
    private final int favor;
    private int hovered = -1;

    public MomoMenuScreen(int momoId, int favor) {
        super(Component.translatable("menu.tinkersnewlife.momo"));
        this.momoId = momoId;
        this.favor = favor;
    }

    private int left() { return (this.width - PANEL_W) / 2; }
    private int top() { return (this.height - PANEL_H) / 2; }
    private int btnX() { return left() + 20; }
    private int btnY(int i) { return top() + 36 + i * (BTN_H + 6); }
    private int btnW() { return PANEL_W - 40; }
    private int backX() { return left() + PANEL_W - 62; }
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
        int x = left();
        int y = top();
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0xFFC6C6C6);
        graphics.fill(x, y, x + PANEL_W, y + 17, 0xFF404040);
        graphics.drawString(this.font, "墨默", x + 8, y + 5, 0xFFFFFF, false);
        graphics.drawString(this.font, "好感度 " + favor, x + 8, y + 21, 0xFF303030, false);

        hovered = -1;
        for (int i = 0; i <= 2; i++) {
            boolean on = enabled(i);
            boolean hov = on && hovering(btnX(), btnY(i), btnW(), BTN_H, mouseX, mouseY);
            if (hov) hovered = i;
            graphics.fill(btnX(), btnY(i), btnX() + btnW(), btnY(i) + BTN_H,
                    !on ? 0xFF4A4A4A : (hov ? 0xFF8FA8D8 : 0xFF6E6E6E));
            String s = label(i);
            graphics.drawString(this.font, s, btnX() + (btnW() - this.font.width(s)) / 2, btnY(i) + 7,
                    on ? 0x202020 : 0xFF7A7A7A, false);
        }
        boolean backHov = hovering(backX(), backY(), 54, 18, mouseX, mouseY);
        graphics.fill(backX(), backY(), backX() + 54, backY() + 18, backHov ? 0xFF8FA8D8 : 0xFF6E6E6E);
        graphics.drawString(this.font, "回退", backX() + 27 - this.font.width("回退") / 2, backY() + 5, 0x202020, false);
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
            this.setScreenCompat(new MomoTalkScreen(favor, name));
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
            if (hovering(backX(), backY(), 54, 18, mouseX, mouseY)) {
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
