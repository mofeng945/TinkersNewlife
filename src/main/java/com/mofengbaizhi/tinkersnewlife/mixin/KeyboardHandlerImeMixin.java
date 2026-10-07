package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §1117s <b>输入法直输汉字（中文 IME）</b> ✓ —— 用户口径：「**我想继续攻克输入法输入汉字**」✓。
 *
 * <h2>为什么这次一定对 ✓（三步实测换来的 ✓）</h2>
 * 前两次挂点失败 ✗ 是因为我挂的是 **`m_90907_`** ✓ —— 那只是"**把字符派发给子控件**"的内部助手 ✗
 * （描述符 `(…;II)V` ✗ 不是字符入口 ✓）。
 * <p>这次用 1.20.1 **官方映射**定位到真身 ✓：
 * <pre>
 * 7628 | void charTyped(long,int,int) -> a    ← 所属类: net.minecraft.client.KeyboardHandler ✓
 * </pre>
 * 再从 **SRG jar 反编译**里读到它的 SRG 名与实现 ✓：
 * <pre>
 * private void m_90889_(long p_90890_, int p_90891_, int p_90892_) {   // 参数就是 (窗口, 码点, 修饰键) ✓
 *     if (Character.charCount(p_90891_) == 1) {
 *         for (char c : Character.toChars(p_90891_)) { … }
 * </pre>
 * ⇒ 它收的是**输入法提交的码点** ✓✓ ⇒ 汉字**直达** ✓（不像 `Screen#keyPressed` 只能拿到按键码 ✗）。
 *
 * <h2>与既有输入的分工（避免"打一个字进两个"✗）</h2>
 * <ul>
 *   <li><b>本类</b> ✓：**所有真实字符**（英文／数字／符号／**汉字** ✓）都从这里进 ✓；
 *       ⚠ 收到后 `ci.cancel()` ✓ 吃掉事件 ⇒ 不再派发给控件 ✓；</li>
 *   <li>{@code ScreenKeyInputMixin} ✓：只保留**退格 / 回车 / Esc** ✓（那三个不是"字符"✓ 走不到这里 ✓）；
 *       ⚠ 它的"按键码翻译字符"已**删除** ✗（否则同一个字母会进两次 ✓）。</li>
 * </ul>
 * <p>⚠ **独立成包** ✗：万一这个 hook 匹配不上（`require = 1` ⇒ 该包被丢 ✗），
 * 也**不会影响**"英文输入 ＋ 过滤 ＋ 高亮"✓（§1117n 的教训 ✓）。闸门依旧是"聚焦 ＋ 当前是加热结构界面"✓。
 */
@Mixin(targets = "net.minecraft.client.KeyboardHandler")
public abstract class KeyboardHandlerImeMixin {

    @Inject(method = "m_90889_(JII)V", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$imeChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        try {
            if (!FluidSearch.isFocused()) return;
            if (!FluidSearch.isHeatingStructureScreen(Minecraft.getInstance().screen)) return;
            if (codePoint < ' ') return;                       // 控制字符不管 ✓
            String q = FluidSearch.getQuery();
            if (q.length() < 64) {
                FluidSearch.setQuery(q + new String(Character.toChars(codePoint)));
                FluidSearch.diag("字符(IME/键盘) '" + new String(Character.toChars(codePoint))
                        + "' ⇒ 查询='" + FluidSearch.getQuery() + "'");
            }
            ci.cancel();                                       // ★ 吃掉 ⇒ 不再派发给控件 ✓
        } catch (Throwable ignored) {
        }
    }
}
