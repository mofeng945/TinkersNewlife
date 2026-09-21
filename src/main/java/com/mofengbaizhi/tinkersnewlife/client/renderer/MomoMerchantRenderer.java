package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 墨默渲染器：套用玩家模型（HumanoidModel / ModelLayers.PLAYER，64x64 玩家皮肤贴图布局）。
 * 走路摆腿/摆臂、跳跃、受击、挥臂攻击等动画均映射玩家模型动画（原版驱动：limbSwing/attackAnim）。
 * 主手格赫罗斯战镰由 ItemInHandLayer 正常渲染；进食时（isEating）收镰刀不渲染手持物品。
 */
public class MomoMerchantRenderer extends HumanoidMobRenderer<MomoMerchant, HumanoidModel<MomoMerchant>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/momo_common.png");

    /**
     * 模型缩放（用户口径：「实体模型大小缩小 0.7 倍，现在有点太高了」✓）。
     * `scale()` 的缩放以**脚底**为原点 ⇒ 缩完她还是站在地上 ✓ 不会浮空/陷地 ✓。
     * 判定箱同步缩到 0.7（见 `ModEntities` 的 `.sized(0.42f, 1.26f)` ✓）⇒ 视觉与碰撞一致 ✓。
     */
    private static final float MODEL_SCALE = 0.7F;

    public MomoMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.35F);   // 影子半径也跟着缩 ✓
        // 玩家模型对应的手持动画：主手（战镰）挥动；进食时收起镰刀
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffer,
                               int packedLight, MomoMerchant entity, float limbSwing, float limbSwingAmount,
                               float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                if (entity.isEating()) return; // 进食动画：镰刀收起来
                super.render(poseStack, buffer, packedLight, entity, limbSwing, limbSwingAmount,
                        partialTick, ageInTicks, netHeadYaw, headPitch);
            }
        });
    }

    @Override
    protected void scale(MomoMerchant entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(MomoMerchant entity) {
        return TEXTURE;
    }
}
