package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoBuy;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHire;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 墨默（武器商人）交易界面：
 * 左侧雇佣栏位（1 个拉莱耶的呼唤 = 雇佣 1 天）；右侧 6 个售卖槽位（每天刷新一批，无交易上限），
 * 每行显示商品图标/名称/交易数量 + 货币价格；点击行购买。
 */
public class MomoTradeScreen extends Screen {

    private record Row(int slot, int x, int y, int w, int h) {}

    private static final int ROW_H = 26;
    private static final int GAP = 4;
    private static final int TOP = 46;
    private static final int HIRE_W = 122;
    private static final int HIRE_H = 84;
    private static final int OFFER_X_OFF = HIRE_W + 14;

    private final int momoId;
    private final int favor;
    /** 雇佣天数（−/＋ 选择 ✓ 1~30 ✓ 用户口径 §455 C ✓） */
    private int hireDays = 1;
    private int dayMinusX, dayPlusX;
    private boolean hired;
    private String employer;
    private final List<Row> rows = new ArrayList<>();
    private int startX;
    private int hireX;
    private int hireY;
    private int offerX;
    private int offerW;

    /** 是否为当前屏幕对应的墨默 */
    public boolean matches(int id) {
        return this.momoId == id;
    }

    /** 服务端推送雇佣状态变化时实时刷新（防止重复上交） */
    public void updateHireState(boolean hired, String employer) {
        this.hired = hired;
        this.employer = employer == null ? "" : employer;
    }

    private static final class View {
        final String category;
        final ItemStack result;
        final ItemStack currency;
        final int price;
        View(String category, ItemStack result, ItemStack currency, int price) {
            this.category = category;
            this.result = result;
            this.currency = currency;
            this.price = price;
        }
    }

    private final List<View> views = new ArrayList<>();

    public MomoTradeScreen(int momoId, List<MomoMerchant.Offer> offers, boolean hired, String employer, int favor) {
        super(Component.translatable("screen.tinkersnewlife.momo.title"));
        this.momoId = momoId;
        this.favor = favor;   // 好感度（随 PacketMomoOpen 同步过来 ✓ 用来显示折后价 ✓）
        this.hired = hired;
        this.employer = employer == null ? "" : employer;
        if (offers != null) {
            for (int i = 0; i < offers.size(); i++) {
                MomoMerchant.Offer offer = offers.get(i);
                if (offer.result().isEmpty()) continue;
                String cat;
                if (i < 2) cat = "screen.tinkersnewlife.momo.cat_cursed_tool";
                else if (i < 4) cat = "screen.tinkersnewlife.momo.cat_crystal";
                else cat = "screen.tinkersnewlife.momo.cat_relic";
                // 显示**折后价**（与服务端 buyFrom 里那一份算法一致 ✓ 见 MomoFavor.priceFactor ✓）
                int shown = Math.max(1, (int) Math.ceil(offer.price()
                        * com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor.priceFactor(this.favor)));
                views.add(new View(cat, offer.result(), new ItemStack(MomoMerchant.currencyForSlot(i)), shown));
            }
        }
    }

    @Override
    protected void init() {
        rows.clear();
        startX = (width - (OFFER_X_OFF + 240)) / 2;
        hireX = startX;
        hireY = TOP;
        offerX = startX + OFFER_X_OFF;
        offerW = 240;
        for (int i = 0; i < views.size(); i++) {
            rows.add(new Row(i, offerX, TOP + i * (ROW_H + GAP), offerW, ROW_H));
        }
    }

