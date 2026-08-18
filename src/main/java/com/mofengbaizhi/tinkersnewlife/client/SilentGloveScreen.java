package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;

public class SilentGloveScreen extends AbstractContainerScreen<SilentGloveContainer> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/gui/silent_glove.png");

    public SilentGloveScreen(SilentGloveContainer container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 148;
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 主背景
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        // 标题栏
        graphics.fill(x, y, x + this.imageWidth, y + 17, 0xFF404040);

        // 空间奇点库标题背景
        graphics.fill(x + 8, y + 18, x + 168, y + 56, 0xFF555555);

        // 绘制 12 个格子（2行 × 6列）
        int startX = x + 44;
        int startY = y + 20;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 6; col++) {
                int slotX = startX + col * 18;
                int slotY = startY + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF8B8B8B);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF373737);
            }
        }

        // ✅ 使用翻译键
        graphics.drawString(this.font, Component.translatable("container.tinkersnewlife.silent_glove"), 
                x + 44, y + 58, 0xCCCCCC, false);

        // 玩家物品栏背景
        int playerInvY = y + 62;
        graphics.fill(x + 7, playerInvY - 1, x + 169, playerInvY + 3 * 18 + 1, 0xFF8B8B8B);
        graphics.fill(x + 7, playerInvY + 3 * 18 + 3, x + 169, playerInvY + 3 * 18 + 18 + 3, 0xFF8B8B8B);

        // 玩家物品栏格子
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = x + 8 + col * 18;
                int slotY = playerInvY + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF555555);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF8B8B8B);
            }
        }

        // 快捷栏格子
        for (int col = 0; col < 9; col++) {
            int slotX = x + 8 + col * 18;
            int slotY = playerInvY + 58;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF555555);
            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFF, false);
    }
}