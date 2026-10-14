package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.render.AeonianOrderClient;
import com.mofengbaizhi.tinkersnewlife.client.render.StarBodyClient;
import com.mofengbaizhi.tinkersnewlife.client.render.StarfallGlowClient;
import com.mofengbaizhi.tinkersnewlife.content.item.AeonianOrderItem;
import com.mofengbaizhi.tinkersnewlife.content.item.StarfallLamentItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * TNL1028 \u2605\u2605 \u4e24\u628a\u6b66\u5668\u7684\u81ea\u7ed8\u5165\u53e3\uff08\u2605\u5171\u7528\u4e00\u6761\u6d41\u7a0b \u2713\uff09
 *
 * <p>\u2605\u7528\u6237\u53cd\u9988\u8fc7\u7684\u4e24\u4e2a\u5751 \u2713\uff1a
 * <ul>
 *   <li>\u2605"\u5171\u7528\u62bd\u53d6\u65f6\u628a\u5f13\u7684\u7269\u54c1\u680f\u5149\u6655\u5fd8\u4e86\u642c\u8fc7\u6765" \u2717
 *       \u21d2 \u2605\u6240\u4ee5\u8fd9\u91cc**\u4e24\u6761\u5165\u53e3\u90fd\u5728** \u2713\uff1aGUI \u5149\u6655\uff08\u5f13 \u2713\uff09\u002b \u81ea\u7ed8\u4e00\u5c42\uff08\u4e24\u628a\u90fd\u884c \u2713\uff09\uff1b</li>
 *   <li>\u2605"\u9570\u5200\u62ff\u5728\u624b\u91cc\u8fd8\u662f\u4e00\u5757\u677f" \u2717
 *       \u21d2 \u2605\u624b\u6301\u59ff\u6001\u8981\u5355\u72ec\u7b97 \u2717 \u2014\u2014 \u2605\u6240\u4ee5\u9570\u5200**\u5148\u53ea\u5728 GUI** \u2713\uff0c
 *       \u2605\u624b\u6301\u4ea4\u8fd8\u539f\u7248 \u2713\uff08\u2605\u4e0b\u4e00\u8f6e\u518d\u505a\u624b\u6301\u900f\u955c \u2713\uff09\u3002</li>
 * </ul>
 */
@Mixin(ItemRenderer.class)
public class StarBodyItemRendererMixin {

    private static long TNL$LOG = 0L;
    private static long TNL$POSE_LOG = 0L;
    private static long TNL$BOUNDS_LOG = 0L;

    /** TNL1029 \u2605\u6a21\u578b\u6709\u51e0\u4e2a\u56db\u8fb9\u5f62\uff08\u2605\u770b\u5b83\u5230\u5e95\u662f\u4e0d\u662f\u4e00\u5f20\u5e73\u9762 \u2713\uff09 */
    private static int tnl$countQuads(BakedModel model) {
        try {
            RandomSource rnd = RandomSource.create(42L);
            int n = 0;
            for (Direction dir : Direction.values()) {
                n += model.getQuads(null, dir, rnd).size();
            }
            n += model.getQuads(null, null, rnd).size();
            return n;
        } catch (Throwable t) {
            return -1;
        }
    }

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

        // \u2605\u2460 \u5f13\u7684\u7269\u54c1\u680f\u5149\u6655\uff08\u2605\u6062\u590d \u2713\uff09\uff1a\u2605\u753b\u5b8c**\u4e0d\u53d6\u6d88** \u2713 \u21d2 \u2605\u539f\u7248\u7269\u54c1\u753b\u5728\u5b83\u4e0a\u9762 \u2713
        if (bow && context == ItemDisplayContext.GUI && StarfallGlowClient.ready()) {
            StarfallGlowClient.tick();
            try {
                pose.pushPose();
                BakedModel glowModel = ForgeHooksClient.handleCameraTransforms(pose, model, context, leftHand);
                pose.translate(-0.5D, -0.5D, -0.5D);
                MultiBufferSource.BufferSource gsrc = buffers instanceof MultiBufferSource.BufferSource b2 ? b2 : null;
                if (gsrc != null) {
                    gsrc.endBatch();
                }
                tnl$emitGlow(pose, buffers.getBuffer(StarfallGlowClient.glowType()));
                if (gsrc != null) {
                    gsrc.endBatch(StarfallGlowClient.glowType());
                }
                pose.popPose();
            } catch (Throwable t) {
                try {
                    pose.popPose();
                } catch (Throwable ignored) {
                }
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1028 glow FAILED: {}", t.toString());
            }
            return;
        }

