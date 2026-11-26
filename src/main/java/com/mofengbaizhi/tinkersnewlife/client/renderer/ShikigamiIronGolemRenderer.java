package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiIronGolem;

/**
 * 魔虚罗渲染器：复用原版铁傀儡模型/纹理/动画，
 * 附加：头顶持续旋转的法轮 + 右手铁剑（退魔之剑）。
 */
public class ShikigamiIronGolemRenderer extends IronGolemRenderer {

    private final ModelPart wheel;
    private final ModelPart sword;

    public ShikigamiIronGolemRenderer(EntityRendererProvider.Context context) {
        super(context);
        wheel = buildWheel();
        sword = buildSword();
    }

    @Override
    public void render(IronGolem entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        // 主体保持原版铁傀儡大小（与碰撞箱一致；双端不再做体型放大，
        // 否则客户端模型/服务端 AABB 错位导致怪物攻击全打头顶上空）
        super.render(entity, entityYaw, partialTick, poseStack, buffer, light);
        if (entity instanceof ShikigamiIronGolem) {
            // 头顶法轮：持续绕 Y 轴旋转（固定贴于原版铁傀儡头顶）
            float age = entity.tickCount + partialTick;
            poseStack.pushPose();
            poseStack.translate(0.0F, 2.9F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 6.0F));
            var vc = buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));
            wheel.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.popPose();
            // 右手铁剑（退魔之剑）
            poseStack.pushPose();
            poseStack.translate(-1.1F, 1.55F, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(75.0F));
            var svc = buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));
            sword.render(poseStack, svc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.popPose();
        }
    }

    /** 头顶法轮：圆盘 + 十字辐条（半径 0.5 格） */
    private static ModelPart buildWheel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("disc", CubeListBuilder.create().addBox(-0.5F, -0.06F, -0.5F, 1.0F, 0.12F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("spoke1", CubeListBuilder.create().addBox(-0.5F, 0.0F, -0.05F, 1.0F, 0.05F, 0.1F), PartPose.ZERO);
        root.addOrReplaceChild("spoke2", CubeListBuilder.create().addBox(-0.05F, 0.0F, -0.5F, 0.1F, 0.05F, 1.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32).bakeRoot();
    }

    /** 铁剑（退魔之剑）：剑刃 + 护手 + 剑柄 */
    private static ModelPart buildSword() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("blade", CubeListBuilder.create().addBox(-0.08F, 0.0F, -0.08F, 0.16F, 1.3F, 0.16F), PartPose.ZERO);
        root.addOrReplaceChild("guard", CubeListBuilder.create().addBox(-0.22F, -0.08F, -0.08F, 0.44F, 0.1F, 0.16F), PartPose.ZERO);
        root.addOrReplaceChild("handle", CubeListBuilder.create().addBox(-0.06F, -0.4F, -0.06F, 0.12F, 0.32F, 0.12F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32).bakeRoot();
    }
}
