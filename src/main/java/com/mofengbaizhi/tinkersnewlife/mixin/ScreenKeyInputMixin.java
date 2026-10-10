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

    /**
     * ⭐⭐ §1249 <b>搜索按钮的点击（挂 {@code Screen} ✓ 最保险的那一层）</b> ✗✗
     *
     * <p>⚠ 用户实测 ✓：「**点搜索按键没反应**」✓ ⇒ ⭐ 两个可能 ✓：
     * ① ⭐ 坐标空间不一致（⭐ 见 `FluidSearch.hitButton` 的双解释 ✓）；
     * ② ⭐ 我原来挂在 ⭐ `AbstractContainerScreen` ✗ ⭐ 而匠魂那个界面**未必**走到那一层 ✓
     *   ⇒ ⭐ 现在改挂 ⭐ `Screen` ✗ —— ⭐ 那是 ⭐ **已经被证明会被调到**的一层 ✓
     *     （⭐ 本类的 `m_7933_`（按键 ✓）就在这儿生效 ✓ ⭐ 说明界面的按键／点击都会走 `Screen` ✓）。
     *
     * <p>⚠ 顺带打一条 **INFO 诊断** ✗（⭐ 每次点击一行 ✗ ⭐ 不刷屏 ✓）：
     * ⭐ 把"鼠标坐标 ✗ 按钮矩形 ✗ 姿态平移 ✗ 命中与否"全打出来 ✓
     * ⇒ ⭐ 万一点不中 ⭐ 看一眼就知道是坐标还是钩子 ✓（⭐ 不再猜 ✓）。
     */
    @Inject(method = "m_6375_(DDI)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$searchButtonClick(double mouseX, double mouseY, int button,
                                       CallbackInfoReturnable<Boolean> cir) {
        try {
            if (button != 0) return;
            if (!FluidSearch.isHeatingStructureScreen(this)) return;
            boolean hit = FluidSearch.hitButton(mouseX, mouseY);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[搜索按钮] 点击 mouse=({}, {}) 按钮=({}, {}) {}x{} 命中={} 展开={}",
                    (int) mouseX, (int) mouseY,
                    FluidSearch.buttonX(), FluidSearch.buttonY(),
                    FluidSearch.buttonW(), FluidSearch.buttonH(),
                    hit, FluidSearch.isExpanded());
            if (hit) {
                FluidSearch.toggleExpanded();
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }

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
