package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiType;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.BatModel;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.FrogModel;
import net.minecraft.client.model.GoatModel;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.WolfModel;
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
 * 式神渲染器：复用原版模型（狼/蝙蝠/蛙/兔/牛/山羊）+ 自定义盒模型（大蛇/满象/魔虚罗），
 * 统一使用纯白纹理并按式神类型染色（半透明"咒灵"质感），体型随施术者亲和/输出缩放。
 * 注：不调用 setupAnim，模型以默认姿态渲染（静态姿态），避免类型转换问题。
 */
public class ShikigamiRenderer extends EntityRenderer<ShikigamiEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/shikigami/white.png");

    private final EntityModel wolf;
    private final EntityModel bat;
    private final EntityModel frog;
    private final EntityModel rabbit;
    private final EntityModel cow;
    private final EntityModel goat;
    private final EntityModel serpent;
    private final EntityModel elephant;
    private final EntityModel mahoraga;

    public ShikigamiRenderer(EntityRendererProvider.Context context) {
        super(context);
        wolf = new WolfModel(WolfModel.createBodyLayer().bakeRoot());
        bat = new BatModel(BatModel.createBodyLayer().bakeRoot());
        frog = new FrogModel(FrogModel.createBodyLayer().bakeRoot());
        rabbit = new RabbitModel(RabbitModel.createBodyLayer().bakeRoot());
        cow = new CowModel(CowModel.createBodyLayer().bakeRoot());
        goat = new GoatModel(GoatModel.createBodyLayer().bakeRoot());
        serpent = new BoxModel(buildSerpent());
        elephant = new BoxModel(buildElephant());
        mahoraga = new BoxModel(buildMahoraga());
    }

    @Override
    public void render(ShikigamiEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        ShikigamiType type = entity.getShikigamiType();
        EntityModel model = switch (type) {
            case NUE -> bat;
            case SERPENT -> serpent;
            case TOAD -> frog;
            case ELEPHANT -> elephant;
            case RABBIT -> rabbit;
            case DEER -> goat;
            case OX -> cow;
            case MAHORAGA -> mahoraga;
            case TIGER -> wolf;      // 虎葬：狼模型放大 + 橙色
            default -> wolf;         // 玉犬：狼模型（白/黑变体）
        };
        float scale = entity.getShikigamiScale();
        if (type == ShikigamiType.TIGER) scale *= 1.6F;
        boolean vanillaModel = type != ShikigamiType.SERPENT
                && type != ShikigamiType.ELEPHANT && type != ShikigamiType.MAHORAGA;

        poseStack.pushPose();
        if (vanillaModel) {
            // 原版模型空间约定（与 LivingEntityRenderer 一致）：-1,-1 翻转 + 下移 1.501；
            // 原版模型头部在 -Z，用 180 - yaw 转向（朝向正确）
            poseStack.scale(-scale, -scale, scale);
            poseStack.translate(0.0F, -1.501F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entity.getYRot()));
        } else {
            // 自定义盒模型：y 向上、头部在 +Z、脚底在 y=0，直接置于实体位置，用 -yaw 转向
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        }
        // 鵺：悬浮高度 + 轻盈浮动
        if (type == ShikigamiType.NUE) {
            poseStack.translate(0.0, 0.35F + (float) Math.sin(entity.tickCount * 0.1F) * 0.08F, 0.0);
        }

        float[] tint = tint(type, entity.getVariant());
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        model.renderToBuffer(poseStack, vc, light, OverlayTexture.NO_OVERLAY, tint[0], tint[1], tint[2], 0.92F);
        poseStack.popPose();
    }

    /** 每类式神的染色（玉犬按变体分黑白） */
    private static float[] tint(ShikigamiType type, int variant) {
        return switch (type) {
            case DOG -> variant == 0 ? new float[]{0.95F, 0.95F, 0.95F} : new float[]{0.12F, 0.12F, 0.15F};
            case NUE -> new float[]{0.45F, 0.25F, 0.65F};
            case SERPENT -> new float[]{0.20F, 0.70F, 0.35F};
            case TOAD -> new float[]{0.25F, 0.65F, 0.25F};
            case ELEPHANT -> new float[]{0.65F, 0.65F, 0.70F};
            case RABBIT -> new float[]{0.80F, 0.60F, 0.40F};
            case DEER -> new float[]{0.85F, 0.70F, 0.50F};
            case OX -> new float[]{0.55F, 0.40F, 0.30F};
            case TIGER -> new float[]{0.95F, 0.55F, 0.15F};
            case MAHORAGA -> new float[]{0.75F, 0.15F, 0.15F};
        };
    }

    // ============================================================
    //  自定义盒模型（大蛇 / 满象 / 魔虚罗）
    // ============================================================

    private static ModelPart buildSerpent() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("tail", CubeListBuilder.create().addBox(-0.25F, -0.25F, -2.0F, 0.5F, 0.5F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("seg1", CubeListBuilder.create().addBox(-0.3F, -0.3F, -1.0F, 0.6F, 0.6F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("seg2", CubeListBuilder.create().addBox(-0.35F, -0.35F, 0.0F, 0.7F, 0.7F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("seg3", CubeListBuilder.create().addBox(-0.4F, -0.4F, 1.0F, 0.8F, 0.8F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create().addBox(-0.45F, -0.45F, 1.9F, 0.9F, 0.9F, 0.9F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    private static ModelPart buildElephant() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().addBox(-0.7F, -0.3F, -0.8F, 1.4F, 1.1F, 1.6F), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create().addBox(-0.45F, 0.0F, 0.7F, 0.9F, 0.9F, 0.8F), PartPose.ZERO);
        root.addOrReplaceChild("trunk", CubeListBuilder.create().addBox(-0.15F, -0.9F, 1.2F, 0.3F, 1.1F, 0.3F), PartPose.ZERO);
        root.addOrReplaceChild("leg_fl", CubeListBuilder.create().addBox(-0.15F, 0.0F, -0.15F, 0.3F, 0.7F, 0.3F), PartPose.offset(-0.45F, 0.0F, -0.5F));
        root.addOrReplaceChild("leg_fr", CubeListBuilder.create().addBox(-0.15F, 0.0F, -0.15F, 0.3F, 0.7F, 0.3F), PartPose.offset(0.45F, 0.0F, -0.5F));
        root.addOrReplaceChild("leg_bl", CubeListBuilder.create().addBox(-0.15F, 0.0F, -0.15F, 0.3F, 0.7F, 0.3F), PartPose.offset(-0.45F, 0.0F, 0.5F));
        root.addOrReplaceChild("leg_br", CubeListBuilder.create().addBox(-0.15F, 0.0F, -0.15F, 0.3F, 0.7F, 0.3F), PartPose.offset(0.45F, 0.0F, 0.5F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    private static ModelPart buildMahoraga() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().addBox(-0.5F, 0.6F, -0.3F, 1.0F, 1.2F, 0.6F), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create().addBox(-0.3F, 1.8F, -0.3F, 0.6F, 0.6F, 0.6F), PartPose.ZERO);
        // 头顶法轮
        root.addOrReplaceChild("wheel", CubeListBuilder.create().addBox(-0.55F, 1.9F, -0.55F, 1.1F, 0.9F, 0.1F), PartPose.ZERO);
        // 四臂
        root.addOrReplaceChild("arm_r1", CubeListBuilder.create().addBox(-0.1F, 0.0F, -0.1F, 0.2F, 0.9F, 0.2F), PartPose.offset(-0.9F, 0.8F, -0.2F));
        root.addOrReplaceChild("arm_r2", CubeListBuilder.create().addBox(-0.1F, 0.0F, -0.1F, 0.2F, 0.9F, 0.2F), PartPose.offset(-0.9F, 0.8F, 0.15F));
        root.addOrReplaceChild("arm_l1", CubeListBuilder.create().addBox(-0.1F, 0.0F, -0.1F, 0.2F, 0.9F, 0.2F), PartPose.offset(0.9F, 0.8F, -0.2F));
        root.addOrReplaceChild("arm_l2", CubeListBuilder.create().addBox(-0.1F, 0.0F, -0.1F, 0.2F, 0.9F, 0.2F), PartPose.offset(0.9F, 0.8F, 0.15F));
        root.addOrReplaceChild("leg_l", CubeListBuilder.create().addBox(-0.15F, 0.0F, -0.15F, 0.3F, 0.6F, 0.3F), PartPose.offset(-0.3F, 0.0F, -0.1F));
        root.addOrReplaceChild("leg_r", CubeListBuilder.create().addBox(-0.15F, 0.0F, -0.15F, 0.3F, 0.6F, 0.3F), PartPose.offset(0.1F, 0.0F, -0.1F));
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** 静态盒模型（无动画） */
    private static final class BoxModel extends EntityModel<ShikigamiEntity> {
        private final ModelPart root;
        BoxModel(ModelPart root) {
            this.root = root;
        }
        @Override
        public void setupAnim(ShikigamiEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {}
        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay,
                                   float r, float g, float b, float a) {
            root.render(pose, vc, light, overlay, r, g, b, a);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(ShikigamiEntity entity) {
        return TEXTURE;
    }
}
