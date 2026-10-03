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
        // §888 用户口径：「物品栏渲染直接继承方块渲染」✓ —— 就是说**同一个 renderDoll()** ✓
        //   （姿势/比例/帽子层全部与放下的方块一致 ✓），这里只多叠一个**观察角度** ✓：
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));   // 与方块同一朝向 ✓
        pose.mulPose(Axis.YP.rotationDegrees(35.0F));    // ← 转个角度：露出侧脸，不再正对镜头 ✓
        pose.mulPose(Axis.XP.rotationDegrees(-10.0F));   // ← 略微俯视 ⇒ 看得见头顶/帽子 ✓
        // §889 用户口径：「现在太小有点看不清」⇒ 按显示场合放大 ✓（方块那边不动 ✓）
        float zoom = switch (ctx) {
            case GUI, FIXED -> 1.95F;                    // 背包/展示框：铺满格子 ✓
            case GROUND -> 1.60F;
            default -> 1.45F;                            // 手持：别挡住视野 ✓
        };
        pose.scale(zoom, zoom, zoom);
        FumoMoBlockEntityRenderer.renderDoll(model, pose, buffer, light, overlay);
        pose.popPose();
    }
}
