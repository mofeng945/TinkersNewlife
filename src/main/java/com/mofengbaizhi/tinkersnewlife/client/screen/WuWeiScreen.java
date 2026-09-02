package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiSelect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 无为转变 形态选择界面：
 * 列出玩家击杀记录中的生物（EntityType id）。点击一项 → 记录为当前形态并关闭 UI，
 * 随后按术式键 C 对自己施放顺转 / 按 F 开启「转变外放」用下一次攻击变形目标。
 * 形态数量超出可视区域时右侧出现滚动条：滚轮上下滚动 / 点击轨道跳转 / 拖动滑块。
 */
public class WuWeiScreen extends Screen {

    private static final int W = 180;      // 列表宽度
    private static final int H = 22;       // 每行高度
    private static final int GAP = 4;      // 行间距
    private static final int ROW_H = H + GAP;
    private static final int SB_W = 4;     // 滚动条宽度
    private static final int SB_GAP = 4;   // 滚动条与列表间距
    private static final int LIST_TOP = 48;  // 列表视口顶部
    private static final int BOTTOM_PAD = 16;

    private final List<String> forms;
    private int startX;
    private int listBottom;
    private int visibleRows;     // 视口内可视行数
    private int maxScroll;       // 最大滚动行数（按行滚动）
    private int scrollOffset = 0;
    private double scrollAccum = 0;  // 平滑滚轮累积
    private boolean dragging = false; // 拖动滚动条滑块中

    public WuWeiScreen(List<String> forms) {
        super(Component.translatable("screen.tinkersnewlife.wu_wei.title"));
        this.forms = forms == null ? new ArrayList<>() : forms;
    }

    @Override
    protected void init() {
        startX = (width - W) / 2;
        listBottom = height - BOTTOM_PAD;
        int viewH = Math.max(0, listBottom - LIST_TOP);
        visibleRows = Math.max(1, viewH / ROW_H);
        maxScroll = Math.max(0, forms.size() - visibleRows);
        scrollOffset = clampScroll(scrollOffset);
    }

    private int rowY(int row) {
        return LIST_TOP + row * ROW_H;
    }

    private int clampScroll(int v) {
        return Math.max(0, Math.min(v, maxScroll));
    }

    private int scrollbarX() {
        return startX + W + SB_GAP;
    }

    private int thumbHeight() {
        int trackH = listBottom - LIST_TOP;
        return Math.max(1, Math.min(trackH, Math.max(12, trackH * visibleRows / forms.size())));
    }

    private int thumbTravel() {
        return (listBottom - LIST_TOP) - thumbHeight();
    }

    /** 点击/拖动的鼠标 y → 滚动行数（滑块中心对齐鼠标） */
    private int clickToOffset(double mouseY) {
        int travel = thumbTravel();
        if (travel <= 0) return 0;
        double frac = (mouseY - LIST_TOP - thumbHeight() / 2.0) / travel;
        return clampScroll((int) Math.round(frac * maxScroll));
    }

    private void drawCentered(GuiGraphics graphics, String key, int y, int color) {
        Component c = Component.translatable(key);
        graphics.drawString(font, c, (width - font.width(c)) / 2, y, color);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        drawCentered(graphics, "screen.tinkersnewlife.wu_wei.title", 12, 0xFFFFFF);
        drawCentered(graphics, "screen.tinkersnewlife.wu_wei.hint", 26, 0xAAAAAA);
        if (forms.isEmpty()) {
            String noData = Component.translatable("screen.tinkersnewlife.wu_wei.empty").getString();
            graphics.drawString(font, noData, (width - font.width(noData)) / 2, 60, 0xFF5555);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int left = startX;
        int right = startX + W;
        // 视口裁剪：只绘制可视区域内的行
        graphics.enableScissor(left, LIST_TOP, right, listBottom + 1);
        int last = Math.min(forms.size(), scrollOffset + visibleRows);
        for (int row = scrollOffset; row < last; row++) {
            int y = rowY(row);
            boolean hover = mouseX >= left && mouseX <= right && mouseY >= y && mouseY <= y + H;
            graphics.fill(left, y, right, y + H, hover ? 0xFF4A4A6A : 0xFF33334A);
            graphics.drawString(font, displayName(forms.get(row)), left + 6, y + 6, 0xFFFFFF);
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            drawScrollbar(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 右侧滚动条：轨道 + 滑块（高度按可视比例） */
    private void drawScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int sbX = scrollbarX();
        int trackTop = LIST_TOP;
        int trackH = listBottom - trackTop;
        graphics.fill(sbX, trackTop, sbX + SB_W, trackTop + trackH, 0x66333333); // 轨道
        int thumbH = thumbHeight();
        int travel = trackH - thumbH;
        int thumbY = Math.max(trackTop, Math.min(trackTop + travel,
                trackTop + (int) Math.round(travel * (double) scrollOffset / maxScroll)));
        boolean hover = mouseX >= sbX && mouseX <= sbX + SB_W && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        graphics.fill(sbX, thumbY, sbX + SB_W, thumbY + thumbH, hover || dragging ? 0xFFCCCCCC : 0xFF8A8A8A);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (forms.isEmpty() || maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        scrollAccum += delta;
        int steps = (int) scrollAccum;
        if (steps != 0) {
            scrollAccum -= steps;
            scrollOffset = clampScroll(scrollOffset - steps);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条区域：点击即把滑块中心移到鼠标处并开始拖动
        if (maxScroll > 0 && button == 0
                && mouseX >= scrollbarX() && mouseX <= scrollbarX() + SB_W
                && mouseY >= LIST_TOP && mouseY <= listBottom) {
            scrollOffset = clickToOffset(mouseY);
            dragging = true;
            return true;
        }
        int left = startX;
        int right = startX + W;
        int last = Math.min(forms.size(), scrollOffset + visibleRows);
        for (int row = scrollOffset; row < last; row++) {
            int y = rowY(row);
            if (mouseX >= left && mouseX <= right && mouseY >= y && mouseY <= y + H) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketWuWeiSelect(forms.get(row)));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            scrollOffset = clickToOffset(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 形态显示名：EntityType 本地化键，找不到则显示注册名 */
    private static String displayName(String entityTypeId) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(entityTypeId));
        if (type == null) return entityTypeId;
        return Component.translatable(type.getDescriptionId()).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
