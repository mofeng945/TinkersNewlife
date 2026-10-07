package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1118a 熔炼炉 / 熔铸炉 搜索框：<b>非字符按键</b>（退格 / 回车 / Esc）✓。
 *
 * <h2>为什么挂在 {@link Screen} ✗</h2>
 * `keyPressed` **不是** `HeatingStructureScreen` 自己声明的方法 ✗（声明在 `Screen` ✓）
 * ⇒ 在目标类里注入会 `could not find any targets` ✗（§1117c 日志实证 ✓）。
 *
 * <h2>两条踩过的坑（写死 ✓）</h2>
 * <ol>
 *   <li>⚠ **官方名走 refmap 在本仓匹配不上** ✗ ⇒ 必须用 **SRG 名 `m_7933_` ＋ `remap = false`** ✓
 *       （§1117h 日志实证：`Critical injection failure: … tnl$searchKey could not find any targets` ✗）；</li>
 *   <li>⚠ 本仓 `defaultRequire = 0` ✗ ⇒ 新注入**一律显式 `require = 1`** ✓（否则静默失效 ✗）。</li>
 * </ol>
 *
 * <h2>分工 ✓</h2>
 * 本类只管**退格 / 回车 / Esc** ✓；**所有真实字符**（含**中文输入法汉字** ✓）
 * 由 {@link KeyboardHandlerImeMixin}（`charTyped` ＝ `m_90889_` ✓）统一收 ✓
 * —— ⚠ 这里**绝不能再翻译字符** ✗ 否则同一个字会进两次 ✓（§1117s 的教训 ✓）。
 */
@Mixin(Screen.class)
public abstract class ScreenKeyInputMixin {

    @Inject(method = "m_7933_(III)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$searchKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!FluidSearch.isFocused() || !FluidSearch.isHeatingStructureScreen(this)) return;

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String q = FluidSearch.getQuery();
                if (!q.isEmpty()) FluidSearch.setQuery(q.substring(0, q.length() - 1));
                cir.setReturnValue(true);
                return;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                FluidSearch.setFocused(false);      // 取消聚焦 ⇒ 之后按键全部放行 ✓（不 cancel ✗）
            }
            // 其余按键一律放行 ✓（WASD 走路、E 关界面、匠魂快捷键都照旧 ✓ —— 用户口径「别禁用 wasd」✓）
        } catch (Throwable ignored) {
        }
    }
}
