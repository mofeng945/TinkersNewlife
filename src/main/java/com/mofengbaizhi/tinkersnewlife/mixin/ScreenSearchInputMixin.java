package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1117c/f <b>流体搜索框的键盘输入</b>（用户口径：「点击无法输入文字」✗ → 本类就是修它的 ✓）。
 *
 * <h2>为什么挂在 {@link Screen} 而不是加热结构界面 ✗（日志实证 ✓）</h2>
 * 早先我把 `keyPressed`/`charTyped` 注入写在目标 `HeatingStructureScreen` 上 ✗ ⇒ 启动日志：
 * <pre>
 * InvalidInjectionException: Critical injection failure:
 *   @Inject annotation on tnl$onKey could not find any targets matching …
 * </pre>
 * ⇒ 因为这两个方法**不是 `HeatingStructureScreen` 自己声明的** ✗（声明在 `Screen` ✓）
 * —— Mixin 的 `@Inject` 只匹配**目标类自己声明**的方法 ✗ ⇒ 表现就是"框能画、字打不进" ✓。
 *
 * <h2>⚠ §1117g 的关键修正：这两处注入**必须写 `require = 1`** ✗</h2>
 * 用户第 5 次报"依旧无法输入"时，日志里**一条 `charTyped`/`keyPressed` 记录都没有** ✗ ——
 * 而本仓 mixin 配置是 `"injectors": { "defaultRequire": 0 }` ✓
 * ⇒ **没写 `require` 的注入，匹配不上就静默跳过** ✗（既没日志、也没效果 ✗）
 * ⇒ 于是"框在、键入无反应、日志干净"✓ 完全对上 ✓。
 * <p>⇒ 现在两处都 `require = 1` ✓：**匹配不上就让启动日志直说** ✗（`Mixin apply failed …` ✓）——
 * 宁可响亮失败 ✗ 也不要静默无效 ✗。
 *
 * <h2>输入规则（配套"打开界面即自动聚焦" ✓）</h2>
 * <ul>
 *   <li>退格 ⇒ 删字 ✓；回车/Esc ⇒ 取消聚焦 ✓；</li>
 *   <li>⚠ **其它按键一律不吃** ✗ ⇒ `E` 关界面、匠魂快捷键照旧可用 ✓；</li>
 *   <li>聚焦时 `charTyped` 的字符追加进查询 ✓（含中文输入法提交的汉字 ✓）。</li>
 * </ul>
 */
@Mixin(Screen.class)
public abstract class ScreenSearchInputMixin {

    /** 临时诊断计数（最多 20 条 ✓ 定位完删 ✗） */
    private static final java.util.concurrent.atomic.AtomicInteger TNL$DIAG =
            new java.util.concurrent.atomic.AtomicInteger();

    /** 退格删字 ✓；回车/Esc 取消聚焦 ✓；**其它按键一律放行** ✗ */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 1)
    private void tnl$searchKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            // §1117g 无条件记前若干条 ✓ —— 用来判定"这个注入到底有没有生效"✓
            if (TNL$DIAG.incrementAndGet() <= 20) {
                FluidSearch.diag("keyPressed 到达 key=" + keyCode + " 界面=" + this.getClass().getSimpleName()
                        + " 是加热结构界面=" + FluidSearch.isHeatingStructureScreen(this)
                        + " 聚焦=" + FluidSearch.isFocused());
            }
            if (!FluidSearch.isFocused() || !FluidSearch.isHeatingStructureScreen(this)) return;
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String q = FluidSearch.getQuery();
                if (!q.isEmpty()) {
                    FluidSearch.setQuery(q.substring(0, q.length() - 1));
                    FluidSearch.diag("退格 ⇒ 查询='" + FluidSearch.getQuery() + "'");
                }
                cir.setReturnValue(true);           // 只吃退格 ✓
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                FluidSearch.setFocused(false);      // 取消聚焦 ⇒ 之后按键全部放行 ✓
                FluidSearch.diag("退出输入（回车/Esc）⇒ 聚焦=false");
                // ⚠ 这里**不 cancel** ✗ ⇒ 让原版照常处理（Esc 该关界面就关 ✓）
            }
        } catch (Throwable ignored) {
        }
    }

    /** 输入字符：追加到查询里 ✓（含中文输入法提交上来的汉字 ✓） */
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 1)
    private void tnl$searchChar(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            FluidSearch.diag("charTyped 到达 '" + codePoint + "' 界面=" + this.getClass().getSimpleName()
                    + " 是加热结构界面=" + FluidSearch.isHeatingStructureScreen(this)
                    + " 聚焦=" + FluidSearch.isFocused());
            if (!FluidSearch.isFocused() || !FluidSearch.isHeatingStructureScreen(this)) return;
            if (codePoint < ' ' || codePoint == 127) return;   // 控制字符交给原版 ✓
            String q = FluidSearch.getQuery();
            if (q.length() < 64) {
                FluidSearch.setQuery(q + codePoint);
                FluidSearch.diag("输入 '" + codePoint + "' ⇒ 查询='" + FluidSearch.getQuery() + "'");
            }
            cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }
}
