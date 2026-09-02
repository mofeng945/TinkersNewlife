package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 墨默渲染器：套用玩家模型（HumanoidModel / ModelLayers.PLAYER，64x64 玩家皮肤贴图布局）。
 * 走路摆腿/摆臂、跳跃、受击、挥臂攻击等动画均映射玩家模型动画（原版驱动：limbSwing/attackAnim）。
 * 主手格赫罗斯战镰由 ItemInHandLayer 正常渲染。
 */
public class MomoMerchantRenderer extends HumanoidMobRenderer<MomoMerchant, HumanoidModel<MomoMerchant>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/momo_common.png");

    public MomoMerchantRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        // 玩家模型对应的手持动画：主手（战镰）挥动
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(MomoMerchant entity) {
        return TEXTURE;
    }
}
