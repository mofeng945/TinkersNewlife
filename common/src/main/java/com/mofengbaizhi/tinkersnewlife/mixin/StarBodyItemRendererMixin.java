package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.render.StarBodyClient;
import com.mofengbaizhi.tinkersnewlife.content.item.AeonianOrderItem;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

import java.util.ArrayList;
import java.util.List;

/**
 * TNL1026 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u4f60\u80fd\u4e0d\u80fd\u628a\u5f13\u63d0\u53d6\u4e00\u4e9b\u5171\u7528\u7684\u65b9\u6cd5\uff0c\u7136\u540e\u4e24\u4e2a\u7269\u54c1\u90fd\u80fd\u8c03" \u2713
 *
 * <p>\u2605\u73b0\u5728\u6574\u6761"\u81ea\u7ed8\u4e00\u5c42"\u7684\u6d41\u7a0b\u53ea\u6709**\u4e00\u4efd** \u2713\uff1a
 * {@link #tnl$renderEffectLayer} \u2713 \u2014\u2014 \u2605\u4e24\u628a\u6b66\u5668\u90fd\u8c03\u5b83 \u2713\uff0c
 * \u2605\u53ea\u662f\u4f20\u5165\u7684 {@link RenderType} \u4e0d\u540c \u2713\u3002
 *
 * <p>\u2605\u6d41\u7a0b\uff08\u2605\u4e00\u6b65\u90fd\u4e0d\u80fd\u5c11 \u2713\uff09\uff1a
 * <ol>
 *   <li>\u2605``pose.pushPose()`` \u2713\uff1b</li>
 *   <li>\u2605``ForgeHooksClient.handleCameraTransforms`` \u2713\uff08\u2605手持/物品栏的显示变换 \u2713\uff09\uff1b</li>
 *   <li>\u2605``translate(-0.5,-0.5,-0.5)`` \u2713\uff08\u2605baked model \u539f点在角\uff0c\u21d2 \u2605要归\u5230方块中\u5fc3 \u2713\uff09\uff1b</li>
 *   <li>\u2605``source.endBatch()`` \u2713 \u2192 \u2605写四边\u5f62 \u2713 \u2192 \u2605``source.endBatch(rt)`` \u2713\uff08\u2605成\u5bf9\uff0c\u2605否\u5219\u5d29 \u2713\uff09\uff1b</li>
 *   <li>\u2605``pose.popPose()`` \u2713 \u002b \u2605``ci.cancel()`` \u2713\u3002</li>
 * </ol>
 */
@Mixin(ItemRenderer.class)
public class StarBodyItemRendererMixin {

    @Inject(method = "m_115143_", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinkersnewlife$effectLayer(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                            PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                            BakedModel model, CallbackInfo ci) {
        if (stack == null) {
            return;
        }
        boolean bow = stack.getItem() instanceof StarfallLamentItem;
        boolean order = stack.getItem() instanceof AeonianOrderItem;
        if (!bow && !order) {
            return;
        }

        // \u2605\u6052\u6b63\u79e9\u5668\uff1a\u2605\u53ea\u5728\u7269\u54c1\u680f\u8d70\u81ea\u5b9a\u4e49\u7740\u8272\u5668 \u2713
        //   \uff08\u2605\u624b\u6301\u7167\u62c4\u5f13\u90a3\u5957\u4f1a\u628a\u5b83\u644a\u6210\u4e00\u5757\u5927\u9ed1\u677f \u2717\uff09
        if (order && context != ItemDisplayContext.GUI) {
            return;
        }

        // \u2605\u9009\u7740\u8272\u5668\uff08\u2605\u5f13 / \u6052\u6b63\u79e9\u5668 \u2713\uff09
        RenderType rt;
        if (order) {
            com.mofengbaizhi.tinkersnewlife.client.render.AeonianOrderClient.tick();
            rt = com.mofengbaizhi.tinkersnewlife.client.render.AeonianOrderClient.ready()
                    ? com.mofengbaizhi.tinkersnewlife.client.render.AeonianOrderClient.orderType() : null;
        } else {
            rt = StarBodyClient.starBodyOrNull();
        }
        if (rt == null) {
            return;
        }
        tnl$renderEffectLayer(pose, buffers, light, model, context, leftHand, rt, order ? "order" : "bow", ci);
    }

    /**
     * \u2605\u2605 \u5171\u7528\u6d41\u7a0b \u2713\uff1a\u2605\u628a\u4f20\u5165\u7684 {@link RenderType} \u5728\u539f\u7248\u4e4b\u524d
     * \u5148\u753b\u4e00\u5c42\uff08\u2605\u7136\u540e\u53d6\u6d88\u539f\u7248 \u2713\uff09\u3002
     *
     * @param tag \u2605\u4ec5\u7528\u4e8e\u65e5\u5fd7\u533a\u5206\u4e24\u628a\u6b66\u5668 \u2713
     */
    private static void tnl$renderEffectLayer(PoseStack pose, MultiBufferSource buffers, int light,
                                              BakedModel model, ItemDisplayContext context, boolean leftHand,
                                              RenderType rt, String tag, CallbackInfo ci) {
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource bs ? bs : null;
        try {
            pose.pushPose();
            BakedModel baked = ForgeHooksClient.handleCameraTransforms(pose, model, context, leftHand);
            pose.translate(-0.5D, -0.5D, -0.5D);
            if (source != null) {
                source.endBatch();
            }
            List<BakedQuad> quads = tnl$emit(baked, pose, buffers.getBuffer(rt), light);
            if (source != null) {
                source.endBatch(rt);
            }
            pose.popPose();
            ci.cancel();
            if (System.currentTimeMillis() - TNL$LOG > 5000L) {
                TNL$LOG = System.currentTimeMillis();
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL1026 drew {} quads through the {} effect layer (ctx={})", quads.size(), tag, context);
            }
        } catch (Throwable t) {
            try {
                pose.popPose();
            } catch (Throwable ignored) {
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1026 {} effect layer FAILED: {}",
                    tag, t.toString());
        }
    }

    /** \u2605\u628a\u6a21\u578b\u7684\u56db\u8fb9\u5f62\u5199\u8fdb\u7ed9\u5b9a\u7f13\u51b2 \u2713\uff08\u2605\u683c\u5f0f\u5fc5\u987b\u4e0e RenderType \u4e00\u81f4 \u2713\uff09 */
    private static List<BakedQuad> tnl$emit(BakedModel baked, PoseStack pose, VertexConsumer vc, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        RandomSource rnd = RandomSource.create(42L);
        List<BakedQuad> quads = new ArrayList<>();
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
