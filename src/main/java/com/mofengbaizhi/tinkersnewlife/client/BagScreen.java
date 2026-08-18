package com.mofengbaizhi.tinkersnewlife.client;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.storage.BagContainer;
import com.mofengbaizhi.tinkersnewlife.network.PacketSortBag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BagScreen extends AbstractContainerScreen<BagContainer> {

    private final int rows;
    private Button sortButton;

    // ✅ 按钮文本的翻译键
    private static final Component SORT_BUTTON_TEXT = 
            Component.translatable("container.tinkersnewlife.bag.sort");

    public BagScreen(BagContainer container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title);
        int totalSlots = container.getBagSlotCount();
        this.rows = totalSlots / 9;
        this.imageWidth = 176;
        this.imageHeight = 114 + rows * 18;
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;

        int buttonX = this.leftPos + this.imageWidth - 55;
        int buttonY = this.topPos + 2;
        this.sortButton = this.addRenderableWidget(Button.builder(
                SORT_BUTTON_TEXT,  // ✅ 使用翻译键
                button -> TinkersNewlife.CHANNEL.sendToServer(new PacketSortBag())
        ).bounds(buttonX, buttonY, 45, 14).build());
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

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);
        graphics.fill(x, y, x + this.imageWidth, y + 17, 0xFF404040);

        int slotStartX = x + 8;
        int slotStartY = y + 18;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = slotStartX + col * 18;
                int slotY = slotStartY + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF8B8B8B);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF373737);
            }
        }

        int playerInvY = y + 18 + rows * 18 + 14;
        graphics.fill(x + 7, playerInvY - 1, x + 169, playerInvY + 3 * 18 + 1, 0xFF8B8B8B);
        graphics.fill(x + 7, playerInvY + 3 * 18 + 3, x + 169, playerInvY + 3 * 18 + 18 + 3, 0xFF8B8B8B);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = x + 8 + col * 18;
                int slotY = playerInvY + row * 18;
                graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF555555);
                graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF8B8B8B);
            }
        }

        for (int col = 0; col < 9; col++) {
            int slotX = x + 8 + col * 18;
            int slotY = playerInvY + 58;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF555555);
            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // ✅ this.title 和 this.playerInventoryTitle 已经是翻译键，无需修改
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFF, false);
    }

    @Override
    public void resize(Minecraft mc, int width, int height) {
        super.resize(mc, width, height);
        if (this.sortButton != null) {
            this.sortButton.setX(this.leftPos + this.imageWidth - 55);
            this.sortButton.setY(this.topPos + 2);
        }
    }
}