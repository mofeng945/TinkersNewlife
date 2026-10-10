package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHire;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 墨默的**雇佣界面**（用户口径 §455 C ✓ 与交易界面分开 ✓；§497 改成与对话/菜单**同一套皮** ✓）。
 *
 * <ul>
 *   <li>右侧**立绘**（表情随好感 ✓）+ 左侧九宫格气泡面板 ✓ 文字走统一的放大字号 ✓；</li>
 *   <li>**− / ＋ 选天数（1~30）** ✓ 或按键 1~9/0 快速 1/2/…/10 ✓；回车 = 雇佣 ✓；</li>
 *   <li>花费 = **N 份等价物**（优先级 1 拉莱耶呼唤 → 30 残骸 → 10 矿石 → 50 金锭 → 20 钻石 ✓）⇒ 五种都列出来 ✓；</li>
 *   <li>**回退 = 回上一级主菜单**（§497 用户口径 ✓）—— 让服务端重发菜单包（带最新好感 ✓）。</li>
 * </ul>
 */
public class MomoHireScreen extends Screen {

    private static final int PANEL_W = 250;
    private static final int PANEL_H = 226;
    private static final int ROW_H = 22;
    private static final int BTN_H = 20;

    private final int momoId;
    private final int favor;
    private int days = 1;

    public MomoHireScreen(int momoId, int favor) {
        super(Component.translatable("screen.tinkersnewlife.momo.hire_title"));
        this.momoId = momoId;
        this.favor = favor;
    }

    private int availW() {
        return Math.max(PANEL_W + 8, this.width - MomoArt.portraitWidth(this.width, this.height) - 24);
    }

    private int left() { return (availW() - PANEL_W) / 2; }
    private int top() { return (this.height - PANEL_H) / 2; }
    /**
     * 天数选择：**面板内单独占一行**（§500）。
     * <p>⚠️ §498 我把它挪到"面板右边的空位"是错的 ✗：面板本来就在「立绘左侧可用区」里居中，
     * 它和立绘之间只剩十几像素（`gapW()` 里那个 `Math.max(140, …)` 下限骗了自己）⇒
     * 那行被摆到**她的头发上**，深色字压深色头发根本看不清 ✗。
     * 现在放回面板内、放在分隔线下面**独立一行**（并把面板加高 196→226 + 下方按钮下移 ✓ 谁也不压谁 ✓）。
     */
    private int minusX() { return left() + 16; }
    private int dayY() { return top() + 50; }
    private int dayTextX() { return minusX() + 24; }
    /** 固定按两位天数（"× 88 天"）算宽 ⇒ 天数变化时 ＋ 按钮**不左右跳** ✓ */
    private int plusX() { return dayTextX() + MomoArt.textWidth(this.font, "× 88 天") + 10; }
    private int rowY(int i) { return top() + 78 + i * ROW_H; }
    private int hireX() { return left() + PANEL_W - 96; }
    private int hireY() { return top() + PANEL_H - 28; }
    private int backX() { return left() + 14; }
    private int backY() { return top() + PANEL_H - 28; }

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

    /** §501 打开雇佣界面时她说的话（用户指定 ✓） */
    private static final String HELLO = "需要我帮忙吗？我出手可不便宜哦？";

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        MomoArt.dim(graphics, this.width, this.height);
        MomoArt.portrait(graphics, MomoArt.exprForFavor(favor), this.width, this.height);   // 与对话/菜单同一张脸 ✓

        int x = left();
        int y = top();

        // §501 开场白气泡：右缘 = 面板右缘 ⇒ 尖角指向她 ✓ 摆在面板**上方** ✓
        int sayH = MomoArt.sayHeight(this.font, HELLO, PANEL_W);
        int[] box = MomoArt.sayRight(graphics, this.font, left() + PANEL_W, Math.max(6, y - 12 - sayH), HELLO, PANEL_W);
        int tailY = Math.max(box[1] + 8, Math.min(box[1] + box[3] - 8, MomoArt.faceY(this.width, this.height)));
        MomoArt.tail(graphics, left() + PANEL_W, tailY, MomoArt.BUBBLE_FILL);

        MomoArt.nine(graphics, MomoArt.BUBBLE, x, y, PANEL_W, PANEL_H);      // 面板 = 同一套九宫格 ✓
        MomoArt.text(graphics, this.font, "雇佣墨默", x + 14, y + 10, MomoArt.TEXT_DARK);
        MomoArt.text(graphics, this.font, "好感度 " + favor, x + 14, y + 10 + MomoArt.LINE_H + 2, 0x505050);
        graphics.fill(x + 12, y + 44, x + PANEL_W - 12, y + 45, 0x558C7F63);

