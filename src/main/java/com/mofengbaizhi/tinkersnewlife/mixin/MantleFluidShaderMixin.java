package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.integration.iris.IrisCompat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 匠魂（TConstruct / Mantle）流体在<b>光影包</b>下整条看不见 ⇒ 用原版着色器兜底。
 *
 * <h3>现象</h3>
 * 用户反馈：装光影包（Oculus + Complementary）时，匠魂本体的<b>冶炼炉 / 焦黑储罐 / 浇筑台</b>
 * 以及配套的排液口、通道、液量表里**流体整个看不见，只剩空壳** ✗；关掉光影就正常 ✓。
 *
 * <h3>根因</h3>
 * 匠魂的流体不是用原版着色器画的：Mantle 自带核心着色器 {@code assets/mantle/shaders/core/fluid.*}
 * （{@code MantleShaders#registerShaders} 里 {@code new ShaderInstance(..., Mantle.getResource("fluid"), ...)}），
 * 而所有流体渲染类型都用它 —— {@code TinkerRenderTypes.SMELTERY_FLUID}（冶炼炉，{@code SmelteryTankRenderer}）
 * 与 {@code MantleRenderTypes.FLUID}（浇筑台/储罐 {@code RenderUtils.renderFluid}、排液口 {@code FaucetBlockEntityRenderer}、
 * 通道 {@code ChannelBlockEntityRenderer}、液量表 {@code GaugeBlockEntityRenderer} …），
 * shader state 都是 {@code new ShaderStateShard(MantleShaders::getConfiguredFluidShader)}。
 * <p>光影包只替换 {@code GameRenderer} 上那些原版 shader getter 返回的程序（Oculus 的
 * {@code MixinGameRenderer} 把 40 多个 getter 全部 HEAD 注入），**模组自己 new 的 ShaderInstance 它管不到**
 * ⇒ 这些流体几何被画在光影包 gbuffer 流程之外 ⇒ 看不见 ✗。
 * <p>Mantle 作者也承认这点：配置项 {@code enableFluidFogFix} 的注释原文就是
 * “This config option is provided as the <b>fix breaks shaders</b>, and slightly broken is better than fully broken.”
 *
 * <h3>做法</h3>
 * 在 {@code MantleShaders#getConfiguredFluidShader} 的 HEAD 处：**只要有光影包在生效**，
 * 就返回 <b>Mantle 自己那条原版回退</b> {@code GameRenderer.getPositionColorTexLightmapShader()}
 * （见 Mantle 源码：{@code Config.ENABLE_FLUID_FOG_FIX.get() ? fluidShader : GameRenderer.getPositionColorTexLightmapShader()}）
 * —— 这条正是「把 {@code enableFluidFogFix} 关掉」走的路 ✓，而且它的属性集
 * （Position / Color / UV0 / UV2 = {@code POSITION_COLOR_TEX_LIGHTMAP}）与 Mantle 自己的
 * {@code fluid.json} 完全一致 ⇒ 不会有属性错位 ✓；同时它是**原版 getter**，光影包会接管它
 * （Oculus `MixinGameRenderer` 把 {@code getPositionColorTexLightmapShader} 也列进注入名单 ✓）
 * ⇒ 流体回来了 ✓。
 * <p>没开光影时**一个字节都不改**：Mantle 的流体雾效修正照旧生效 ✓
 * ⇒ 比让用户去改 {@code config/mantle-client.toml} 更好：不用动配置，也不会在无光影时丢掉雾效修正 ✓。
 * <p>客户端专用（{@code MantleShaders} 本身就是客户端类），已登记在 {@code tinkersnewlife.mixins.json} 的
 * {@code client} 数组里 ✓；{@code require = 1} 让万一 Mantle 改了方法名时**在日志里明确报错**而不是静默失效 ✓。
 */
@Mixin(targets = "slimeknights.mantle.client.render.MantleShaders", remap = false)
public class MantleFluidShaderMixin {

    @Inject(method = "getConfiguredFluidShader", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void tinkersnewlife$vanillaFluidShaderUnderShaderpack(CallbackInfoReturnable<ShaderInstance> cir) {
        if (IrisCompat.isShaderPackInUse()) {
            // 与 Mantle 自己 enableFluidFogFix=false 时走的是同一个方法（属性集与 fluid.json 一致）
            cir.setReturnValue(GameRenderer.getPositionColorTexLightmapShader());
        }
    }
}
