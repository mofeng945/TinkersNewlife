package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel;
import com.mofengbaizhi.tinkersnewlife.content.item.WizardArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 巫师套装的<b>盔甲渲染层</b>：让"穿戴外观"吃上<b>匠魂生成器产出的按材料贴图</b> ✓。
 *
 * <p>为什么必须有这一层：{@code Item#getArmorTexture} 整件只能给**一张**贴图 ✗，
 * 而法帽分 3 组部件、每组要用**自己材料槽**那张图（帽檐 / 塔身 / 饰带…）⇒ 只能在拿到
 * {@link MultiBufferSource} 的地方**逐组换图** ✓。
 *
 * <p>⭐ 现在做成**泛型**（{@code T extends LivingEntity}）⇒ 玩家、<b>盔甲架（假人）</b>、
 * 以及任何 {@link HumanoidModel} 系渲染器都能挂 ✓
 * （之前只挂玩家渲染器 ✗ ⇒ 假人穿这套盔甲时会"消失"，因为 {@code getArmorTexture} 返回了透明图 ✗）。
 *
 * <p>兜底策略（重要）：本层在**真的画出东西**之后，才让原版层改画透明贴图（见
 * {@link WizardArmorTextures#isLayerOk()} ✓）；一旦这层抛异常 ⇒ 立刻置 false，原版层照常画灰图 ✓
 * ⇒ <b>盔甲绝不会整件消失</b> ✓。
 */
public class WizardArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public WizardArmorLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, T entity,
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
                ItemStack stack = entity.getItemBySlot(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof WizardArmorItem)) continue;
                model.renderMulti(poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY, stack, slot);
                drew = true;
            }
            // ⚠ 只在"确实画了"时置 true，**画不到东西时不要置 false** ✗
            //   （否则别的实体渲染一次就把标志冲掉 ⇒ 同一个实体一会儿双画、一会儿透明 ✗）
            if (drew) { WizardArmorTextures.setLayerOk(true); }
        } catch (Throwable t) {
            WizardArmorTextures.setLayerOk(false);   // 兜底：交回原版层 ✓
        }
    }

    /** 姿态拷贝：逐部件 copyFrom（同名部件 ✓，自加部件是它们的子节点 ⇒ 自动跟随 ✓） */
    private static void copyPose(HumanoidModel<?> src, WizardArmorModel dst) {
        dst.head.copyFrom(src.head);
        dst.hat.copyFrom(src.hat);
        dst.body.copyFrom(src.body);
        dst.rightArm.copyFrom(src.rightArm);
        dst.leftArm.copyFrom(src.leftArm);
        dst.rightLeg.copyFrom(src.rightLeg);
        dst.leftLeg.copyFrom(src.leftLeg);
    }
}