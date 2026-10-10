package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketSummonShikigami;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 十影术式 式神选择界面
 * <p>
 * 按下释放键（服务端确认）后打开：5×2 网格列出十种式神，
 * 每格内以 3D 展示该式神的实体模型 + 名称/调伏状态/召唤消耗。
 * 已调伏式神正常显示；未调伏的标红提示（召唤后进入调伏战，击败即解锁）。
 * 点击式神 → 发送召唤包并关闭。咒力在服务端召唤时才扣除。
 * 3D 实体渲染由 {@link GuiEntityViewer} 提供。
 */
public class ShikigamiScreen extends Screen {

    private record Slot(int typeIndex, int x, int y, int w, int h) {}

    private final List<Slot> slots = new ArrayList<>();
    /** 每种式神一个客户端展示实体（懒创建/复用） */
    private final List<LivingEntity> dummies = new ArrayList<>();

    private static final int COLS = 5;
    private static final int W = 100;
    private static final int H = 124;
    private static final int GAP = 8;
    private static final int START_Y = 48;

    public ShikigamiScreen() {
        super(Component.translatable("screen.tinkersnewlife.ten_shadows"));
    }

    @Override
    protected void init() {
        slots.clear();
        int totalW = COLS * W + (COLS - 1) * GAP;
        int startX = (width - totalW) / 2;
        ShikigamiType[] types = ShikigamiType.values();
        for (int i = 0; i < types.length; i++) {
            int row = i / COLS;
            int col = i % COLS;
            slots.add(new Slot(i, startX + col * (W + GAP), START_Y + row * (H + GAP), W, H));
        }
        // 展示实体：每种式神一个（仅渲染用，不进世界）
        Minecraft mc = Minecraft.getInstance();
        if (dummies.isEmpty() && mc.level != null) {
            for (ShikigamiType type : types) {
                dummies.add(GuiEntityViewer.createDummy(type.entityType()));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.ten_shadows.title"), 8, 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.ten_shadows.hint"), 8, 20, 0xAAAAAA);
        int affinity = ClientCurseData.getAffinity();
        int output = ClientCurseData.getOutput();
        double curse = ClientCurseData.getCurse();
        ShikigamiType[] types = ShikigamiType.values();
        for (Slot s : slots) {
            ShikigamiType type = types[s.typeIndex];
            boolean tamed = ClientCurseData.isShikigamiTamed(type);
            boolean hover = mouseX >= s.x && mouseX <= s.x + s.w && mouseY >= s.y && mouseY <= s.y + s.h;
            int cost = type.summonCost(affinity, output);
            boolean affordable = ClientCurseData.isInfinite() || curse >= cost;
            int bg = tamed ? (hover ? 0xFF4A6A4A : 0xFF334A33)
                           : (hover ? 0xFF6A4A3A : 0xFF4A3328);
            graphics.fill(s.x, s.y, s.x + s.w, s.y + s.h, bg);
            graphics.drawString(font, Component.translatable(type.getLangKey()), s.x + 5, s.y + 3, 0xFFFFFF);
            // 调伏状态
            graphics.drawString(font, Component.translatable(
                            tamed ? "screen.tinkersnewlife.ten_shadows.tamed"
                                  : "screen.tinkersnewlife.ten_shadows.untamed"),
                    s.x + 5, s.y + 14, tamed ? 0x55FF55 : 0xFFAA55);
            // 具体消耗：咒力不足标红
            graphics.drawString(font, Component.translatable("screen.tinkersnewlife.ten_shadows.cost_value", cost),
                    s.x + 5, s.y + 26, affordable ? 0x88CCFF : 0xFF5555);
            // 3D 实体展示（格底中央）
            if (s.typeIndex < dummies.size()) {
                LivingEntity dummy = dummies.get(s.typeIndex);
                if (dummy != null) {
                    float scale = GuiEntityViewer.fitScale(dummy, 40.0F, 6.0F, 40.0F);
                    GuiEntityViewer.render(graphics, dummy, s.x + s.w / 2, s.y + s.h - 8, scale, mouseX, mouseY);
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ShikigamiType[] types = ShikigamiType.values();
            for (Slot s : slots) {
                if (mouseX >= s.x && mouseX <= s.x + s.w && mouseY >= s.y && mouseY <= s.y + s.h) {
                    TinkersNewlife.CHANNEL.sendToServer(new PacketSummonShikigami(s.typeIndex));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
