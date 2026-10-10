package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.client.handler.UnnameableAmbience;
import com.mofengbaizhi.tinkersnewlife.client.renderer.UnnameableGlitchRenderer;
import com.mofengbaizhi.tinkersnewlife.client.renderer.UnnameableWhisperRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * 「不可名状」的画面表现层（仅客户端 HUD 叠加）：<b>信号干扰花屏 + 低语文字</b>。
 *
 * <h2>⚠ 为什么是 {@code IGuiOverlay} 而不是 {@code RenderGuiEvent.Post}</h2>
 * 最初这两个效果挂在 {@code RenderGuiEvent.Post} 上，结果在实机里<b>什么都不显示</b>
 * （音频正常，因为音频走的是 ClientTick，跟渲染无关）。
 * 本模组其它 HUD（咒力条、呪蔵提示、傀儡血条……）用的都是
 * {@code RegisterGuiOverlaysEvent#registerAboveAll} + {@code IGuiOverlay}，
 * 在实机里一直正常 —— 于是这两个效果也统一改走这条路（注册见
 * {@code ClientEventHandler#registerOverlays}），照着能跑的方式写。
 *
 * <h2>强度</h2>
 * 强度由 {@link UnnameableAmbience} 平滑给出：获得效果渐渐浮现、效果结束渐渐褪去，
 * 低于 {@link UnnameableAmbience#MIN_LEVEL} 就完全不画。
 * 打开任何界面（{@code mc.screen != null}）或隐藏 HUD（F1）时不叠加，避免遮挡交互控件。
 */
public final class UnnameableOverlay {

    private UnnameableOverlay() {
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null || mc.options.hideGui) return;

        float level = UnnameableAmbience.current();
        if (level <= UnnameableAmbience.MIN_LEVEL) return;

        int tick = mc.player.tickCount;
        UnnameableGlitchRenderer.render(graphics, width, height, tick, level);
        if (UnnameableWhisperRenderer.enabled()) {
            UnnameableWhisperRenderer.render(graphics, width, height, tick, level);
        }
    }
}
