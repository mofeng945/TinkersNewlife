package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.MoltenForgeBlockEntity;
import com.mofengbaizhi.tinkersnewlife.content.menu.MoltenForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.client.GuiUtil;

import java.util.List;

/**
 * 融锻炉界面：对照匠魂熔炉（{@code melter.png} 原生背景 + 工具槽 + 多流体立管显示）。
 */
public class MoltenForgeScreen extends AbstractContainerScreen<MoltenForgeMenu> {

    private static final ResourceLocation BACKGROUND = TConstruct.getResource("textures/gui/melter.png");

    public MoltenForgeScreen(MoltenForgeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
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
        // 匠魂熔炉原生背景
        GuiUtil.drawBackground(graphics, this, BACKGROUND);

        // 工具槽
        this.renderSlot(graphics, 80, 35);

        // 多流体立管（右侧）：每个流体一个色条
        MoltenForgeBlockEntity te = this.menu.getTile();
        if (te != null) {
            List<FluidStack> fluids = te.getFluids();
            int x = 116;
            int y = 30;
            for (int i = 0; i < fluids.size() && i < MoltenForgeBlockEntity.TANKS; i++) {
                FluidStack fs = fluids.get(i);
                if (fs.isEmpty()) continue;
                int color = 0xFF8090A0;   // 流体色块（tooltip 展示名称与量）
                graphics.fill(x, y, x + 46, y + 14, color);
                graphics.fill(x + 1, y + 1, x + 45, y + 13, 0x80000000);
                if (this.isHovering(x, y, 46, 14, mouseX, mouseY)) {
                    graphics.renderTooltip(this.font, Component.literal(
                            fs.getFluid().getFluidType().getDescription().getString() + " x" + fs.getAmount()),
                            mouseX, mouseY);
                }
                y += 18;
            }
        }
    }

    private void renderSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
    }

    private boolean isHovering(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
