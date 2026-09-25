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

import javax.annotation.Nullable;
import java.util.List;

/**
 * <b>维度通行证 选择界面</b>（§660／§661）：两种用法都弹这个界面，只是模式不同。
 *
 * <h2>两种模式</h2>
 * <table border="1">
 *   <tr><th></th><th>自由模式（在伟大白色空间里用）</th><th>锁定模式（在其它维度用）</th></tr>
 *   <tr><td>维度</td><td><b>下拉</b>选（只列服务端算好的"去过且不在黑名单"的维度）</td>
 *       <td><b>锁死</b>为伟大白色空间，不能选</td></tr>
 *   <tr><td>坐标</td><td>x、y、z <b>三个格子都能填</b></td>
 *       <td>只能填 x／z；<b>y 不能填</b>（固定＝白色空间地面表层）</td></tr>
 * </table>
 *
 * <p>x／z 的默认值：自由模式＝玩家当前坐标；锁定模式＝<b>右键点到的那个方块</b>的 x／z
 * —— 也就是用户口径里的「相对落点」，玩家不改就是正对门的那一列 ✓。
 *
 * <p>这个界面<b>不消耗通行证</b>：消耗发生在服务端确认开门成功之后
 * （见 {@link PacketCreatePortal#handle}）⇒ 中途关掉界面不会白扔一张 ✓。
 * 同理，锁定模式的"只能填 x／z"只是<b>界面约束</b>，服务端那边会再把维度和 y 强制覆盖一遍 ✓
 * ⇒ 客户端改包也没法在别的维度把 y 填到天上。
 */
