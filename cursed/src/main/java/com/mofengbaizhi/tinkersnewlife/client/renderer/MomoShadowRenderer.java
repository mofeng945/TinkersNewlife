package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * TNL1282 ★★拉莱耶之影（分身）的渲染器 ✓
 *
 * <p>用户口径：分身外形 = **墨默本体略带透明、颜色偏黑**的形象。
 *
 * <h3>⭐ 两条一起做才对</h3>
 * <ol>
 *   <li>⭐**偏黑** ✗：⭐`RenderSystem.setShaderColor(0.34, 0.34, 0.42, alpha)` —— 颜色会乘进贴图 ✓；</li>
 *   <li>⭐**透明** ✗：⭐模型默认走 {@code entityCutoutNoCull}（★只做 alpha 测试、**不混合** ✓）
 *       ⇒ ⭐光设 alpha **根本不会变淡** ✓ ⇒ ⭐把 {@link MultiBufferSource} 包一层：
 *       ⭐任何**顶点格式相同**的请求都改成 {@link RenderType#entityTranslucent} ✓
 *       （⭐这是仓库里 `ThrustGhostRenderer` 验证过的做法 ✓）；</li>
 * </ol>
 * ⚠ 画完必须把 `setShaderColor` 还原成 `(1,1,1,1)` ✗ ★否则后面所有东西都会带这层颜色 ✓。
 */
public class MomoShadowRenderer extends KtuluMomoRenderer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");

    /** ★偏黑＋半透明 */
    private static final float TINT_R = 0.34F;
    private static final float TINT_G = 0.34F;
    private static final float TINT_B = 0.42F;
    private static final float TINT_A = 0.78F;

    public MomoShadowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(KtuluMomo entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        RenderType translucent = RenderType.entityTranslucent(TEXTURE);
        final VertexFormat format = translucent.format();
        MultiBufferSource wrapped = type -> (type != null && type.format() == format)
                ? buffer.getBuffer(translucent)
                : buffer.getBuffer(type);
        RenderSystem.setShaderColor(TINT_R, TINT_G, TINT_B, TINT_A);
        try {
            super.render(entity, entityYaw, partialTick, poseStack, wrapped, packedLight);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
