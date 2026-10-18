package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.MomoShadow;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * TNL1282 ★★拉莱耶之影（分身）的渲染器 ✓
 *
 * <p>用户口径：分身外形 = **墨默本体略带透明、颜色偏黑**的形象。
 *
 * <h3>为什么用原版玩家模型</h3>
 * 本体渲染器本来就写着"复用商人墨默的模型（**玩家模型**）" ⇒ 分身直接用
 * {@link HumanoidModel} ＋ 同一张贴图 ✗ ⭐外形一致 ✓
 * （⚠ 上一版让分身继承本体去复用 `KtuluMomoModel`，结果把本体的**血量内核**串了 ✓
 *  见 {@code MomoShadow} 类注释 ✓ 现在分身是独立 Monster ✓）。
 *
 * <h3>⭐ 两条一起做才对</h3>
 * <ol>
 *   <li>⭐**偏黑** ✗：`RenderSystem.setShaderColor(0.34, 0.34, 0.42, alpha)` —— 颜色会乘进贴图 ✓；</li>
 *   <li>⭐**透明** ✗：模型默认走 `entityCutoutNoCull`（★只做 alpha 测试、**不混合** ✓）
 *       ⇒ ⭐把 {@link MultiBufferSource} 包一层 ✗ ⭐同顶点格式的请求改走
 *       {@link RenderType#entityTranslucent} ✓（★`ThrustGhostRenderer` 验证过的做法 ✓）；</li>
 * </ol>
 * ⚠ 画完必须把 `setShaderColor` 还原成 `(1,1,1,1)` ✗ ★否则后面所有东西都会带这层颜色 ✓。
 */
public class MomoShadowRenderer extends HumanoidMobRenderer<MomoShadow, HumanoidModel<MomoShadow>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");

    /** ★偏黑＋半透明 */
    private static final float TINT_R = 0.34F;
    private static final float TINT_G = 0.34F;
    private static final float TINT_B = 0.42F;
    private static final float TINT_A = 0.78F;

    /** ★与本体一致的模型缩放 */
    private static final float MODEL_SCALE = 0.85F;

    public MomoShadowRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.43F);
    }

    @Override
    public ResourceLocation getTextureLocation(MomoShadow entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(MomoShadow entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public void render(MomoShadow entity, float entityYaw, float partialTick, PoseStack poseStack,
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
