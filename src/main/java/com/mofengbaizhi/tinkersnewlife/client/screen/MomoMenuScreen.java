package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.content.menu.MomoMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 墨默三选项菜单的**客户端界面**（用户口径 §455 A/E）。
 *
 * <p>三个主按钮 **对话 / 交易 / 雇佣** ＋ 右下角 **回退** ✓；
 * 好感度为负时：**雇佣变灰不可点** ✓（用户口径：负数不能雇佣 ✓）；**交易仍然可点** ✓ 只是**更贵** ✓
 * （`MomoFavor.priceFactor`：负好感每点 +1.5% ⇒ −50 时 1.75 倍 ✓ 用户口径"会涨价" ✓）；**对话**负数时也不可点 ✓。
 *
 * <p>自绘（`graphics.fill` 画面板与按钮 ✓ 沿用仓库里 {@code SilentGloveScreen} 的写法 ✓ 不用贴图 ✓）。
 * 批 1 只有这一屏 ✓；批 4 的对话屏、批 2 的交易屏、批 3 的雇佣屏都会各自带**回退** ✓。
 */
public class MomoMenuScreen extends AbstractContainerScreen<MomoMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_DARK = 0xFF8B8B8B;
    private static final int BTN = 0xFF6E6E6E;
    private static final int BTN_HOVER = 0xFF8FA8D8;
    private static final int BTN_OFF = 0xFF4A4A4A;
    private static final int TEXT = 0xFF202020;
    private static final int TEXT_OFF = 0xFF7A7A7A;

    public MomoMenuScreen(MomoMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 190;
        this.imageHeight = 150;
        this.inventoryLabelY = -1000;   // 不画玩家背包标签 ✓（这个界面没有背包 ✗）
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    private int left() { return (this.width - this.imageWidth) / 2; }
    private int top() { return (this.height - this.imageHeight) / 2; }

    private int btnX() { return left() + 20; }
    private int btnY(int index) { return top() + 34 + index * 26; }
    private int btnW() { return this.imageWidth - 40; }
    private int btnH() { return 20; }

    private int backX() { return left() + this.imageWidth - 62; }
    private int backY() { return top() + this.imageHeight - 26; }

    private boolean enabled(int index) {
        if (index == MomoMenu.BTN_TALK) return this.menu.canTalk();
        if (index == MomoMenu.BTN_TRADE) return true;   // 负数**照样能交易**，只是更贵（用户口径 ✓ 见 priceFactor）
        if (index == MomoMenu.BTN_HIRE) return this.menu.canHire();
        return true;
    }

    private static String label(int index) {
        return switch (index) {
            case MomoMenu.BTN_TALK -> "对话";
            case MomoMenu.BTN_TRADE -> "交易";
            case MomoMenu.BTN_HIRE -> "雇佣";
            default -> "回退";
        };
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = left();
        int y = top();
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        graphics.fill(x, y, x + this.imageWidth, y + 17, PANEL_DARK);
        for (int i = 0; i <= 2; i++) {
            boolean on = enabled(i);
            boolean hover = on && isHovering(btnX(), btnY(i), btnW(), btnH(), mouseX, mouseY);
            graphics.fill(btnX(), btnY(i), btnX() + btnW(), btnY(i) + btnH(), !on ? BTN_OFF : (hover ? BTN_HOVER : BTN));
        }
        boolean backHover = isHovering(backX(), backY(), 54, 18, mouseX, mouseY);
        graphics.fill(backX(), backY(), backX() + 54, backY() + 18, backHover ? BTN_HOVER : BTN);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);
        for (int i = 0; i <= 2; i++) {
            boolean on = enabled(i);
            String s = label(i);
            int w = this.font.width(s);
            graphics.drawString(this.font, s, btnX() + (btnW() - w) / 2, btnY(i) + 6,
                    on ? TEXT : TEXT_OFF, false);
        }
        graphics.drawString(this.font, label(MomoMenu.BTN_BACK), backX() + 27 - this.font.width(label(MomoMenu.BTN_BACK)) / 2,
                backY() + 5, TEXT, false);
        // 好感度显示（调试友好 ✓ 玩家一眼能看到自己多少好感 ✓）
        graphics.drawString(this.font, "好感度 " + this.menu.favor(), left() + 8, top() + 20, TEXT, false);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            if (isHovering(backX(), backY(), 54, 18, mouseX, mouseY)) {
                this.onClose();   // 回退：客户端直接关（服务端随之关掉容器）
                return true;
            }
            for (int i = 0; i <= 2; i++) {
                if (enabled(i) && isHovering(btnX(), btnY(i), btnW(), btnH(), mouseX, mouseY)) {
                    // 自建显式包：容器按钮包在无槽位菜单上点不动
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.sendToServer(
                            new com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuAction(this.menu.momoId(), i));
                    if (i == MomoMenu.BTN_TALK) {
                        // ⭐ 对话树在**客户端直接打开** ✓（文案全是静态的 ✓ 好感度已在菜单里同步 ✓ 不用新网络包 ✓）
                        String name = this.minecraft.player == null ? ""
                                : this.minecraft.player.getGameProfile().getName();
                        this.minecraft.setScreen(new MomoTalkScreen(this.menu.favor(), name));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
