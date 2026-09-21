package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoBuy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 墨默的**交易界面**（用户口径 §455 B ✓ **重绘版：与雇佣界面分开** ✓ **仿原版村民的排布** ✓）。
 *
 * <p>排版照原版村民窗的观感：一列**报价行** ✓ 每行 = 商品图标 + 名称 + 右侧**货币图标 × 数量** ✓
 * 悬停高亮 ✓ 点击买下 ✓。顶部显示**好感度与折扣**（价格已按好感算好 ✓ 与服务端同一算法 ✓）。
 * 雇佣那一栏**已搬去独立的雇佣界面**（{@link MomoHireScreen}）✓。
 */
public class MomoTradeScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int ROW_H = 26;
    private static final int PANEL_TOP_PAD = 32;

    private final int momoId;
    private final int favor;
    private final int[] sold;
    public boolean hired;
    public String employer;
    private final List<Row> rows = new ArrayList<>();

    private record Row(ItemStack result, ItemStack currency, int price, int base) {}

    private int left() { return (this.width - PANEL_W) / 2; }
    private int top() { return Math.max(10, (this.height - panelH()) / 2); }
    private int rowX() { return left() + 8; }
    private int rowY(int i) { return top() + PANEL_TOP_PAD + i * ROW_H; }
    private int rowW() { return PANEL_W - 16; }
    private int panelH() { return PANEL_TOP_PAD + Math.max(1, rows.size()) * ROW_H + 30; }
    private int backX() { return left() + PANEL_W - 62; }
    private int backY() { return top() + panelH() - 24; }

    public MomoTradeScreen(int momoId, List<MomoMerchant.Offer> offers, boolean hired, String employer, int favor, int[] sold) {
        super(Component.translatable("screen.tinkersnewlife.momo.title"));
        this.momoId = momoId;
        this.hired = hired;
        this.employer = employer == null ? "" : employer;
        this.favor = favor;
        this.sold = sold == null ? new int[0] : sold;
        if (offers != null) {
            for (int i = 0; i < offers.size(); i++) {
                MomoMerchant.Offer offer = offers.get(i);
                if (offer.result().isEmpty()) continue;
                // 显示折后价（与服务端 buyFrom 同一算法 ✓）
                int shown = Math.max(1, (int) Math.ceil(offer.price() * MomoFavor.priceFactor(favor)));
                rows.add(new Row(offer.result().copy(), new ItemStack(MomoMerchant.currencyForSlot(i)), shown, offer.price()));
            }
        }
    }

    /** 兼容旧调用：这只墨默是不是本界面的那只（PacketMomoHireState 会调 ✓） */
    public boolean matches(int id) {
        return this.momoId == id;
    }

    /** 兼容旧调用（PacketMomoHireState 会调 ✓） */
    public void updateHireState(boolean hired, String employer) {
        this.hired = hired;
        this.employer = employer == null ? "" : employer;
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
        int h = panelH();
        graphics.fill(x, y, x + PANEL_W, y + h, 0xFFC6C6C6);
        graphics.fill(x, y, x + PANEL_W, y + 17, 0xFF404040);
        graphics.drawString(this.font, "墨默 · 交易", x + 8, y + 5, 0xFFFFFF, false);

        int pct = (int) Math.round((1.0D - MomoFavor.priceFactor(favor)) * 100.0D);
        String fav = "好感度 " + favor + (pct > 0 ? "（降价 " + pct + "%）" : (pct < 0 ? "（涨价 " + (-pct) + "%）" : ""));
        graphics.drawString(this.font, fav, x + 8, y + 20, 0xFF303030, false);
        if (hired) {
            graphics.drawString(this.font, "已受雇于 " + employer, x + 8, y + h - 38, 0x606060, false);
        }

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = rowY(i);
            boolean soldOut = i < sold.length && sold[i] >= MomoMerchant.MAX_PER_DAY;
            boolean hover = !soldOut && hovering(rowX(), ry, rowW(), ROW_H - 2, mouseX, mouseY);
            graphics.fill(rowX(), ry, rowX() + rowW(), ry + ROW_H - 2, soldOut ? 0xFF6A6A6A : (hover ? 0xFF9FB6DE : 0xFF8A8A8A));
            graphics.renderItem(r.result(), rowX() + 2, ry + 3);
            String name = r.result().getHoverName().getString();
            if (r.result().getCount() > 1) name = name + " ×" + r.result().getCount();
            graphics.drawString(this.font, name, rowX() + 22, ry + 4, soldOut ? 0x8A8A8A : 0x202020, false);
            int cx = rowX() + rowW() - 46;
            graphics.renderItem(r.currency(), cx, ry + 3);
            if (soldOut) {
                graphics.drawString(this.font, "缺货", cx + 20, ry + 7, 0xFFAA00, false);
            } else {
                // 原价用红色删除线划掉 + 显示折后价（用户口径）
                int px = cx + 20;
                if (r.base() != r.price()) {
                    String base = "×" + r.base();
                    graphics.drawString(this.font,
                            net.minecraft.network.chat.Component.literal(base).withStyle(
                                    net.minecraft.ChatFormatting.RED,
                                    net.minecraft.ChatFormatting.STRIKETHROUGH),
                            px, ry + 7, 0xFF5555);
                    px += this.font.width(base) + 4;
                }
                graphics.drawString(this.font, "×" + r.price(), px, ry + 7,
                        r.price() > r.base() ? 0xAA0000 : 0x1F6B1F, false);
            }
        }

        boolean backHover = hovering(backX(), backY(), 54, 18, mouseX, mouseY);
        graphics.fill(backX(), backY(), backX() + 54, backY() + 18, backHover ? 0xFF8FA8D8 : 0xFF6E6E6E);
        graphics.drawString(this.font, "回退", backX() + 27 - this.font.width("回退") / 2, backY() + 5, 0x202020, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hovering(backX(), backY(), 54, 18, mouseX, mouseY)) {
                this.onClose();
                return true;
            }
            for (int i = 0; i < rows.size(); i++) {
                int ry = rowY(i);
                if (i < sold.length && sold[i] >= MomoMerchant.MAX_PER_DAY) continue;   // 缺货行点不动
                if (hovering(rowX(), ry, rowW(), ROW_H - 2, mouseX, mouseY)) {
                    TinkersNewlife.CHANNEL.sendToServer(new PacketMomoBuy(momoId, i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
