package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.menu.EeExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * <b>EE 抽取方块 GUI 的屏幕</b>（§558）。
 *
 * <h2>布局（常量全在 {@link EeExtractorMenu} 里 ✓ 别再两处各写一份 ✗）</h2>
 * <pre>
 *   0 .. 17   标题栏（深色）
 *   33        抽取槽（1 格，居中 80,33）
 *   64        信息行："缓存 EE：x / 4000"
 *   84 .. 138 玩家背包 3×9
 *   142 .. 160 快捷栏
 * </pre>
 *
 * <h2>贴图</h2>
 * 本仓库既有的三个界面（{@code SilentGloveScreen} / {@code BagScreen} / {@code QuantumVaultScreen}）
 * 都是<b>用 {@code GuiGraphics#fill} 画</b>的 ✓（它们的 {@code silent_glove.png} 只是个 103 字节的小图标，
 * 并不是 176×166 的面板 ✓）—— 好处是"贴图丢了界面也不会变成紫黑格"✓。
 * 本界面<b>两者都做</b> ✓：先 {@code blit} 我们自己生成的面板贴图
 * （{@code assets/tinkersnewlife/textures/gui/ee_extractor.png} ✓ 由
 * {@code tools/GenEeExtractorGui.ps1} 程序化生成 ✓ 是<b>新文件</b> ✓），
 * 再在<b>槽位与玩家背包格上画凹槽</b> ✓ ⇒ 就算那张贴图将来被删掉，界面也仍然可用 ✓（最坏只是没有底色 ✓）。
 *
 * <h2>数值怎么来的</h2>
 * {@code 缓存 EE} 走菜单的数据槽（§558 选的那一种同步 ✓ 见 {@code EeExtractorMenu#cachedEe()}）✓
 * ⇒ 每帧现读 ✓ 所以 GUI 开着的时候能看到它一点点涨 ✓（不用我们自己发包 ✓）。
 */
public class EeExtractorScreen extends AbstractContainerScreen<EeExtractorMenu> {

    /** 程序化生成的面板贴图（176×166 ✓ 纯装饰 ✓ 见类注释） */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/gui/ee_extractor.png");

    private static final int W = EeExtractorMenu.IMAGE_WIDTH;
    private static final int H = EeExtractorMenu.IMAGE_HEIGHT;

    public EeExtractorScreen(EeExtractorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = EeExtractorMenu.INV_X;
        this.inventoryLabelY = EeExtractorMenu.INV_Y - 11;   // 正好在背包第一行上方 ✓
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // ① 面板贴图（新文件 ✓ 不是任何既有贴图 ✓）—— 走标准 256×256 图集坐标 ✓
        graphics.blit(TEXTURE, x, y, 0, 0, W, H);

        // ② 槽位凹槽：自绘 ✓（贴图丢了也不会"看不出哪一格能放东西"✓ 与 QuantumVaultScreen 同一套画法 ✓）
        for (net.minecraft.world.inventory.Slot slot : this.menu.slots) {
            drawSlotFrame(graphics, x + slot.x, y + slot.y);
        }
    }

    /** 画一个 18×18 凹槽（照 {@code QuantumVaultScreen#drawSlotFrame} ✓ 界面观感一致） */
    private void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x, y, x + 16, y + 16, 0xFF373737);
        graphics.fill(x, y, x + 16, y + 1, 0xFF000000);
        graphics.fill(x, y, x + 1, y + 16, 0xFF000000);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        // 信息行：缓存 EE（每帧现读数据槽 ✓ 所以会随抽能实时涨 ✓）
        Component info = Component.translatable("gui.tinkersnewlife.ee_extractor.cache",
                this.menu.cachedEe(), this.menu.cacheCapacity());
        graphics.drawString(this.font, info, EeExtractorMenu.INV_X, EeExtractorMenu.INFO_Y, 0x404040, false);
    }
}
