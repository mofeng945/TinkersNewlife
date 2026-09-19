package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenSpiritScreen.RowData;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 咒灵操术 个体列表 GUI：
 * 每行 = 一名被记录的亡灵个体（3D 实体展示 + 名称 + 释放状态），带滚动条；
 * mode 0 点击=释放/收回，mode 1 点击=献祭（清除数据并蓄力漩涡）。
 * 列表布局/滚动/点击逻辑由 {@link AbstractRowListScreen} 基类提供，
 * 3D 实体渲染由 {@link GuiEntityViewer} 提供。
 */
public class CursedSpiritScreen extends AbstractRowListScreen<RowData> {

    private static final int ROW_H = 44;
    private static final int W = 260;
    private static final int LIST_TOP = 34;
    private static final int BOTTOM_PAD = 22;

    private final int mode;
    private final List<LivingEntity> dummies = new ArrayList<>();

    public CursedSpiritScreen(int mode, List<RowData> rows) {
        super(Component.translatable(mode == 0
                        ? "screen.tinkersnewlife.spirit.title"
                        : "screen.tinkersnewlife.spirit.title_sacrifice"),
                rows, W, ROW_H, ROW_H, LIST_TOP, BOTTOM_PAD);
        this.mode = mode;
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        if (dummies.isEmpty() && mc.level != null) {
            for (RowData r : rows) {
                EntityType<?> type = EntityType.byString(r.type).orElse(null);
                dummies.add(GuiEntityViewer.createDummy(type, r.nbt));
            }
        }
    }

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        Component title = Component.translatable(mode == 0
                ? "screen.tinkersnewlife.spirit.title"
                : "screen.tinkersnewlife.spirit.title_sacrifice");
        graphics.drawString(font, title, (width - font.width(title)) / 2, 12, 0xFFFFFF);
        Component count = Component.translatable("screen.tinkersnewlife.spirit.count", rows.size());
        graphics.drawString(font, count, (width - font.width(count)) / 2, 22, 0x9A9A9A);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, RowData data, int index,
                           int x, int y, int w, int h, boolean hover,
                           double mouseX, double mouseY) {
        graphics.fill(x, y, x + w, y + h, hover ? 0x55333333 : 0x33222222);
        graphics.fill(x, y, x + w, y + 1, 0xFF444444);

        // 3D 实体展示
        if (index < dummies.size()) {
            LivingEntity dummy = dummies.get(index);
            if (dummy != null) {
                float scale = GuiEntityViewer.fitScale(dummy, 26.0F, 6.0F, 26.0F);
                GuiEntityViewer.render(graphics, dummy, x + 34, y + h - 6, scale, mouseX, mouseY);
            }
        }
        // 名称 + 状态
        graphics.drawString(font, data.name == null || data.name.isEmpty() ? "?" : data.name,
                x + 66, y + 6, 0xFFFFFF);
        graphics.drawString(font, Component.translatable(data.released
                        ? "screen.tinkersnewlife.spirit.released"
                        : "screen.tinkersnewlife.spirit.standby"),
                x + 66, y + 26, data.released ? 0x7CFF7C : 0xC8C8C8);
    }

    @Override
    protected void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        Component foot = Component.translatable(mode == 0
                ? "screen.tinkersnewlife.spirit.foot"
                : "screen.tinkersnewlife.spirit.foot_sacrifice");
        graphics.drawString(font, foot, (width - font.width(foot)) / 2, height - 16, 0x9A9A9A);
    }

    /**
     * 服务端回执（{@code PacketSpiritState}）：按 uid 覆盖"是否在场上"，并删掉服务端已不再记录的个体行。
     *
     * <p>⭐ 界面上"已在场上 / 未释放"这行字<b>只有服务端说了算</b>：
     * 本方法只做"照抄回执"，不推断、也绝不先行置位 ——
     * 否则服务端那次释放失败时，UI 会停在成功态（用户实测的"UI 说在场上、其实没有"✗）。
     * <p>只改/删、不新增：新增行没有 NBT 就画不出 3D 展示，等玩家重开界面时服务端会发完整列表。
     */
    public void applyState(List<String> uids, List<Boolean> released) {
        java.util.Map<String, Boolean> state = new java.util.HashMap<>();
        for (int i = 0; i < uids.size(); i++) {
            state.put(uids.get(i), i < released.size() && released.get(i));
        }
        boolean changed = false;
        for (RowData r : rows) {
            Boolean now = state.get(r.uid);
            if (now != null && now.booleanValue() != r.released) {
                r.released = now.booleanValue();
                changed = true;
            }
        }
        for (int i = rows.size() - 1; i >= 0; i--) {
            String uid = rows.get(i).uid;
            if (uid == null || uid.isEmpty() || state.containsKey(uid)) continue;
            rows.remove(i);
            if (i < dummies.size()) {
                dummies.remove(i);
            }
            changed = true;
        }
        if (changed) {
            refreshLayout();
        }
    }

    @Override
    protected void onRowClick(int index, RowData row) {
        // 只发"请求"（带 uid，服务端按 uid 选行）；UI 不自行改状态，等服务端回执 + 聊天提示
        TinkersNewlife.CHANNEL.sendToServer(new PacketSpiritSelect(mode, index, row.uid));
        Minecraft.getInstance().setScreen(null);
    }
}
