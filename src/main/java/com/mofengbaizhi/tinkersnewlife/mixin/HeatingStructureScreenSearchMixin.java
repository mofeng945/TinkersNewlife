package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1116 <b>熔炼炉 / 熔铸炉：流体搜索框本体</b>（用户口径 ✓）。
 *
 * <h2>为什么自己画、不用 {@code EditBox}</h2>
 * 目标类的父类是匠魂自己的 {@code MultiModuleScreen<HeatingStructureContainerMenu>} ✓（**不是**原版屏幕 ✗）
 * ⇒ 往它身上挂原版控件要处理泛型/父类镜像 ✗ 很容易把 mixin 写崩 ✗。
 * 这里改成**纯自绘**：矩形＋文字 ✓ 五个注入点全在"它本来就有的方法"上 ✓ 风险最小 ✓。
 *
 * <h2>注入点（混淆名＋描述符 ✓ 目标类实测 ✓）</h2>
 * <ul>
 *   <li>{@code m_181908_()} ＝ {@code init} ✓：重置聚焦状态 ✓；</li>
 *   <li>{@code m_7286_(Lnet/minecraft/client/gui/GuiGraphics;FII)V} ＝ {@code renderBg} ✓：画搜索框 ✓；</li>
 *   <li>{@code m_7933_(III)Z} ＝ {@code keyPressed} ✓（继承自原版屏幕 ✓ 也能注入 ✓）：退格/回车/Esc ✓；</li>
 *   <li>{@code m_5534_(CI)Z} ＝ {@code charTyped} ✓：输入字符（含 IME 提交的汉字 ✓）；</li>
 *   <li>{@code m_6375_(DDI)Z} ＝ {@code mouseClicked} ✓：点框里＝聚焦并**吃掉**这次点击 ✓（免得同时抽了流体 ✗）；点框外＝失焦但**放行** ✓。</li>
 * </ul>
 *
 * <h2>安全口径</h2>
 * {@code require = 1} ✓（匹配不上 ⇒ 启动报错 ✗ 不静默失效 ✗）；{@code remap = false} ✓（目标是模组类 ✓）；
 * 每处注入都 try/catch ✓（最坏只是"没有搜索框" ✓ **不影响匠魂界面本身** ✗）。
 * 查询一旦变化就写进 {@link FluidSearch#setQuery(String)} ✓ ⇒ 过滤由 `GuiSmelteryTankSearchMixin` 生效 ✓。
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.client.screen.HeatingStructureScreen")
public abstract class HeatingStructureScreenSearchMixin {

    /** 原版屏幕的 GUI 左上角（`AbstractContainerScreen` 的字段 ✓ 可 shadow ✓） */
    @Shadow @org.spongepowered.asm.mixin.Final protected int leftPos;
    @Shadow @org.spongepowered.asm.mixin.Final protected int topPos;
    /** 原版屏幕的宽度（同一个类 ✓） */
    @Shadow @org.spongepowered.asm.mixin.Final protected int imageWidth;
    /** 字体（`Screen#font` ✓） */
    @Shadow protected net.minecraft.client.gui.Font font;

    /** 搜索框是否聚焦（**按屏幕实例** ✓ 不是全局静态 ✗） */
    @Unique private boolean tnl$searchFocused;

    /** 搜索框区域（每帧按当前布局算 ✓ 布局会随分辨率变 ✓） */
    @Unique private int tnl$searchX() {
        return this.leftPos + 6;
    }

    @Unique private int tnl$searchY() {
        return this.topPos + 6;
    }

    @Unique private int tnl$searchW() {
        return Math.max(60, this.imageWidth - 12);
    }

    @Unique private boolean tnl$insideBox(double mx, double my) {
        int x = tnl$searchX();
        int y = tnl$searchY();
        int w = tnl$searchW();
        return mx >= x && mx < x + w && my >= y && my < y + 14;
    }

    /** init：每次打开界面都从"未聚焦"开始 ✓（查询文本保留 ✓ 方便反复看 ✓） */
    @Inject(method = "m_181908_", at = @At("TAIL"), require = 1, remap = false)
    private void tnl$onInit(CallbackInfo ci) {
        try {
            this.tnl$searchFocused = false;
        } catch (Throwable ignored) {
        }
    }

    /** renderBg：画搜索框（自绘 ✓） */
    @Inject(method = "m_7286_(Lnet/minecraft/client/gui/GuiGraphics;FII)V", at = @At("TAIL"), require = 1, remap = false)
    private void tnl$drawSearchBox(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        try {
            int x = tnl$searchX();
            int y = tnl$searchY();
            int w = tnl$searchW();
            int h = 14;
            // 底板 + 边框（聚焦时高亮边框 ✓ 一眼看出"正在输入"）
            graphics.fill(x, y, x + w, y + h, this.tnl$searchFocused ? 0xE0202020 : 0xC0101010);
            int border = this.tnl$searchFocused ? 0xFF7FD4FF : 0xFF505050;
            graphics.fill(x, y, x + w, y + 1, border);
            graphics.fill(x, y + h - 1, x + w, y + h, border);
            graphics.fill(x, y, x + 1, y + h, border);
            graphics.fill(x + w - 1, y, x + w, y + h, border);
            String q = FluidSearch.getQuery();
            if (q.isEmpty()) {
                // 空查询 ⇒ 画灰色用法提示 ✓（用户口径里那几种语法都提示出来 ✓）
                graphics.drawString(this.font, "搜索流体：@模组  #标签  空格=AND  | =OR  -排除  支持拼音",
                        x + 4, y + 3, 0xFF707070, false);
            } else {
                graphics.drawString(this.font, q + (this.tnl$searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L ? "_" : ""),
                        x + 4, y + 3, 0xFFFFFFFF, false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** keyPressed：聚焦时吃键 ✓（退格删字 / 回车·Esc 失焦 / 其它吞掉免得触发匠魂快捷键 ✗） */
    @Inject(method = "m_7933_(III)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$onKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!this.tnl$searchFocused) return;
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String q = FluidSearch.getQuery();
                if (!q.isEmpty()) {
                    FluidSearch.setQuery(q.substring(0, q.length() - 1));
                }
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.tnl$searchFocused = false;
            }
            cir.setReturnValue(true);   // 聚焦时一律吃掉 ✓
        } catch (Throwable ignored) {
        }
    }

    /** charTyped：追加字符 ✓（含中文输入法提交上来的汉字 ✓） */
    @Inject(method = "m_5534_(CI)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$onChar(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!this.tnl$searchFocused) return;
            if (codePoint < ' ' || codePoint == 127) return;      // 控制字符不管 ✓ 交给原版 ✓
            String q = FluidSearch.getQuery();
            if (q.length() < 64) {
                FluidSearch.setQuery(q + codePoint);
            }
            cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }

    /** mouseClicked：框内 ⇒ 聚焦并吃掉这次点击 ✓（否则会顺手抽一格流体 ✗）；框外 ⇒ 放行 ✓ */
    @Inject(method = "m_6375_(DDI)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$onClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean inside = tnl$insideBox(mouseX, mouseY);
            this.tnl$searchFocused = inside;
            if (inside) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }
}
