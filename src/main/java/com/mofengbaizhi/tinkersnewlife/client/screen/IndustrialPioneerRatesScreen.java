package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.rate.PacketOpenPioneerRates;
import com.mofengbaizhi.tinkersnewlife.network.rate.PacketRequestPioneerRates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 「工业开拓之证」的产率界面（§742 建 · §743 四列 · §746 加<b>搜索框 ＋ 流体页 ＋ 能量页</b> ✓）
 *
 * <h2>三个页签（用户口径「加上流体显示和能量显示」✓）</h2>
 * <pre>
 *   物品 ：[物品图标]  名称   目前总量（个）   产率（个/时）
 *   流体 ：[它的桶图标] 名称   目前总量（mB）   产率（mB/时）
 *   能量 ：整维度一行：当前储能 X FE ／ 净产率 +Y FE/时
 * </pre>
 *
 * <h2>搜索框（用户口径 ✓）</h2>
 * 顶部一个 {@link EditBox}：输入即筛 ✓（大小写不敏感 ✓ **显示名与注册名都能搜** ✓ 直接粘 id 也行 ✓）；
 * 三个页签**各自记住搜索词** ✓（切页签不互相清空 ✓，5 秒一次的动态刷新也不清空 ✓）。
 *
 * <h2>滚动（用户口径 ✓）</h2>
 * 比屏幕长时：<b>滚轮滚动</b> ✓ ＋ 右侧<b>滚动条可拖</b> ✓
 * —— 由父类 {@link AbstractRowListScreen} 提供 ✓ 本屏**没有覆盖** `mouseScrolled` ✓。
 *
 * <h2>⚠ 没有新增任何 GUI 贴图（守 §1 ✓）</h2>
 * 面板/行都是纯色 {@code graphics.fill} ✓；物品图标 `renderFakeItem` ✓；
 * 流体画的是**它自己的桶**图标（{@code Fluid#getBucket} ✓）✓。
 */
public class IndustrialPioneerRatesScreen extends AbstractRowListScreen<PacketOpenPioneerRates.Row> {

    private static final int LIST_WIDTH = 320;
    private static final int ROW_PITCH = 20;
    private static final int ROW_FILL = 18;

    /** 列锚点 */
    private static final int ICON_X = 4;
    private static final int NAME_X = 24;
    private static final int TOTAL_RIGHT_PAD = 96;
    private static final int RATE_RIGHT_PAD = 8;

    private final String dimensionKey;
    private int totalKinds;

    /** 服务端送来的**全部**行（三类混在一起 ✓）；{@link #rows} 是"当前页签 ＋ 当前搜索"的视图 ✓ */
    private final List<PacketOpenPioneerRates.Row> allRows = new ArrayList<>();
    /** 当前页签（{@link PacketOpenPioneerRates#KIND_ITEM} 等 ✓） */
    private int activeKind = PacketOpenPioneerRates.KIND_ITEM;
    /** 三个页签各自的搜索词 ✓ */
    private final String[] queries = {"", "", ""};

    private EditBox searchBox;
    private Button itemTab;
    private Button fluidTab;
    private Button energyTab;
    private int refreshTicker;

    public IndustrialPioneerRatesScreen(String dimensionKey, int totalKinds,
                                        List<PacketOpenPioneerRates.Row> serverRows) {
        super(Component.translatable("screen.tinkersnewlife.pioneer_rates.title",
                        Component.translatable(dimensionKey)),
                new ArrayList<>(), LIST_WIDTH, ROW_PITCH, ROW_FILL, 84, 40);
        this.dimensionKey = dimensionKey;
        this.totalKinds = totalKinds;
        replaceRows(totalKinds, serverRows);
    }

    // ============================================================
    //  控件：三个页签 ＋ 搜索框
    // ============================================================

    @Override
    protected void init() {
        super.init();                                  // 先把列表几何算好 ✓
        int x = (width - LIST_WIDTH) / 2;
        int tabWidth = 72;

        itemTab = addRenderableWidget(Button.builder(tabLabel(PacketOpenPioneerRates.KIND_ITEM),
                        b -> switchTab(PacketOpenPioneerRates.KIND_ITEM))
                .bounds(x, 60, tabWidth, 18).build());
        fluidTab = addRenderableWidget(Button.builder(tabLabel(PacketOpenPioneerRates.KIND_FLUID),
                        b -> switchTab(PacketOpenPioneerRates.KIND_FLUID))
                .bounds(x + tabWidth + 4, 60, tabWidth, 18).build());
        energyTab = addRenderableWidget(Button.builder(tabLabel(PacketOpenPioneerRates.KIND_ENERGY),
                        b -> switchTab(PacketOpenPioneerRates.KIND_ENERGY))
                .bounds(x + (tabWidth + 4) * 2, 60, tabWidth, 18).build());

        searchBox = new EditBox(font, x + (tabWidth + 4) * 3 + 4, 60,
                LIST_WIDTH - (tabWidth + 4) * 3 - 4, 18,
                Component.translatable("screen.tinkersnewlife.pioneer_rates.search"));
        searchBox.setMaxLength(48);
        searchBox.setHint(Component.translatable("screen.tinkersnewlife.pioneer_rates.search"));
        searchBox.setValue(queries[activeKind]);
        searchBox.setResponder(text -> {
            queries[activeKind] = text == null ? "" : text;
            applyFilter();
        });
        addRenderableWidget(searchBox);

        applyFilter();
    }

    private Component tabLabel(int kind) {
        String key = switch (kind) {
            case PacketOpenPioneerRates.KIND_FLUID -> "screen.tinkersnewlife.pioneer_rates.tab.fluid";
            case PacketOpenPioneerRates.KIND_ENERGY -> "screen.tinkersnewlife.pioneer_rates.tab.energy";
            default -> "screen.tinkersnewlife.pioneer_rates.tab.item";
        };
        // 当前页签加个箭头 ✓（不额外画贴图 ✓）
        return Component.literal(kind == activeKind ? "▶ " : "").append(Component.translatable(key));
    }

    private void switchTab(int kind) {
        if (kind == activeKind) return;
        activeKind = kind;
        if (searchBox != null) searchBox.setValue(queries[kind]);   // 切页签恢复该页签的搜索词 ✓
        if (itemTab != null) itemTab.setMessage(tabLabel(PacketOpenPioneerRates.KIND_ITEM));
        if (fluidTab != null) fluidTab.setMessage(tabLabel(PacketOpenPioneerRates.KIND_FLUID));
        if (energyTab != null) energyTab.setMessage(tabLabel(PacketOpenPioneerRates.KIND_ENERGY));
        applyFilter();
    }

    /** 按"当前页签 ＋ 搜索词"重算可见行 ✓（改完必须 {@code refreshLayout} ✓ 否则滚动条/命中判定错位 ✗） */
    private void applyFilter() {
        String query = queries[activeKind].trim().toLowerCase(Locale.ROOT);
        rows.clear();
        for (PacketOpenPioneerRates.Row row : allRows) {
            if (row.kind() != activeKind) continue;
            if (!query.isEmpty() && !matches(row, query)) continue;
            rows.add(row);
        }
        refreshLayout();
    }

    /** 匹配：**显示名**（已翻译 ✓）或**注册名**包含搜索词即可 ✓（大小写不敏感 ✓） */
    private boolean matches(PacketOpenPioneerRates.Row row, String query) {
        if (row.kind() == PacketOpenPioneerRates.KIND_ENERGY) return true;
        if (row.id().toLowerCase(Locale.ROOT).contains(query)) return true;
        Component name = displayName(row);
        return name != null && name.getString().toLowerCase(Locale.ROOT).contains(query);
    }

    // ============================================================
    //  §744：动态刷新（每 5 秒要一次最新数据 ✓）
    // ============================================================

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicker < 100) return;      // 100 tick = 5 秒 ✓
        refreshTicker = 0;
        TinkersNewlife.CHANNEL.sendToServer(new PacketRequestPioneerRates(dimensionId()));
    }

    private String dimensionId() {
        String prefix = "dimension.";
        return dimensionKey.startsWith(prefix) ? dimensionKey.substring(prefix.length()) : dimensionKey;
    }

    /** 服务端推来新数据时<b>原地替换</b> ✓（返回 true = 已更新 ✓ 调用方不必再开新屏 ✓） */
    public static boolean applyUpdate(String dimensionKey, int totalKinds,
                                      List<PacketOpenPioneerRates.Row> newRows) {
        if (!(Minecraft.getInstance().screen instanceof IndustrialPioneerRatesScreen screen)) return false;
        if (!screen.dimensionKey.equals(dimensionKey)) return false;
        screen.replaceRows(totalKinds, newRows);
        return true;
    }

    private void replaceRows(int totalKinds, List<PacketOpenPioneerRates.Row> newRows) {
        this.totalKinds = totalKinds;
        allRows.clear();
        if (newRows != null) allRows.addAll(newRows);
        applyFilter();                 // ★ 搜索词与页签**保留** ✓（刷新不该把玩家搜的东西清掉 ✗）
    }

    // ============================================================
    //  画：表头 / 行 / 空提示 / 页脚
    // ============================================================

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.title",
                Component.translatable(dimensionKey)), 8, 0xFFD4924B);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.window"),
                20, 0x9A9A9A);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.note"),
                31, 0x6F6F7F);

        Component unit = unitLabel(activeKind);
        int x = startX;
        int w = listWidth;
        if (activeKind == PacketOpenPioneerRates.KIND_ENERGY) {
            graphics.drawString(font, Component.translatable("screen.tinkersnewlife.pioneer_rates.col.energy"),
                    x + NAME_X, 44, 0xB0B0C0);
        } else {
            graphics.drawString(font, Component.translatable("screen.tinkersnewlife.pioneer_rates.col_name"),
                    x + NAME_X, 44, 0xB0B0C0);
            drawRight(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.col_total.unit", unit),
                    x + w - TOTAL_RIGHT_PAD, 44, 0xB0B0C0);
            drawRight(graphics, Component.translatable("screen.tinkersnewlife.pioneer_rates.col_rate.unit", unit),
                    x + w - RATE_RIGHT_PAD, 44, 0xB0B0C0);
        }
        graphics.fill(x, 57, x + w, 58, 0xFF55556A);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, PacketOpenPioneerRates.Row row, int index,
                           int x, int y, int w, int h, boolean hover, double mouseX, double mouseY) {
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF4A4A6A : 0xFF33334A);

        if (row.kind() == PacketOpenPioneerRates.KIND_ENERGY) {
            // 能量页：整维度一行，分两句更好读 ✓
            graphics.drawString(font,
                    Component.translatable("screen.tinkersnewlife.pioneer_rates.energy.stored",
                            formatAmount(row.total())), x + NAME_X, y + 6, 0xC8C8E0);
            drawRight(graphics,
                    Component.translatable("screen.tinkersnewlife.pioneer_rates.energy.rate",
                            formatRate(row.perHour())),
                    x + w - RATE_RIGHT_PAD, y + 6, row.perHour() >= 0.0D ? 0x7CFF7C : 0xE05555);
            return;
        }

        ItemStack icon = iconOf(row);
        if (!icon.isEmpty()) {
            graphics.renderFakeItem(icon, x + ICON_X, y + 1);
        }
        Component name = displayName(row);
        String text = name == null ? row.id() : name.getString();
        String shown = font.plainSubstrByWidth(text, Math.max(8, w - TOTAL_RIGHT_PAD - NAME_X - 8));
        if (shown.length() < text.length()) shown = shown + "…";
        graphics.drawString(font, shown, x + NAME_X, y + 6, icon.isEmpty() ? 0x9A9A9A : 0xFFFFFF);

        drawRight(graphics, Component.literal(formatAmount(row.total())),
                x + w - TOTAL_RIGHT_PAD, y + 6, 0xC8C8E0);
        drawRight(graphics, Component.literal(formatRate(row.perHour())),
                x + w - RATE_RIGHT_PAD, y + 6, row.perHour() >= 0.0D ? 0x7CFF7C : 0xE05555);
    }

    @Override
    protected void onRowClick(int index, PacketOpenPioneerRates.Row row) {
        // 只读列表 ✓（以后要"点进去看来源"可以挂这里 ✓）
    }

    @Override
    protected void drawEmptyMessage(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean searching = !queries[activeKind].trim().isEmpty();
        drawCentered(graphics, Component.translatable(searching
                        ? "screen.tinkersnewlife.pioneer_rates.empty.search"
                        : "screen.tinkersnewlife.pioneer_rates.empty"),
                (listTop + listBottom) / 2, 0x9A9A9A);
    }

    @Override
    protected void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = listBottom + 6;
        Component line = rows.size() < totalKinds && activeKind != PacketOpenPioneerRates.KIND_ENERGY
                ? Component.translatable("screen.tinkersnewlife.pioneer_rates.footer.truncated", totalKinds, rows.size())
                : Component.translatable("screen.tinkersnewlife.pioneer_rates.footer.showing", rows.size());
        drawCentered(graphics, line, y, 0x9A9A9A);
    }

    // ============================================================
    //  小工具：图标 / 名字 / 单位 / 数字格式
    // ============================================================

    private static ItemStack iconOf(PacketOpenPioneerRates.Row row) {
        ResourceLocation id = ResourceLocation.tryParse(row.id());
        if (id == null) return ItemStack.EMPTY;
        if (row.kind() == PacketOpenPioneerRates.KIND_FLUID) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
            if (fluid == null) return ItemStack.EMPTY;
            Item bucket = fluid.getBucket();                 // 画它的桶 ✓（流体本身没有"物品图标"✓）
            return bucket == null ? ItemStack.EMPTY : new ItemStack(bucket);
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static Component displayName(PacketOpenPioneerRates.Row row) {
        ResourceLocation id = ResourceLocation.tryParse(row.id());
        if (id == null) return null;
        if (row.kind() == PacketOpenPioneerRates.KIND_FLUID) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(id);
            // ⚠ Fluid 本身没有 getDisplayName() ✗（我第一版写错了 ✓）⇒ 用 1 mB 的 FluidStack 取名字 ✓
            return fluid == null ? null : new net.minecraftforge.fluids.FluidStack(fluid, 1).getDisplayName();
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? null : item.getDescription();
    }

    private static Component unitLabel(int kind) {
        String key = switch (kind) {
            case PacketOpenPioneerRates.KIND_FLUID -> "screen.tinkersnewlife.pioneer_rates.unit.mb";
            case PacketOpenPioneerRates.KIND_ENERGY -> "screen.tinkersnewlife.pioneer_rates.unit.fe";
            default -> "screen.tinkersnewlife.pioneer_rates.unit.item";
        };
        return Component.translatable(key);
    }

    private void drawCentered(GuiGraphics graphics, Component component, int y, int color) {
        graphics.drawCenteredString(font, component, width / 2, y, color);
    }

    private void drawRight(GuiGraphics graphics, Component component, int right, int y, int color) {
        graphics.drawString(font, component, right - font.width(component), y, color);
    }

    private static String formatAmount(long value) {
        long abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";
        if (abs >= 1_000_000_000L) return sign + String.format(Locale.ROOT, "%.2fG", abs / 1_000_000_000.0D);
        if (abs >= 1_000_000L) return sign + String.format(Locale.ROOT, "%.2fM", abs / 1_000_000.0D);
        if (abs >= 10_000L) return sign + String.format(Locale.ROOT, "%.2fk", abs / 1_000.0D);
        return sign + Long.toString(abs);
    }

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
