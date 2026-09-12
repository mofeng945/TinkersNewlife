package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiSelect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 无为转变 形态选择界面：
 * <p>
 * 网格布局（每行 <b>3</b> 个生物），每格上方以 3D 展示该生物模型、下方是名字；
 * 顶部有<b>搜索框</b>（支持本地化名 / 英文名 / 注册名，{@code @模组} 过滤命名空间）。
 * 背景保持原版那层灰色半透明压暗（{@code renderBackground} 不动）。
 * 点击某格 → 记录为当前形态并关闭 UI，随后按术式键 C 对自己施放顺转 / 按 F 开启「转变外放」；
 * 回车 = 直接选用搜索结果里的第一个。
 * 滚动/网格/点击逻辑由 {@link AbstractRowListScreen} 提供，3D 实体渲染由 {@link GuiEntityViewer} 提供。
 */
public class WuWeiScreen extends AbstractRowListScreen<String> {

    /** 每行格子数（需求：一行 3 个） */
    private static final int COLUMNS = 3;
    private static final int CELL_W = 96;
    private static final int CELL_H = 62;
    private static final int CELL_PITCH = 66;
    private static final int W = CELL_W * COLUMNS;
    private static final int LIST_TOP = 52;
    private static final int BOTTOM_PAD = 14;
    /** 搜索框 */
    private static final int SEARCH_Y = 33;
    private static final int SEARCH_H = 14;

    /** 全部形态（id → 不过滤），{@link #rows} 是过滤后的可见列表 */
    private final List<String> allForms;
    /** 当前选中的形态 id（空 = 未选择） */
    private final String selected;

    /** 条目下标 → 客户端展示实体（懒创建；过滤后下标会变，故每次过滤都清空重建） */
    private final Map<Integer, LivingEntity> dummies = new HashMap<>();
    /** id → 本地化名 */
    private final Map<String, String> localNames = new HashMap<>();
    /** id → 小写搜索串（本地化名 + 英文名 + 注册名 + 命名空间） */
    private final Map<String, String> searchKeys = new HashMap<>();

    private EditBox searchBox;
    private String filter = "";

    /** 本帧悬停的形态（用于延迟到裁剪区之外画 tooltip） */
    private String hovered;
    private boolean hoveredTruncated;

    public WuWeiScreen(List<String> forms, String selected) {
        super(Component.translatable("screen.tinkersnewlife.wu_wei.title"), new ArrayList<>(),
                W, CELL_PITCH, CELL_H, LIST_TOP, BOTTOM_PAD, COLUMNS);
        this.allForms = new ArrayList<>(forms == null ? List.of() : forms);
        this.selected = selected == null ? "" : selected;
    }

