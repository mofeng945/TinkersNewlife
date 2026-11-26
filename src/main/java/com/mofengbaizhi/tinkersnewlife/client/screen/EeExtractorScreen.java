package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.content.menu.EeExtractorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * <b>EE 抽取方块 GUI 的屏幕</b>（§558 建 · <b>§604 按用户口径整屏重画</b> ✓）。
 *
 * <h2>§604 为什么推倒重画（用户原话："重画，模仿我其他gui已有风格，比如量子背包"）</h2>
 * 上一版是"<b>程序化生成的贴图</b> + 自绘凹槽"两层叠着画 ✗ ⇒ 用户实机看到"整个 GUI 绘制错位" ✗
 * （贴图里烤死的孔位是本脚本自己写的一套坐标 ✓ 一旦和菜单里的槽位坐标不一致，就会**画出两套格子** ✗，
 * 而且那张图是**一次生成、永久留档**的 ✗ 改了布局它不会跟着变 ✗）。
 * <p>⇒ 本版**彻底不画贴图** ✓，照 {@code QuantumVaultScreen}（量子背包）那一套做：
 * <pre>
 *   ① 面板 = 一次 {@code fill(0xFFC6C6C6)}
 *   ② 标题条 = {@code fill(0xFF404040)} 高 21（与量子背包同高 ✓）
 *   ③ 凹槽 = 三层 {@code fill}（外圈 0x8B8B8B / 内填 0x373737 / 内侧上左各 1px 黑 ✓ 与量子背包逐字一致 ✓）
 *   ④ <b>槽位坐标直接取自 {@code menu.slots}</b> ✓ —— 和原版画物品用的是**同一套坐标** ✓
 *      ⇒ "框和物品错位"这件事从结构上就不可能再发生 ✓✓
 * </pre>
 *
 * <h2>布局（常量全在 {@link EeExtractorMenu} 里 ✓ 不两处各写一份 ✗）</h2>
 * <pre>
 *   0 .. 21    标题条（深色）+ 21 行紫强调线（本模组水晶色 0x6D2BA8）
 *   8, 36      提示行："放有电的水晶"（用户说不知道那个槽是干嘛的 ⇒ 直接在界面上写明 ✓）
 *   80, 33     抽取槽（1 格，居中）
 *   8, 61      信息行："缓存 EE：x / 4000"
 *   71         分隔线
 *   8, 73      "物品栏"标签
 *   84 / 102 / 120  玩家背包 3×9
 *   142             快捷栏
 * </pre>
 * ⚠ 凹槽**只**由 {@code menu.slots} 驱动 ✓（含那个抽取槽 ✓ 它在菜单里就是第 0 号槽 ✓）
 * ⇒ 界面上**不可能**再出现任何"菜单里没有的格子" ✓。
 */
public class EeExtractorScreen extends AbstractContainerScreen<EeExtractorMenu> {

    private static final int W = EeExtractorMenu.IMAGE_WIDTH;
    private static final int H = EeExtractorMenu.IMAGE_HEIGHT;

    // ---- 与 QuantumVaultScreen / BagScreen 同一套配色（用户口径：仿已有风格 ✓）----
    /** 面板底色（量子背包同款 ✓） */
    private static final int PANEL_FACE = 0xFFC6C6C6;
    /** 标题条（量子背包同款，高 21 ✓） */
    private static final int TITLE_BAR = 0xFF404040;
    /** 本模组的水晶紫（只做 1px 强调线 ✓ 用户没让去掉 ✓） */
    private static final int CRYSTAL_VIOLET = 0xFF6D2BA8;
    /** 凹槽三层（与量子背包的 {@code drawSlotFrame} 逐字一致 ✓） */
    private static final int SLOT_BORDER = 0xFF8B8B8B;
    private static final int SLOT_FILL = 0xFF373737;
    private static final int SLOT_EDGE = 0xFF000000;
    /** 分隔线（亮面上的深灰 ✓） */
    private static final int SEPARATOR = 0xFF555555;
    /** 标题文字：标题条是**深色** ⇒ 必须用浅色 ✗（上一版用 0x404040 与底色同色 = 隐形 ✗ 见 §603 ✓） */
    private static final int TITLE_COLOR = 0xFFE0E0E0;
    /** 亮面上的文字（标签 / 信息行 / 提示行 ✓ 深灰在亮面上清楚 ✓） */
    private static final int LABEL_COLOR = 0x404040;

    /** 标题条高度（与量子背包一致 ✓） */
    private static final int TITLE_H = 21;
    /** 提示行的 Y（在标题条之下、抽取槽之上 ✓） */
    private static final int HINT_Y = 36;

    public EeExtractorScreen(EeExtractorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = EeExtractorMenu.INV_X;
        this.inventoryLabelY = EeExtractorMenu.INV_Y - 11;   // 正好在背包第一行上方 ✓（71 的分隔线在它上面 ✓ 不穿字 ✓）
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        final int x = this.leftPos;
        final int y = this.topPos;

        // ① 面板 + 标题条（纯 fill ✓ 不再 blit 任何贴图 ✓ ⇒ 不存在"贴图与代码两套坐标"的错位 ✗）
        graphics.fill(x, y, x + W, y + H, PANEL_FACE);
        graphics.fill(x, y, x + W, y + TITLE_H, TITLE_BAR);
        graphics.fill(x + 2, y + TITLE_H, x + W - 2, y + TITLE_H + 1, CRYSTAL_VIOLET);

        // ② 分隔线（在信息行之下、"物品栏"标签之上 ✓）
        graphics.fill(x + 7, y + 71, x + W - 7, y + 72, SEPARATOR);

        // ③ 凹槽：**只**按菜单里的槽位画 ✓ —— 与原版绘制物品用的是同一套 (leftPos + slot.x, topPos + slot.y) ✓
        for (Slot slot : this.menu.slots) {
            drawSlotFrame(graphics, x + slot.x, y + slot.y);
        }
    }

    /** 画一个 18×18 凹槽（与 {@code QuantumVaultScreen#drawSlotFrame} 逐字一致 ✓ 用户口径：仿已有风格 ✓） */
    private void drawSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
        graphics.fill(x, y, x + 16, y + 1, SLOT_EDGE);
        graphics.fill(x, y, x + 1, y + 16, SLOT_EDGE);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题：标题条是深色 ⇒ 用浅色 ✓（§603：上一版用 0x404040 = 与底色同色 ⇒ 隐形 ✗ 对比度 1.000 ✗）
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_COLOR, false);

        // 提示行：用户口径"不知道那个格子是干嘛的" ⇒ 直接写在界面上 ✓
        graphics.drawString(this.font,
                Component.translatable("gui.tinkersnewlife.ee_extractor.hint"),
                EeExtractorMenu.INV_X, HINT_Y, LABEL_COLOR, false);

        // 信息行：缓存 EE（每帧现读菜单数据槽 ✓ 会随抽能实时涨 ✓）
        Component info = Component.translatable("gui.tinkersnewlife.ee_extractor.cache",
                this.menu.cachedEe(), this.menu.cacheCapacity());
        graphics.drawString(this.font, info, EeExtractorMenu.INV_X, EeExtractorMenu.INFO_Y, LABEL_COLOR, false);

        // 玩家物品栏标签
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, LABEL_COLOR, false);
    }
}
