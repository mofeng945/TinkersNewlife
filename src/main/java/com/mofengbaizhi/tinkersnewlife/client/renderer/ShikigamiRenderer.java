package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.FrogModel;
import net.minecraft.client.model.GoatModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
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
import net.minecraft.util.Mth;

/**
 * 式神渲染器：全部改用原版生物模型 + 原版纹理 + 原版动画公式。
 * <p>
 * 模型映射（贴合生物原型）：玉犬=狼、鵺=幻翼、蚀蠹=蠹虫、蛤蟆=青蛙、川豚=猪、
 * 脱兔=兔子、愈羊=山羊、贯牛=牛、怒角=橙色毛羊、魔虚罗=铁傀儡（头顶旋转轮盘+持铁剑）。
 * <p>
 * 原版模型类的 setupAnim 绑定具体实体类型，直接传式神实体会 CCE，
 * 因此这里只用原版 createBodyLayer() 的几何，动画公式自行复刻。
 */
public class ShikigamiRenderer extends EntityRenderer<ShikigamiEntity> {

    /** 每个式神对应的原版纹理 */
    private static final ResourceLocation[] TEXTURES = {
            new ResourceLocation("minecraft", "textures/entity/wolf/wolf.png"),        // DOG
            new ResourceLocation("minecraft", "textures/entity/phantom.png"),           // NUE
            new ResourceLocation("minecraft", "textures/entity/silverfish.png"),        // SERPENT
            new ResourceLocation("minecraft", "textures/entity/frog/temperate_frog.png"),// TOAD
            new ResourceLocation("minecraft", "textures/entity/pig/pig.png"),           // ELEPHANT
            new ResourceLocation("minecraft", "textures/entity/rabbit/brown.png"),      // RABBIT
            new ResourceLocation("minecraft", "textures/entity/goat/goat.png"),         // DEER
            new ResourceLocation("minecraft", "textures/entity/cow/cow.png"),           // OX
            new ResourceLocation("minecraft", "textures/entity/sheep/sheep.png"),       // TIGER
            new ResourceLocation("minecraft", "textures/entity/iron_golem/iron_golem.png"), // MAHORAGA
    };
    private static final ResourceLocation SHEEP_FUR = new ResourceLocation("minecraft", "textures/entity/sheep/sheep_fur.png");

    /** 每类式神的模型根（原版几何） */
    private final ModelPart[] roots = new ModelPart[ShikigamiType.values().length];
    /** 怒角羊毛层（橙色） */
    private final ModelPart sheepFur;
    /** 魔虚罗头顶轮盘（旋转） */
    private final ModelPart mahoragaWheel;
    /** 魔虚罗右手铁剑（退魔之剑） */
    private final ModelPart ironSword;

    public ShikigamiRenderer(EntityRendererProvider.Context context) {
        super(context);
        roots[ShikigamiType.DOG.ordinal()] = WolfModel.createBodyLayer().bakeRoot();
        roots[ShikigamiType.NUE.ordinal()] = PhantomModel.createBodyLayer().bakeRoot();
        roots[ShikigamiType.SERPENT.ordinal()] = SilverfishModel.createBodyLayer().bakeRoot();
        roots[ShikigamiType.TOAD.ordinal()] = FrogModel.createBodyLayer().bakeRoot().getChild("root");
        roots[ShikigamiType.ELEPHANT.ordinal()] = PigModel.createBodyLayer(CubeDeformation.NONE).bakeRoot();
        roots[ShikigamiType.RABBIT.ordinal()] = RabbitModel.createBodyLayer().bakeRoot();
        roots[ShikigamiType.DEER.ordinal()] = GoatModel.createBodyLayer().bakeRoot();
        roots[ShikigamiType.OX.ordinal()] = CowModel.createBodyLayer().bakeRoot();
        roots[ShikigamiType.TIGER.ordinal()] = SheepModel.createBodyLayer().bakeRoot();
        roots[ShikigamiType.MAHORAGA.ordinal()] = IronGolemModel.createBodyLayer().bakeRoot();
        sheepFur = SheepFurModel.createFurLayer().bakeRoot();
        mahoragaWheel = buildMahoragaWheel();
        ironSword = buildIronSword();
    }

    // ============================================================
    //  动画（复刻原版公式，传入 ShikigamiEntity 通用字段）
    // ============================================================