public class DimensionPassScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 206;
    private static final int ROW_H = 12;
    private static final int MAX_VISIBLE_ROWS = 8;

    private static final int ROW_DIMENSION_Y = 54;
    private static final int ROW_X_Y = 86;
    private static final int ROW_Y_Y = 106;
    private static final int ROW_Z_Y = 126;
    private static final int ROW_INVALID_Y = 150;
    private static final int ROW_BUTTON_Y = 166;

    private final List<String> dimensions;
    private final BlockPos anchor;
    /** 锁定模式的维度 id；自由模式为 null ⇒ 用 {@link #locked} 判断模式 */
    @Nullable
    private final String lockedDimensionId;
    private final int lockedY;
    private final boolean locked;

    private int selected;
    private boolean dropdownOpen;
    private int scroll;

    @Nullable
    private Button dropdownButton;
    @Nullable
    private Button confirmButton;
    @Nullable
    private EditBox xBox;
    /** 锁定模式下**没有**这个输入格（y 不可填） */
    @Nullable
    private EditBox yBox;
    @Nullable
    private EditBox zBox;

    private int panelLeft;
    private int panelTop;
    private int listTop;
    private int listHeight;

    public DimensionPassScreen(List<String> dimensions, BlockPos anchor,
                              @Nullable String lockedDimensionId, int lockedY) {
        super(Component.translatable(lockedDimensionId == null
                ? "screen.tinkersnewlife.dimension_pass.title"
                : "screen.tinkersnewlife.dimension_pass.title_locked"));
        this.dimensions = List.copyOf(dimensions);
        this.anchor = anchor;
        this.lockedDimensionId = lockedDimensionId;
        this.lockedY = lockedY;
        this.locked = lockedDimensionId != null;
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

        if (!locked) {
            dropdownButton = Button.builder(Component.empty(), b -> dropdownOpen = !dropdownOpen)
                    .bounds(panelLeft + 10, panelTop + ROW_DIMENSION_Y, PANEL_W - 20, 18)
                    .build();
            addRenderableWidget(dropdownButton);
            refreshDropdownLabel();
        }

        // 锁定模式的 x/z 默认取"右键点到的那个方块"（相对落点）；自由模式取玩家当前坐标
        BlockPos base = locked
                ? anchor
                : (Minecraft.getInstance().player == null
                        ? anchor : Minecraft.getInstance().player.blockPosition());
        xBox = editBox(panelTop + ROW_X_Y, Integer.toString(base.getX()));
        zBox = editBox(panelTop + ROW_Z_Y, Integer.toString(base.getZ()));
        if (!locked) {
            yBox = editBox(panelTop + ROW_Y_Y, Integer.toString(base.getY()));
        }

        confirmButton = Button.builder(
                        Component.translatable("screen.tinkersnewlife.dimension_pass.confirm"), b -> confirm())
                .bounds(panelLeft + 10, panelTop + ROW_BUTTON_Y, 98, 20)
                .build();
        addRenderableWidget(confirmButton);
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.tinkersnewlife.dimension_pass.cancel"), b -> onClose())
                .bounds(panelLeft + PANEL_W - 108, panelTop + ROW_BUTTON_Y, 98, 20)
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
                Component.translatable(locked
                        ? "screen.tinkersnewlife.dimension_pass.hint_locked"
                        : "screen.tinkersnewlife.dimension_pass.hint"),
                width / 2, panelTop + 25, 0x9A9A9A);

        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.dimension_pass.dimension"),
                panelLeft + 10, panelTop + 42, 0xCCCCCC, false);
        if (locked) {
            // 维度锁死：画一块"只读"的框，把目的地写在中间
            graphics.fill(panelLeft + 10, panelTop + ROW_DIMENSION_Y,
                    panelLeft + PANEL_W - 10, panelTop + ROW_DIMENSION_Y + 18, 0x80202030);
            graphics.renderOutline(panelLeft + 10, panelTop + ROW_DIMENSION_Y,
                    PANEL_W - 20, 18, 0xFF8080A0);
            String name = displayName(lockedDimensionId);
            graphics.drawCenteredString(font, Component.literal(name),
                    width / 2, panelTop + ROW_DIMENSION_Y + 5, 0xBFE8FF);
        } else {
            // 右上角提示：列表里只剩"去过的维度"
            String unlocked = Component.translatable(
                    "screen.tinkersnewlife.dimension_pass.unlocked", dimensions.size()).getString();
            graphics.drawString(font, unlocked,
                    panelLeft + PANEL_W - 10 - font.width(unlocked), panelTop + 42, 0x777777, false);
        }

        graphics.drawString(font, "X", panelLeft + 16, panelTop + ROW_X_Y + 4, 0xCCCCCC, false);
        graphics.drawString(font, "Y", panelLeft + 16, panelTop + ROW_Y_Y + 4, 0xCCCCCC, false);
        graphics.drawString(font, "Z", panelLeft + 16, panelTop + ROW_Z_Y + 4, 0xCCCCCC, false);

        if (locked) {
            // y 不可填：把固定值画在 y 那一行该在的位置上
            graphics.fill(panelLeft + 34, panelTop + ROW_Y_Y, panelLeft + PANEL_W - 26,
                    panelTop + ROW_Y_Y + 16, 0x60181828);
            graphics.drawString(font,
                    Component.translatable("screen.tinkersnewlife.dimension_pass.fixed_y", lockedY),
                    panelLeft + 38, panelTop + ROW_Y_Y + 4, 0xAAAAAA, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!coordsValid()) {
            graphics.drawString(font, Component.translatable(locked
                            ? "screen.tinkersnewlife.dimension_pass.invalid_xz"
                            : "screen.tinkersnewlife.dimension_pass.invalid"),
                    panelLeft + 10, panelTop + ROW_INVALID_Y, 0xFF5555, false);
        }
        // 下拉列表画在最上层，盖住下面的坐标格（原版下拉就是这个观感）
        if (!locked && dropdownOpen) renderDropdown(graphics, mouseX, mouseY);
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
        if (id == null) return "?";
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
        if (xBox == null || zBox == null) return false;
        if (!locked && (selected < 0 || selected >= dimensions.size())) return false;
        try {
            parseCoord(xBox.getValue());
            parseCoord(zBox.getValue());
            if (!locked && yBox != null) parseCoord(yBox.getValue());
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
        if (!coordsValid() || xBox == null || zBox == null) return;
        String dimensionId = locked ? lockedDimensionId : dimensions.get(selected);
        if (dimensionId == null) return;
        // 锁定模式的 y 只是"照实报一个数"，服务端会强制覆盖成地面表层 ✓
        int y = locked || yBox == null ? lockedY : parseCoord(yBox.getValue());
        TinkersNewlife.CHANNEL.sendToServer(new PacketCreatePortal(anchor, dimensionId,
                parseCoord(xBox.getValue()), y, parseCoord(zBox.getValue())));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!locked && dropdownOpen && button == 0 && dropdownButton != null) {
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
        if (!locked && dropdownOpen && dimensions.size() > MAX_VISIBLE_ROWS) {
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
