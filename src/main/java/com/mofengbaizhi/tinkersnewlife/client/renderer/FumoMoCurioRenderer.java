package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * <b>戴在头上的 fufu（Curios 头部饰品栏）</b>（§894 起，§898 修真根因，§899 抽出共用代码）——
 * 结构照诡厄本体的 {@code PlushieCurioRenderer}（**只学结构** ✓ 不抄任何资源 ✓ 那几个玩偶是 ARR ✗）：
 * 取玩家的头 ⇒ {@code head.translateAndRotate(pose)} ⇒ 画玩偶 ✓
 * （玩偶本体就是方块/物品栏那一只 `PlayerModel` ✓ 见 {@link FumoMoHeadRender}）。
 * <p>真正的绘制在 {@link FumoMoHeadRender}（与原版头盔槽那条路**共用** ✓）。
 * <p>⚠ {@code CuriosLayer} 自身**不做任何 PoseStack 变换**（已反汇编确认 ✓）⇒ 我们拿到的就是实体模型根空间 ✓。
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
        // §1079 头顶跟着皮肤走 ✓（饰品栈是哪只 fufu，就画哪张皮肤 ✓；不是 fufu 时用默认贴图 ✓ 不崩 ✓）
        net.minecraft.resources.ResourceLocation texture =
                stack.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.FumoMoBaseItem fumo
                        ? fumo.texture()
                        : FumoMoBlockEntityRenderer.TEXTURE;
        FumoMoHeadRender.render(pose, buffer, light, headed, texture);
    }
}
