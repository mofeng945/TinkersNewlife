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
 * 都是<b>用 {@code GuiGraphics#fill} 画</b>的 ✓（⚠ §602 更正：{@code textures/gui/silent_glove.png}
 * <b>根本不存在</b> ✗ —— 当年那句"它是个 103 字节的小图标"是错的 ✓ 那个文件在 {@code textures/gui/modifiers/} 里 ✓）。
 * 本界面<b>两者都做</b> ✓：先 {@code blit} 我们自己生成的面板贴图
 * （{@code assets/tinkersnewlife/textures/gui/ee_extractor.png} ✓ 由
 * {@code tools/GenEeExtractorGui.ps1} 程序化生成 ✓ 是<b>新文件</b> ✓），
 * 再在<b>槽位与玩家背包格上画凹槽</b> ✓ ⇒ 就算那张贴图将来被删掉，界面也仍然可用 ✓（最坏只是没有底色 ✓）。
 *
 * <h2>§602 核查出来的一处排版缺陷（已修 ✓）</h2>
 * 生成脚本把"物品栏分隔线"画在 <b>y=76</b> ✗，而"物品栏"标签是 {@code INV_Y - 11 = 73} 起、<b>9 px 高</b>（占 73..81）✗
 * ⇒ <b>那条线正好从字中间穿过去</b> ✗。现在：信息行上移到 y=61（占 61..69）✓、把贴图那条盖掉 ✓、
 * 在 <b>y=71</b> 自己画一条位置正确的 ✓（71 在信息行之下、标签之上 ✓）。
 * ⚠ <b>不去改那张 PNG</b> ✗ —— 仓库铁律：{@code assets/**&#47;textures/**} 下的既有文件一律不覆盖 ✓，
 * 所以修的是"画的时候" ✓。
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

    /** 面板底色（与 {@code tools/GenEeExtractorGui.ps1} 的 {@code $C_FACE} 一致 ✓） */
    private static final int PANEL_FACE = 0xFFC6C6C6;
    /** 分隔线颜色（与生成脚本的 {@code $C_DARK} 一致 ✓） */
    private static final int SEPARATOR = 0xFF555555;
    /** 分隔线左右留白（与生成脚本一致：x=7 .. W-7 ✓） */
    private static final int SEP_MARGIN = 7;

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

        // ①-B §602 修排版：贴图那条分隔线在 y=76 ✗，会从"物品栏"标签（73..81）中间穿过 ✗
        //     ⇒ ① 用底色把它盖掉 ✓ ② 在 y=71 自己画一条位置正确的 ✓（71 在信息行之下、标签之上 ✓）
        graphics.fill(x + SEP_MARGIN, y + 76, x + W - SEP_MARGIN, y + 77, PANEL_FACE);
        graphics.fill(x + SEP_MARGIN, y + 71, x + W - SEP_MARGIN, y + 72, SEPARATOR);

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
