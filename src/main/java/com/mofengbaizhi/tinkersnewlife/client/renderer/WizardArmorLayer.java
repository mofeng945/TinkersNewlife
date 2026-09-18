package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel;
import com.mofengbaizhi.tinkersnewlife.content.item.WizardArmorItem;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 巫师套装的<b>盔甲渲染层</b>：让"穿戴外观"吃上<b>匠魂生成器产出的按材料贴图</b> ✓。
 *
 * <p>为什么必须有这一层：{@code Item#getArmorTexture} 整件只能给**一张**贴图 ✗，
 * 而法帽有 5 组部件、每组要用**自己材料槽**那张图（帽檐锁链基底 / 帽筒魔术布料 / 塔身镶板 /
 * 锥顶第二块锁链基底 / 饰带法袍系带）⇒ 只能在拿到 {@link MultiBufferSource} 的地方**逐组换图** ✓。
 *
 * <p>兜底策略（重要）：本层只在**真的画出东西**之后，才让原版层改画透明贴图（见
 * {@link WizardArmorTextures#isLayerOk()} ✓）；一旦这层抛异常或没画，原版层会照常画
 * {@code all_grey.png} + 顶点着色 ⇒ <b>盔甲绝不会整件消失</b> ✓。
 */
public class WizardArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public WizardArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        try {
            WizardArmorModel model = WizardArmorTextures.model();
            if (model == null) {
                WizardArmorTextures.setLayerOk(false);
                return;
            }
            copyPose(getParentModel(), model);
            boolean drew = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof WizardArmorItem)) continue;
                model.renderMulti(poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY, stack, slot);
                drew = true;
            }
            WizardArmorTextures.setLayerOk(drew);
        } catch (Throwable t) {
            WizardArmorTextures.setLayerOk(false);   // 兜底：交回原版层 ✓
        }
    }

    /** 姿态拷贝：逐部件 copyFrom（同名部件 ✓，自加部件是它们的子节点 ⇒ 自动跟随 ✓） */
    private static void copyPose(PlayerModel<?> src, WizardArmorModel dst) {
        dst.head.copyFrom(src.head);
        dst.hat.copyFrom(src.hat);
        dst.body.copyFrom(src.body);
        dst.rightArm.copyFrom(src.rightArm);
        dst.leftArm.copyFrom(src.leftArm);
        dst.rightLeg.copyFrom(src.rightLeg);
        dst.leftLeg.copyFrom(src.leftLeg);
    }
}