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

            // ⭐ §1250 **诊断**（⭐ 每次按键一行 ✗ ⭐ 只在炉子界面 ✓）：⭐ 看 `m_7933_` 到底有没有被调到 ✓
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[搜索按键] keyPressed key={} 当前词=「{}」", keyCode, FluidSearch.getQuery());
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                FluidSearch.backspace();       // ⭐ §1250 抽成公共方法 ＋ 去重 ✓
                cir.setReturnValue(true);
                return;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                FluidSearch.setFocused(false);      // 取消聚焦 ⇒ 之后按键全部放行 ✓（不 cancel ✗）
            }

            // ⭐⭐ 「打开/关闭背包」键（默认 E ✓）：⭐ **聚焦时必须吃掉** ✗
            //   —— ⚠ 用户实测：「匠魂炉子搜索框按 e 会退出界面」✗
            //     ⇒ 根因就是这里原先把它放行 ✓ ⇒ 落到原版 `Screen#m_7933_` ⇒ 原版执行"开/关背包"⇒ **整个界面被关掉** ✗
            //     ⇒ 想在搜索框里打 "e"（例如搜 "netherite"）就打不了 ✗ ✓。
            //   ⭐ **吃掉它是安全的** ✓ —— 字符本身由 {@link KeyboardHandlerImeMixin}（`charTyped` ✓）负责收 ✓
            //     ⚠ 反证在 §1117s：当年在**这里**翻译字符码导致"一个字进两次" ✗
            //       ⇒ 说明 `keyPressed` 返回 true **并不会**阻止 `charTyped` ✓ ⇒ 这里只管吞键 ✗ 不管输字 ✓。
            //   ⚠ 按本仓 §813 的规矩**不写死 E** ✗ —— 比对真实的 `key.inventory` 映射 ✓ 玩家改键也跟得上 ✓。
            //   ⚠ 只在**聚焦时**生效 ✓（取消聚焦后 E 仍可关界面 ✓ 保留原版习惯 ✓）。
            var options = net.minecraft.client.Minecraft.getInstance().options;
            if (options != null && options.keyInventory != null
                    && options.keyInventory.matches(keyCode, scanCode)) {
                cir.setReturnValue(true);
                return;
            }

            // 其余按键一律放行 ✓（WASD 走路、匠魂快捷键都照旧 ✓ —— 用户口径「别禁用 wasd」✓）
        } catch (Throwable ignored) {
        }
    }
}
