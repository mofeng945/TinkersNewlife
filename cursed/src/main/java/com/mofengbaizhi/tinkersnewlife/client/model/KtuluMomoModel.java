package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * TNL1089 Warden style wind up for Astral Resonance.
 *
 * The pose has to be applied inside the model's own setupAnim: the renderer's scale hook looked like
 * the right place, but the log proved the flag did arrive (24 charging=true lines) while nothing
 * showed, so setupAnim runs after scale and was overwriting us. A dedicated model avoids that.
 */
public class KtuluMomoModel extends HumanoidModel<KtuluMomo> {

    public KtuluMomoModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(KtuluMomo entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!entity.tnl$isCharging()) {
            return;
        }
        float shake = (float) Math.sin(ageInTicks * 1.7F) * 0.05F;
        this.body.xRot = -0.38F + shake;
        this.head.xRot = -0.45F + shake * 0.5F;
        this.rightArm.xRot = -1.95F + shake;
        this.rightArm.yRot = 0.55F;
        this.leftArm.xRot = -1.95F - shake;
        this.leftArm.yRot = -0.55F;
        this.rightLeg.xRot = 0.28F;
        this.leftLeg.xRot = -0.28F;
    }
}
