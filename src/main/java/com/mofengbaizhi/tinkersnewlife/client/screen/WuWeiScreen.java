package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiSelect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 无为转变 形态选择界面：
 * 列出玩家击杀记录中的生物（EntityType id），每行左侧以 3D 展示该生物模型；
 * 点击一项 → 记录为当前形态并关闭 UI，随后按术式键 C 对自己施放顺转 / 按 F 开启「转变外放」。
 * 当前已选中的形态高亮显示。滚动/布局/点击逻辑由 {@link AbstractRowListScreen} 基类提供，
 * 3D 实体渲染由 {@link GuiEntityViewer} 提供。
 */
public class WuWeiScreen extends AbstractRowListScreen<String> {

    private static final int W = 260;
    private static final int ROW_H = 44;
    private static final int LIST_TOP = 48;
    private static final int BOTTOM_PAD = 16;

    /** 当前选中的形态 id（空 = 未选择） */
    private final String selected;
    /** 行号 → 客户端展示实体（懒创建，仅可视行会创建） */
    private final Map<Integer, LivingEntity> dummies = new HashMap<>();

    public WuWeiScreen(List<String> forms, String selected) {
        super(Component.translatable("screen.tinkersnewlife.wu_wei.title"),
                forms, W, ROW_H, ROW_H, LIST_TOP, BOTTOM_PAD);
        this.selected = selected == null ? "" : selected;
    }

    private LivingEntity dummyOf(int index) {
        return dummies.computeIfAbsent(index, i -> {
            String id = rows.get(i);
            EntityType<?> type = id == null ? null
                    : ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(id));
            return GuiEntityViewer.createDummy(type);
        });
    }

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.wu_wei.title"), 12, 0xFFFFFF);
        drawCentered(graphics, Component.translatable("screen.tinkersnewlife.wu_wei.hint"), 26, 0xAAAAAA);
    }

    @Override
    protected void drawEmptyMessage(GuiGraphics graphics, int mouseX, int mouseY) {
        Component noData = Component.translatable("screen.tinkersnewlife.wu_wei.empty");
        graphics.drawString(font, noData, (width - font.width(noData)) / 2, 60, 0xFF5555);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, String row, int index,
                           int x, int y, int w, int h, boolean hover,
                           double mouseX, double mouseY) {
        boolean isCurrent = !selected.isEmpty() && selected.equals(row);
        graphics.fill(x, y, x + w, y + h, isCurrent ? 0xFF2E4A2E
                : hover ? 0xFF4A4A6A : 0xFF33334A);
        if (isCurrent) {
            graphics.fill(x, y, x + w, y + 1, 0xFF7CFF7C);
        }

        // 3D 实体展示
        LivingEntity dummy = dummyOf(index);
        if (dummy != null) {
            float scale = GuiEntityViewer.fitScale(dummy, 26.0F, 6.0F, 26.0F);
            GuiEntityViewer.render(graphics, dummy, x + 34, y + h - 6, scale, mouseX, mouseY);
        }
        // 名称（当前形态绿色）
        graphics.drawString(font, displayName(row), x + 66, y + 5,
                isCurrent ? 0x7CFF7C : 0xFFFFFF);
        // 第二行：当前形态 / 点击选用
        Component state = isCurrent
                ? Component.translatable("screen.tinkersnewlife.wu_wei.current")
                : Component.translatable("screen.tinkersnewlife.wu_wei.click");
        graphics.drawString(font, state, x + 66, y + 25, isCurrent ? 0x7CFF7C : 0xC8C8C8);
    }

    @Override
    protected void onRowClick(int index, String row) {
        TinkersNewlife.CHANNEL.sendToServer(new PacketWuWeiSelect(row));
        onClose();
    }

    /** 形态显示名：EntityType 本地化键，找不到则显示注册名 */
    private static String displayName(String entityTypeId) {
        var type = entityTypeId == null ? null
                : ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(entityTypeId));
        if (type == null) return entityTypeId == null ? "?" : entityTypeId;
        return Component.translatable(type.getDescriptionId()).getString();
    }

    private void drawCentered(GuiGraphics graphics, Component c, int y, int color) {
        graphics.drawString(font, c, (width - font.width(c)) / 2, y, color);
    }
}
