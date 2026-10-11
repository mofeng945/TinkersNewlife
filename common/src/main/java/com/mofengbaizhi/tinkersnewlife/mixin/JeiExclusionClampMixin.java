package com.mofengbaizhi.tinkersnewlife.mixin;

import mezz.jei.library.gui.helpers.ScreenHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/**
 * §1309 兜住 JEI 的崩溃："width must be >= 0"。
 *
 * <p>根因（有崩溃栈为证）：Mantle 的 {@code JEIPlugin$MultiModuleContainerHandler} 会给
 * {@code MultiModuleScreen} 的子类（匠魂工匠台/工匠站）返回**负宽度**的矩形，
 * 而 JEI 的 {@code ImmutableRect2i} 直接抛 IllegalArgumentException ⇒ 渲染界面即崩。
 *
 * <p>这里在 {@code ScreenHelper#getGuiExclusionAreas} 返回处把**负值钳成 0**，
 * 对所有界面通用，且不改任何 mod 的布局数据（只是让 JEI 不再收到非法值）。
 * ⚠ 需要 JEI 的类在 mixin 类路径上（本仓已 compileOnly ✓）⇒ 未装 JEI 时本 mixin 不加载 ✓。
 */
@Mixin(value = ScreenHelper.class, remap = false)
public class JeiExclusionClampMixin {

    @Inject(method = "getGuiExclusionAreas", at = @At("RETURN"), cancellable = true, remap = false)
    private void tnl$clampNegativeRects(Screen screen, CallbackInfoReturnable<Stream<Rect2i>> cir) {
        try {
            Stream<Rect2i> stream = cir.getReturnValue();
            if (stream == null) {
                return;
            }
            cir.setReturnValue(stream.map(r -> {
                if (r == null || (r.getWidth() >= 0 && r.getHeight() >= 0)) {
                    return r;
                }
                return new Rect2i(r.getX(), r.getY(), Math.max(0, r.getWidth()), Math.max(0, r.getHeight()));
            }));
        } catch (Throwable ignored) {
        }
    }
}