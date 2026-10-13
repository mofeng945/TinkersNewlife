package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.render.StarBodyClient;
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

import java.util.List;

/**
 * TNL952 \u2605\u2605 \u6700\u540e\u4e00\u6761\u8def\uff1a\u2605**\u7167\u65e0\u5c3d\u8d2a\u5a6a**\u7684\u6ce8\u5165\u70b9\u81ea\u5df1\u753b \u2713
 *
 * <p>\u2605\u7528\u6237\u9009\uff1a\u2605"\u505a" \u2713
 *
 * <p>\u2605\u4e3a\u4ec0\u4e48\u53ea\u80fd\u8d70\u8fd9\u6761 \u2717\uff1a\u2605\u65e5\u5fd7\u5df2\u7ecf\u6392\u9664\u4e24\u6761\u8def \u2713\uff1a
 * <ul>
 *   <li>\u2605BEWLR\uff1a\u2605``getCustomRenderer`` \u53ea\u6709 YSM \u002b TaCZ \u6765\u95ee \u2717\uff1b</li>
 *   <li>\u2605``ItemBlockRenderTypes#m_109279_``\uff1a\u2605``TNL950`` **\u96f6\u884c** \u2717
 *       \u21d2 \u2605\u539f\u7248\u6e32\u67d3\u8def\u5f84\u538b\u6839\u4e0d\u7ecf\u8fc7\u5b83 \u2713\uff08\u2605\u4e24\u6b21\u72ec\u7acb\u9a8c\u8bc1 \u2713\uff09\u3002</li>
 * </ul>
 *
 * <p>\u2605\u800c\u65e0\u5c3d\u8d2a\u5a6a\u7684 ``MixinItemRenderer`` \u5c31\u662f \u2713\uff1a
 * \u2605``@Inject(method="render", at=HEAD, cancellable)`` \u21d2 \u2605``ci.cancel()``
 * \u21d2 \u2605``ForgeHooksClient.handleCameraTransforms(...)`` \u21d2 \u2605\u81ea\u5df1\u7528\u81ea\u5df1\u7684
 * ``RenderType`` \u753b\u6a21\u578b\u56db\u8fb9\u5f62 \u2713\u3002
 *
 * <p>\u2605\u4e0a\u4e00\u6b21\u6211\u8d70\u540c\u6837\u7684\u4e8b\u5374\u628a\u5f13\u753b\u6ca1\u4e86 \u2717\uff0c
 * \u2605\u539f\u56e0\uff1a\u2605\u6211\u7528\u4e86 ``NEW_ENTITY`` \u9876\u70b9\u683c\u5f0f \u2717\uff0c
 * \u2605\u800c baked model \u7684\u56db\u8fb9\u5f62\u662f ``BLOCK`` \u9876\u70b9\u6d41 \u2717 \u21d2 \u2605\u5e03\u5c40\u4e0d\u7b26 \u2713
 * \u2605\u73b0\u5728\u5df2\u7ecf\u7edf\u4e00\u6210 BLOCK \u2713\uff08\u2605\u7740\u8272\u5668 json / vsh / RenderType \u4e09\u8005\u4e00\u81f4 \u2713\uff09\u3002
 *
 * <p>\u2605\u515c\u5e95 \u2713\uff1a\u2605\u53ea\u6709"\u771f\u7684\u753b\u5b8c\u4e86"\u624d ``cancel`` \u2713\uff0c
 * \u2605\u4e2d\u95f4\u629b\u5f02\u5e38\u5c31**\u4e0d\u53d6\u6d88** \u2713 \u21d2 \u2605\u539f\u7248\u7167\u5e38\u753b \u2713
 * \u21d2 \u2605\u5f13**\u4e0d\u4f1a\u518d\u6d88\u5931** \u2713\u3002
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
            return;                                   // \u2605\u7740\u8272\u5668\u6ca1\u597d \u21d2 \u2605\u8d70\u539f\u7248 \u2713
        }
        try {
            pose.pushPose();
            BakedModel baked = ForgeHooksClient.handleCameraTransforms(pose, model, context, leftHand);
            VertexConsumer vc = buffers.getBuffer(rt);
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
                    vc.uv2(light);                       // \u2605BLOCK \u91cc\u5149\u7167\u5728 UV2 \u2713
                    vc.normal(n, 0.0F, 1.0F, 0.0F);
                    vc.endVertex();
                }
            }
            pose.popPose();
            ci.cancel();                                 // \u2605\u771f\u753b\u5b8c\u4e86\u624d\u53d6\u6d88 \u2713
            if (System.currentTimeMillis() - TNL$LOG > 3000L) {
                TNL$LOG = System.currentTimeMillis();
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL952 drew {} quads through the star render type (ctx={})", quads.size(), context);
            }
        } catch (Throwable t) {
            try {
                pose.popPose();
            } catch (Throwable ignored) {
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL952 draw FAILED: {}", t.toString());
        }
    }

    private static long TNL$LOG = 0L;
}
