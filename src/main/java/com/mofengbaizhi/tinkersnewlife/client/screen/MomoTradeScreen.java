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

    private static final int PANEL_W = 264;
    private static final int ROW_H = 26;
    private static final int PANEL_TOP_PAD = 46;

    private final int momoId;
    private final int favor;
    private int[] sold;   // 会被 PacketMomoSoldState 就地刷新 ✓
    public boolean hired;
    public String employer;
    private final List<Row> rows = new ArrayList<>();

    private record Row(ItemStack result, ItemStack currency, int price, int base) {}

    private int left() {
        return Math.max(12, (Math.max(PANEL_W + 8, this.width - MomoArt.portraitWidth(this.width, this.height) - 24)
                - PANEL_W) / 2);
    }
    private int top() { return Math.max(10, (this.height - panelH()) / 2); }
    private int rowX() { return left() + 12; }
    private int rowY(int i) { return top() + PANEL_TOP_PAD + i * ROW_H; }
    private int rowW() { return PANEL_W - 24; }
    private int panelH() { return PANEL_TOP_PAD + Math.max(1, rows.size()) * ROW_H + 34; }
    private int backX() { return left() + PANEL_W - 68; }
    private int backY() { return top() + panelH() - 26; }

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
    /** 就地刷新今日已买次数（`PacketMomoSoldState` 收到就调 ⇒ 缺货立刻变灰，不用重开界面） */
    public void updateSold(int[] sold) {
        this.sold = sold == null ? new int[0] : sold;
    }
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

    /** §501 打开交易界面时她说的话（用户指定 ✓） */
    private static final String HELLO = "嗯……你好，有什么想要的吗？";

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        MomoArt.dim(graphics, this.width, this.height);
        MomoArt.portrait(graphics, MomoArt.exprForFavor(favor), this.width, this.height);   // §497 同一套皮：右侧立绘 ✓

        int x = left();
        int y = top();
        int h = panelH();

        // §501 开场白气泡：右缘 = 面板右缘 ⇒ 尖角从这里指向她 ✓ 摆在面板**上方** ✓
        int sayH = MomoArt.sayHeight(this.font, HELLO, PANEL_W);
        int[] box = MomoArt.sayRight(graphics, this.font, left() + PANEL_W, Math.max(6, y - 12 - sayH), HELLO, PANEL_W);
        int tailY = Math.max(box[1] + 8, Math.min(box[1] + box[3] - 8, MomoArt.faceY(this.width, this.height)));
        MomoArt.tail(graphics, left() + PANEL_W, tailY, MomoArt.BUBBLE_FILL);

        MomoArt.nine(graphics, MomoArt.BUBBLE, x, y, PANEL_W, h);                            // 面板 = 同一套九宫格 ✓
        MomoArt.text(graphics, this.font, "墨默 · 交易", x + 14, y + 10, MomoArt.TEXT_DARK);

        int pct = (int) Math.round((1.0D - MomoFavor.priceFactor(favor)) * 100.0D);
        String fav = "好感度 " + favor + (pct > 0 ? "（降价 " + pct + "%）" : (pct < 0 ? "（涨价 " + (-pct) + "%）" : ""));
        MomoArt.text(graphics, this.font, fav, x + 14, y + 12 + MomoArt.LINE_H + 2, 0x505050);
        if (hired) {
            MomoArt.text(graphics, this.font, "已受雇于 " + employer, x + 14, y + 12 + (MomoArt.LINE_H + 2) * 2, 0x606060);
        }
        graphics.fill(x + 12, y + PANEL_TOP_PAD - 8, x + PANEL_W - 12, y + PANEL_TOP_PAD - 7, 0x558C7F63);

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = rowY(i);
            boolean soldOut = i < sold.length && sold[i] >= MomoMerchant.MAX_PER_DAY;
            boolean hover = !soldOut && hovering(rowX(), ry, rowW(), ROW_H - 2, mouseX, mouseY);
            MomoArt.nine(graphics, MomoArt.OPTION, rowX(), ry, rowW(), ROW_H - 2);           // 报价行 = 九宫格 ✓
            graphics.fill(rowX() + 2, ry + 2, rowX() + rowW() - 2, ry + ROW_H - 4,
                    soldOut ? MomoArt.DISABLED_TINT : (hover ? MomoArt.HOVER_TINT : MomoArt.PLAYER_TINT));
            graphics.renderItem(r.result(), rowX() + 4, ry + 3);
            String name = r.result().getHoverName().getString();
            if (r.result().getCount() > 1) name = name + " ×" + r.result().getCount();
            MomoArt.text(graphics, this.font, name, rowX() + 26, ry + 5,
                    soldOut ? 0x707070 : MomoArt.TEXT_DARK);
            // ⭐ 价格块（货币图标 + 原价 + 折后价）**整体右对齐**，宽度不够就**整体左移**（原价一定要画 ✓ 不许写到行外 ✗）
            String now = "×" + r.price();
            String base = "×" + r.base();
            boolean showBase = !soldOut && r.base() != r.price();
            int nowW = MomoArt.textWidth(this.font, now);
            int baseW = showBase ? MomoArt.textWidth(this.font, base) : 0;
            int totalW = baseW + (showBase ? 5 : 0) + nowW;
            int iconX = Math.max(rowX() + 76, rowX() + rowW() - 6 - totalW - 20);
            graphics.renderItem(r.currency(), iconX, ry + 3);
            int ty = ry + (ROW_H - 2 - MomoArt.LINE_H) / 2 + 1;
            if (soldOut) {
                MomoArt.text(graphics, this.font, "缺货", iconX + 22, ty, 0xFFAA00);
            } else {
                int px = iconX + 22;
                if (showBase) {
                    MomoArt.text(graphics, this.font,
                            net.minecraft.network.chat.Component.literal(base).withStyle(
                                    net.minecraft.ChatFormatting.RED,
                                    net.minecraft.ChatFormatting.STRIKETHROUGH).getVisualOrderText(),
                            px, ty, 0xFF5555);
                    px += baseW + 5;
                }
                MomoArt.text(graphics, this.font, now, px, ty, r.price() > r.base() ? 0xAA0000 : 0x1F6B1F);
            }
        }

        boolean backHover = hovering(backX(), backY(), 60, 20, mouseX, mouseY);
        MomoArt.nine(graphics, MomoArt.OPTION, backX(), backY(), 60, 20);
        if (backHover) graphics.fill(backX() + 2, backY() + 2, backX() + 58, backY() + 18, MomoArt.HOVER_TINT);
        MomoArt.text(graphics, this.font, "回退", backX() + 30 - MomoArt.textWidth(this.font, "回退") / 2, backY() + 4,
                MomoArt.TEXT_DARK);
    }

    /**
     * §497 回上一级主菜单（服务端重发菜单包、带最新好感 ✓ 交易后好感会 +1 ✓）。
     * <p>⚠️ §499 **不 `onClose()`** ✗：先关界面会让 MC `grabMouse()` 把鼠标拉回屏幕中心 ✗ ⇒ 等菜单包来替换本屏 ✓。
     */
    private void backToMenu() {
        TinkersNewlife.CHANNEL.sendToServer(
                new com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuAction(momoId, 4));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hovering(backX(), backY(), 60, 20, mouseX, mouseY)) {
                backToMenu();                        // §497：回上一级主菜单 ✓（不再直接关 GUI ✗）
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
