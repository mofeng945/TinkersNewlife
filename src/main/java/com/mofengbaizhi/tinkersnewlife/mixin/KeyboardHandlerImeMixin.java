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

    /**
     * ⭐⭐⭐⭐ §1251 <b>真正收键盘的那一层：⭐ `KeyboardHandler#m_90893_(JIII)V`</b> ✗✗
     *
     * <p>⭐⭐ 用户实测逼出来的 ✓（2026-10-10）：
     * 「**还是删不掉：我希望输入的时候聚焦输入事件而不是键盘事件，关闭搜索框是回到键盘事件，
     *   现在我输入时一按 e 就退出 ui 了**」✓
     *
     * <h2>⚠ 根因（⭐ 这次是**确证** ✗ 不是猜 ✓）</h2>
     * ⚠ ⭐ 退格与"吃 e" ⭐ **两件事都失效** ✗ ⭐ 而它们都在 ⭐ `ScreenKeyInputMixin`（⭐ 挂 `Screen#keyPressed` ✓）✓
     * ⇒ ⭐ 结论 ✗：⭐ 匠魂那个界面的 ⭐ **`Screen#keyPressed` 根本没被调到** ✓
     * ⭐ 而"能打字"只证明 ⭐ `charTyped`（⭐ 挂在 `KeyboardHandler` ✓）**通** ✓
     * ⚠ ⭐ 两者是 ⭐ **独立派发** ✗ ⇒ ⭐ 一个通不代表另一个通 ✓ ✓。
     *
     * <h2>⭐ 所以挂哪儿 ✗</h2>
     * ⭐ `KeyboardHandler#m_90893_(JIII)V` ✗ —— ⭐ 那是 ⭐ **GLFW 按键回调的真身** ✓
     * （⭐ 与 `charTyped` 的 `m_90889_` ⭐ 同一个类 ✗ ⭐ 反编译实测 ✓：
     * {@code public void m_90893_(long window, int key, int scancode, int action, int modifiers)} ✓
     * ⭐ 见本仓 §1118a 记录的那个 SRG jar 路径 ✓）
     * ⇒ ⭐ **它一定被调到** ✓ ⭐ 因为字符那条就是从这里旁边的 `charTyped` 进来的 ✓ ✓。
     *
     * <h2>⭐ 语义（⭐ 正是用户要的 ✓）</h2>
     * <ul>
     *   <li>⭐ **展开时** ✗：⭐ 退格删字 ✓ ⭐ 回车／Esc **收起搜索框**（⭐ ⇒ ⭐ 键盘事件立刻回到游戏 ✓）
     *       ✗ ⭐ 并把"开背包／丢东西／副手交换"那几个键 **吃掉** ✓（⭐ 字符仍由 `charTyped` 进 ✓ ——
     *       ⭐ 本仓 §1117s 已证 ⭐ `keyPressed` 返回 true **不会**阻止 `charTyped` ✓）；</li>
     *   <li>⭐ **收起时** ✗：⭐ 本钩子**直接放行** ✓ ⇒ ⭐ 按 E 照常开/关背包 ✓ ⭐ 走路／快捷键一律照旧 ✓。</li>
     * </ul>
     * ⚠ ⭐ 不写死 E ✗ —— ⭐ 比真实的 ⭐ `options.keyInventory／keyDrop／keySwapOffhand` 映射 ✓
     * （⭐ 本仓 §813 的规矩：⭐ 改了键也要跟得上 ✓）。
     */
    @Inject(method = "m_90893_(JIII)V", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$searchKeyReal(long window, int key, int scancode, int action, int modifiers,
                                   CallbackInfo ci) {
        try {
            if (action != 1 && action != 2) return;                  // ⭐ 只处理"按下"与"重复" ✓（重复 ⇒ 按住退格连删 ✓）
            if (!FluidSearch.isExpanded() || !FluidSearch.isFocused()) return;
            if (!FluidSearch.isHeatingStructureScreen(Minecraft.getInstance().screen)) return;
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[搜索按键·真身] key={} scan={} action={} 词=「{}」",
                    key, scancode, action, FluidSearch.getQuery());
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                FluidSearch.backspace();
                ci.cancel();
                return;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                FluidSearch.setExpanded(false);                      // ⭐ 收起 ⇒ ⭐ 键盘事件回到游戏 ✓
                ci.cancel();
                return;
            }
            var options = Minecraft.getInstance().options;
            if (options != null && (tnl$km(options.keyInventory, key, scancode)
                    || tnl$km(options.keyDrop, key, scancode)
                    || tnl$km(options.keySwapOffhand, key, scancode))) {
                ci.cancel();                                        // ⭐ 吃掉 ⇒ ⭐ 不开背包／不掉东西 ✓
            }
        } catch (Throwable ignored) {
        }
    }

    /** ⭐ 键位映射是否匹配 ✗（⭐ null 安全 ✓ ⭐ 不写死字母 ✓） */
    private static boolean tnl$km(net.minecraft.client.KeyMapping km, int key, int scancode) {
        return km != null && km.matches(key, scancode);
    }

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
