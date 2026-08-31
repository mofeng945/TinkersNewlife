package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.ProjectionPhantomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 投射咒法 玩家虚影渲染：套用原版玩家模型，皮肤用本地玩家，半透明蓝色调。
 */
public class ProjectionPhantomRenderer extends EntityRenderer<ProjectionPhantomEntity> {

    private final PlayerModel<net.minecraft.world.entity.LivingEntity> model;

    public ProjectionPhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
    }

    @Override
    public void render(ProjectionPhantomEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        // 原版玩家模型空间：scale(-1,-1,1) + 下移 1.501 + 180-yaw
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        // 站立姿态：手臂自然下垂
        model.leftArm.xRot = -1.9F;
        model.rightArm.xRot = -1.9F;
        model.leftArm.zRot = 0.1F;
        model.rightArm.zRot = -0.1F;
        float age = entity.tickCount + partialTick;
        model.setupAnim(entity, 0, 0, age, 0, 0);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
        model.renderToBuffer(poseStack, vc, light, OverlayTexture.NO_OVERLAY, 0.45F, 0.7F, 1.0F, 0.5F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ProjectionPhantomEntity entity) {
        // 虚影皮肤：优先虚影主人（本地玩家通常是施术者），否则默认 Steve
        if (Minecraft.getInstance().player != null) {
            return Minecraft.getInstance().getSkinManager()
                    .getInsecureSkinLocation(Minecraft.getInstance().player.getGameProfile());
        }
        return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
    }
}
