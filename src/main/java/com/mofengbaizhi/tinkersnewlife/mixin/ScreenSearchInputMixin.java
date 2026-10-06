package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1117h <b>流体搜索框的键盘输入</b>（用户口径：「点击无法输入文字」✗）。
 *
 * <h2>三轮实证得到的结论（都写死在这里 ✓ 别再重走 ✗）</h2>
 * <ol>
 *   <li><b>不能挂在 `HeatingStructureScreen`</b> ✗：`keyPressed`/`charTyped` 不是它自己声明的 ⇒
 *       {@code could not find any targets} ⇒ `require=1` ⇒ 整包被丢 ✗；</li>
 *   <li>⚠ <b>不能只写"官方名"</b> ✗：{@code method = "keyPressed"}（默认 remap ⇒ 走 refmap）在**本仓**映射不到 ✗
 *       ⇒ 日志实证：{@code Critical injection failure: @Inject annotation on tnl$searchKey could not find any targets} ✗
 *       （本仓既有 mixin 大量用 `remap = false` + SRG 名 ✓ 就是这个原因 ✓）；</li>
 *   <li>⚠ <b>`Screen` 上没有 `(char,int)` 方法</b> ✗（直接从 SRG jar 反编译确认 ✓）
 *       ⇒ 所以 {@code charTyped} 这条路**放弃** ✗，字符改用 <b>按键码自己翻译</b> ✓。</li>
 * </ol>
 *
 * <h2>现在怎么工作 ✓</h2>
 * 注入 {@code m_7933_(III)Z}（＝ {@code keyPressed} ✓ **SRG jar 已确认它就在 `Screen` 里** ✓，`remap = false` ✓）：
 * <ul>
 *   <li><b>退格</b> ⇒ 删一个字 ✓；<b>回车 / Esc</b> ⇒ 取消聚焦 ✓（Esc 不 cancel ✓ 让原版照常关界面 ✓）；</li>
 *   <li><b>字母 / 数字</b> ⇒ 直接进查询 ✓（查询本身按小写比对 ✓ 所以大小写无所谓 ✓）；</li>
 *   <li><b>语法符号</b> ⇒ 按 Shift 状态翻译：`@ # - | : . / , _ ` 等 ✓
 *       ⇒ 打 `iron` / `@tconstruct` / `#molten` / `铁|铜` / `-水` 都够用 ✓；</li>
 *   <li>⚠ **其它按键一律放行** ✗ ⇒ `E` 关界面、匠魂快捷键不受影响 ✓。</li>
 * </ul>
 * <p>⚠ 中文输入法直接输入汉字暂时拿不到（`charTyped` 那条路在 `Screen` 上不存在 ✗）——
 * 但**拼音可用**（装了「通用拼音搜索」时 ✓）⇒ 日常检索不受影响 ✓。
 */
@Mixin(Screen.class)
public abstract class ScreenSearchInputMixin {

    /** 临时诊断计数（最多 20 条 ✓ 定位完删 ✗） */
    private static final java.util.concurrent.atomic.AtomicInteger TNL$DIAG =
            new java.util.concurrent.atomic.AtomicInteger();

    @Inject(method = "m_7933_(III)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$searchKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (TNL$DIAG.incrementAndGet() <= 20) {
                FluidSearch.diag("keyPressed 到达 key=" + keyCode + " 界面=" + this.getClass().getSimpleName()
                        + " 是加热结构界面=" + FluidSearch.isHeatingStructureScreen(this)
                        + " 聚焦=" + FluidSearch.isFocused());
            }
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
                return;
            }
            char c = tnl$keyToChar(keyCode, modifiers);
            if (c != 0) {
                String q = FluidSearch.getQuery();
                if (q.length() < 64) FluidSearch.setQuery(q + c);
                cir.setReturnValue(true);           // 只吃"我们翻译得出的字符" ✓
            }
            // 其余按键：什么都不做 ⇒ 放行 ✓
        } catch (Throwable ignored) {
        }
    }

    /**
     * 按键码 → 字符 ✓（**替代不可用的 `charTyped`** ✗ 见类注释 ③）。
     * <p>只翻译"检索真正需要"的字符 ✓：字母、数字、以及语法符号 `@ # - | : . / , _ ` ✓。
     */
    private static char tnl$keyToChar(int keyCode, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));      // 统一按小写入查询 ✓
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            if (shift) {
                if (keyCode == GLFW.GLFW_KEY_2) return '@';         // Shift+2 ⇒ @（模组前缀 ✓）
                if (keyCode == GLFW.GLFW_KEY_3) return '#';         // Shift+3 ⇒ #（标签前缀 ✓）
                return 0;
            }
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_MINUS:
                return shift ? '_' : '-';                            // -排除 / _ 下划线 ✓
            case GLFW.GLFW_KEY_BACKSLASH:
                return shift ? '|' : '\\';                            // | OR ✓
            case GLFW.GLFW_KEY_SEMICOLON:
                return shift ? ':' : ';';                             // namespace:path ✓
            case GLFW.GLFW_KEY_PERIOD:
                return '.';
            case GLFW.GLFW_KEY_SLASH:
                return '/';
            case GLFW.GLFW_KEY_COMMA:
                return ',';
            case GLFW.GLFW_KEY_SPACE:
                return ' ';                                          // 空格 = AND ✓
            case GLFW.GLFW_KEY_KP_0: case GLFW.GLFW_KEY_KP_1: case GLFW.GLFW_KEY_KP_2:
            case GLFW.GLFW_KEY_KP_3: case GLFW.GLFW_KEY_KP_4: case GLFW.GLFW_KEY_KP_5:
            case GLFW.GLFW_KEY_KP_6: case GLFW.GLFW_KEY_KP_7: case GLFW.GLFW_KEY_KP_8:
            case GLFW.GLFW_KEY_KP_9:
                return (char) ('0' + (keyCode - GLFW.GLFW_KEY_KP_0));
            default:
                return 0;
        }
    }
}
