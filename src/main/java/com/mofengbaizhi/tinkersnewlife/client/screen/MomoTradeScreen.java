package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import com.mofengbaizhi.tinkersnewlife.network.PacketMomoBuy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 墨默（武器商人）交易界面：
 * 6 个售卖槽位——前 2 咒具、中间 2 咒术水晶（货币：格赫罗斯残骸）、后 2 旧日遗物（货币：格赫罗斯矿石）。
 * 每行显示商品图标/名称 + 货币价格；点击行 → C2S 购买。
 */
public class MomoTradeScreen extends Screen {

    private record Row(int slot, int x, int y, int w, int h) {}

    private static final int W = 230;
    private static final int H = 26;
    private static final int GAP = 4;
    private static final int TOP = 46;

    private final int momoId;
    private final List<Row> rows = new ArrayList<>();

    /** 客户端视图数据 */
    private static final class View {
        final String category;      // 本地化键
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

    public MomoTradeScreen(int momoId, List<MomoMerchant.Offer> offers) {
        super(Component.translatable("screen.tinkersnewlife.momo.title"));
        this.momoId = momoId;
        if (offers != null) {
            for (int i = 0; i < offers.size(); i++) {
                MomoMerchant.Offer offer = offers.get(i);
                if (offer.result().isEmpty()) continue;
                String cat;
                if (i < 2) cat = "screen.tinkersnewlife.momo.cat_cursed_tool";
                else if (i < 4) cat = "screen.tinkersnewlife.momo.cat_crystal";
                else cat = "screen.tinkersnewlife.momo.cat_relic";
                Item currency = MomoMerchant.currencyForSlot(i);
                views.add(new View(cat, offer.result(), new ItemStack(currency), offer.price()));
            }
        }
    }

    @Override
    protected void init() {
        rows.clear();
        int startX = (width - W) / 2;
        for (int i = 0; i < views.size(); i++) {
            rows.add(new Row(i, startX, TOP + i * (H + GAP), W, H));
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
        if (views.isEmpty()) {
            drawCentered(graphics, "screen.tinkersnewlife.momo.empty", 70, 0xFF5555);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (Row r : rows) {
            boolean hover = mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h;
            graphics.fill(r.x, r.y, r.x + r.w, r.y + r.h, hover ? 0xFF4A4A6A : 0xFF33334A);
            View v = views.get(r.slot);
            // 分类（左）
            Component cat = Component.translatable(v.category);
            graphics.drawString(font, cat, r.x + 6, r.y + 2, 0x8FB0FF);
            // 商品名
            graphics.drawString(font, v.result.getHoverName(), r.x + 6, r.y + 13, 0xFFFFFF);
            // 货币图标 + 价格（右）
            int priceW = font.width("×" + v.price);
            graphics.renderItem(v.currency, r.x + r.w - 26 - priceW, r.y + 4);
            graphics.drawString(font, "×" + v.price, r.x + r.w - 6 - priceW, r.y + 8, 0xFFD76A);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
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
