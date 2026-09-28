package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.network.rate.PacketOpenPioneerRates;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

/**
 * 「工业开拓之证」的产率查看界面（§742）—— 服务端把数据发过来后开这个屏 ✓
 *
 * <p>只做**只读列表** ✓：每行 = 物品图标 ＋ 名字 ＋ 「个/时」✓；滚轮/拖动滚动条由
 * {@link AbstractRowListScreen} 现成提供 ✓（本屏只负责画"表头 / 行 / 空提示 / 页脚"✓）。
 *
 * <p>⚠ **不新增任何 GUI 贴图** ✓：面板与行都是纯色 {@code graphics.fill} ✓（守 §1 ✓），
 * 物品图标走 {@code renderFakeItem} ✓。
 */
public class IndustrialPioneerRatesScreen extends AbstractRowListScreen<PacketOpenPioneerRates.Row> {

    /** 列表宽度（宽一点，物品名 + 数字才排得下 ✓） */
    private static final int LIST_WIDTH = 300;
    private static final int ROW_PITCH = 20;
    private static final int ROW_FILL = 18;

    private final String dimensionKey;
    private final int totalKinds;

    public IndustrialPioneerRatesScreen(String dimensionKey, int totalKinds,
                                        List<PacketOpenPioneerRates.Row> rows) {
        super(Component.translatable("screen.tinkersnewlife.pioneer_rates.title",
                        Component.translatable(dimensionKey)),
                rows, LIST_WIDTH, ROW_PITCH, ROW_FILL, 52, 40);
        this.dimensionKey = dimensionKey;
        this.totalKinds = totalKinds;
    }

    // ============================================================
    //  表头 / 行 / 空提示 / 页脚
    // ============================================================

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.title",
                Component.translatable(dimensionKey)), 10, 0xFFD4924B);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.window"),
                24, 0x9A9A9A);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.note"),
                36, 0x6F6F7F);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, PacketOpenPioneerRates.Row row, int index,
                           int x, int y, int w, int h, boolean hover, double mouseX, double mouseY) {
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF4A4A6A : 0xFF33334A);

        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(row.itemId()));
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        if (!stack.isEmpty()) {
            graphics.renderFakeItem(stack, x + 4, y + 1);
        }
        Component name = stack.isEmpty()
                ? Component.literal(row.itemId())
                : stack.getHoverName();
        graphics.drawString(font, name, x + 26, y + 6, stack.isEmpty() ? 0x9A9A9A : 0xFFFFFF);

        String rate = formatRate(row.perHour());
        graphics.drawString(font, rate, x + w - 8 - font.width(rate), y + 6,
                row.perHour() >= 0.0D ? 0x7CFF7C : 0xE05555);
    }

    @Override
    protected void onRowClick(int index, PacketOpenPioneerRates.Row row) {
        // 只读列表：点击不做任何事 ✓（以后要"点进去看来源"可以挂这里 ✓）
    }

    @Override
    protected void drawEmptyMessage(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.empty"),
                (listTop + listBottom) / 2, 0x9A9A9A);
    }

    @Override
    protected void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = listBottom + 6;
        if (rows.size() < totalKinds) {
            drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.footer.truncated",
                    totalKinds, rows.size()), y, 0x9A9A9A);
        } else {
            drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.footer",
                    totalKinds), y, 0x9A9A9A);
        }
    }

    // ============================================================
    //  小工具
    // ============================================================

    private void drawCentered(GuiGraphics graphics, Component component, int y, int color) {
        graphics.drawCenteredString(font, component, width / 2, y, color);
    }

    /** 数字格式：大数用 1.2k / 3.4M ✓（小数目直接整数 ✓），负数带 − ✓ */
    private static String formatRate(double value) {
        double abs = Math.abs(value);
        String sign = value < 0 ? "-" : "+";
        if (abs >= 1_000_000_000.0D) return sign + String.format(Locale.ROOT, "%.2fG", abs / 1_000_000_000.0D);
        if (abs >= 1_000_000.0D) return sign + String.format(Locale.ROOT, "%.2fM", abs / 1_000_000.0D);
        if (abs >= 10_000.0D) return sign + String.format(Locale.ROOT, "%.2fk", abs / 1_000.0D);
        if (abs >= 100.0D) return sign + String.format(Locale.ROOT, "%.0f", abs);
        return sign + String.format(Locale.ROOT, "%.1f", abs);
    }
}
