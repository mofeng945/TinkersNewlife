package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.FumoMoDoll;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * <b>原版头盔槽里的 fufu</b>（§899）—— 自己挂的一层 ✓。
 * <p>为什么不走原版的护甲层：{@code HumanoidArmorLayer.renderArmorPiece} 第一句就是
 * <pre>if ($$9 instanceof ArmorItem armoritem) { … getArmorModelHook(…) … }</pre>
 * ⇒ **非 {@code ArmorItem} 的物品在 1.20.1 根本进不去那个分支** ✗（已核对 1.20.1 源码 ✓）。
 * 要让它变成 {@code ArmorItem} 就得放弃 {@code BlockItem}（方块就放不下来了 ✗）⇒ 不值当 ✗
 * ⇒ 改成自己往玩家渲染器上挂一层：物品仍然是 {@code BlockItem}（方块照常能放 ✓），
 * 头盔槽里戴着时由这一层画玩偶 ✓。
 * <p>（之前日志里那条"护甲模型被调用"是本整合包里别的 mod 对 {@code HumanoidArmorLayer} 的 mixin
 * 打出来的，与本层无关 —— 见备忘录 §898 ✓。）
 */
public class FumoMoHeadLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    public FumoMoHeadLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        // 只有"原版头盔槽里放着 fufu"才画 ✓（饰品栏那条路由 FumoMoCurioRenderer 负责 ✓）
        if (!entity.getItemBySlot(EquipmentSlot.HEAD).is(FumoMoDoll.FUMO_MO_ITEM.get())) return;
        if (!(this.getParentModel() instanceof HeadedModel headed)) return;
        FumoMoHeadRender.render(pose, buffer, light, headed);
    }
}
