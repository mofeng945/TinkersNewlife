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
 * TNL1028 ★★ 两把武器的自绘入口（★共用一条流程 ✓）
 *
 * <p>★用户反馈过的两个坑 ✓：
 * <ul>
 *   <li>★"共用抽取时把弓的物品栏光晕忘了搬过来" ✗
 *       ⇒ ★所以这里**两条入口都在** ✓：GUI 光晕（弓 ✓）+ 自绘一层（两把都行 ✓）；</li>
 *   <li>★"镰刀拿在手里还是一块板" ✗
 *       ⇒ ★手持姿态要单独算 ✗ —— ★所以镰刀**先只在 GUI** ✓，
 *       ★手持交还原版 ✓（★下一轮再做手持透镜 ✓）。</li>
 * </ul>
 */
@Mixin(ItemRenderer.class)
public class StarBodyItemRendererMixin {

    private static long TNL$LOG = 0L;
    private static long TNL$POSE_LOG = 0L;
    private static long TNL$BOUNDS_LOG = 0L;

    /** TNL1029 ★模型有几个四边形（★看它到底是不是一张平面 ✓） */
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
        // TNL1056 user: hide the scythe in hand while the hook is out (the hook effect marks it)
        if (order && net.minecraft.client.Minecraft.getInstance().player != null
                && net.minecraft.client.Minecraft.getInstance().player.getMainHandItem().getItem()
                        instanceof AeonianOrderItem orderItem) {
            try {
                var marker = com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_MARKER.get();
                if (net.minecraft.client.Minecraft.getInstance().player.getCooldowns().isOnCooldown(marker)
                        && net.minecraft.client.Minecraft.getInstance().player.getMainHandItem().getItem() == orderItem) {
                    ci.cancel();
                    return;
                }
            } catch (Throwable ignored) {
            }
        }


        // ★① 弓的物品栏光晕（★恢复 ✓）：★画完**不取消** ✓ ⇒ ★原版物品画在它上面 ✓
        // TNL1042 user: give the scythe the same inventory glow as the bow
        if ((bow || order) && context == ItemDisplayContext.GUI && StarfallGlowClient.ready()) {
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
            // TNL1042 the early return here was why the inventory bow lost its starfield layer:
            // the shared effect layer below never ran, so the item was drawn as a plain sprite.
            // Now order is: glow (background) -> effect layer (the item) -> cancel vanilla.
        }

        // ★② 镰刀：★先只在物品栏走自定义着色器 ✓
        // TNL1030 ★★ 用户：★"测了"但只有 GUI 那组 ✗
        //   ★真因 ✗：★手持提前 `return` 了 ✗ ⇒ ★探针根本没跑到 ✓
        //   ★现在 ✓：★手持时**只打探针不画** ✓（★原版照常画 ✓ ⇒ ★不变板 ✓）
        //     ★拿到手持的 diag/trans/quads 后我就能算出修正量 ✓
        // TNL1034 ★★ 用户：★"弓都已经完美实现过一版了你怎么还反复走之前试错过的路" ✓
        //   ★我错在哪 ✗：★弓的"手持透镜"**一直是好的** ✓
        //     ★而我反复去改**姿态/属性/绑图** ✗ —— ★那些弓都没改过 ✓
        //   ★现在 ✓：★把镰刀的 fsh **照搬弓的** ✓（★遮罩/输出/alpha/坐标全一致 ✓）
        //     ⇒ ★手持重新打开 ✓（★现在它与弓是同一条路 ✓）
        RenderType rt = bow ? StarBodyClient.starBodyOrNull()
                : (AeonianOrderClient.ready() ? AeonianOrderClient.orderType() : null);
        if (order) {
            AeonianOrderClient.tick();
        }
        if (rt == null) {
            return;
        }
        // TNL1029 ★★ 用户：★"镰刀拿在手里还是一块板" ✓
        //   ★我不再凭感觉调 ✗ —— ★先把"姿态矩阵"打出来 ✓
        //   ★看什么 ✓：★对角项（★缩放 ✓）+ ★平移项 ✓ + ★模型自带的 display 变换 ✓
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

    /** ★共用：★在原版之前先画一层，★然后取消原版 ✓ */
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
            // TNL1031 ★★ 手持探针结果：★diag=(1,1,1) trans=(0.56,-0.52,-0.72) ✓
            //   ⇒ ★姿态本身**正常**（★缩放 1 ✓ + ★在镜头前 0.7 ✓）✗
            //   ★所以"变板"不是姿态问题 ✗ ⇒ ★得看**几何本身** ✓
            //   ★现在：★把这些四边形的**包围盒**打出来 ✓
            //     （★平面片 ⇒ ★某一轴几乎为 0 ✓；★方块 ⇒ ★三轴都接近 1 ✓）
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

    /** ★光晕：★一个铺满 UV 0~1 的四边形 ✓ */
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

    /** ★把模型四边形写进给定缓冲 ✓ */
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
