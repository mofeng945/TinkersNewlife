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
 * 「工业开拓之证」的产率查看界面（§742 建 · §743 按用户口径改成四列 ✓）
 *
 * <h2>每行的四列（用户口径 ✓）</h2>
 * <pre>
 *   [物品模型]  物品名称            目前总量      产率
 * </pre>
 * <ul>
 *   <li>物品模型 = {@code renderFakeItem} ✓（物品图标 ✓ 不加任何新 GUI 贴图 ✓ 守 §1 ✓）；</li>
 *   <li>目前总量 = <b>最近一次采样那一刻</b>该维度所有来源里这种物品的总数 ✓
 *       （不是实时读数 ✗ 见 {@code ContainerRateManager#totalNow} ✓）；</li>
 *   <li>产率 = 最近 1 小时的<b>净</b>产率（个/时 ✓）正绿负红 ✓。</li>
 * </ul>
 *
 * <h2>滚动（用户口径 ✓）</h2>
 * 列表比屏幕长时：<b>滚轮滚动</b> ✓ ＋ 右侧<b>滚动条可拖</b> ✓
 * —— 这两样都由父类 {@link AbstractRowListScreen} 提供 ✓ 本屏**没有覆盖** `mouseScrolled` ✓（继承即可 ✓）。
 */
public class IndustrialPioneerRatesScreen extends AbstractRowListScreen<PacketOpenPioneerRates.Row> {

    /** 列表宽度（四列排布需要宽一点 ✓） */
    private static final int LIST_WIDTH = 320;
    private static final int ROW_PITCH = 20;
    private static final int ROW_FILL = 18;

    /** 列锚点（相对行左端 x 的偏移 ✓） */
    private static final int ICON_X = 4;
    private static final int NAME_X = 24;
    /** 总量列的右边缘（相对行右端 w 的偏移 ✓） */
    private static final int TOTAL_RIGHT_PAD = 92;
    /** 产率列的右边缘 */
    private static final int RATE_RIGHT_PAD = 8;

    private final String dimensionKey;
    private final int totalKinds;

    public IndustrialPioneerRatesScreen(String dimensionKey, int totalKinds,
                                        List<PacketOpenPioneerRates.Row> rows) {
        super(Component.translatable("screen.tinkersnewlife.pioneer_rates.title",
                        Component.translatable(dimensionKey)),
                rows, LIST_WIDTH, ROW_PITCH, ROW_FILL, 64, 40);
        this.dimensionKey = dimensionKey;
        this.totalKinds = totalKinds;
    }

    // ============================================================
    //  表头（标题 ＋ 口径 ＋ 列名）
    // ============================================================

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.title",
                Component.translatable(dimensionKey)), 8, 0xFFD4924B);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.window"),
                20, 0x9A9A9A);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.note"),
                31, 0x6F6F7F);
        // 列名行（与行内四列对齐 ✓）
        int x = startX;
        int w = listWidth;
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.pioneer_rates.col_name"),
                x + NAME_X, 45, 0xB0B0C0);
        drawRight(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.col_total"),
                x + w - TOTAL_RIGHT_PAD, 45, 0xB0B0C0);
        drawRight(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.col_rate"),
                x + w - RATE_RIGHT_PAD, 45, 0xB0B0C0);
        graphics.fill(x, 57, x + w, 58, 0xFF55556A);
    }

    // ============================================================
    //  一行：模型 / 名称 / 目前总量 / 产率
    // ============================================================

    @Override
    protected void drawRow(GuiGraphics graphics, PacketOpenPioneerRates.Row row, int index,
                           int x, int y, int w, int h, boolean hover, double mouseX, double mouseY) {
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF4A4A6A : 0xFF33334A);

        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(row.itemId()));
        ItemStack stack = item == null ? ItemStack.EMPTY : new ItemStack(item);

        // ① 物品模型
        if (!stack.isEmpty()) {
            graphics.renderFakeItem(stack, x + ICON_X, y + 1);
        }

        // ② 物品名称（放不下就截断加省略号 ✓ 免得压到数字列 ✗）
        Component name = stack.isEmpty() ? Component.literal(row.itemId()) : stack.getHoverName();
        int nameMax = w - TOTAL_RIGHT_PAD - NAME_X - 8;
        String nameText = font.plainSubstrByWidth(name.getString(), Math.max(8, nameMax));
        if (nameText.length() < name.getString().length()) nameText = nameText + "…";
        graphics.drawString(font, nameText, x + NAME_X, y + 6,
                stack.isEmpty() ? 0x9A9A9A : 0xFFFFFF);

        // ③ 目前总量（右对齐到总量列 ✓）
        drawRight(graphics, Component.literal(formatAmount(row.total())),
                x + w - TOTAL_RIGHT_PAD, y + 6, 0xC8C8E0);

        // ④ 产率（个/时 ✓ 正绿负红 ✓）
        drawRight(graphics, Component.literal(formatRate(row.perHour())),
                x + w - RATE_RIGHT_PAD, y + 6, row.perHour() >= 0.0D ? 0x7CFF7C : 0xE05555);
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

    private void drawRight(GuiGraphics graphics, Component component, int right, int y, int color) {
        graphics.drawString(font, component, right - font.width(component), y, color);
    }

    /** 数量（个 ✓）：1.2k / 3.4M / 1.5G ✓ 小数目直接整数 ✓ */
    private static String formatAmount(long value) {
        long abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";
        if (abs >= 1_000_000_000L) return sign + String.format(Locale.ROOT, "%.2fG", abs / 1_000_000_000.0D);
        if (abs >= 1_000_000L) return sign + String.format(Locale.ROOT, "%.2fM", abs / 1_000_000.0D);
        if (abs >= 10_000L) return sign + String.format(Locale.ROOT, "%.2fk", abs / 1_000.0D);
        return sign + Long.toString(abs);
    }

    /** 产率（个/时 ✓）：带正负号 ✓ */
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
