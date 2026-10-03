package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * <b>戴在头上的 fufu（Curios 头部槽）</b>（§894）——
 * 结构照诡厄本体的 {@code PlushieCurioRenderer}（已反编译核对 ✓ 只学结构，不抄任何资源 ✓）：
 * <ol>
 *   <li>从 {@code RenderLayerParent} 拿到玩家模型，若是 {@link HeadedModel} 就取它的头；</li>
 *   <li>{@code head.translateAndRotate(pose)} ⇒ **完全跟随头部**（转头就跟着转 ✓）；</li>
 *   <li>{@code scale(1.875, -1.875, -1.875)} ＋ {@code translate(-0.5, 0.255, -0.5)}
 *       —— 把"方块尺度(0~1、y 向下)"换成局部 1/1.875 的模型尺度 ✓（诡厄用的就是这个数 ✓）；</li>
 *   <li>画我们自己的 {@link com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModel}
 *       （只含 fufu 的空壳人形 ✓ UV 全标准皮肤布局 ✓）。</li>
 * </ol>
 */
public class FumoMoCurioRenderer implements ICurioRenderer {

    /** §895 探针：只打一次，确认 Curios 到底有没有来调渲染器 */
    private static volatile boolean tnl$probeLogged = false;

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
                                                                         PoseStack pose,
                                                                         RenderLayerParent<T, M> renderLayerParent,
                                                                         MultiBufferSource buffer, int light,
                                                                         float limbSwing, float limbSwingAmount,
                                                                         float partialTicks, float ageInTicks,
                                                                         float netHeadYaw, float headPitch) {
        if (!tnl$probeLogged) {
            tnl$probeLogged = true;
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo").info(
                    "[fufu] Curios 渲染器被调用 ✓ 物品={} 玩家模型={} 是不是 HeadedModel={} 槽位={}",
                    stack.getItem(), renderLayerParent.getModel().getClass().getSimpleName(),
                    renderLayerParent.getModel() instanceof HeadedModel, slotContext.identifier());
        }
        if (stack.isEmpty()) return;
        if (!(renderLayerParent.getModel() instanceof HeadedModel headed)) return;
        com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModel model =
                com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModelHolder.get();
        // 头部件自身的旋转清零：头部角度已经由 translateAndRotate 带进来了 ✓（否则会转两遍 ✗）
        model.head.xRot = 0.0F;
        model.head.yRot = 0.0F;
        model.head.zRot = 0.0F;
        pose.pushPose();
        headed.getHead().translateAndRotate(pose);
        pose.scale(1.875F, -1.875F, -1.875F);
        pose.translate(-0.5D, 0.255D, -0.5D);
        model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(FumoMoBlockEntityRenderer.TEXTURE)),
                light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }
}
