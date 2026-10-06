package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1117c <b>流体搜索框的键盘输入</b>（用户口径：「点击无法输入文字」✗ → 本类就是修它的 ✓）。
 *
 * <h2>为什么挂在 {@link Screen} 而不是加热结构界面 ✗（日志实证 ✓）</h2>
 * 之前我把 {@code keyPressed}/{@code charTyped} 的注入写在
 * `HeatingStructureScreenSearchMixin`（目标 `HeatingStructureScreen` ✗）⇒ 启动日志：
 * <pre>
 * InvalidInjectionException: Critical injection failure:
 *   @Inject annotation on tnl$onKey could not find any targets matching …
 * </pre>
 * ⇒ 原因 ✓：这两个方法**不是 `HeatingStructureScreen` 自己声明的** ✗（它们声明在 `Screen` ✓）
 * —— Mixin 的 `@Inject` 只匹配**目标类自己声明**的方法 ✗（沿父类继承不算 ✓）
 * ⇒ `require = 1` ⇒ **整个 mixin 被丢弃** ✗ ⇒ 表现就是"**框能画、字打不进**" ✓。
 *
 * <p>⇒ 改挂到 `Screen` ✓（原版类 ✓ 方法就在它自己身上 ✓ 一定匹配得上 ✓），
 * 并用**两道严格闸门**保证绝不影响别的界面 ✗：
 * <ol>
 *   <li>当前界面必须是 {@code HeatingStructureScreen} ✓（{@link FluidSearch#isHeatingStructureScreen} ✓）；</li>
 *   <li>搜索框必须处于**聚焦**状态 ✓（只有点过框才会聚焦 ✓）。</li>
 * </ol>
 * 两道都过才处理 ✓ 并**吃掉**该次按键 ✓（免得顺手触发匠魂快捷键 ✗）。
 *
 * <p>⚠ 用**官方名**（`keyPressed`/`charTyped` ✓ 默认 `remap = true` ✓ 由 refmap 解析 ✓）
 * —— 本仓既有 mixin 也是这么写的（见 `ItemRendererMixin` ✓）。
 */
@Mixin(Screen.class)
public abstract class ScreenSearchInputMixin {

    /** 退格：删一个字符 ✓ */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void tnl$searchKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!FluidSearch.isFocused() || !FluidSearch.isHeatingStructureScreen(this)) return;
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String q = FluidSearch.getQuery();
                if (!q.isEmpty()) FluidSearch.setQuery(q.substring(0, q.length() - 1));
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                FluidSearch.setFocused(false);      // 回车/Esc ⇒ 退出输入 ✓ 让匠魂照常收键 ✓
                return;
            }
            cir.setReturnValue(true);               // 聚焦时一律吃掉 ✓
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
            if (q.length() < 64) FluidSearch.setQuery(q + codePoint);
            cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }
}
