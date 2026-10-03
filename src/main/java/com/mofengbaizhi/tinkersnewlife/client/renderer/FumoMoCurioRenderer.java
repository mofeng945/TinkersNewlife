package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * <b>戴在头上的 fufu（Curios 头部槽）</b>（§894 起，§898 修真根因）——
 * <ol>
 *   <li>从 {@code RenderLayerParent} 拿玩家模型，是 {@link HeadedModel} 就取它的头；</li>
 *   <li>{@code head.translateAndRotate(pose)} ⇒ 完全跟随头部（转头就跟着转 ✓）；</li>
 *   <li>画 {@link com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModel}
 *       （只含 fufu 的空壳人形 ✓）。</li>
 * </ol>
 * <p>⚠ <b>§898 之前一直"看不见"的真根因</b>（已对照 1.20.1 源码确证 ✓）{@code HumanoidModel} 继承
 * {@code AgeableListModel}，而 {@code EntityModel.young} **默认是 true** ⇒ 我们**自己 new** 出来的模型
 * 会走 {@code renderToBuffer} 的"幼年体"分支（{@code scale(1.5/2)} ＋ {@code translate(0, 16/16, 0)} 下移 16px）
 * ⇒ 挂在 head 下的玩偶被缩小并下移 16px、**落进躯干内部** ⇒ 实机什么都看不到 ✗。
 * 玩家模型/盔甲模型是渲染器每帧给它们赋 {@code young} 的 ✓，自己造的这份没人赋 ✗
 * ⇒ 见 {@code FumoMoHeadModelHolder.get()}（显式 {@code young=false} ✓）＋ 这里再确认一次 ✓。
 * <p>另外这里**直接渲染 {@code model.head} 子树**（而不是 {@code renderToBuffer}）⇒ 从结构上绕开幼年体分支 ✓。
 */
public class FumoMoCurioRenderer implements ICurioRenderer {

    /** §895 探针：只打一次，确认 Curios 到底有没有来调渲染器 */
    private static volatile boolean tnl$probeLogged = false;
    /** §898 探针：只打一次，把真实姿态矩阵记下来（以后再出问题就不用猜了 ✓） */
    private static volatile boolean tnl$poseLogged = false;

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
        // §898 ★ 每次渲染都确认 young=false（幼年体分支会把玩偶缩小并下移 16px ⇒ 塞进身体里 ✗）
        model.young = false;
        // §898 ★ 部件强制可见（这个实例是两条渲染路共用的，防别的路径把它关掉 ✗）
        model.head.getAllParts().forEach(part -> {
            part.visible = true;
            part.skipDraw = false;
        });
        // 头部件自身的旋转清零：头部角度已经由 translateAndRotate 带进来了 ✓（否则会转两遍 ✗）
        model.head.xRot = 0.0F;
        model.head.yRot = 0.0F;
        model.head.zRot = 0.0F;
        pose.pushPose();
        headed.getHead().translateAndRotate(pose);
        pose.scale(com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModel.SCALE,
                com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModel.SCALE,
                com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModel.SCALE);
        if (!tnl$poseLogged) {
            tnl$poseLogged = true;
            Matrix4f m = pose.last().pose();
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo").info(
                    "[fufu] 姿态诊断：缩放≈({}, {}, {}) 平移(px)=({}, {}, {}) young={} head 子树部件数={}",
                    String.format("%.3f", m.m00()), String.format("%.3f", m.m11()), String.format("%.3f", m.m22()),
                    String.format("%.2f", m.m03() * 16), String.format("%.2f", m.m13() * 16),
                    String.format("%.2f", m.m23() * 16),
                    model.young, model.head.getAllParts().count());
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(
                FumoMoBlockEntityRenderer.TEXTURE));
        // 直接渲染 head 子树：head 本身是空节点（无方块 ✓），方块全在它的子节点上 ✓
        model.head.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
