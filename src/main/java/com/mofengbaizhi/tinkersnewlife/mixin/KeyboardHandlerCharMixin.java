package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §1117i <b>让中文（输入法）也能进搜索框</b> ✓（用户口径：「能输入英文，中文不行」✗）。
 *
 * <h2>为什么不能挂在 `Screen` 上 ✗（实测 ✓）</h2>
 * 从 **SRG jar 反编译确认** ✓：`Screen` 里**没有任何** `(char,int)` 方法 ✗
 * ⇒ 之前那版只能从**按键码翻译英文** ✓（所以英文能用 ✓ 中文不行 ✗）。
 *
 * <h2>中文码点实际走哪条路 ✓（从 `Screen` 的反编译字符串里挖出来的 ✓）</h2>
 * <pre>
 * Screen.m_96579_(() -&gt; KeyboardHandler.m_90907_((GuiEventListener)$$3, p_90891_, p_90892_),
 *                 "charTyped event handler", …)
 * </pre>
 * ⇒ 输入法提交的**字符码点**最终由 `KeyboardHandler.m_90907_` 派发给控件 ✓
 * ⇒ 它的签名从 class 常量池读到的是 ✓：`(L…GuiEventListener;II)V` ✓
 * （＝ 控件 ＋ **字符码点** ＋ 修饰键 ✓ 返回 **void** ✓ ⇒ 正好用 `CallbackInfo` ✓ 不用猜返回类型 ✓）。
 *
 * <h2>为什么单独一个 mixin ✗</h2>
 * ⚠ **故意与 `ScreenSearchInputMixin` 分开** ✓：万一这个 hook 匹配不上（`require = 1` ⇒ 整包会被丢 ✗），
 * 也**不会连累已经能用的英文/ASCII 输入** ✓ —— 两个 mixin 各自独立失败 ✓（本轮的血泪教训 ✓ 见 §1117 ✓）。
 *
 * <p>闸门照旧两道 ✓：搜索框聚焦 ✓ ＋ 当前界面是加热结构界面 ✓ ⇒ 都过才收字符并**吃掉事件** ✓
 * （免得同时再发给控件 ✗）。
 */
@Mixin(targets = "net.minecraft.client.KeyboardHandler")
public abstract class KeyboardHandlerCharMixin {

    /**
     * 中文/输入法字符 ⇒ 追加进搜索查询 ✓。
     * <p>⚠ §1117k <b>必须写完整描述符</b> ✗：上一版只写了方法名 `m_90907_` ⇒
     * 日志实证 {@code Critical injection failure: @Inject annotation on tnl$searchCharTyped could not find any targets} ✗
     * ⇒ 现在补上参数描述符（**与 class 常量池里读到的一模一样** ✓）：
     * {@code (Lnet/minecraft/client/gui/components/events/GuiEventListener;II)V} ✓
     * —— 因为运行时那个类里可能还有**同名重载** ✓ ⇒ 只给名字匹配不上 ✓。
     */
    @Inject(method = "m_90907_(Lnet/minecraft/client/gui/components/events/GuiEventListener;II)V",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void tnl$searchCharTyped(GuiEventListener listener, int codePoint, int modifiers, CallbackInfo ci) {
        try {
            if (!FluidSearch.isFocused()) return;
            if (!FluidSearch.isHeatingStructureScreen(Minecraft.getInstance().screen)) return;
            if (codePoint < ' ' || codePoint == 127) return;      // 控制字符不管 ✓
            String q = FluidSearch.getQuery();
            if (q.length() < 64) {
                FluidSearch.setQuery(q + (char) codePoint);
                FluidSearch.diag("charTyped(IME) '" + (char) codePoint + "' ⇒ 查询='" + FluidSearch.getQuery() + "'");
            }
            ci.cancel();                                          // 吃掉 ✓ 不再发给控件 ✓
        } catch (Throwable ignored) {
        }
    }
}
