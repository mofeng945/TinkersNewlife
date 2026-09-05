package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketConstructSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 构筑术式「拟造物品栏」：仿创造模式搜索的物品选择界面。
 * <p>
 * 候选 = 所有有合成配方的物品（客户端从配方管理器枚举）；顶部搜索框按
 * 显示名/注册名过滤；每行显示 图标 + 名称 + 预估咒力。点击即发送
 * {@link PacketConstructSelect} 并关闭，服务端权威校验/扣费后发放 60 秒临时物品。
 */
public class ConstructSelectScreen extends AbstractRowListScreen<String> {

    private static final int W = 280;
    private static final int ROW_H = 26;
    private static final int LIST_TOP = 58;
    private static final int BOTTOM_PAD = 18;

    /** 全部候选（有合成配方的物品 id，注册名排序）；配方管理器变化时自动重建 */
    private static List<String> allCandidates;
    private static Object cachedRecipeManager;

    private EditBox searchBox;
    private String filter = "";

    public ConstructSelectScreen() {
        super(Component.translatable("screen.tinkersnewlife.construct.title"),
                new ArrayList<>(collectCandidates()), W, ROW_H, ROW_H, LIST_TOP, BOTTOM_PAD);
    }

    /** 枚举所有有合成配方的物品（客户端配方管理器与服务器一致）；配方更新时自动重建缓存 */
    private static List<String> collectCandidates() {        var level = Minecraft.getInstance().level;
        Object rm = level == null ? null : level.getRecipeManager();
        if (allCandidates != null && cachedRecipeManager == rm) {
            return allCandidates;
        }
        allCandidates = new ArrayList<>();
        cachedRecipeManager = rm;
        Set<String> ids = new HashSet<>();
        if (level != null) {
            var access = level.registryAccess();
            try {
                for (var recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                    ItemStack out = recipe.getResultItem(access);
                    if (out == null || out.isEmpty()) continue;
                    Item item = out.getItem();
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                    if (key != null) {
                        ids.add(key.toString());
                    }
                }
            } catch (Throwable t) {
                // 个别特殊配方异常不阻塞整个界面
            }
        }
        allCandidates.addAll(ids);
        allCandidates.sort(String::compareTo);
        return allCandidates;
    }

    @Override
    protected void init() {
        super.init();
        searchBox = new EditBox(font, startX, 30, listWidth, 18,
                Component.translatable("screen.tinkersnewlife.construct.search"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(s -> {
            filter = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
            rebuildRows();
        });
        // ⭐ 用 addRenderableWidget 注册：EditBox 进入 Screen 的 children，
        // 点击后由 Screen 的焦点管理（setFocused）接管，字符/按键事件才会派发给它
        addRenderableWidget(searchBox);
    }

    /** 依据搜索词重建可见行（rows 为基类持有的可变列表） */
    private void rebuildRows() {
        rows.clear();
        for (String id : collectCandidates()) {
            if (filter.isEmpty() || matches(id)) {
                rows.add(id);
            }
        }
        // 重新计算滚动上限
        maxScroll = Math.max(0, rows.size() - visibleRows);
        scrollOffset = clampScroll(scrollOffset);
    }

    private boolean matches(String id) {
        String name = displayName(id).toLowerCase(Locale.ROOT);
        return name.contains(filter) || id.toLowerCase(Locale.ROOT).contains(filter);
    }

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.construct.title"), 12, 0xFFFFFF);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.construct.hint"), 52, 0x9A9A9A);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, String row, int index,
                           int x, int y, int w, int h, boolean hover,
                           double mouseX, double mouseY) {
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF4A4A6A : 0xFF33334A);
        ItemStack stack = itemOf(row);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 4, y + 4);
        }
        String name = displayName(row);
        graphics.drawString(font, name, x + 26, y + 8, 0xFFFFFF);
        // 右侧：预估咒力 + 拟造耗时（1 咒力 = 1 tick）
        String costText;
        int color;
        if (ClientCurseData.isInfinite()) {
            costText = Component.translatable("screen.tinkersnewlife.construct.cost_free").getString();
            color = 0xFFD4924B;
        } else {
            Item item = stack.getItem();
            int cost = stack.isEmpty() ? 0 : ConstructTechnique.computeCost(
                    ClientCurseData.getAffinity(), ClientCurseData.getOutput(), item);
            costText = Component.translatable("screen.tinkersnewlife.construct.cost_time",
                    cost, (cost + 19) / 20).getString();
            color = ClientCurseData.getCurse() >= cost ? 0xFFD4924B : 0xFFE05555;
        }
        graphics.drawString(font, costText, x + w - 4 - font.width(costText), y + 8, color);
    }

    @Override
    protected void onRowClick(int index, String row) {
        TinkersNewlife.CHANNEL.sendToServer(new PacketConstructSelect(row));
        onClose();
    }

    // ============ 工具 ============

    private static ItemStack itemOf(String id) {
        ResourceLocation loc = ResourceLocation.tryParse(id);
        if (loc == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(loc);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String displayName(String id) {
        ItemStack stack = itemOf(id);
        if (stack.isEmpty()) return id;
        return stack.getHoverName().getString();
    }

    private void drawCentered(GuiGraphics graphics, Component c, int y, int color) {
        graphics.drawString(font, c, (width - font.width(c)) / 2, y, color);
    }
}
