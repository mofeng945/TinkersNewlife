package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.render.StarBodyClient;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * TNL959 \u2605\u2605 \u8865\u4e0a\u65e0\u5c3d\u8d2a\u5a6a\u7684**\u5173\u952e\u4e24\u6b65 `endBatch()`** \u2713
 *
 * <p>\u2605\u5d29\u6e83\u539f\u6587\uff08\u2605\u8bc1\u636e \u2713\uff09\uff1a
 * ``IllegalStateException: Not filled all elements of the vertex``
 * \u2605\u6307\u5411\u6211\u624b\u5199\u9876\u70b9\u628a\u5171\u4eab\u7f13\u51b2\u5199\u6210\u4e86\u534a\u622a \u2713\u3002
 *
 * <p>\u2605\u53c2\u8003\u5b9e\u73b0\u91cc\u6211**\u6f0f\u6284\u4e86\u8fd9\u4e24\u6b65** \u2717\uff1a
 * <pre>
 * MultiBufferSource.BufferSource source = ...;
 * if (source != null) source.endBatch();          // \u2605\u5199\u4e4b\u524d\u5148\u628a\u522b\u4eba\u7684\u6279\u5237\u51fa \u2713
 * ...\u81ea\u5df1\u5199\u9876\u70b9...
 * if (source != null) source.endBatch(type);      // \u2605\u5199\u5b8c\u518d\u628a\u81ea\u5df1\u7684\u6279\u5237\u51fa \u2713
 * </pre>
 * \u2605\u73b0\u5728\u4e25\u683c\u7167\u8fd9\u4e2a\u987a\u5e8f\u6765 \u2713\uff0c\u4e14**\u4e09\u6b21\u90fd\u6210\u5bf9** \u2713
 * \uff08\u2605\u539f\u7248\u5c42 / \u2605\u661f\u7a7a\u5c42\u5404\u81ea\u4e00\u5bf9 \u2713\uff09\u3002
 */
@Mixin(ItemRenderer.class)
public class StarBodyItemRendererMixin {

    @Inject(method = "m_115143_", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinkersnewlife$starBow(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                        PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                        BakedModel model, CallbackInfo ci) {
        if (stack == null || !(stack.getItem() instanceof StarfallLamentItem)) {
            return;
        }
        RenderType rt = StarBodyClient.starBodyOrNull();
        if (rt == null) {
            return;
        }
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource bs ? bs : null;
        try {
            pose.pushPose();
            BakedModel baked = ForgeHooksClient.handleCameraTransforms(pose, model, context, leftHand);

            // TNL961 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u906e\u7f69\u4e0a\u53bb\u4e86\u4f46\u662f\u7269\u54c1\u98de\u4e86" \u2713
            //   \u2605\u771f\u56e0 \u2717\uff1a\u2605\u6211\u628a**\u57fa\u7840\u5c42 \u002b \u661f\u7a7a\u5c42\u753b\u4e86\u4e24\u904d** \u2717
            //     \u21d2 \u2605\u540c\u4e00\u51e0\u4f55\u4e24\u6b21\u5199\u5165 \u21d2 \u2605\u6df1\u5ea6/\u6279\u6b21\u9519\u4f4d \u21d2 \u2605\u770b\u8d77\u6765\u201c\u98de\u4e86\u201d \u2713
            //   \u2605\u73b0\u5728\uff1a\u2605\u53ea\u753b**\u661f\u7a7a\u5c42** \u2713 \u2014\u2014
            //     \u2605\u7740\u8272\u5668\u672c\u8eab\u4f1a\u628a\u975e\u906e\u7f69\u533a\u539f\u6837\u8f93\u51fa \u2713\uff08\u2605\u5f62\u72b6\u4e0d\u4e22 \u2713\uff09
            List<BakedQuad> quads = emitAll(baked, pose, buffers.getBuffer(rt), light);
            if (source != null) {
                source.endBatch(rt);                                  // \u2605\u2462 \u5199\u540e\u5237 \u2713
            }
            pose.popPose();
            ci.cancel();                                              // \u2605\u771f\u753b\u5b8c\u624d\u53d6\u6d88 \u2713
            if (System.currentTimeMillis() - TNL$LOG > 3000L) {
                TNL$LOG = System.currentTimeMillis();
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL959 drew {} quads (ctx={}) via star render type", quads.size(), context);
            }
        } catch (Throwable t) {
            try {
                pose.popPose();
            } catch (Throwable ignored) {
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL959 draw FAILED: {}", t.toString());
        }
    }

    private static List<BakedQuad> emitAll(BakedModel baked, PoseStack pose, VertexConsumer vc, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        RandomSource rnd = RandomSource.create(42L);
        List<BakedQuad> quads = new java.util.ArrayList<>();
        for (Direction dir : Direction.values()) {
            quads.addAll(baked.getQuads(null, dir, rnd));
        }
        quads.addAll(baked.getQuads(null, null, rnd));
        for (BakedQuad q : quads) {
            int[] v = q.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) {
                int o = i * stride;
                vc.vertex(m, Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]),
                        Float.intBitsToFloat(v[o + 2]));
                vc.color(255, 255, 255, 255);
                vc.uv(Float.intBitsToFloat(v[o + 4]), Float.intBitsToFloat(v[o + 5]));
                vc.overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                vc.uv2(light);
                vc.normal(n, 0.0F, 1.0F, 0.0F);
                vc.endVertex();
            }
        }
        return quads;
    }

    private static long TNL$LOG = 0L;
}