    /** 应用每类式神的动画（在 render 前调用） */
    private void applyAnim(ShikigamiEntity entity, ShikigamiType type, ModelPart root,
                           float limbSwing, float limbSwingAmount, float ageInTicks,
                           float netHeadYaw, float headPitch) {
        switch (type) {
            case DOG, DEER, OX, ELEPHANT -> {
                // 四足：头 + 腿摆动（QuadrupedModel 公式）
                setPartRot(root, "head", headPitch * Mth.DEG_TO_RAD, netHeadYaw * Mth.DEG_TO_RAD, 0);
                float f = limbSwing * 0.6662F;
                setPartRot(root, "right_hind_leg", Mth.cos(f) * 1.4F * limbSwingAmount, 0, 0);
                setPartRot(root, "left_hind_leg", Mth.cos(f + (float) Math.PI) * 1.4F * limbSwingAmount, 0, 0);
                setPartRot(root, "right_front_leg", Mth.cos(f + (float) Math.PI) * 1.4F * limbSwingAmount, 0, 0);
                setPartRot(root, "left_front_leg", Mth.cos(f) * 1.4F * limbSwingAmount, 0, 0);
            }
            case RABBIT -> {
                // 兔子：后腿大跨度 + 前腿
                setPartRot(root, "head", headPitch * Mth.DEG_TO_RAD, netHeadYaw * Mth.DEG_TO_RAD, 0);
                float f = limbSwing * 0.6662F;
                setPartRot(root, "left_hind_foot", Mth.cos(f) * 1.4F * limbSwingAmount, 0, 0);
                setPartRot(root, "right_hind_foot", Mth.cos(f + (float) Math.PI) * 1.4F * limbSwingAmount, 0, 0);
                setPartRot(root, "left_haunch", Mth.cos(f + (float) Math.PI) * 1.4F * limbSwingAmount, 0, 0);
                setPartRot(root, "right_haunch", Mth.cos(f) * 1.4F * limbSwingAmount, 0, 0);
                setPartRot(root, "left_front_leg", Mth.cos(f + (float) Math.PI) * 1.4F * limbSwingAmount, 0, 0);
                setPartRot(root, "right_front_leg", Mth.cos(f) * 1.4F * limbSwingAmount, 0, 0);
            }
            case NUE -> {
                // 幻翼：翅膀拍动 + 尾巴摆动（PhantomModel 公式；wing_tip 挂在 wing_base 下）
                float t = ageInTicks * 0.3F;
                ModelPart body = root.getChild("body");
                ModelPart lwBase = body.getChild("left_wing_base");
                ModelPart rwBase = body.getChild("right_wing_base");
                lwBase.zRot = Mth.cos(t) * 0.1F;
                rwBase.zRot = -Mth.cos(t) * 0.1F;
                lwBase.getChild("left_wing_tip").zRot = Mth.cos(t + (float) Math.PI) * 0.1F;
                rwBase.getChild("right_wing_tip").zRot = -Mth.cos(t + (float) Math.PI) * 0.1F;
                ModelPart tailBase = body.getChild("tail_base");
                tailBase.xRot = Mth.cos(t) * 0.15F;
                tailBase.getChild("tail_tip").xRot = Mth.cos(t + 0.5F) * 0.15F;
            }
            case SERPENT -> {
                // 蠹虫：身体波浪（segment1..3，1-based）
                float f = ageInTicks * 0.9F;
                for (int i = 1; i <= 3; i++) {
                    setPartRot(root, "segment" + i,
                            Mth.cos(f + i * 0.15F * (float) Math.PI) * (float) Math.PI * 0.05F * (1 + Math.abs(i - 2)), 0, 0);
                }
            }
            case TOAD -> {
                // 青蛙：蹲伏 + 轻微呼吸（不调用原版 AnimationState）
                ModelPart body = root.getChild("body");
                body.y = (float) Math.sin(ageInTicks * 0.1F) * 0.01F;
            }
            case MAHORAGA -> {
                // 铁傀儡：腿摆动（IronGolemModel 公式）
                ModelPart head = root.getChild("head");
                head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
                head.xRot = headPitch * Mth.DEG_TO_RAD;
                float wave = Mth.triangleWave(limbSwing, 13.0F) * limbSwingAmount;
                root.getChild("right_leg").xRot = -1.5F * wave;
                root.getChild("left_leg").xRot = 1.5F * wave;
                // 轮盘持续旋转
                mahoragaWheel.yRot = ageInTicks * 0.15F;
            }
            default -> {}
        }
    }

    private static void setPartRot(ModelPart root, String name, float xRot, float yRot, float zRot) {
        if (root.hasChild(name)) {
            ModelPart part = root.getChild(name);
            part.xRot = xRot;
            part.yRot = yRot;
            part.zRot = zRot;
        }
    }