        // \u2605\u2461 \u9570\u5200\uff1a\u2605\u5148\u53ea\u5728\u7269\u54c1\u680f\u8d70\u81ea\u5b9a\u4e49\u7740\u8272\u5668 \u2713
        // TNL1030 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6d4b\u4e86"\u4f46\u53ea\u6709 GUI \u90a3\u7ec4 \u2717
        //   \u2605\u771f\u56e0 \u2717\uff1a\u2605\u624b\u6301\u63d0\u524d `return` \u4e86 \u2717 \u21d2 \u2605\u63a2\u9488\u6839\u672c\u6ca1\u8dd1\u5230 \u2713
        //   \u2605\u73b0\u5728 \u2713\uff1a\u2605\u624b\u6301\u65f6**\u53ea\u6253\u63a2\u9488\u4e0d\u753b** \u2713\uff08\u2605\u539f\u7248\u7167\u5e38\u753b \u2713 \u21d2 \u2605\u4e0d\u53d8\u677f \u2713\uff09
        //     \u2605\u62ff\u5230\u624b\u6301\u7684 diag/trans/quads \u540e\u6211\u5c31\u80fd\u7b97\u51fa\u4fee\u6b63\u91cf \u2713
        if (order && context != ItemDisplayContext.GUI) {
            if (System.currentTimeMillis() - TNL$POSE_LOG > 4000L) {
                TNL$POSE_LOG = System.currentTimeMillis();
                org.joml.Matrix4f hm = pose.last().pose();
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL1030 held pose ctx={} diag=({},{},{}) trans=({},{},{}) quads={}",
                        context, hm.m00(), hm.m11(), hm.m22(), hm.m30(), hm.m31(), hm.m32(),
                        tnl$countQuads(model));
            }
            return;
        }

        RenderType rt = bow ? StarBodyClient.starBodyOrNull()
                : (AeonianOrderClient.ready() ? AeonianOrderClient.orderType() : null);
        if (order) {
            AeonianOrderClient.tick();
        }
        if (rt == null) {
            return;
        }
        // TNL1029 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u9570\u5200\u62ff\u5728\u624b\u91cc\u8fd8\u662f\u4e00\u5757\u677f" \u2713
        //   \u2605\u6211\u4e0d\u518d\u51ed\u611f\u89c9\u8c03 \u2717 \u2014\u2014 \u2605\u5148\u628a"\u59ff\u6001\u77e9\u9635"\u6253\u51fa\u6765 \u2713
        //   \u2605\u770b\u4ec0\u4e48 \u2713\uff1a\u2605\u5bf9\u89d2\u9879\uff08\u2605\u7f29\u653e \u2713\uff09\u002b \u2605\u5e73\u79fb\u9879 \u2713 \u002b \u2605\u6a21\u578b\u81ea\u5e26\u7684 display \u53d8\u6362 \u2713
        if (order && System.currentTimeMillis() - TNL$POSE_LOG > 4000L) {
            TNL$POSE_LOG = System.currentTimeMillis();
            org.joml.Matrix4f mm = pose.last().pose();
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL1029 order pose ctx={} diag=({},{},{}) trans=({},{},{}) quads={}",
                    context, mm.m00(), mm.m11(), mm.m22(), mm.m30(), mm.m31(), mm.m32(),
                    tnl$countQuads(model));
        }
        tnl$renderEffectLayer(pose, buffers, light, model, context, leftHand, rt, order ? "order" : "bow", ci);
    }

    /** \u2605\u5171\u7528\uff1a\u2605\u5728\u539f\u7248\u4e4b\u524d\u5148\u753b\u4e00\u5c42\uff0c\u2605\u7136\u540e\u53d6\u6d88\u539f\u7248 \u2713 */
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
            // TNL1031 \u2605\u2605 \u624b\u6301\u63a2\u9488\u7ed3\u679c\uff1a\u2605diag=(1,1,1) trans=(0.56,-0.52,-0.72) \u2713
            //   \u21d2 \u2605\u59ff\u6001\u672c\u8eab**\u6b63\u5e38**\uff08\u2605\u7f29\u653e 1 \u2713 \u002b \u2605\u5728\u955c\u5934\u524d 0.7 \u2713\uff09\u2717
            //   \u2605\u6240\u4ee5"\u53d8\u677f"\u4e0d\u662f\u59ff\u6001\u95ee\u9898 \u2717 \u21d2 \u2605\u5f97\u770b**\u51e0\u4f55\u672c\u8eab** \u2713
            //   \u2605\u73b0\u5728\uff1a\u2605\u628a\u8fd9\u4e9b\u56db\u8fb9\u5f62\u7684**\u5305\u56f4\u76d2**\u6253\u51fa\u6765 \u2713
            //     \uff08\u2605\u5e73\u9762\u7247 \u21d2 \u2605\u67d0\u4e00\u8f74\u51e0\u4e4e\u4e3a 0 \u2713\uff1b\u2605\u65b9\u5757 \u21d2 \u2605\u4e09\u8f74\u90fd\u63a5\u8fd1 1 \u2713\uff09
            if (System.currentTimeMillis() - TNL$BOUNDS_LOG > 4000L) {
                TNL$BOUNDS_LOG = System.currentTimeMillis();
                float mnx = Float.MAX_VALUE, mny = Float.MAX_VALUE, mnz = Float.MAX_VALUE;
                float mxx = -Float.MAX_VALUE, mxy = -Float.MAX_VALUE, mxz = -Float.MAX_VALUE;
                for (BakedQuad q2 : quads) {
                    int[] vv = q2.getVertices();
                    int st = vv.length / 4;
                    for (int i2 = 0; i2 < 4; i2++) {
                        float px = Float.intBitsToFloat(vv[i2 * st]);
                        float py = Float.intBitsToFloat(vv[i2 * st + 1]);
                        float pz = Float.intBitsToFloat(vv[i2 * st + 2]);
                        mnx = Math.min(mnx, px); mny = Math.min(mny, py); mnz = Math.min(mnz, pz);
                        mxx = Math.max(mxx, px); mxy = Math.max(mxy, py); mxz = Math.max(mxz, pz);
                    }
                }
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL1031 {} model bounds x=({},{}) y=({},{}) z=({},{}) quads={} ctx={}",
                        tag, mnx, mxx, mny, mxy, mnz, mxz, quads.size(), context);
            }
            if (source != null) {
                source.endBatch(rt);
            }
            pose.popPose();
            ci.cancel();
            if (System.currentTimeMillis() - TNL$LOG > 5000L) {
                TNL$LOG = System.currentTimeMillis();
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL1028 drew {} quads through the {} effect layer (ctx={})", quads.size(), tag, context);
            }
        } catch (Throwable t) {
            try {
                pose.popPose();
            } catch (Throwable ignored) {
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1028 {} effect layer FAILED: {}",
                    tag, t.toString());
        }
    }

    /** \u2605\u5149\u6655\uff1a\u2605\u4e00\u4e2a\u94fa\u6ee1 UV 0~1 \u7684\u56db\u8fb9\u5f62 \u2713 */
    private static void tnl$emitGlow(PoseStack pose, VertexConsumer vc) {
        com.mojang.blaze3d.vertex.PoseStack.Pose pe = pose.last();
        Matrix4f m = pe.pose();
        Matrix3f n = pe.normal();
        float s = 1.35F;
        float o = (1.0F - s) * 0.5F;
        float[][] corners = {
                {o, o, 0.0F, 1.0F},
                {o + s, o, 1.0F, 1.0F},
                {o + s, o + s, 1.0F, 0.0F},
                {o, o + s, 0.0F, 0.0F}
        };
        for (float[] c : corners) {
            vc.vertex(m, c[0], c[1], 0.015F)
                    .color(255, 255, 255, 255)
                    .uv(c[2], c[3])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(0xF000F0)
                    .normal(n, 0.0F, 0.0F, 1.0F)
                    .endVertex();
        }
    }

    /** \u2605\u628a\u6a21\u578b\u56db\u8fb9\u5f62\u5199\u8fdb\u7ed9\u5b9a\u7f13\u51b2 \u2713 */
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
                vc.overlayCoords(OverlayTexture.NO_OVERLAY);
                vc.uv2(light);
                vc.normal(n, 0.0F, 1.0F, 0.0F);
                vc.endVertex();
            }
        }
        return quads;
    }
}
