package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §1118a 熔炼炉 / 熔铸炉 搜索框：<b>字符输入（含中文输入法）</b> ✓。
 *
 * <h2>真身怎么定位的 ✓（三步实测 ✓ 不是猜 ✓）</h2>
 * 1. 用 1.20.1 **官方映射**逐行定位 ✓：
 *    <pre>7628 | void charTyped(long,int,int) -> a    ← 所属类: net.minecraft.client.KeyboardHandler ✓</pre>
 * 2. 本机**没有 `javap`** ✗（`javapath\java.exe` 只是 JRE 包装 ✓）⇒ 用 `java -jar tools\cfr.jar` 反编译 SRG jar ✓
 *    ⚠ 且先打印头部**验真** ✓（上一轮读到坏文件导致结论全错 ✗）；
 * 3. 得到实现 ✓：
 *    <pre>private void m_90889_(long window, int codePoint, int modifiers) {
 *        if (Character.charCount(codePoint) == 1) { for (char c : Character.toChars(codePoint)) { … } }</pre>
 * ⇒ **SRG 名 ＝ `m_90889_`** ✓✓（⚠ 之前挂的 `m_90907_` 只是"派发给子控件"的助手 ✗ 描述符 `(…;II)V` 已露馅 ✗）。
 *
 * <h2>为什么必须独立成包 ✗</h2>
 * `require = 1` ⇒ 万一这个 hook 匹配不上，**只丢本包** ✓ ⇒ 英文输入/过滤/高亮**都不受影响** ✓（§1117n 教训 ✓）。
 * 闸门两道 ✓：搜索框聚焦 ✓ ＋ 当前界面是加热结构界面 ✓。
 */
@Mixin(targets = "net.minecraft.client.KeyboardHandler")
public abstract class KeyboardHandlerImeMixin {

    @Inject(method = "m_90889_(JII)V", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$imeChar(long window, int codePoint, int modifiers, CallbackInfo ci) {
        try {
            if (!FluidSearch.isFocused()) return;
            if (!FluidSearch.isHeatingStructureScreen(Minecraft.getInstance().screen)) return;
            // ⭐ §1250 **诊断**（⭐ 每次一个 codePoint 一行 ✗ ⭐ 只在炉子界面 ✓ 不刷屏 ✓）
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[搜索输入] charTyped codePoint={}（'{}'）当前词=「{}」",
                    codePoint, codePoint >= ' ' ? String.valueOf((char) codePoint) : "控制符", FluidSearch.getQuery());
            // ⭐⭐ §1250 **退格也从这里收** ✗（⭐ 用户实测："能打字但退格没用" ✓
            //   ⇒ ⭐ 打字走的是本方法 ✓ ⭐ 那么退格**很可能也只从这里来** ✓（⭐ 控制字符 8 ＝ 退格 ✗ 127 ＝ Delete ✓））
            if (codePoint == 8 || codePoint == 127) {
                FluidSearch.backspace();
                ci.cancel();
                return;
            }
            if (codePoint < ' ') return;                       // 其余控制字符不管 ✓
            String q = FluidSearch.getQuery();
            if (q.length() < 64) {
                FluidSearch.setQuery(q + new String(Character.toChars(codePoint)));
            }
            ci.cancel();                                       // ★ 吃掉 ⇒ 不再派发给控件 ✓
        } catch (Throwable ignored) {
        }
    }
}
