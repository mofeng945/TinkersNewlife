package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.gui.GuiGraphics;
import slimeknights.tconstruct.smeltery.client.screen.module.GuiSmelteryTank;
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

    /**
     * ⚠ §1116 修（用户实测"没有出现搜索框"✗）：**这里绝不能 shadow 原版屏幕的字段** ✗！
     *
     * <p>第一版我写了 `@Shadow leftPos / topPos / imageWidth / font` ✗ —— 那些是**原版**里的成员 ✓
     * ⇒ 运行时是**混淆名（SRG）** ✗ ⇒ Mixin 按 `leftPos` 找不到 ⇒ 抛
     * {@code InvalidMixinException: @Shadow field leftPos was not located} ✗ ⇒ **整个 mixin 被丢弃** ✓
     * （只是 WARN 级 ✗ ⇒ 既没崩、也没有搜索框 ✓ —— 正是用户的现场 ✓）。
     *
     * <p>⇒ 改成**只 shadow 匠魂自己的成员** ✓（模组字段名不混淆 ✓ 名字就是源码里那个 ✓）：
     * 坐标从 {@link GuiSmelteryTank#getX()} 等公开方法拿 ✓，字体直接用 {@code Minecraft.getInstance().font} ✓。
     */
    @Shadow
    private GuiSmelteryTank tank;

    /** 搜索框是否聚焦（**按屏幕实例** ✓ 不是全局静态 ✗） */
    @Unique private boolean tnl$searchFocused;

    /**
     * 搜索框区域 ✓ —— **坐标不由本类算** ✗：由 `GuiSmelteryTankSearchMixin` 每帧写进
     * {@link FluidSearch#setBoxRect} ✓（那边同时持有流体列坐标与 `GuiGraphics` ✓ 见 §1117 ✓）。
     */
    @Unique private boolean tnl$insideBox(double mx, double my) {
        int x = FluidSearch.boxX();
        int y = FluidSearch.boxY();
        int w = FluidSearch.boxW();
        int h = FluidSearch.boxH();
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** init：每次打开界面都从"未聚焦"开始 ✓（查询文本保留 ✓ 方便反复看 ✓） */
    @Inject(method = "m_181908_", at = @At("TAIL"), require = 1, remap = false)
    private void tnl$onInit(CallbackInfo ci) {
        try {
            this.tnl$searchFocused = false;
        } catch (Throwable ignored) {
        }
    }

    /**
     * §1117 ⚠ <b>画搜索框的活已搬到 `GuiSmelteryTankSearchMixin`</b> ✗ —— 本类**只负责输入** ✓。
     *
     * <p>原因 ✓：在 screen 这边定位就得 shadow **原版**的 `leftPos/topPos/imageWidth` ✗
     * （运行期是混淆名 ⇒ {@code InvalidMixinException} ⇒ **整个 mixin 被丢弃** ✗ 见 §1117 日志 ✓），
     * 而流体列的 `x/y/width` 又是**匠魂自己的私有字段** ✗（**跨类** shadow 不到 ✗）。
     * ⇒ 让"本来就持有流体列坐标＋`GuiGraphics`"的那个 mixin 去画 ✓，并把矩形记进
     * {@link FluidSearch#setBoxRect} ✓ 供本类判定命中 ✓。
     */

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
