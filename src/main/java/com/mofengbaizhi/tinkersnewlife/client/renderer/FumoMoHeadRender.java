package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModel;
import com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModelHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * <b>把 fufu 画在"头"上</b>（§899）—— 两条渲染路**共用同一段代码** ✓：
 * <ul>
 *   <li>Curios 头部饰品栏（{@link FumoMoCurioRenderer}）；</li>
 *   <li>原版头盔槽（{@link FumoMoHeadLayer}，自己挂的 {@code RenderLayer} ✓）。</li>
 * </ul>
 * <p>★ 两个必须记住的坑：
 * <ol>
 *   <li><b>{@code young} 必须显式关掉</b>（§898 真根因）：{@code EntityModel.young} 默认是 {@code true}
 *       ⇒ 自造模型会走 {@code AgeableListModel} 的幼年体分支（×0.75 ＋ 下移 16px）⇒ 被塞进躯干里 ✗；</li>
 *   <li>这里**直接渲染 {@code model.head} 子树**（而不是 {@code renderToBuffer}）⇒ 结构上绕开那个分支 ✓
 *       （{@code head} 本身是空节点、方块全挂在它的子节点上 ✓）。</li>
 * </ol>
 */
public final class FumoMoHeadRender {

    /** §898 姿态探针：只打一次，把真实矩阵记进日志（以后再出问题就不用猜了 ✓） */
    private static volatile boolean tnl$poseLogged = false;

    private FumoMoHeadRender() {}

    /**
     * 把 fufu 画在当前 PoseStack 上（本方法内部自己从玩家的头开始定位 ✓ 调用方不用先摆姿态 ✓）。
     *
     * @param headed 佩戴者的模型（玩家模型即可 ✓ 只要它实现了 {@link HeadedModel}）
     */
    public static void render(PoseStack pose, MultiBufferSource buffer, int light, HeadedModel headed) {
        FumoMoHeadModel model = FumoMoHeadModelHolder.get();
        // §898 ★ young 必须是 false（两条路共用同一个实例 ⇒ 这里修一处、两处都好 ✓）
        model.young = false;
        ModelPart head = model.head;
        // §898 ★ 强制可见：防别的路径（例如护甲层）把这个共用实例的部件关掉 ✗
        head.getAllParts().forEach(part -> {
            part.visible = true;
            part.skipDraw = false;
        });
        // 头部件自身角度清零：头部角度已经由 translateAndRotate 带进来了 ✓（否则会转两遍 ✗）
        head.xRot = 0.0F;
        head.yRot = 0.0F;
        head.zRot = 0.0F;

        pose.pushPose();
        headed.getHead().translateAndRotate(pose);
        float s = FumoMoHeadModel.SCALE;
        pose.scale(s, s, s);
        if (!tnl$poseLogged) {
            tnl$poseLogged = true;
            org.joml.Matrix4f m = pose.last().pose();
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo").info(
                    "[fufu] 姿态诊断：缩放≈({}, {}, {}) 平移(格)=({}, {}, {}) young={} head 子树部件数={}",
                    String.format("%.3f", m.m00()), String.format("%.3f", m.m11()), String.format("%.3f", m.m22()),
                    String.format("%.3f", m.m03()), String.format("%.3f", m.m13()), String.format("%.3f", m.m23()),
                    model.young, head.getAllParts().count());
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(FumoMoBlockEntityRenderer.TEXTURE));
        head.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
