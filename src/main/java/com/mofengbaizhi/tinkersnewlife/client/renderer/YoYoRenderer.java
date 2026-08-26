package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import java.util.UUID;

/**
 * 悠悠球渲染器：渲染旋转的悠悠球轮子，并绘制玩家手到实体的连线（弓弦颜色）。
 * <p>
 * 飞行/飞回阶段：绕 Z 轴旋转模拟轮子滚动；停滞阶段：缓慢自转。
 */
public class YoYoRenderer extends EntityRenderer<YoYoEntity> {

    public YoYoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(YoYoEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        var stack = entity.getDisplayItem();
        if (stack.isEmpty()) return;

        // 绘制玩家手到实体的连线（弓弦颜色）
        renderLine(entity, partialTicks, poseStack, buffer);

        poseStack.pushPose();

        float rotation;
        int phase = entity.getPhase();
        if (phase == YoYoEntity.PHASE_STALLED) {
            // 停滞：缓慢自转
            rotation = (entity.tickCount + partialTicks) * 0.5f;
        } else {
            // 飞行/飞回：快速旋转模拟滚动
            rotation = (entity.tickCount + partialTicks) * 1.5f;
        }

        // 绕 Z 轴旋转（轮子平面垂直于视线）
        Quaternionf quat = new Quaternionf().rotationZ(rotation);
        poseStack.mulPose(quat);

        poseStack.scale(0.6f, 0.6f, 0.6f);

        var itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, packedLight, 0,
                poseStack, buffer, entity.level(), 0);

        poseStack.popPose();
    }

    /**
     * 绘制玩家手到悠悠球实体的连线。
     * <p>
     * 线从玩家手位置（插值后的位置）到实体位置，颜色取弓弦材质颜色（ARGB）。
     */
    private void renderLine(YoYoEntity entity, float partialTicks,
                            PoseStack poseStack, MultiBufferSource buffer) {
        UUID ownerUuid = entity.getOwnerUUID();
        if (ownerUuid == null) return;

        // 玩家插值后的位置（发射时手在眼睛下方一点）
        Player player = entity.level().getPlayerByUUID(ownerUuid);
        if (player == null) return;

        Vec3 entityPos = entity.getPosition(partialTicks);
        Vec3 playerPos = player.getPosition(partialTicks).add(0, player.getEyeHeight() - 0.2, 0);

        // 相对坐标（渲染器坐标系原点为实体位置）
        float dx = (float) (playerPos.x - entityPos.x);
        float dy = (float) (playerPos.y - entityPos.y);
        float dz = (float) (playerPos.z - entityPos.z);

        // 弓弦颜色：从弓弦部件材质 VariantId 解析（MaterialTooltipCache，客户端专用）
        float r, g, b, a;
        String variantStr = entity.getBowstringVariant();
        TextColor textColor = null;
        if (variantStr != null && !variantStr.isEmpty()) {
            try {
                textColor = MaterialTooltipCache.getColor(MaterialVariantId.tryParse(variantStr));
            } catch (Exception ignored) {}
        }
        if (textColor != null) {
            int rgb = textColor.getValue();
            r = ((rgb >> 16) & 0xFF) / 255.0f;
            g = ((rgb >> 8) & 0xFF) / 255.0f;
            b = (rgb & 0xFF) / 255.0f;
            a = 1.0f;
        } else {
            // 默认白色
            r = 1.0f; g = 1.0f; b = 1.0f; a = 1.0f;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        Matrix4f mat = poseStack.last().pose();

        // 第一段：玩家手 → 实体（起点在玩家手，终点在实体）
        consumer.vertex(mat, dx, dy, dz).color(r, g, b, a).normal(0, 1, 0).endVertex();
        consumer.vertex(mat, 0, 0, 0).color(r, g, b, a).normal(0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(YoYoEntity entity) {
        return null;
    }
}
