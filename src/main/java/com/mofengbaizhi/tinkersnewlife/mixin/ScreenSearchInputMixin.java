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
 * 早先我把 `keyPressed`/`charTyped` 的注入写在目标 `HeatingStructureScreen` 上 ✗ ⇒ 启动日志：
 * <pre>
 * InvalidInjectionException: Critical injection failure:
 *   @Inject annotation on tnl$onKey could not find any targets matching …
 * </pre>
 * ⇒ 因为这两个方法**不是 `HeatingStructureScreen` 自己声明的** ✗（声明在 `Screen` ✓）
 * —— Mixin 的 `@Inject` 只匹配**目标类自己声明**的方法 ✗ ⇒ `require = 1` ⇒ **整个 mixin 被丢弃** ✗
 * ⇒ 表现就是"框能画、字打不进" ✓。
 *
 * <h2>§1117f 起的输入规则（"自动聚焦"配套 ✓ 很关键 ✗）</h2>
 * 搜索框现在**打开界面即聚焦** ✓（见 {@code HeatingStructureScreenSearchMixin} ✓），所以：
 * <ul>
 *   <li><b>只处理三种按键</b>：退格（删字 ✓）、回车/Esc（取消聚焦 ✓ 之后按键全部放行 ✓）；
 *       ⚠ 其它按键**一律不吃** ✗ ⇒ `E` 关界面、匠魂自己的快捷键照旧可用 ✓；</li>
 *   <li><b>字符输入</b>（`charTyped` ✓）在聚焦时追加进查询 ✓（含中文输入法提交的汉字 ✓）；</li>
 *   <li>两道严格闸门 ✓：①当前界面是加热结构界面 ✓ ②搜索框处于聚焦 ✓ ⇒ 都过才动 ✓（绝不影响别的界面 ✗）。</li>
 * </ul>
 *
 * <p>⚠ 用**官方名**（`keyPressed`/`charTyped` ✓ 默认 `remap = true` ✓ 由 refmap 解析 ✓）—— 与仓库既有 mixin 同口径 ✓。
 */
@Mixin(Screen.class)
public abstract class ScreenSearchInputMixin {

    /** 退格删字 ✓；回车/Esc 取消聚焦 ✓；**其它按键一律放行** ✗ */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void tnl$searchKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
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
            // 其它按键：什么都不做 ⇒ 不吃 ✓ 放行 ✓
        } catch (Throwable ignored) {
        }
    }

    /** 输入字符：追加到查询里 ✓（含中文输入法提交上来的汉字 ✓） */
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void tnl$searchChar(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
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