    @Override
    public void render(ShikigamiEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        ShikigamiType type = entity.getShikigamiType();
        int ord = type.ordinal();
        ModelPart root = roots[ord];
        float scale = entity.getShikigamiScale();
        // 大小调整：蠹虫/兔子偏小、铁傀儡偏大，按生物原型微调
        if (type == ShikigamiType.SERPENT) scale *= 1.6F;
        if (type == ShikigamiType.RABBIT) scale *= 1.4F;
        if (type == ShikigamiType.MAHORAGA) scale *= 0.85F;

        // 动画
        float limbSwing = entity.walkAnimation.position(partialTick);
        float limbSwingAmount = entity.walkAnimation.speed(partialTick);
        float ageInTicks = entity.tickCount + partialTick;
        applyAnim(entity, type, root, limbSwing, limbSwingAmount, ageInTicks,
                entity.yHeadRot - entity.getYRot(), entity.getXRot());

        // 原版模型空间约定：scale(-1,-1,1) + 下移 1.501 + 180-yaw 转向（头部在 -Z）
        poseStack.pushPose();
        poseStack.scale(-scale, -scale, scale);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));

        // 玉犬黑白变体：白 / 黑染色（基于原版狼纹理）
        float[] tint = new float[]{1.0F, 1.0F, 1.0F};
        if (type == ShikigamiType.DOG) {
            tint = entity.getVariant() == 0 ? new float[]{1.0F, 1.0F, 1.0F} : new float[]{0.18F, 0.18F, 0.22F};
        }

        ResourceLocation tex = TEXTURES[ord];
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(tex));
        root.render(poseStack, vc, light, OverlayTexture.NO_OVERLAY, tint[0], tint[1], tint[2], 1.0F);

        // 怒角：橙色羊毛层
        if (type == ShikigamiType.TIGER) {
            VertexConsumer furVc = buffer.getBuffer(RenderType.entityCutout(SHEEP_FUR));
            sheepFur.render(poseStack, furVc, light, OverlayTexture.NO_OVERLAY, 1.0F, 0.55F, 0.1F, 1.0F);
        }

        // 魔虚罗：头顶旋转轮盘 + 右手铁剑
        if (type == ShikigamiType.MAHORAGA) {
            renderMahoragaExtras(poseStack, buffer, light);
        }
        poseStack.popPose();
    }

    /** 魔虚罗头顶轮盘 + 右手铁剑（在铁傀儡模型空间内，16 单位 = 1 格） */
    private void renderMahoragaExtras(PoseStack pose, MultiBufferSource buffer, int light) {
        // 头顶轮盘：定位到头部上方并绕 Y 轴持续旋转
        pose.pushPose();
        pose.translate(0.0F, -34.0F, 0.0F); // 铁傀儡头顶约 -2.1 格（模型 y 负向上）
        pose.mulPose(Axis.YP.rotation(mahoragaWheel.yRot));
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(TEXTURES[ShikigamiType.MAHORAGA.ordinal()]));
        mahoragaWheel.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();

        // 右手铁剑（退魔之剑）：跟随右臂位置，剑尖向下
        ModelPart root = roots[ShikigamiType.MAHORAGA.ordinal()];
        if (root.hasChild("right_arm")) {
            ModelPart arm = root.getChild("right_arm");
            pose.pushPose();
            arm.translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(25.0F));
            pose.translate(0.0F, -14.0F, 0.0F); // 沿臂长移动到手部
            VertexConsumer swordVc = buffer.getBuffer(RenderType.entityCutout(TEXTURES[ShikigamiType.MAHORAGA.ordinal()]));
            ironSword.render(pose, swordVc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            pose.popPose();
        }
    }

    // ============================================================
    //  自定义部件：魔虚罗轮盘 + 铁剑（16 单位 = 1 格，与原版模型一致）
    // ============================================================

    /** 头顶法轮：薄圆盘 + 十字辐条 */
    private static ModelPart buildMahoragaWheel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 盘面（半径 8 单位 = 0.5 格，厚 1.5）
        root.addOrReplaceChild("disc", CubeListBuilder.create().addBox(-8.0F, 0.0F, -8.0F, 16.0F, 1.5F, 16.0F), PartPose.ZERO);
        // 十字辐条（略高出盘面）
        root.addOrReplaceChild("spoke1", CubeListBuilder.create().addBox(-8.0F, 0.5F, -1.0F, 16.0F, 1.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("spoke2", CubeListBuilder.create().addBox(-1.0F, 0.5F, -8.0F, 2.0F, 1.0F, 16.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }

    /** 铁剑（退魔之剑）：剑刃 + 护手 + 剑柄（剑尖向下） */
    private static ModelPart buildIronSword() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // 剑刃：长 20 单位（1.25 格）
        root.addOrReplaceChild("blade", CubeListBuilder.create().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 20.0F, 2.0F), PartPose.ZERO);
        // 护手
        root.addOrReplaceChild("guard", CubeListBuilder.create().addBox(-3.0F, -1.0F, -1.5F, 6.0F, 2.0F, 3.0F), PartPose.ZERO);
        // 剑柄（向上）
        root.addOrReplaceChild("handle", CubeListBuilder.create().addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32).bakeRoot();
    }

    @Override
    public ResourceLocation getTextureLocation(ShikigamiEntity entity) {
        return TEXTURES[entity.getShikigamiType().ordinal()];
    }
}
