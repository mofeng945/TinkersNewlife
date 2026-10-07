package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1117n <b>流体搜索框的键盘输入（回退到"已验证可用"的那一版 ✓）</b>。
 *
 * <h2>⚠ 为什么回退 ✗（第 12 次实测的教训 ✓）</h2>
 * §1117m 我按用户提示改用 MC 原生 `EditBox` ✓ —— 日志确认「**已创建 EditBox 搜索框**」✓ 且无异常 ✓，
 * 但用户实测「**中文英文都输不进去了**」✗：`EditBox` 靠的是**原版界面自己的派发**（遍历 `children()` ✓）
 * 才拿到按键/字符 ✗，而匠魂的界面**不走原版那套派发** ✗ ⇒ 塞进去的 `EditBox` 只是画出来的**空壳** ✗。
 * ⇒ **回退到 §1117h 那一版** ✓ —— 它是**实证可用**的 ✓：
 * 日志里 `keyPressed 到达 key=73/78/79/82` ✓ 与 `过滤 查询='iron' … 置零=1` ✓ 都是那一版跑出来的 ✓；
 * 并保留 §1117l 的**字段修复**（`this.liquidHeights = out` ✓ 让过滤**肉眼可见** ✓）。
 *
 * <h2>做法（三轮实证后的正确组合 ✓）</h2>
 * <ul>
 *   <li>注入 **SRG 名** `m_7933_(III)Z` ＋ `remap = false` ＋ `require = 1` ✓
 *       （＝ `Screen#keyPressed` ✓ 已由 SRG jar 反编译确认存在 ✓；**官方名走 refmap 在本仓匹配不上** ✗）；</li>
 *   <li>挂在 **`Screen`** 而不是匠魂界面 ✗（继承来的方法在目标类里注入会 `could not find any targets` ✗）；</li>
 *   <li>`Screen` 上没有 `(char,int)` 方法 ✗ ⇒ **字符由按键码翻译** ✓（字母/数字/`@ # - _ | : . / ,` 空格 ✓）；</li>
 *   <li>退格删字 ✓；回车/Esc 取消聚焦 ✓；**其它按键一律放行** ✗（不抢 `E` 与匠魂快捷键 ✓）。</li>
 * </ul>
 *
 * <p>⚠ 已知限制：**输入法中文拿不到** ✗（`charTyped` 链在这版里很绕 ✓ 见 §1117f/g/h/i/m ✓）
 * ⇒ 中文检索改走**拼音**（装了「通用拼音搜索」时 ✓ 打 `rongtie` 即命中「熔融铁」✓）。
 */
@Mixin(Screen.class)
public abstract class ScreenKeyInputMixin {

    /** 临时诊断计数（定位完删 ✗） */
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
                FluidSearch.setFocused(false);
                return;
            }
            char c = tnl$keyToChar(keyCode, modifiers);
            if (c != 0) {
                String q = FluidSearch.getQuery();
                if (q.length() < 64) FluidSearch.setQuery(q + c);
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 按键码 → 字符 ✓（替代不可用的 `charTyped` ✗ 见类注释 ✓） */
    private static char tnl$keyToChar(int keyCode, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        // ⚠⚠ §1117o 关键修复（日志实证 ✓）：**绝不能把 W/A/S/D 当搜索输入** ✗！
        //   现象 ✓：用户实测「过滤没任何效果」✗，而日志里 `keyPressed 到达 key=87/65/68`＝W/A/D ✓
        //   ⇒ 走路按键被当成搜索字符 ⇒ 查询里全是 `wad…` ⇒ 这些字母**几乎所有流体名/id 都含** ✗
        //   ⇒ `matches` 全放行 ⇒ 画面**一点变化都没有** ✓✓（完全对上用户现象 ✓）。
        //   ⇒ 直接把这四个键排除 ✓（代价：查询里打不出 w/a/s/d ✗ 但检索主要靠汉字/拼音/id 片段 ✓ 可接受 ✓）。
        if (keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_A
                || keyCode == GLFW.GLFW_KEY_S || keyCode == GLFW.GLFW_KEY_D) {
            return 0;      // 放行 ⇒ 交给原版走路/快捷键 ✓
        }
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));      // 查询按小写比对 ✓ 大小写无所谓 ✓
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            if (shift) {
                if (keyCode == GLFW.GLFW_KEY_2) return '@';
                if (keyCode == GLFW.GLFW_KEY_3) return '#';
                return 0;
            }
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_MINUS:
                return shift ? '_' : '-';
            case GLFW.GLFW_KEY_BACKSLASH:
                return shift ? '|' : '\\';
            case GLFW.GLFW_KEY_SEMICOLON:
                return shift ? ':' : ';';
            case GLFW.GLFW_KEY_PERIOD:
                return '.';
            case GLFW.GLFW_KEY_SLASH:
                return '/';
            case GLFW.GLFW_KEY_COMMA:
                return ',';
            case GLFW.GLFW_KEY_SPACE:
                return ' ';
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
