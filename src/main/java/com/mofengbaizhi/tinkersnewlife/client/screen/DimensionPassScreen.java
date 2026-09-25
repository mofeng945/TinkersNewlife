package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.portal.PacketCreatePortal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * <b>维度通行证 选择界面</b>（§660）：在伟大白色空间里用通行证时弹出。
 *
 * <p>布局（一块居中面板）：
 * <ul>
 *   <li><b>目标维度</b>：下拉按钮，点开后在按钮下方铺一张覆盖式列表
 *       （超过 {@value #MAX_VISIBLE_ROWS} 个就滚轮翻页）；维度名优先取
 *       {@code dimension.<命名空间>.<路径>} 翻译键，没有就显示 id ✓；</li>
 *   <li><b>坐标</b>：<b>3 个独立输入格</b> x / y / z，默认填玩家当前所在坐标；</li>
 *   <li>底部「开 门」/「取消」；ESC 先收下拉、再关界面；回车＝确认 ✓。</li>
 * </ul>
 *
 * <p>这个界面<b>不消耗通行证</b> —— 消耗发生在服务端确认开门成功之后
 * （见 {@link PacketCreatePortal#handle}）⇒ 中途关掉界面不会白扔一张 ✓。
 */
public class DimensionPassScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 206;
    private static final int ROW_H = 12;
    private static final int MAX_VISIBLE_ROWS = 8;

    private final List<String> dimensions;
    private final BlockPos anchor;

    private int selected;
    private boolean dropdownOpen;
    private int scroll;

    private Button dropdownButton;
    private Button confirmButton;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;

    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listHeight;

    public DimensionPassScreen(List<String> dimensions, BlockPos anchor) {
        super(Component.translatable("screen.tinkersnewlife.dimension_pass.title"));
        this.dimensions = List.copyOf(dimensions);
        this.anchor = anchor;
        this.selected = this.dimensions.isEmpty() ? -1 : 0;
    }

    // ============================================================
    //  布局
    // ============================================================

    @Override
    protected void init() {
        panelLeft = (width - PANEL_W) / 2;
        panelTop = Math.max(16, (height - PANEL_H) / 2);
        listTop = panelTop + 74;
        int rows = Math.min(dimensions.size(), MAX_VISIBLE_ROWS);
        listHeight = rows * ROW_H + 2;

        dropdownButton = Button.builder(Component.empty(), b -> dropdownOpen = !dropdownOpen)
                .bounds(panelLeft + 10, panelTop + 54, PANEL_W - 20, 18)
                .build();
        addRenderableWidget(dropdownButton);
        refreshDropdownLabel();

        BlockPos start = Minecraft.getInstance().player == null
                ? anchor
                : Minecraft.getInstance().player.blockPosition();
        xBox = editBox(panelTop + 86, Integer.toString(start.getX()));
        yBox = editBox(panelTop + 106, Integer.toString(start.getY()));
        zBox = editBox(panelTop + 126, Integer.toString(start.getZ()));

        confirmButton = Button.builder(
                        Component.translatable("screen.tinkersnewlife.dimension_pass.confirm"), b -> confirm())
                .bounds(panelLeft + 10, panelTop + 166, 98, 20)
                .build();
        addRenderableWidget(confirmButton);
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.tinkersnewlife.dimension_pass.cancel"), b -> onClose())
                .bounds(panelLeft + PANEL_W - 108, panelTop + 166, 98, 20)
                .build());
    }

    private EditBox editBox(int y, String initial) {
        EditBox box = new EditBox(font, panelLeft + 34, y, PANEL_W - 60, 16, Component.empty());
        box.setMaxLength(12);
        box.setValue(initial);
        addRenderableWidget(box);
        return box;
    }

    private void refreshDropdownLabel() {
        if (dropdownButton == null) return;
        String name = selected >= 0 && selected < dimensions.size()
                ? displayName(dimensions.get(selected))
                : Component.translatable("screen.tinkersnewlife.dimension_pass.none").getString();
        dropdownButton.setMessage(Component.literal(name + " \u25BC"));
    }

    // ============================================================
    //  渲染
    // ============================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + PANEL_H, 0xE0101018);

        graphics.drawCenteredString(font, title, width / 2, panelTop + 10, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("screen.tinkersnewlife.dimension_pass.hint"),
                width / 2, panelTop + 25, 0x9A9A9A);
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.dimension_pass.dimension"),
                panelLeft + 10, panelTop + 42, 0xCCCCCC, false);
        graphics.drawString(font, "X", panelLeft + 16, panelTop + 90, 0xCCCCCC, false);
        graphics.drawString(font, "Y", panelLeft + 16, panelTop + 110, 0xCCCCCC, false);
        graphics.drawString(font, "Z", panelLeft + 16, panelTop + 130, 0xCCCCCC, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!coordsValid()) {
            graphics.drawString(font, Component.translatable("screen.tinkersnewlife.dimension_pass.invalid"),
                    panelLeft + 10, panelTop + 150, 0xFF5555, false);
        }
        // 下拉列表画在最上层，盖住下面的坐标格（原版下拉就是这个观感）
        if (dropdownOpen) renderDropdown(graphics, mouseX, mouseY);
    }

    private void renderDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelLeft + 10;
        int w = PANEL_W - 20;
        graphics.fill(x, listTop, x + w, listTop + listHeight, 0xF0080810);
        graphics.renderOutline(x, listTop, w, listHeight, 0xFF8080A0);

        int rows = Math.min(dimensions.size(), MAX_VISIBLE_ROWS);
        for (int i = 0; i < rows; i++) {
            int index = scroll + i;
            if (index >= dimensions.size()) break;
            int rowY = listTop + 1 + i * ROW_H;
            boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (index == selected) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW_H, 0xFF2E4A6E);
            } else if (hover) {
                graphics.fill(x + 1, rowY, x + w - 1, rowY + ROW_H, 0xFF3A3A55);
            }
            String name = displayName(dimensions.get(index));
            String shown = font.width(name) > w - 10
                    ? font.plainSubstrByWidth(name, w - 16) + "…"
                    : name;
            graphics.drawString(font, shown, x + 4, rowY + 2, 0xFFFFFF, false);
        }
        if (dimensions.size() > MAX_VISIBLE_ROWS) {
            String more = Math.min(scroll + rows, dimensions.size()) + "/" + dimensions.size();
            graphics.drawString(font, more, x + w - font.width(more) - 3,
                    listTop + listHeight + 2, 0x9A9A9A, false);
        }
    }

    /** 维度展示名：优先 {@code dimension.<命名空间>.<路径>}，缺了就直接用 id */
    private String displayName(String id) {
        String langKey = "dimension." + id.replace(':', '.');
        String name = Component.translatable(langKey).getString();
        return name.equals(langKey) ? id : name;
    }

    // ============================================================
    //  每帧状态
    // ============================================================

    @Override
    public void tick() {
        if (confirmButton != null) confirmButton.active = coordsValid();
    }

    private boolean coordsValid() {
        if (xBox == null || yBox == null || zBox == null) return false;
        if (selected < 0 || selected >= dimensions.size()) return false;
        try {
            parseCoord(xBox.getValue());
            parseCoord(yBox.getValue());
            parseCoord(zBox.getValue());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int parseCoord(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) throw new NumberFormatException("empty");
        return Integer.parseInt(text);
    }

    // ============================================================
    //  输入
    // ============================================================

    private void confirm() {
        if (!coordsValid()) return;
        TinkersNewlife.CHANNEL.sendToServer(new PacketCreatePortal(anchor, dimensions.get(selected),
                parseCoord(xBox.getValue()), parseCoord(yBox.getValue()), parseCoord(zBox.getValue())));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdownOpen && button == 0) {
            int x = panelLeft + 10;
            int w = PANEL_W - 20;
            if (mouseX >= x && mouseX <= x + w && mouseY >= listTop && mouseY < listTop + listHeight) {
                int rows = Math.min(dimensions.size(), MAX_VISIBLE_ROWS);
                int i = (int) ((mouseY - listTop - 1) / ROW_H);
                if (i >= 0 && i < rows) {
                    int index = scroll + i;
                    if (index < dimensions.size()) {
                        selected = index;
                        refreshDropdownLabel();
                    }
                }
                dropdownOpen = false;
                return true;
            }
            // 点在列表之外：收起。但点在"下拉按钮"上时不预先收起，
            // 否则按钮自己的 onPress 会再翻一次 ⇒ 关不掉 ✗
            boolean onButton = mouseX >= dropdownButton.getX()
                    && mouseX <= dropdownButton.getX() + dropdownButton.getWidth()
                    && mouseY >= dropdownButton.getY()
                    && mouseY <= dropdownButton.getY() + dropdownButton.getHeight();
            if (!onButton) dropdownOpen = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (dropdownOpen && dimensions.size() > MAX_VISIBLE_ROWS) {
            int max = dimensions.size() - MAX_VISIBLE_ROWS;
            int step = delta > 0 ? -1 : 1;
            scroll = Math.max(0, Math.min(max, scroll + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dropdownOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dropdownOpen = false;
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && confirmButton != null && confirmButton.active) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
