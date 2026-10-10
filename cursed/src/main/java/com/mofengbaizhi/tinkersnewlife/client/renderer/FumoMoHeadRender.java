package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModelHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * <b>把玩偶摆到"头"上</b>（§901）—— 三条路（Curios 头部饰品栏 / 原版头盔槽 / 以后要加的都算）
 * <b>共用同一段代码</b> ✓。
 *
 * <h3>做法：直接复用方块/物品栏那一只玩偶（用户点出来的 ✓）</h3>
 * 玩偶本体就是 {@link FumoMoBlockEntityRenderer#buildDollModel()} 出来的 {@link PlayerModel} ✓，
 * 姿势是 {@link FumoMoBlockEntityRenderer#poseDoll} ✓ ⇒ <b>和方块/物品栏里长得一模一样</b> ✓，
 * 且"玩偶长什么样"全仓只有一处定义 ✓（不再手搓第二份模型 ✗）。
 *
 * <h3>只差"摆在哪"</h3>
 * <ul>
 *   <li>方块那条路：0.5 缩放 ＋ 实体模型那套 y 翻转 ＋ {@code translate(0,-1.501,0)} ⇒ 坐在方块上；</li>
 *   <li>头顶这条路：从玩家的头开始（{@code head.translateAndRotate} ⇒ 跟着头转 ✓），
 *       再整体下移 {@link #HEAD_DROP} 并缩放 {@link #DOLL_SCALE} ⇒ 玩偶"坐"在玩家头顶上 ✓。</li>
 * </ul>
 * <b>⚠ 为什么头顶这条路不需要那套 y 翻转</b>：渲染层拿到的姿态空间**就是模型坐标所在的空间**
 * （{@code ModelPart.translateAndRotate} 把像素除以 16 之后直接作用在这个空间里 ✓，
 * 实体渲染器的 {@code scale(-1,-1,1)} 早已包含在内 ✓）⇒ 把自己当成玩家模型的一个部件直接画就行 ✓，
 * 画出来天然是正的 ✓（再翻一次才会倒 ✗）。
 *
 * <h3>摆放数字怎么来的</h3>
 * 玩偶模型（{@code PlayerModel}，坐姿、头 ×1.40、四肢 ×0.92）在模型坐标里：
 * 头顶 ≈ −0.70 格、最低点（屁股/腿根）≈ +0.91 格。
 * 要求最低点落在玩家头顶（−0.5 格）再下沉 1px ⇒
 * <code>HEAD_DROP + DOLL_SCALE × 0.91 = −0.5 + 0.0625</code> ⇒ {@code HEAD_DROP ≈ −0.89} ✓
 * ⇒ 玩偶占 −1.24 ~ −0.44 格 ⇒ 贴在头顶上方约 0.06~0.80 格，总高约 0.8 格 ✓。
 */
public final class FumoMoHeadRender {

    /** 玩偶整体缩放（与方块那只同为 0.5 ⇒ 大小观感一致 ✓） */
    public static final float DOLL_SCALE = 0.5F;

    /**
     * 把玩偶"落到"玩家头顶用的偏移量（单位：**格** ✓，在 {@link #DOLL_SCALE} 缩放**之前**作用 ⇒ 不受缩放影响 ✓）。
     * <p>推导：要求最低点落在玩家头顶（−0.5 格）再下沉 1px ⇒ `HEAD_DROP + 0.5 × 0.94 ≈ −0.44`
     * ⇒ 算得 ≈ −0.89 ✓；§903 再按用户口径**整体上调 2px**（2/16 = 0.125 格 ✓）⇒ −1.015；
     * §904 又下调 **0.5px**（0.5/16 = 0.03125 格 ✓）⇒ **−0.98375** ✓。
     * <p>⚠ 想再调：**改这个数就是改格数** ✓（1px = 0.0625 格；负得越多 ⇒ 越高 ✓）。
     */
    public static final double HEAD_DROP = -0.98375D;

    /** §898 姿态探针：只打一次，把真实矩阵记进日志（以后再出问题就不用猜了 ✓） */
    private static volatile boolean tnl$poseLogged = false;

    private FumoMoHeadRender() {}

    /**
     * 把玩偶画在当前 PoseStack 上（内部自己从"玩家的头"开始定位 ✓ 调用方不用先摆姿态 ✓）。
     *
     * @param headed 佩戴者的模型（玩家模型即可 ✓ 只要实现了 {@link HeadedModel}）
     */
    public static void render(PoseStack pose, MultiBufferSource buffer, int light, HeadedModel headed) {
        render(pose, buffer, light, headed, FumoMoBlockEntityRenderer.TEXTURE);
    }

    /**
     * §1079 带贴图的版本：<b>头顶也跟着皮肤走</b> ✓（原版头盔槽那条路取头盔格物品的皮肤 ✓、
     * Curios 那条路取饰品栈的皮肤 ✓）⇒ 戴哪个皮肤的 fufu，头顶就是哪个 ✓。
     *
     * @param texture 这只 fufu 的皮肤贴图（拿不到就传默认那张 ✓）
     */
    public static void render(PoseStack pose, MultiBufferSource buffer, int light, HeadedModel headed,
                              net.minecraft.resources.ResourceLocation texture) {
        PlayerModel<?> doll = FumoMoHeadModelHolder.get();
        // ★ §898 关键：young 必须是 false，否则走幼年体分支（×0.75 ＋ 下移 16px）⇒ 被塞进躯干里 ✗
        doll.young = false;

        pose.pushPose();
        headed.getHead().translateAndRotate(pose);   // 到头部枢轴（带上头的朝向 ✓ 转头就跟着转 ✓）
        pose.translate(0.0D, HEAD_DROP, 0.0D);       // 落到头顶（注意：这一步在缩放之前 ⇒ 单位是"格" ✓）
        pose.scale(DOLL_SCALE, DOLL_SCALE, DOLL_SCALE);
        FumoMoBlockEntityRenderer.poseDoll(doll);    // 和方块/物品栏那只**同一套姿势** ✓
        if (!tnl$poseLogged) {
            tnl$poseLogged = true;
            org.joml.Matrix4f m = pose.last().pose();
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo").info(
                    "[fufu] 头顶姿态诊断：缩放≈({}, {}, {}) 平移(格)=({}, {}, {}) young={}",
                    String.format("%.3f", m.m00()), String.format("%.3f", m.m11()), String.format("%.3f", m.m22()),
                    String.format("%.3f", m.m03()), String.format("%.3f", m.m13()), String.format("%.3f", m.m23()),
                    doll.young);
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        doll.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
