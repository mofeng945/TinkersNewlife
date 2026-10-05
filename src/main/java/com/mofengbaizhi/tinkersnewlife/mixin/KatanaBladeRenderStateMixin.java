package com.mofengbaizhi.tinkersnewlife.mixin;

// 移植自 TiCEX (MIT): moffy.ticex.mixin.slashblade.BladeRenderStateMixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.TicEXSBRenderers;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.KatanaContexts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * <b>本 mixin 是整条"按材料渲染刀身"的入口</b> —— 移植 TiCEX {@code BladeRenderStateMixin}（MIT）。
 *
 * <p>把本体的 {@code BladeRenderState#renderOverrided(…9 参…)} <b>整段包起来</b> ✓，
 * 交给 {@link TicEXSBRenderers#renderWrapped} ✓ —— 于是"按材料换贴图/换颜色"就发生在
 * **本体自己的渲染流程里** ✓ 而不用改本体一行代码 ✓。
 *
 * <p>外加一处 {@code MultiBufferSource#getBuffer} 的改写 ✓：当上下文里放了"要替换的顶点缓冲"时改用它 ✓
 * （TiCEX 用它把顶点写进自己准备的 buffer ✓；我们这轮没有 shader 支线 ⇒ 该上下文**始终为空** ✓
 * ⇒ 恒走原方法 ✓ 与原版行为一致 ✓，但保留这段逻辑以便后续补 shader 时不用再动 mixin ✓）。
 *
 * <p>⚠ <b>与 TiCEX 的差异</b>：原文带 {@code @Debug(export = true)} ✗ —— 那会在每次启动时把
 * 目标类导出成 class 文件（往 run 目录写文件 ✗）⇒ **去掉** ✓（与功能无关 ✓）。
 * <p>⚠ <b>已核对字节码</b>：{@code BladeRenderState#renderOverrided(...9 参...)} 存在 ✓（`javap -p` 实查 ✓）。
 * <p>⚠ <b>第二处与原版的差异（本仓口径，必要 ✓）</b>：{@code MultiBufferSource#getBuffer} 是**原版方法** ✗ ——
 * 原文写 MCP 名 ＋ {@code remap = true}（靠 Mixin 注解处理器生成的 refmap ✗），
 * 而本仓**禁用了那个处理器**、refmap 是手写的（见 {@code tinkersnewlife.refmap.json} ✗ 里面没有这一条 ✗）
 * ⇒ 照本仓既有写法（{@code ItemRendererMixin} 那条"SRG 名直连兜底"✓）改成
 * <b>SRG 名 ＋ {@code remap = false}</b> ✓（SRG 名 `m_6299_` 取自本仓
 * {@code build/reobfJar/mappings.tsrg} 实查 ✓）。生产环境（= 用户实际跑的整合包 ✓）下这条才有效 ✓。
 */
@Mixin(value = BladeRenderState.class, remap = false)
public abstract class KatanaBladeRenderStateMixin {

    @WrapMethod(method = "renderOverrided(Lnet/minecraft/world/item/ItemStack;Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILjava/util/function/Function;Z)V")
    private static void renderOverridedWrapped(ItemStack stack, WavefrontObject model, String target, ResourceLocation texture,
                                               PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn,
                                               Function<ResourceLocation, RenderType> renderTypeGetter, boolean enableEffect,
                                               Operation<Void> original) {
        TicEXSBRenderers.renderWrapped(original::call, stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn,
                renderTypeGetter, enableEffect);
    }

    @WrapOperation(
            method = "renderOverrided(Lnet/minecraft/world/item/ItemStack;Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILjava/util/function/Function;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;m_6299_(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", ordinal = 0, remap = false))
    private static VertexConsumer swapBuffer(MultiBufferSource instance, RenderType renderType, Operation<VertexConsumer> original) {
        VertexConsumer vertexConsumer = KatanaContexts.SB_SWAP_VC.get();
        return vertexConsumer != null ? vertexConsumer : original.call(instance, renderType);
    }
}
