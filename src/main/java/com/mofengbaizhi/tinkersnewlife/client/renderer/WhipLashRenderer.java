package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipLashEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipPhysics;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * <b>鞭身渲染</b>（§1047／§1053）—— 把 {@link WhipPhysics} 那 <b>57 个点</b>连成一条<b>正对镜头</b>的带状面片 ✓。
 *
 * <h2>画法</h2>
 * 每两个相邻点构成一节四边形 ✓；宽度从<b>根部最粗</b>收到<b>梢部最细</b> ✓
 * ⇒ 一眼就是一条鞭子 ✓（不是一根棍子 ✗）。带面始终朝相机张开 ✓（用「段方向 × 视线」当侧向 ✓）
 * ⇒ 从任何角度看都是一条有宽度的鞭 ✓，不会退化成一条线 ✗。
 *
 * <h2>⚠ 渲染类型：只用原版 shader getter（光影包才认 ✓ 本仓的老教训）</h2>
 * 用原版 {@code GameRenderer::getRendertypeEntityCutoutNoCullShader} ✓ ＋ 原版顶点格式
 * {@code NEW_ENTITY} ✓ ＋ 不剔除 ✓ ＋ 正常写深度 ✓ ＋ 真正的 lightmap（鞭子是"实体"，要受光照 ✓，
 * 这点与刀光那种自发光不同 ✓）。
 */
public class WhipLashRenderer extends EntityRenderer<WhipLashEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/entity/whip_lash.png");

    /**
     * 半宽 ＝ <b>照参照模组的逐段碰撞半径收放</b> ✓（0.059 → 0.018，梢端 0.0388 ✓）
     * 再乘一个可读性系数 ✓（纯视觉 ✓ 与物理半径解耦 ✓）。
     */
    private static final float HALF_WIDTH_SCALE = 1.35F;
    /** 顶点色（偏米白的皮革色 ✓ 贴图只提供明暗 ✓） */
    private static final int TINT = 0xE6DCC8;

    private static RenderType whipRenderType;

    public WhipLashRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(WhipLashEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(WhipLashEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        try {
            WhipPhysics physics = entity.physics();
            if (physics == null || !physics.isStarted()) {
                return;
            }
            RenderType type = renderType();
            if (type == null) {
                return;
            }

            Vec3 anchor = entity.getPosition(partialTick);
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            int r = (TINT >> 16) & 0xFF;
            int g = (TINT >> 8) & 0xFF;
            int b = TINT & 0xFF;
            int segments = WhipPhysics.POINTS - 1;

            poseStack.pushPose();
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer consumer = buffer.getBuffer(type);

            for (int i = 0; i < segments; i++) {
                Vec3 a = physics.point(i);
                Vec3 c = physics.point(i + 1);
                Vec3 along = c.subtract(a);
                if (along.lengthSqr() < 1.0E-8D) {
                    continue;
                }
                Vec3 side = along.cross(cam.subtract(a));
                if (side.lengthSqr() < 1.0E-8D) {
                    continue;
                }
                side = side.normalize();

                float f0 = (float) i / segments;
                float f1 = (float) (i + 1) / segments;
                float w0 = (float) (WhipPhysics.segmentRadius(i) * HALF_WIDTH_SCALE);
                float w1 = (float) (WhipPhysics.segmentRadius(i + 1) * HALF_WIDTH_SCALE);

                Vec3 a0 = a.add(side.scale(w0));
                Vec3 a1 = a.subtract(side.scale(w0));
                Vec3 c0 = c.add(side.scale(w1));
                Vec3 c1 = c.subtract(side.scale(w1));

                // 四边形：a0 → a1 → c1 → c0（不剔除 ⇒ 绕序无所谓 ✓）
                emit(consumer, matrix, a0, anchor, packedLight, r, g, b, f0, 0.0F);
                emit(consumer, matrix, a1, anchor, packedLight, r, g, b, f0, 1.0F);
                emit(consumer, matrix, c1, anchor, packedLight, r, g, b, f1, 1.0F);
                emit(consumer, matrix, c0, anchor, packedLight, r, g, b, f1, 0.0F);
            }

            poseStack.popPose();
        } catch (Throwable ignored) {
            // 鞭身画不出来只是少个特效 ✓ 绝不影响游戏 ✓
        }
    }

    /** 世界坐标 → 实体局部坐标（poseStack 原点 = 实体 ✓）后写出顶点 ✓ */
    private static void emit(VertexConsumer consumer, Matrix4f matrix, Vec3 world, Vec3 anchor,
                             int packedLight, int r, int g, int b, float u, float v) {
        consumer.vertex(matrix,
                        (float) (world.x - anchor.x),
                        (float) (world.y - anchor.y),
                        (float) (world.z - anchor.z))
                .color(r, g, b, 255)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static RenderType renderType() {
        if (whipRenderType == null) {
            try {
                whipRenderType = RenderType.create(
                        "tinkersnewlife_whip_lash",
                        DefaultVertexFormat.NEW_ENTITY,
                        VertexFormat.Mode.QUADS,
                        2048, false, true,
                        RenderType.CompositeState.builder()
                                // ⭐ 原版 shader getter（光影包只替换这些 ✓）
                                .setShaderState(new RenderStateShard.ShaderStateShard(
                                        GameRenderer::getRendertypeEntityCutoutNoCullShader))
                                .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, false, false))
                                .setTransparencyState(new RenderStateShard.TransparencyStateShard(
                                        "tinkersnewlife_whip_transparency", () -> {
                                            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                                            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                                        }, () -> {
                                            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                                        }))
                                .setCullState(new RenderStateShard.CullStateShard(false))
                                .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                                .setDepthTestState(new RenderStateShard.DepthTestStateShard("lequal", 515))
                                .setLightmapState(new RenderStateShard.LightmapStateShard(true))
                                .setOverlayState(new RenderStateShard.OverlayStateShard(true))
                                .createCompositeState(false));
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[鞭子] 鞭身渲染类型创建失败，鞭身已跳过: {}", t.toString());
            }
        }
        return whipRenderType;
    }
}
