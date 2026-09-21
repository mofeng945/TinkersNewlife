package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHire;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 墨默的**雇佣界面**（用户口径 §455 C ✓ 与交易界面**分开** ✓）。
 *
 * <ul>
 *   <li>**− / ＋ 选天数（1~30）** ✓ 或直接按键 1~9/0 快速设成 1/2/…/10 ✓；</li>
 *   <li>花费 = **N 份等价物** ✓ 优先级 **1 拉莱耶呼唤 → 30 格赫罗斯残骸 → 10 格赫罗斯矿石 → 50 金锭 → 20 钻石** ✓
 *       屏幕上把**五种等价物各自要多少**都列出来（图标 + 数量 ✓）⇒ 玩家一眼知道能拿什么付 ✓；</li>
 *   <li>点"雇佣 N 天"⇒ 发 {@link PacketMomoHire}（带天数 ✓）✓ 服务端按优先级扣、不够就一个都不扣 ✓；</li>
 *   <li>**回退**按钮 + ESC ✓ ⇒ 关掉本屏（要回菜单就再右键墨默 ✓）。</li>
 * </ul>
 */
public class MomoHireScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 190;

    private final int momoId;
    private final int favor;
    private int days = 1;

    public MomoHireScreen(int momoId, int favor) {
        super(Component.translatable("screen.tinkersnewlife.momo.hire_title"));
        this.momoId = momoId;
        this.favor = favor;
    }

    private int left() { return (this.width - PANEL_W) / 2; }
    private int top() { return (this.height - PANEL_H) / 2; }
    private int rowY(int i) { return top() + 46 + i * 22; }
    private int minusX() { return left() + 18; }
    private int plusX() { return left() + 96; }
    private int dayY() { return top() + 22; }
    private int hireX() { return left() + PANEL_W - 90; }
    private int hireY() { return top() + PANEL_H - 26; }
    private int backX() { return left() + 12; }
    private int backY() { return top() + PANEL_H - 26; }

    private boolean hovering(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private ItemStack[] units() {
        return new ItemStack[] {
                new ItemStack(ModItems.RLYEH_CALL.get()),
                new ItemStack(ModItems.GHELOTH_REMAINS.get()),
                new ItemStack(ModItems.GHELOTH_ORE.get()),
                new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT),
                new ItemStack(net.minecraft.world.item.Items.DIAMOND)
        };
    }

    private static final int[] PER = { 1, 30, 10, 50, 20 };

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
        graphics.drawString(this.font, "雇佣墨默", x + 8, y + 5, 0xFFFFFF, false);
        graphics.drawString(this.font, "好感度 " + favor + "（负好感不能雇佣）", x + 8, y + PANEL_H - 40, 0xFF303030, false);

        // 天数选择
        boolean minusHov = hovering(minusX(), dayY(), 16, 16, mouseX, mouseY);
        boolean plusHov = hovering(plusX(), dayY(), 16, 16, mouseX, mouseY);
        graphics.fill(minusX(), dayY(), minusX() + 16, dayY() + 16, minusHov ? 0xFF8FA8D8 : 0xFF6E6E6E);
        graphics.fill(plusX(), dayY(), plusX() + 16, dayY() + 16, plusHov ? 0xFF8FA8D8 : 0xFF6E6E6E);
        graphics.drawString(this.font, "−", minusX() + 5, dayY() + 4, 0x202020, false);
        graphics.drawString(this.font, "＋", plusX() + 3, dayY() + 4, 0x202020, false);
        String d = "× " + days + " 天";
        graphics.drawString(this.font, d, minusX() + 24, dayY() + 4, 0x202020, false);

        // 五种等价物的应付量
        ItemStack[] us = units();
        for (int i = 0; i < us.length; i++) {
            int ry = rowY(i);
            graphics.renderItem(us[i], x + 10, ry - 4);
            graphics.drawString(this.font, "× " + (PER[i] * days), x + 32, ry, 0xFF303030, false);
            if (i == 0) graphics.drawString(this.font, "（优先扣这个）", x + 96, ry, 0x2A6A2A, false);
        }

        // 雇佣 / 回退
        boolean hireHov = hovering(hireX(), hireY(), 78, 18, mouseX, mouseY);
        graphics.fill(hireX(), hireY(), hireX() + 78, hireY() + 18, hireHov ? 0xFF8FA8D8 : 0xFF6E6E6E);
        graphics.drawString(this.font, "雇佣 " + days + " 天", hireX() + 39 - this.font.width("雇佣 " + days + " 天") / 2,
                hireY() + 5, 0x202020, false);
        boolean backHov = hovering(backX(), backY(), 54, 18, mouseX, mouseY);
        graphics.fill(backX(), backY(), backX() + 54, backY() + 18, backHov ? 0xFF8FA8D8 : 0xFF6E6E6E);
        graphics.drawString(this.font, "回退", backX() + 27 - this.font.width("回退") / 2, backY() + 5, 0x202020, false);
    }

    private void doHire() {
        if (favor < 0) return;
        TinkersNewlife.CHANNEL.sendToServer(new PacketMomoHire(momoId, days));
        this.onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hovering(minusX(), dayY(), 16, 16, mouseX, mouseY)) {
                days = Math.max(1, days - 1);
                return true;
            }
            if (hovering(plusX(), dayY(), 16, 16, mouseX, mouseY)) {
                days = Math.min(30, days + 1);
                return true;
            }
            if (hovering(hireX(), hireY(), 78, 18, mouseX, mouseY)) {
                doHire();
                return true;
            }
            if (hovering(backX(), backY(), 54, 18, mouseX, mouseY)) {
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= 49 && keyCode <= 57) {           // 1~9 ⇒ 直接设天数
            days = keyCode - 48;
            return true;
        }
        if (keyCode == 48) {                            // 0 ⇒ 10 天
            days = 10;
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {         // 回车 ⇒ 雇佣
            doHire();
            return true;
        }
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
