package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §1118a 熔炼炉 / 熔铸炉：打开界面即**自动聚焦**搜索框 ✓（本类只剩这一件事 ✗）。
 *
 * <h2>为什么"自动聚焦"而不是"点框才聚焦" ✗</h2>
 * "点框聚焦"要把**鼠标绝对坐标**与"画在面板内相对坐标里的框"严格对齐 ✗ —— 为此连踩三坑 ✓
 * （① shadow 原版 `leftPos` ⇒ 整个 mixin 被丢 ✗；② `getGuiLeft()/getGuiTop()` ⇒ 实测未生效 ✗；
 * ③ 读绘制姿态平移量 ⇒ 判定框过大 ✗ 日志里连 (277,78) 都判命中 ✗）
 * ⇒ 干脆不要这个交互 ✓：打开就聚焦 ✓ 直接打字 ✓；按 回车/Esc 取消聚焦后所有按键放行 ✓。
 *
 * <h2>输入由谁处理 ✓</h2>
 * 退格/回车/Esc ⇒ {@link ScreenKeyInputMixin} ✓；字符（含**输入法汉字** ✓）⇒ {@link KeyboardHandlerImeMixin} ✓；
 * 画框/过滤/高亮 ⇒ {@code GuiSmelteryTankSearchMixin} ✓（它同时持有 `GuiGraphics` 与流体列坐标 ✓）。
 *
 * <p>⚠ `m_181908_` 是**每 tick 都被调用**的方法 ✗（§1117k 实测 ✓）⇒ 这里只做幂等的"重申聚焦" ✓。
 * <p>⚠ ⚠ 顺带记一条：往**每 tick 调用**的方法里塞日志/打印必须去重 ✗ —— 我曾因它每秒刷 20 条把诊断额度吃光 ✗
 * （§1117k ✓ 已随 §1118a 全部移除 ✓）。
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.client.screen.HeatingStructureScreen")
public abstract class HeatingStructureScreenSearchMixin {

    @Inject(method = "m_181908_", at = @At("TAIL"), require = 1, remap = false)
    private void tnl$keepFocus(CallbackInfo ci) {
        try {
            FluidSearch.setFocused(true);
        } catch (Throwable ignored) {
        }
    }
}
