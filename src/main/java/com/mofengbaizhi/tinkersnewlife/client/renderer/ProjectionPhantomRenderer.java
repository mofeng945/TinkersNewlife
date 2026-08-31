package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.ProjectionPhantomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 投射咒法 玩家虚影渲染：蓝色半透明人形（box 拼装）。
 */
public class ProjectionPhantomRenderer extends EntityRenderer<ProjectionPhantomEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/projection_phantom.png");
    private final PhantomModel model = new PhantomModel();

    public ProjectionPhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ProjectionPhantomEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.renderToBuffer(poseStack, vc, light, OverlayTexture.NO_OVERLAY, 0.4F, 0.7F, 1.0F, 0.55F);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ProjectionPhantomEntity entity) {
        return TEXTURE;
    }

    /** 蓝色虚影人形模型 */
    private static final class PhantomModel extends EntityModel<ProjectionPhantomEntity> {
        private final ModelPart root;

        PhantomModel() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition r = mesh.getRoot();
            // 头
            r.addOrReplaceChild("head", CubeListBuilder.create().addBox(-0.25F, -0.5F, -0.25F, 0.5F, 0.5F, 0.5F), PartPose.offset(0, -1.6F, 0));
            // 躯干
            r.addOrReplaceChild("body", CubeListBuilder.create().addBox(-0.3F, -0.5F, -0.2F, 0.6F, 0.8F, 0.4F), PartPose.offset(0, -0.8F, 0));
            // 腿
            r.addOrReplaceChild("leg_l", CubeListBuilder.create().addBox(-0.28F, 0, -0.15F, 0.28F, 0.8F, 0.3F), PartPose.offset(0, 0, 0));
            r.addOrReplaceChild("leg_r", CubeListBuilder.create().addBox(0, 0, -0.15F, 0.28F, 0.8F, 0.3F), PartPose.offset(0, 0, 0));
            // 手臂
            r.addOrReplaceChild("arm_l", CubeListBuilder.create().addBox(-0.28F, -0.1F, -0.12F, 0.24F, 0.7F, 0.24F), PartPose.offset(-0.3F, -1.35F, 0));
            r.addOrReplaceChild("arm_r", CubeListBuilder.create().addBox(0.04F, -0.1F, -0.12F, 0.24F, 0.7F, 0.24F), PartPose.offset(0.3F, -1.35F, 0));
            root = LayerDefinition.create(mesh, 16, 16).bakeRoot();
        }

        @Override
        public void setupAnim(ProjectionPhantomEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {}

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay,
                                   float r, float g, float b, float a) {
            root.render(pose, vc, light, overlay, r, g, b, a);
        }
    }
}
