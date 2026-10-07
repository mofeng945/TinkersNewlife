package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §1117n <b>熔炼炉 / 熔铸炉：打开界面即自动聚焦搜索框</b> ✓（本类只剩这一件事 ✗）。
 *
 * <h2>为什么"自动聚焦"而不是"点框才聚焦" ✗</h2>
 * "点框聚焦"需要把**鼠标绝对坐标**与"画在面板内相对坐标里的框"严格对齐 ✗ ——
 * 为它连踩三坑 ✓（① shadow 原版 `leftPos` ⇒ 整个 mixin 被丢 ✗；② `getGuiLeft()/getGuiTop()` ⇒ 实测未生效 ✗；
 * ③ 读绘制姿态平移量 ⇒ 判定框过大 ✗ 日志：连 (277,78) 都判命中 ✗）。
 * ⇒ 干脆**不要这个交互** ✓：打开界面就聚焦 ✓，用户**直接打字** ✓。
 *
 * <h2>由谁处理输入 ✓</h2>
 * {@link ScreenKeyInputMixin} ✓ —— 它挂在**原版 `Screen`** 上（SRG 名 `m_7933_` ＋ `remap = false` ✓ 实证可用 ✓），
 * 因为 `keyPressed` **不是** `HeatingStructureScreen` 自己声明的方法 ✗
 * ⇒ 在本类注入会 `could not find any targets` ✗（§1117c 日志实证 ✓）。
 *
 * <p>⚠ 本类**只做聚焦** ✗：不创建控件（`EditBox` 在此界面拿不到原版派发 ✗ 见 §1117m/n ✓）、
 * 不画东西（画框在 {@code GuiSmelteryTankSearchMixin} ✓ 它同时持有 `GuiGraphics` 与流体列坐标 ✓）。
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.client.screen.HeatingStructureScreen")
public abstract class HeatingStructureScreenSearchMixin {

    /**
     * ⚠ `m_181908_` 是**每 tick 都被调用**的方法 ✗（§1117k 实测 ✓）⇒ 这里只把聚焦**重申**为 true ✓
     * （幂等 ✓ 无副作用 ✓ 也让"Esc 取消聚焦"在下次打开/刷新时恢复 ✓）。
     */
    @Inject(method = "m_181908_", at = @At("TAIL"), require = 1, remap = false)
    private void tnl$keepFocus(CallbackInfo ci) {
        try {
            FluidSearch.setFocused(true);
            // §1117r 打开界面时跑一次拼音自检 ✓（诊断去重 ⇒ 只记一次 ✓）
            FluidSearch.selfTestPinyin();
        } catch (Throwable ignored) {
        }
    }
}
