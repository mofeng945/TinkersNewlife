package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenSpiritScreen.RowData;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * 咒灵操术 个体列表 GUI：
 * 每行 = 一名被记录的亡灵个体（名称 + 3D 实体展示 + 释放状态），带滚动条；
 * mode 0 点击=释放/收回，mode 1 点击=献祭（清除数据并蓄力漩涡）。
 */
public class CursedSpiritScreen extends Screen {

    private static final int ROW_H = 44;
    private static final int W = 260;
    private static final int LIST_TOP = 34;
    private static final int BOTTOM_PAD = 22;

    private final int mode;
    private final List<RowData> rows;
    private final List<LivingEntity> dummies = new ArrayList<>();

    private int startX;
    private int listBottom;
    private int visibleRows;
    private int scrollOffset = 0;

    public CursedSpiritScreen(int mode, List<RowData> rows) {
        super(Component.translatable(mode == 0
                ? "screen.tinkersnewlife.spirit.title"
                : "screen.tinkersnewlife.spirit.title_sacrifice"));
        this.mode = mode;
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (dummies.isEmpty() && mc.level != null) {
            for (RowData r : rows) {
                EntityType<?> type = EntityType.byString(r.type).orElse(null);
                if (type == null) continue;
                if (!(type.create(mc.level) instanceof LivingEntity living)) continue;
                CompoundTag nbt = r.nbt.copy();
                nbt.remove("UUID");
                nbt.remove("Pos");
                nbt.remove("Dimension");
                nbt.remove("Motion");
                nbt.remove("WorldUUIDMost");
                nbt.remove("WorldUUIDLeast");
                try {
                    living.load(nbt);
                } catch (Throwable ignored) {
                    // 客户端仅展示，加载失败则退回原样
                }
                dummies.add(living);
            }
        }
        startX = (width - W) / 2;
        listBottom = height - BOTTOM_PAD;
        int viewH = Math.max(0, listBottom - LIST_TOP);
        visibleRows = Math.max(1, viewH / ROW_H);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() - visibleRows)));
    }

    private int rowY(int row) {
        return LIST_TOP + (row - scrollOffset) * ROW_H;
    }

    private boolean inList(double mx, double my) {
        return mx >= startX && mx <= startX + W && my >= LIST_TOP && my <= listBottom;
    }

    private int clickedRow(double mx, double my) {
        if (!inList(mx, my)) return -1;
        int r = (int) ((my - LIST_TOP) / ROW_H) + scrollOffset;
        return r >= 0 && r < rows.size() ? r : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = clickedRow(mouseX, mouseY);
            if (row >= 0) {
                TinkersNewlife.CHANNEL.sendToServer(new PacketSpiritSelect(mode, row));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, rows.size() - visibleRows);
        if (max > 0) {
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        Component title = Component.translatable(mode == 0
                ? "screen.tinkersnewlife.spirit.title"
                : "screen.tinkersnewlife.spirit.title_sacrifice");
        graphics.drawString(font, title, (width - font.width(title)) / 2, 12, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.tinkersnewlife.spirit.count", rows.size()),
                (width - font.width(Component.translatable("screen.tinkersnewlife.spirit.count", rows.size()))) / 2,
                22, 0x9A9A9A);

        int max = Math.max(0, rows.size() - visibleRows);
        int shown = Math.min(rows.size(), scrollOffset + visibleRows);
        for (int r = scrollOffset; r < shown; r++) {
            drawRow(graphics, r, mouseX, mouseY);
        }
        // 滚动指示
        if (max > 0) {
            graphics.drawString(font, Component.translatable("screen.tinkersnewlife.spirit.scroll"),
                    startX + W + 6, LIST_TOP + 8, 0x9A9A9A);
        }
        Component foot = Component.translatable(mode == 0
                ? "screen.tinkersnewlife.spirit.foot"
                : "screen.tinkersnewlife.spirit.foot_sacrifice");
        graphics.drawString(font, foot, (width - font.width(foot)) / 2, height - 16, 0x9A9A9A);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics graphics, int row, double mouseX, double mouseY) {
        int y = rowY(row);
        if (y < LIST_TOP - ROW_H || y > listBottom) return;
        boolean hover = inList(mouseX, mouseY) && y <= mouseY && mouseY <= y + ROW_H;
        RowData data = rows.get(row);
        graphics.fill(startX, y, startX + W, y + ROW_H, hover ? 0x55333333 : 0x33222222);
        graphics.fill(startX, y, startX + W, y + 1, 0xFF444444);

        // 3D 实体展示
        if (row - scrollOffset < dummies.size()) {
            LivingEntity dummy = dummies.get(row - scrollOffset);
            if (dummy != null) {
                float scale = clamp(26.0F / Math.max(0.5F, dummy.getBbHeight()), 6.0F, 26.0F);
                drawEntity(graphics, dummy, startX + 34, y + ROW_H - 6, scale, mouseX - (startX + 34), mouseY - (y + ROW_H - 6));
            }
        }
        // 名称 + 状态
        graphics.drawString(font, data.name == null || data.name.isEmpty() ? "?" : data.name,
                startX + 66, y + 6, 0xFFFFFF);
        graphics.drawString(font, Component.translatable(data.released
                        ? "screen.tinkersnewlife.spirit.released"
                        : "screen.tinkersnewlife.spirit.standby"),
                startX + 66, y + 26, data.released ? 0x7CFF7C : 0xC8C8C8);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 行内 3D 实体渲染（等价背包实体渲染） */
    private void drawEntity(GuiGraphics graphics, LivingEntity entity, int x, int feetY, float size,
                            double mouseX, double mouseY) {
        float yawF = (float) Math.atan(mouseX / 40.0);
        float pitchF = (float) Math.atan(mouseY / 40.0);
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf rotPitch = new Quaternionf().rotateX(pitchF * 20.0F * ((float) Math.PI / 180F));
        rot.mul(rotPitch);

        float body = entity.yBodyRot;
        float yr = entity.getYRot();
        float xr = entity.getXRot();
        float hro = entity.yHeadRotO;
        float hr = entity.yHeadRot;
        entity.yBodyRot = 180.0F + yawF * 20.0F;
        entity.setYRot(180.0F + yawF * 40.0F);
        entity.setXRot(-pitchF * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, feetY, 50.0D);
        pose.mulPoseMatrix(new Matrix4f().scaling(size, size, -size));
        pose.mulPose(rot);
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotPitch.conjugate();
        dispatcher.overrideCameraOrientation(rotPitch);
        dispatcher.setRenderShadow(false);
        com.mojang.blaze3d.systems.RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                pose, graphics.bufferSource(), 15728880));
        graphics.flush();
        dispatcher.setRenderShadow(true);
        dispatcher.overrideCameraOrientation(null);
        pose.popPose();

        entity.yBodyRot = body;
        entity.setYRot(yr);
        entity.setXRot(xr);
        entity.yHeadRotO = hro;
        entity.yHeadRot = hr;
    }
}
