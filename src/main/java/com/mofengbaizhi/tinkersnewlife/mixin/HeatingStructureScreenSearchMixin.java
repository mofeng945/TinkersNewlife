package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1116 / §1117c <b>熔炼炉 · 熔铸炉 流体搜索框：本类只管"点击聚焦"与"打开时重置"</b> ✓。
 *
 * <h2>分工（三个 mixin ＋ 一个工具类 ✓ 各司其职 ✗ 别混）</h2>
 * <ul>
 *   <li>{@link GuiSmelteryTankSearchMixin} ✓：**画**搜索框（它同时持有 `GuiGraphics` 与匠魂流体列坐标 ✓）、
 *       过滤（不匹配的高度置 0 ✓）、命中描边 ✓；</li>
 *   <li>{@link ScreenSearchInputMixin} ✓：**键盘输入**（挂在原版 `Screen` 上 ✓）；</li>
 *   <li><b>本类</b> ✓：只碰 {@code HeatingStructureScreen} **自己声明**的两个方法
 *       —— {@code m_6375_(DDI)Z}（mouseClicked ✓ 点击聚焦 ✓）与 {@code m_181908_()}（init ✓ 打开时重置聚焦 ✓）。</li>
 * </ul>
 *
 * <h2>⚠ 两条踩过的坑（都写在注释里 ✓ 免得下次再犯 ✗）</h2>
 * <ol>
 *   <li><b>绝不能 shadow 原版成员</b> ✗（§1117 ✓）：原版字段运行期是混淆名 ✗ ⇒
 *       {@code @Shadow field leftPos was not located} ⇒ **整个 mixin 被丢弃** ✗。
 *       需要原版数据时**先找原版公开方法** ✓（本类的 {@code getGuiLeft()/getGuiTop()} 就是这么用的 ✓）。</li>
 *   <li><b>`@Inject` 只匹配"目标类自己声明"的方法</b> ✗（§1117c ✓）：`keyPressed`/`charTyped`
 *       声明在 `Screen` ✓ ⇒ 在这里注入会 {@code could not find any targets} ⇒ 整包被丢弃 ✗
 *       ⇒ 所以键盘那部分搬到了 {@link ScreenSearchInputMixin} ✓。</li>
 * </ol>
 *
 * <p>⚠ 另外：**不要**在这里 shadow 匠魂的 {@code tank} 字段 ✗（本类已经不需要它了 ✓
 * —— 坐标由 `FluidSearch` 的矩形静态字段提供 ✓，而那是 `GuiSmelteryTankSearchMixin` 每帧写进去的 ✓）。
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.client.screen.HeatingStructureScreen")
public abstract class HeatingStructureScreenSearchMixin {

    /**
     * 搜索框区域 ✓ —— 坐标由 `GuiSmelteryTankSearchMixin` 每帧写进 {@link FluidSearch#setBoxRect} ✓。
     *
     * <p>⚠ §1117b <b>必须做"面板内 → 屏幕绝对"的换算</b> ✗ —— 用户实测「点击无法输入文字」✓：
     * 匠魂的模块坐标是**面板内相对坐标** ✓（渲染前整体 `translate(leftPos, topPos)` ✓），
     * 而 {@code mouseClicked} 给的是**屏幕绝对坐标** ✗ ⇒ 不换算就永远判成"点在框外" ✓。
     * 换算用原版**公开方法** {@code getGuiLeft()/getGuiTop()} ✓（编译期官方名 ✓ 运行期 Forge 重映射 ✓
     * ⇒ **不用 shadow 原版字段** ✓ 见上面坑 ①）。
     */
    @Unique private boolean tnl$insideBox(double mx, double my) {
        int x = FluidSearch.boxX();
        int y = FluidSearch.boxY();
        int w = FluidSearch.boxW();
        int h = FluidSearch.boxH();
        if (net.minecraft.client.Minecraft.getInstance().screen
                instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> sc) {
            x += sc.getGuiLeft();
            y += sc.getGuiTop();
        }
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 点击：框内 ⇒ 聚焦并**吃掉**这次点击 ✓（否则会顺手抽走一格流体 ✗）；框外 ⇒ 失焦但**放行** ✓ */
    @Inject(method = "m_6375_(DDI)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$onClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean inside = tnl$insideBox(mouseX, mouseY);
            FluidSearch.setFocused(inside);
            // §1117d 临时诊断 ✓：把"鼠标点在哪、框在哪、有没有命中"写进日志 ✓（定位完删 ✗）
            FluidSearch.diag("点击 mouse=(" + (int) mouseX + "," + (int) mouseY + ") 框=("
                    + FluidSearch.boxX() + "," + FluidSearch.boxY() + "," + FluidSearch.boxW() + "x"
                    + FluidSearch.boxH() + ") 命中=" + inside + " ⇒ 聚焦=" + FluidSearch.isFocused());
            if (inside) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 打开界面时：从"未聚焦"开始 ✓（查询文本保留 ✓ 方便反复看 ✓） */
    @Inject(method = "m_181908_", at = @At("TAIL"), require = 1, remap = false)
    private void tnl$onInit(CallbackInfo ci) {
        try {
            FluidSearch.setFocused(false);
        } catch (Throwable ignored) {
        }
    }
}
