package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketConstructSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 构筑术式「拟造物品栏」——<b>仿 JEI 的网格界面</b>。
 *
 * <p>布局：一行 9 个，格子 18×18，<b>只画物品模型</b>；光标悬停格子时显示该物品的完整提示框
 * （在原本的 tooltip 末尾追加一行拟造费用/耗时，咒力不足标红、咒力无限显示免费）。
 * 滚轮翻页，左键点击即发送 {@link PacketConstructSelect} 并关闭（服务端权威校验与扣费）。
 *
 * <p>搜索（空格分隔多个关键词，<b>全部命中</b>才算匹配）：
 * <ul>
 *   <li>普通文本：物品<b>本地化名称</b>、<b>英文名称</b>、注册名（{@code modid:path}）任一包含即可；</li>
 *   <li>{@code @modid}：按模组过滤（也可直接写模组名片段）；</li>
 *   <li>{@code #forge:ingots}：按物品<b>标签</b>过滤，写片段（如 {@code #ingot}）也能命中。</li>
 * </ul>
 * 英文名来自所有命名空间的 {@code lang/en_us.json}（只加载一次并缓存），
 * 因此中文环境下也能用英文检索。
 */
public class ConstructSelectScreen extends Screen {

    // ---- 网格几何 ----
    private static final int COLS = 9;
    private static final int CELL = 18;
    private static final int GRID_W = COLS * CELL;
    private static final int PAD = 6;
    private static final int HEADER_H = 40;
    private static final int FOOTER_H = 16;

    /** 可见行数（可随窗口高度自适应，最少 3 行） */
    private int visibleRows = 8;

    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH;

    private EditBox searchBox;
    private String filter = "";
    private int scrollRow = 0;

    // ---- 候选缓存 ----
    /** 一条候选：物品 + 检索用的小写串（注册名 / 模组 / 本地化名 / 英文名） */
    private record Entry(Item item, String id, String mod, String name, String enName) {}

    private static List<Entry> index;
    private static Object cachedRecipeManager;
    private static int cachedRecipeCount = -1;

    /** descriptionId → 英文名（小写），来自所有 lang/en_us.json，只加载一次 */
    private static Map<String, String> EN_NAMES;

    private final List<Entry> shown = new ArrayList<>();

    public ConstructSelectScreen() {
        super(Component.translatable("screen.tinkersnewlife.construct.title"));
    }

    // ============================================================
    //  候选索引
    // ============================================================

    private static List<Entry> collectIndex() {
        var level = Minecraft.getInstance().level;
        Object rm = level == null ? null : level.getRecipeManager();
        int count = rm == null ? -1 : level.getRecipeManager().getRecipes().size();
        if (index != null && cachedRecipeManager == rm && cachedRecipeCount == count) return index;

        index = new ArrayList<>();
        cachedRecipeManager = rm;
        cachedRecipeCount = count;
        if (level == null) return index;

        ensureEnglishNames();
        var map = ConstructTechnique.constructibleMap(level.getRecipeManager(), level.registryAccess());
        List<Item> items = new ArrayList<>(map.keySet());
        items.sort((a, b) -> {
            ResourceLocation ka = ForgeRegistries.ITEMS.getKey(a);
            ResourceLocation kb = ForgeRegistries.ITEMS.getKey(b);
            return String.valueOf(ka).compareTo(String.valueOf(kb));
        });
        for (Item item : items) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null) continue;
            ItemStack probe = new ItemStack(item);
            String name = probe.getHoverName().getString().toLowerCase(Locale.ROOT);
            String en = EN_NAMES.getOrDefault(probe.getDescriptionId(), "");
            index.add(new Entry(item, key.toString().toLowerCase(Locale.ROOT), key.getNamespace().toLowerCase(Locale.ROOT),
                    name, en));
        }
        return index;
    }

    /** 读取所有命名空间的 en_us.json（只看物品相关的 key），供中文环境下用英文搜索 */
    private static void ensureEnglishNames() {
        if (EN_NAMES != null) return;
        EN_NAMES = new HashMap<>();
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            var found = rm.listResources("lang", p -> p.getPath().endsWith("en_us.json"));
            for (var res : found.values()) {
                try (Reader reader = res.openAsReader()) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    for (var e : obj.entrySet()) {
                        if (e.getValue().isJsonPrimitive()) {
                            EN_NAMES.put(e.getKey(), e.getValue().getAsString().toLowerCase(Locale.ROOT));
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            TinkersNewlife.LOGGER.debug("[构筑] 英文名缓存已建立：{} 条", EN_NAMES.size());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[构筑] 读取英文名失败，英文搜索将退化为按注册名匹配: {}", t.toString());
        }
    }

    // ============================================================
    //  初始化 / 布局
    // ============================================================

    @Override
    protected void init() {
        super.init();
        visibleRows = Math.max(3, Math.min(12, (this.height - HEADER_H - FOOTER_H - PAD * 3) / CELL));
        panelW = GRID_W + PAD * 2;
        panelH = HEADER_H + visibleRows * CELL + FOOTER_H;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        gridX = panelX + PAD;
        gridY = panelY + HEADER_H;
        gridW = GRID_W;
        gridH = visibleRows * CELL;

        searchBox = new EditBox(font, panelX + PAD, panelY + 22, panelW - PAD * 2, 14,
                Component.translatable("screen.tinkersnewlife.construct.search"));
        searchBox.setMaxLength(96);
        searchBox.setValue(filter);
        searchBox.setResponder(s -> {
            filter = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
            scrollRow = 0;
            rebuild();
        });
        addRenderableWidget(searchBox);
        rebuild();
    }

    /** 按当前搜索词重建可见列表（空格分隔，全部命中） */
    private void rebuild() {
        shown.clear();
        String[] tokens = filter.isEmpty() ? new String[0] : filter.split("\\s+");
        for (Entry e : collectIndex()) {
            boolean ok = true;
            for (String t : tokens) {
                if (!matches(e, t)) {
                    ok = false;
                    break;
                }
            }
            if (ok) shown.add(e);
        }
        int maxRow = Math.max(0, totalRows() - visibleRows);
        scrollRow = Math.max(0, Math.min(scrollRow, maxRow));
    }

    private boolean matches(Entry e, String token) {
        if (token.isEmpty()) return true;
        // @模组
        if (token.startsWith("@")) {
            String m = token.substring(1);
            return m.isEmpty() || e.mod.contains(m);
        }
        // #标签（支持写片段）
        if (token.startsWith("#")) {
            String tagText = token.substring(1);
            if (tagText.isEmpty()) return true;
            ResourceLocation tagId = ResourceLocation.tryParse(tagText);
            if (tagId != null) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
                if (e.item.builtInRegistryHolder().is(tag)) return true;
            }
            return e.item.builtInRegistryHolder().tags()
                    .anyMatch(t -> t.location().toString().toLowerCase(Locale.ROOT).contains(tagText));
        }
        // 普通文本：本地化名 / 英文名 / 注册名 / 模组名
        return e.name.contains(token) || e.enName.contains(token)
                || e.id.contains(token) || e.mod.contains(token);
    }

    private int totalRows() {
        return (shown.size() + COLS - 1) / COLS;
    }

    // ============================================================
    //  渲染
    // ============================================================

    /**
     * 不画模糊背景，只压一层半透明黑；<b>面板本身也在这里画</b>——
     * 原版 {@code Screen.render} 会先调本方法再画控件，所以搜索框自然落在面板之上，且不会重复压暗。
     */
    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);

        // 面板
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF101014);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF23232B);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0x40FFFFFF);

        // 标题 + 结果计数
        graphics.drawString(font, this.title, panelX + PAD, panelY + 7, 0xFFE0E0E0, false);
        String count = shown.size() + " / " + collectIndex().size();
        graphics.drawString(font, count, panelX + panelW - PAD - font.width(count), panelY + 7, 0x9A9A9A, false);

        // 网格区域底
        graphics.fill(gridX - 1, gridY - 1, gridX + gridW + 1, gridY + gridH + 1, 0xFF15151A);

        // 滚动条
        int rows = totalRows();
        if (rows > visibleRows) {
            int trackX = gridX + gridW + 2;
            graphics.fill(trackX, gridY, trackX + 2, gridY + gridH, 0xFF15151A);
            int thumbH = Math.max(8, gridH * visibleRows / rows);
            int thumbY = gridY + (gridH - thumbH) * scrollRow / Math.max(1, rows - visibleRows);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF8A8AA0);
        }

        // 底部提示
        Component hint = Component.translatable("screen.tinkersnewlife.construct.hint_jei");
        graphics.drawString(font, hint, panelX + PAD, panelY + panelH - 12, 0x8A8A9A, false);

        if (shown.isEmpty()) {
            Component empty = Component.translatable("screen.tinkersnewlife.construct.empty");
            graphics.drawString(font, empty, gridX + (gridW - font.width(empty)) / 2, gridY + gridH / 2 - 4,
                    0x9A9A9A, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 背景 + 面板 + 控件（搜索框）由原版流程绘制
        super.render(graphics, mouseX, mouseY, partialTick);

        // 物品图标（只画可见行）+ 悬停高亮
        int firstIndex = scrollRow * COLS;
        int lastIndex = Math.min(shown.size(), firstIndex + visibleRows * COLS);
        int hoveredIndex = -1;
        for (int i = firstIndex; i < lastIndex; i++) {
            int slot = i - firstIndex;
            int cx = gridX + (slot % COLS) * CELL;
            int cy = gridY + (slot / COLS) * CELL;
            boolean hover = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            if (hover) {
                hoveredIndex = i;
                graphics.fill(cx, cy, cx + CELL, cy + CELL, 0x60FFFFFF);
            }
            graphics.renderItem(new ItemStack(shown.get(i).item()), cx + 1, cy + 1);
        }

        // 悬停物品的完整提示框（原版 tooltip + 一行拟造费用）
        if (hoveredIndex >= 0 && hoveredIndex < shown.size()) {
            Item item = shown.get(hoveredIndex).item();
            ItemStack stack = new ItemStack(item);
            List<Component> lines = new ArrayList<>(
                    stack.getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL));
            lines.add(costLine(item));
            graphics.renderTooltip(font, lines, stack.getTooltipImage(), mouseX, mouseY);
        }
    }

    /** 费用行：咒力无限 → 免费；否则 费用 + 预计秒数（1 咒力 = 1 tick） */
    private Component costLine(Item item) {
        if (ClientCurseData.isInfinite()) {
            return Component.translatable("screen.tinkersnewlife.construct.cost_free_line");
        }
        var level = Minecraft.getInstance().level;
        double term = level == null ? 0.0
                : ConstructTechnique.ingredientTerm(level.getRecipeManager(), level.registryAccess(), item);
        int cost = ConstructTechnique.computeCost(ClientCurseData.getAffinity(), ClientCurseData.getOutput(), item, term);
        boolean enough = ClientCurseData.getCurse() >= cost;
        Component text = Component.translatable("screen.tinkersnewlife.construct.cost_time", cost, (cost + 19) / 20);
        return Component.translatable("screen.tinkersnewlife.construct.cost_line",
                text.copy().withStyle(enough ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RED));
    }

    // ============================================================
    //  交互
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int i = hoveredSlot(mouseX, mouseY);
            if (i >= 0 && i < shown.size()) {
                onPick(shown.get(i));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 鼠标所在的候选下标（不在格子上返回 -1） */
    private int hoveredSlot(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseX >= gridX + gridW || mouseY < gridY || mouseY >= gridY + gridH) return -1;
        int col = (int) ((mouseX - gridX) / CELL);
        int row = (int) ((mouseY - gridY) / CELL);
        int idx = (scrollRow + row) * COLS + col;
        if (col < 0 || col >= COLS || idx < 0 || idx >= shown.size()) return -1;
        // 该行不足 9 个时，后面的空位不算
        if (idx >= (scrollRow + row + 1) * COLS) return -1;
        return idx;
    }

    private void onPick(Entry entry) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(entry.item());
        if (key == null) return;
        TinkersNewlife.CHANNEL.sendToServer(new PacketConstructSelect(key.toString()));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxRow = Math.max(0, totalRows() - visibleRows);
        if (maxRow > 0) {
            scrollRow = Math.max(0, Math.min(maxRow, scrollRow - (int) Math.signum(delta) * (hasShiftDown() ? 3 : 1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