    // ============================================================
    //  初始化 / 过滤
    // ============================================================

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, startX, SEARCH_Y, listWidth, SEARCH_H,
                Component.translatable("screen.tinkersnewlife.wu_wei.search"));
        searchBox.setMaxLength(64);
        searchBox.setValue(filter);
        searchBox.setResponder(s -> {
            filter = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
            rebuild();
        });
        addRenderableWidget(searchBox);
        rebuild();
    }

    /** 按当前搜索词重建可见列表（空格分隔，全部命中才算） */
    private void rebuild() {
        rows.clear();
        String[] tokens = filter.isEmpty() ? new String[0] : filter.split("\\s+");
        for (String id : allForms) {
            boolean ok = true;
            for (String t : tokens) {
                if (!matches(id, t)) {
                    ok = false;
                    break;
                }
            }
            if (ok) rows.add(id);
        }
        dummies.clear();          // 下标已变，旧实体对不上号了
        scrollOffset = 0;
        refreshLayout();
    }

    private boolean matches(String id, String token) {
        if (token.isEmpty()) return true;
        // @模组：按命名空间过滤
        if (token.startsWith("@")) {
            String m = token.substring(1);
            if (m.isEmpty()) return true;
            EntityType<?> type = type(id);
            ResourceLocation key = type == null ? null : ForgeRegistries.ENTITY_TYPES.getKey(type);
            return key != null && key.getNamespace().toLowerCase(Locale.ROOT).contains(m);
        }
        return searchKey(id).contains(token);
    }

    /** 搜索串：本地化名 + 英文名 + 注册名（都小写），只在首次需要时拼一次 */
    private String searchKey(String id) {
        return searchKeys.computeIfAbsent(id, key -> {
            EntityType<?> type = type(key);
            String en = type == null ? "" : LangNameIndex.en(type.getDescriptionId());
            return (displayName(key) + "\n" + en + "\n" + key).toLowerCase(Locale.ROOT);
        });
    }

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hovered = null;
        hoveredTruncated = false;
        super.render(graphics, mouseX, mouseY, partialTick);
        // tooltip 在裁剪区之外画，避免被视口切掉
        if (hovered != null && hoveredTruncated) {
            graphics.renderTooltip(font, Component.literal(displayName(hovered)), mouseX, mouseY);
        }
    }

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.wu_wei.title"), 8, 0xFFFFFF);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.wu_wei.hint"), 20, 0xAAAAAA);
        // 右上角：命中数 / 总数
        String count = rows.size() + " / " + allForms.size();
        graphics.drawString(font, count, startX + listWidth - font.width(count), 8, 0x9A9A9A, false);
    }

    @Override
    protected void drawEmptyMessage(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean noData = allForms.isEmpty();
        Component msg = Component.translatable(noData
                ? "screen.tinkersnewlife.wu_wei.empty"
                : "screen.tinkersnewlife.wu_wei.no_result");
        graphics.drawString(font, msg, (width - font.width(msg)) / 2, listTop + 14,
                noData ? 0xFF5555 : 0xAAAAAA);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, String row, int index,
                           int x, int y, int w, int h, boolean hover,
                           double mouseX, double mouseY) {
        boolean isCurrent = !selected.isEmpty() && selected.equals(row);
        // 灰色半透明底：当前形态偏绿、悬停稍亮
        int bg = isCurrent ? 0xB02E4A2E : hover ? 0xB04A4A6A : 0x9033334A;
        graphics.fill(x + 2, y + 1, x + w - 2, y + h - 1, bg);
        if (isCurrent) {
            graphics.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFF7CFF7C);
        }

        // 3D 实体展示：居中、脚底贴着名字上方
        int cx = x + w / 2;
        int feetY = y + h - 15;
        LivingEntity dummy = dummyOf(index);
        if (dummy != null) {
            float scale = GuiEntityViewer.fitScale(dummy, 30.0F, 5.0F, 30.0F);
            GuiEntityViewer.render(graphics, dummy, cx, feetY, scale, mouseX, mouseY);
        }

        // 名字（居中，过长截断并用 tooltip 补全）
        String name = displayName(row);
        int maxW = w - 8;
        boolean truncated = font.width(name) > maxW;
        String shown = truncated ? font.plainSubstrByWidth(name, maxW - 6) + "…" : name;
        graphics.drawString(font, shown, cx - font.width(shown) / 2, y + h - 11,
                isCurrent ? 0x7CFF7C : 0xFFFFFF, false);
        if (hover && truncated) {
            hovered = row;
            hoveredTruncated = true;
        }
    }

    /** 展示实体（懒创建）；{@code index} 是 {@link #rows} 里的真实下标 */
    private LivingEntity dummyOf(int index) {
        if (index < 0 || index >= rows.size()) return null;
        if (dummies.containsKey(index)) return dummies.get(index);
        LivingEntity e = GuiEntityViewer.createDummy(type(rows.get(index)));
        dummies.put(index, e);   // 允许 null：记住"这个形态没有预览"，别每帧重试
        return e;
    }

    // ============================================================
    //  输入
    // ============================================================

    @Override
    protected void onRowClick(int index, String row) {
        TinkersNewlife.CHANNEL.sendToServer(new PacketWuWeiSelect(row));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            // 回车：直接选用搜索结果里的第一个
            if ((keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && !rows.isEmpty()) {
                onRowClick(0, rows.get(0));
                return true;
            }
            // ESC：先清空搜索词，再退出输入焦点（避免误关界面）
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                if (!searchBox.getValue().isEmpty()) {
                    searchBox.setValue("");
                } else {
                    searchBox.setFocused(false);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ============================================================
    //  名字 / 类型
    // ============================================================

    private static EntityType<?> type(String entityTypeId) {
        return entityTypeId == null ? null
                : ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(entityTypeId));
    }

    /** 形态显示名：EntityType 本地化键，找不到则显示注册名 */
    private String displayName(String entityTypeId) {
        return localNames.computeIfAbsent(entityTypeId == null ? "?" : entityTypeId, id -> {
            EntityType<?> t = type(id);
            return t == null ? id : Component.translatable(t.getDescriptionId()).getString();
        });
    }

    private void drawCentered(GuiGraphics graphics, Component c, int y, int color) {
        graphics.drawString(font, c, (width - font.width(c)) / 2, y, color);
    }
}
