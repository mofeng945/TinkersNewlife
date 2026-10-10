package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⭐⭐ §1248 熔炼炉／熔铸炉：<b>右上角那个"搜索按钮"的点击</b> ✗（用户口径 ✓）。
 *
 * <p>用户口径 ✓ 2026-10-10：「**能不能把熔炼炉搜索框改成首先在右上角显示一个可点击的搜索按键，
 * 按下后搜索框展开，否则不展开情况下不会开启输入**」✓
 *
 * <h2>为什么挂在 {@code AbstractContainerScreen} ✗</h2>
 * ⭐ `mouseClicked`（{@code m_6375_} ✓）**不是** `HeatingStructureScreen` 自己声明的方法 ✗
 * —— ⭐ 它声明在 ⭐ `AbstractContainerScreen` ✓（⭐ 本仓 §1117c 的血泪教训同款 ✓：
 * ⭐ 在"没声明它"的类里注入 ⇒ `could not find any targets` ✗ ⭐ 而 `require = 1` ⭐ 会把**整个 mixin 丢掉** ✗）。
 * <p>⭐ 挂在 ⭐ **HEAD ＋ `cancellable`** ✗ ⇒ ⭐ 点中按钮那一下 ⭐ **整个界面都不再处理** ✓
 * （⭐ 不会顺带点到槽位／流体条 ✓）。
 * <p>⚠ ⭐ 两道闸门 ✓：⭐ ① 当前界面必须是**加热结构界面**（⭐ 熔炼炉／熔铸炉同一个类 ✓）
 * ✗ ⭐ ② 命中 ⭐ `FluidSearch` 记下的**按钮矩形** ✓ ⇒ ⭐ 别的容器界面**零影响** ✓。
 *
 * <p>⚠ ⭐ 按钮矩形由 ⭐ `GuiSmelteryTankSearchMixin` **每帧写入** ✗
 * （⭐ 那个 mixin 手上才有流体列的 `x／y／width` ✓ ⭐ 见 `FluidSearch` 里那段说明 ✓）；
 * ⭐ 收起状态下它被写成**屏幕外** ✗ ⇒ ⭐ 怎么点都点不中 ✓（⭐ 不可能"隔着空气聚焦" ✓）。
 */
@Mixin(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class)
public abstract class ContainerClickSearchMixin {

    @Inject(method = "m_6375_(DDI)Z", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void tnl$searchButtonClick(double mouseX, double mouseY, int button,
                                       CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!FluidSearch.isHeatingStructureScreen(net.minecraft.client.Minecraft.getInstance().screen)) {
                return;
            }
            if (FluidSearch.hitButton(mouseX, mouseY)) {
                FluidSearch.toggleExpanded();     // ⭐ 展开 ⇒ ⭐ 同时聚焦 ✓；⭐ 收起 ⇒ ⭐ 同时清词 ＋ 取消聚焦 ✓
                cir.setReturnValue(true);         // ⭐ 吃掉这一下 ✓
            }
        } catch (Throwable ignored) {
            // fail-safe：⭐ 最多"点不动按钮" ✗ ⭐ 绝不影响界面本身 ✓
        }
    }
}