        // 天数选择：面板内独立一行（§500 ✓ 在分隔线下面、五种等价物上面 ✓）
        boolean minusHov = hovering(minusX(), dayY(), 18, 18, mouseX, mouseY);
        boolean plusHov = hovering(plusX(), dayY(), 18, 18, mouseX, mouseY);
        MomoArt.nine(graphics, MomoArt.OPTION, minusX(), dayY(), 18, 18);
        MomoArt.nine(graphics, MomoArt.OPTION, plusX(), dayY(), 18, 18);
        if (minusHov) graphics.fill(minusX() + 2, dayY() + 2, minusX() + 16, dayY() + 16, MomoArt.HOVER_TINT);
        if (plusHov) graphics.fill(plusX() + 2, dayY() + 2, plusX() + 16, dayY() + 16, MomoArt.HOVER_TINT);
        MomoArt.text(graphics, this.font, "−", minusX() + 5, dayY() + 4, MomoArt.TEXT_DARK);
        MomoArt.text(graphics, this.font, "＋", plusX() + 3, dayY() + 4, MomoArt.TEXT_DARK);
        MomoArt.text(graphics, this.font, "× " + days + " 天", dayTextX(), dayY() + 4, MomoArt.TEXT_DARK);

        // 五种等价物的应付量
        ItemStack[] us = units();
        for (int i = 0; i < us.length; i++) {
            int ry = rowY(i);
            graphics.renderItem(us[i], x + 12, ry - 6);
            MomoArt.text(graphics, this.font, "× " + (PER[i] * days), x + 36, ry, MomoArt.TEXT_DARK);
            if (i == 0) {
                MomoArt.text(graphics, this.font, "（优先扣这个）", x + 104, ry, 0x2A6A2A);
            }
        }

        // 雇佣 / 回退（回退 = 回主菜单 ✓ §497）
        boolean hireHov = hovering(hireX(), hireY(), 82, BTN_H, mouseX, mouseY);
        boolean backHov = hovering(backX(), backY(), 56, BTN_H, mouseX, mouseY);
        boolean canHire = favor >= 0;
        MomoArt.nine(graphics, MomoArt.OPTION, hireX(), hireY(), 82, BTN_H);
        MomoArt.nine(graphics, MomoArt.OPTION, backX(), backY(), 56, BTN_H);
        if (hireHov && canHire) graphics.fill(hireX() + 2, hireY() + 2, hireX() + 80, hireY() + BTN_H - 2, MomoArt.HOVER_TINT);
        if (backHov) graphics.fill(backX() + 2, backY() + 2, backX() + 54, backY() + BTN_H - 2, MomoArt.HOVER_TINT);
        String h = "雇佣 " + days + " 天";
        MomoArt.text(graphics, this.font, h, hireX() + 41 - MomoArt.textWidth(this.font, h) / 2, hireY() + 4,
                canHire ? MomoArt.TEXT_DARK : MomoArt.DISABLED_TEXT);
        MomoArt.text(graphics, this.font, "回退", backX() + 28 - MomoArt.textWidth(this.font, "回退") / 2, backY() + 4,
                MomoArt.TEXT_DARK);
    }

    private void doHire() {
        if (favor < 0) return;
        TinkersNewlife.CHANNEL.sendToServer(new PacketMomoHire(momoId, days));
        this.onClose();
    }

    /**
     * §497 回上一级主菜单（服务端重发菜单包、带最新好感 ✓）。
     * <p>⚠️ §499 **不 `onClose()`** ✗：先关界面会让 MC `grabMouse()` 把鼠标拉回屏幕中心 ✗ ⇒ 等菜单包来替换本屏 ✓。
     */
    private void backToMenu() {
        TinkersNewlife.CHANNEL.sendToServer(new PacketMomoMenuAction(momoId, 4));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hovering(minusX(), dayY(), 18, 18, mouseX, mouseY)) {
                days = Math.max(1, days - 1);
                return true;
            }
            if (hovering(plusX(), dayY(), 18, 18, mouseX, mouseY)) {
                days = Math.min(30, days + 1);
                return true;
            }
            if (hovering(hireX(), hireY(), 82, BTN_H, mouseX, mouseY)) {
                doHire();
                return true;
            }
            if (hovering(backX(), backY(), 56, BTN_H, mouseX, mouseY)) {
                backToMenu();
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
