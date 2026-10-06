package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §1116 / §1117f <b>熔炼炉 · 熔铸炉 流体搜索框：打开界面即"自动聚焦"</b> ✓。
 *
 * <h2>为什么彻底砍掉"点击聚焦"✗（三轮踩坑换来的结论 ✓）</h2>
 * 原本设计是"点一下搜索框才聚焦" ✓ ⇒ 需要把**鼠标绝对坐标**和"画在面板内相对坐标里的框"对齐 ✗
 * ⇒ 连踩三次 ✗：① shadow 原版 `leftPos` ⇒ mixin 被丢 ✗；② `getGuiLeft()/getGuiTop()` ⇒ 实测未生效 ✗；
 * ③ 读绘制姿态的平移量 ⇒ 判定框变得过大（日志里连 (277,78)、(308,81) 都 `命中=true` ✗）。
 *
 * <p>⇒ 现在**不再需要任何坐标换算** ✓：打开界面就 `focused = true` ✓，**直接打字即可** ✓
 * （用户实测日志已证明"聚焦"这一环本身是可用的 ✓：`命中=true ⇒ 聚焦=true` ✓）。
 *
 * <p>⚠ 本类现在**只做一件事** ✗：init 时把搜索框设为聚焦 ✓。
 * 键盘输入在 {@link ScreenSearchInputMixin} ✓；画框/过滤/高亮在 {@link GuiSmelteryTankSearchMixin} ✓。
 *
 * <h2>两条踩过的坑（保留在此 ✓）</h2>
 * <ol>
 *   <li><b>绝不 shadow 原版成员</b> ✗ —— 原版字段运行期是混淆名 ⇒ {@code InvalidMixinException} ⇒
 *       **整个 mixin 被丢弃** ✗（WARN 级 ✓ 表现为"没效果"而非崩溃 ✓）；</li>
 *   <li><b>`@Inject` 只匹配目标类自己声明的方法</b> ✗ —— `keyPressed`/`charTyped` 声明在 `Screen` ✓
 *       ⇒ 在本类注入会 {@code could not find any targets} ⇒ 同样整包被丢 ✗。</li>
 * </ol>
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.client.screen.HeatingStructureScreen")
public abstract class HeatingStructureScreenSearchMixin {

    /**
     * 打开界面 ⇒ **自动聚焦搜索框** ✓（用户直接打字即可 ✓ 无需点击 ✓）。
     * <p>⚠ 只有**退格/回车/Esc** 会被吃 ✓（见 {@link ScreenSearchInputMixin} ✓）——
     * 其余按键一律放行 ✓ ⇒ `E` 关界面、匠魂自己的快捷键都**不受影响** ✗。
     */
    @Inject(method = "m_181908_", at = @At("TAIL"), require = 1, remap = false)
    private void tnl$onInit(CallbackInfo ci) {
        try {
            FluidSearch.setFocused(true);
            FluidSearch.diag("界面打开 ⇒ 自动聚焦=true（查询='" + FluidSearch.getQuery() + "'）");
        } catch (Throwable ignored) {
        }
    }
}