    private void drawCentered(GuiGraphics graphics, String key, int y, int color) {
        Component c = Component.translatable(key);
        graphics.drawString(font, c, (width - font.width(c)) / 2, y, color);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawCentered(graphics, "screen.tinkersnewlife.momo.title", 12, 0xFFFFFF);
        drawCentered(graphics, "screen.tinkersnewlife.momo.hint", 26, 0xAAAAAA);

        drawHirePanel(graphics, mouseX, mouseY);
        if (views.isEmpty()) {
            drawCentered(graphics, "screen.tinkersnewlife.momo.empty", 80, 0xFF5555);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (Row r : rows) {
            boolean hover = mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h;
            graphics.fill(r.x, r.y, r.x + r.w, r.y + r.h, hover ? 0xFF4A4A6A : 0xFF33334A);
            View v = views.get(r.slot);
            Component cat = Component.translatable(v.category);
            graphics.drawString(font, cat, r.x + 6, r.y + 2, 0x8FB0FF);
            // 商品名 + 交易数量（一次卖 N 个）
            String name = v.result.getHoverName().getString();
            int nameW = font.width(name);
            graphics.drawString(font, name, r.x + 6, r.y + 13, 0xFFFFFF);
            if (v.result.getCount() > 1) {
                String count = "×" + v.result.getCount();
                int countX = r.x + 8 + nameW;
                int priceZone = r.x + r.w - 92;
                if (countX + font.width(count) < priceZone) {
                    graphics.drawString(font, count, countX, r.y + 13, 0xBFBFBF);
                }
            }
            // 货币图标 + 价格（右）
            int priceW = font.width("×" + v.price);
            graphics.renderItem(v.currency, r.x + r.w - 30 - priceW, r.y + 4);
            graphics.drawString(font, "×" + v.price, r.x + r.w - 8 - priceW, r.y + 8, 0xFFD76A);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 左侧雇佣栏：拉莱耶的呼唤 ×1 = 雇佣一天 */
    private void drawHirePanel(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hover = mouseX >= hireX && mouseX <= hireX + HIRE_W && mouseY >= hireY && mouseY <= hireY + HIRE_H;
        graphics.fill(hireX, hireY, hireX + HIRE_W, hireY + HIRE_H, hover ? 0xFF4A3A5A : 0xFF332F42);
        // 标题
        Component head = Component.translatable("screen.tinkersnewlife.momo.hire_title");
        graphics.drawString(font, head, hireX + (HIRE_W - font.width(head)) / 2, hireY + 4, 0xFFE0A0);
        // 价格：拉莱耶的呼唤
        ItemStack rlyeh = new ItemStack(ModItems.RLYEH_CALL.get());
        graphics.renderItem(rlyeh, hireX + 6, hireY + 20);
        String price = "×1";
        graphics.drawString(font, price, hireX + 28, hireY + 24, 0xFFD76A);
        Component line2 = Component.translatable("screen.tinkersnewlife.momo.hire_cost");
        graphics.drawString(font, line2, hireX + 6, hireY + 40, 0xFFFFFF);
        // ⭐ 雇佣天数选择（− / ＋ ✓ 1~30 ✓ 用户口径 §455 C）
        dayMinusX = hireX + 34; dayPlusX = hireX + HIRE_W - 18;
        graphics.drawString(font, ("×" + hireDays + " 天"), hireX + 50, hireY + 24, 0xFFD76A);
        graphics.fill(dayMinusX, hireY + 20, dayMinusX + 12, hireY + 32, 0xFF5A4A6A);
        graphics.fill(dayPlusX, hireY + 20, dayPlusX + 12, hireY + 32, 0xFF5A4A6A);
        graphics.drawString(font, "−", dayMinusX + 3, hireY + 22, 0xFFFFFF);
        graphics.drawString(font, "+", dayPlusX + 3, hireY + 22, 0xFFFFFF);
        // 状态
        String status;
        int color;
        if (hired) {
            status = Component.translatable("screen.tinkersnewlife.momo.hired_state").getString()
                    + (employer.isEmpty() ? "" : " " + employer);
            color = 0x7FE07F;
        } else {
            status = Component.translatable("screen.tinkersnewlife.momo.not_hired").getString();
            color = 0xBFBFBF;
        }
        graphics.drawString(font, status, hireX + 6, hireY + 66, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 雇佣栏
            // 先判天数按钮（在雇佣栏内部 ✓ 要抢在整栏点击之前 ✓）
            if (mouseX >= dayMinusX && mouseX <= dayMinusX + 12 && mouseY >= hireY + 20 && mouseY <= hireY + 32) {
                hireDays = Math.max(1, hireDays - 1);
                return true;
            }
            if (mouseX >= dayPlusX && mouseX <= dayPlusX + 12 && mouseY >= hireY + 20 && mouseY <= hireY + 32) {
                hireDays = Math.min(30, hireDays + 1);
                return true;
            }
            if (mouseX >= hireX && mouseX <= hireX + HIRE_W && mouseY >= hireY && mouseY <= hireY + HIRE_H) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketMomoHire(momoId, hireDays));   // 带上天数 ✓
                return true;
            }
            // 商品行
            for (Row r : rows) {
                if (mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h) {
                    TinkersNewlife.CHANNEL.sendToServer(new PacketMomoBuy(momoId, r.slot));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
