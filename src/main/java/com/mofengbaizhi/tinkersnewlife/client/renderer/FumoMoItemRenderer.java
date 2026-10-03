package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * <b>物品栏里的 fufu</b>（§881 用户口径：「物品栏显示和方块显示统一」✓）：
 * 用 {@link BlockEntityWithoutLevelRenderer} 渲染**同一个玩家模型、同一套姿势** ✓
 * ⇒ 背包/手里的 fufu 和放下的 fufu 长得完全一样 ✓。
 */
public class FumoMoItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** 单例（Forge 的 {@code RegisterClientExtensionsEvent} 里注册 ✓） */
    public static final FumoMoItemRenderer INSTANCE = new FumoMoItemRenderer();

    private PlayerModel<?> model;

    public FumoMoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        if (model == null) model = FumoMoBlockEntityRenderer.newModel();
        pose.pushPose();
        // GUI/手持里也稍微转个角度，看得见脸 ✓（世界坐标那套不变 ✓）
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        FumoMoBlockEntityRenderer.renderDoll(model, pose, buffer, light, overlay);
        pose.popPose();
    }
}
