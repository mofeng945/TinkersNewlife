package com.mofengbaizhi.tinkersnewlife.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用「滚动行选择」GUI 基类：
 * <p>
 * 无为转变 / 咒灵操术等"一行一个可点选项、超出视口滚动"的术式选择界面共用。
 * 基类负责：居中布局、视口裁剪、滚轮（平滑累积）、右侧滚动条（轨道+滑块+拖拽）、
 * 行 hover 判定与点击分发；每行画什么（文字/3D 实体）由子类 {@link #drawRow} 决定。
 * <p>
 * {@code columns > 1} 时同一"视觉行"里并排 {@code columns} 个格子（网格布局，
 * 无为转变就是 3 列）。子类无需改 {@link #drawRow} 签名：基类传进来的
 * {@code x/w} 已经是<b>格子</b>的几何，{@code index} 始终是<b>真实条目下标</b>。
 */
public abstract class AbstractRowListScreen<T> extends Screen {

    // ===== 由子类传入的几何参数 =====
    protected final List<T> rows;
    protected final int listWidth;   // 列表宽度
    protected final int rowPitch;    // 行距（相邻两行 top 间距，含间距）
    protected final int rowFill;     // 行内容/点击高度（≤ rowPitch）
    protected final int listTop;     // 列表视口顶部
    protected final int bottomPad;   // 底部留白
    protected final int columns;     // 每行的格子数（1 = 传统单列）

    protected int startX;
    protected int listBottom;
    protected int visibleRows;       // 可视行数（网格时 = 可视"视觉行"数）
    protected int maxScroll;
    protected int scrollOffset = 0;
    protected int cellWidth;         // 单个格子宽度（= listWidth / columns）
    protected int lineCount;         // 总视觉行数 = ceil(条目数 / columns)

    private double scrollAccum = 0;  // 平滑滚轮累积
    private boolean dragging = false; // 拖动滚动条滑块中

    protected AbstractRowListScreen(Component title, List<T> rows, int listWidth, int rowPitch,
                                    int rowFill, int listTop, int bottomPad) {
        this(title, rows, listWidth, rowPitch, rowFill, listTop, bottomPad, 1);
    }

    protected AbstractRowListScreen(Component title, List<T> rows, int listWidth, int rowPitch,
                                    int rowFill, int listTop, int bottomPad, int columns) {
        super(title);
        this.rows = rows == null ? new ArrayList<>() : rows;
        this.listWidth = listWidth;
        this.rowPitch = rowPitch;
        this.rowFill = rowFill;
        this.listTop = listTop;
        this.bottomPad = bottomPad;
        this.columns = Math.max(1, columns);
        this.cellWidth = Math.max(1, listWidth / this.columns);
    }

    // ============================================================
    //  布局 / 滚动几何
    // ============================================================

    @Override
    protected void init() {
        startX = (width - listWidth) / 2;
        listBottom = height - bottomPad;
        refreshLayout();
    }

    /**
     * 重算网格/视口/滚动上限（窗口尺寸变化、或子类过滤后 {@link #rows} 变了都要调）。
     * 过滤搜索框改内容后记得调它，否则滚动条与命中判定会对不上。
     */
    protected void refreshLayout() {
        cellWidth = Math.max(1, listWidth / columns);
        lineCount = (rows.size() + columns - 1) / columns;
        int viewH = Math.max(0, listBottom - listTop);
        visibleRows = Math.max(1, viewH / rowPitch);
        maxScroll = Math.max(0, lineCount - visibleRows);
        scrollOffset = clampScroll(scrollOffset);
    }

    /** 视觉行在屏幕上的 y（相对滚动偏移，内容随 scrollOffset 增大而上移） */
    protected int rowY(int row) {
        return listTop + (row - scrollOffset) * rowPitch;
    }

    protected int clampScroll(int v) {
        return Math.max(0, Math.min(v, maxScroll));
    }

    protected int scrollbarX() {
        return startX + listWidth + 4;
    }

    private int thumbHeight() {
        int trackH = listBottom - listTop;
        return Math.max(1, Math.min(trackH, Math.max(12, trackH * visibleRows / Math.max(1, lineCount))));
    }

    private int thumbTravel() {
        return (listBottom - listTop) - thumbHeight();
    }

    /** 点击/拖动的鼠标 y → 滚动行数（滑块中心对齐鼠标） */
    private int clickToOffset(double mouseY) {
        int travel = thumbTravel();
        if (travel <= 0) return 0;
        double frac = (mouseY - listTop - thumbHeight() / 2.0) / travel;
        return clampScroll((int) Math.round(frac * maxScroll));
    }

    /** 鼠标是否在列表可视区内 */
    protected boolean inList(double mouseX, double mouseY) {
        return mouseX >= startX && mouseX <= startX + listWidth
                && mouseY >= listTop && mouseY <= listBottom;
    }

    /** 命中第几个条目（按格子行列 + 行内容高度 rowFill 判定），未命中返回 -1 */
    protected int rowAt(double mouseX, double mouseY) {
        if (mouseX < startX || mouseX > startX + listWidth) return -1;
        int line = (int) ((mouseY - listTop) / rowPitch) + scrollOffset;
        if (line < 0 || line >= lineCount) return -1;
        int y = rowY(line);
        if (mouseY < y || mouseY > y + rowFill) return -1;
        int col = Math.min(columns - 1, Math.max(0, (int) ((mouseX - startX) / cellWidth)));
        int idx = line * columns + col;
        return idx < rows.size() ? idx : -1;
    }

    // ============================================================
    //  子类钩子
    // ============================================================

    /** 列表上方内容（标题/提示等），在滚动行之前绘制 */
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {}

    /** 每行内容：背景、文字、3D 实体等（y 已按滚动对齐） */
    protected abstract void drawRow(GuiGraphics graphics, T row, int index,
                                    int x, int y, int w, int h, boolean hover,
                                    double mouseX, double mouseY);

    /** 列表为空时绘制提示（默认不画） */
    protected void drawEmptyMessage(GuiGraphics graphics, int mouseX, int mouseY) {}

    /** 列表下方内容（脚注等） */
    protected void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {}

    /** 点击某一行（index 为真实行号） */
    protected abstract void onRowClick(int index, T row);

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        drawHeader(graphics, mouseX, mouseY);
        if (rows.isEmpty()) {
            drawEmptyMessage(graphics, mouseX, mouseY);
            drawFooter(graphics, mouseX, mouseY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int left = startX;
        int right = startX + listWidth;
        // 视口裁剪：只绘制可视区域内的行
        graphics.enableScissor(left, listTop, right, listBottom + 1);
        int last = Math.min(lineCount, scrollOffset + visibleRows);
        for (int line = scrollOffset; line < last; line++) {
            int y = rowY(line);
            if (y + rowFill < listTop || y > listBottom) continue;
            for (int col = 0; col < columns; col++) {
                int idx = line * columns + col;
                if (idx >= rows.size()) break;
                int x = left + col * cellWidth;
                boolean hover = mouseX >= x && mouseX <= x + cellWidth
                        && mouseY >= y && mouseY <= y + rowFill;
                drawRow(graphics, rows.get(idx), idx, x, y, cellWidth, rowFill, hover, mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            drawScrollbar(graphics, mouseX, mouseY);
        }
        drawFooter(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 右侧滚动条：轨道 + 滑块（高度按可视比例） */
    private void drawScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int sbX = scrollbarX();
        int trackH = listBottom - listTop;
        graphics.fill(sbX, listTop, sbX + 4, listTop + trackH, 0x66333333); // 轨道
        int thumbH = thumbHeight();
        int travel = trackH - thumbH;
        int thumbY = Math.max(listTop, Math.min(listTop + travel,
                listTop + (int) Math.round(travel * (double) scrollOffset / maxScroll)));
        boolean hover = mouseX >= sbX && mouseX <= sbX + 4
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH,
                hover || dragging ? 0xFFCCCCCC : 0xFF8A8A8A);
    }

    // ============================================================
    //  鼠标输入
    // ============================================================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (rows.isEmpty() || maxScroll <= 0) {
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
        if (button == 0 && maxScroll > 0
                && mouseX >= scrollbarX() && mouseX <= scrollbarX() + 4
                && mouseY >= listTop && mouseY <= listBottom) {
            scrollOffset = clickToOffset(mouseY);
            dragging = true;
            return true;
        }
        if (button == 0) {
            int r = rowAt(mouseX, mouseY);
            if (r >= 0) {
                onRowClick(r, rows.get(r));
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
