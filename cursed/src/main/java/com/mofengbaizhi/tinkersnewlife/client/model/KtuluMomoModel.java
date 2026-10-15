package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * TNL1091 Astral Resonance charge, a Warden inspired wind up in three visible stages.
 *
 * progress 0.00 - 0.45 : lean forward a little, lower the head, bring both hands up over the face
 * progress 0.45 - 0.75 : hold the guard, small tremble
 * progress 0.75 - 1.00 : swing both arms back and open them, then the shriek goes off
 *
 * The progress comes from the hidden marker cooldown, which is the one channel proven to reach the
 * client (24 charging=true probe lines), so the animation is driven by a real value and not a guess.
 */
public class KtuluMomoModel extends HumanoidModel<KtuluMomo> {

    /** set by the renderer right before super.render, so setupAnim can read it */
    public static boolean CHARGING = false;
    /** 0 = just started, 1 = about to shriek */
    public static float PROGRESS = 0.0F;

    public KtuluMomoModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(KtuluMomo entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!CHARGING) {
            return;
        }
        float t = Math.max(0.0F, Math.min(1.0F, PROGRESS));
        float tremble = (float) Math.sin(ageInTicks * 2.2F) * 0.04F * Math.min(1.0F, t * 2.0F);

        // stage 1: guard up. stage 3: swing back. between them the arms stay over the face.
        float up;
        if (t < 0.45F) {
            up = t / 0.45F;                       // 0 -> 1 raising the hands
        } else if (t < 0.75F) {
            up = 1.0F;                            // holding
        } else {
            up = 1.0F - (t - 0.75F) / 0.25F;      // 1 -> 0 swinging the hands back down and behind
        }
        float backSwing = t < 0.75F ? 0.0F : (t - 0.75F) / 0.25F;

        // slight forward lean that deepens as it charges, then arches back on release
        this.body.xRot = 0.16F * Math.min(1.0F, t * 1.6F) - 0.34F * backSwing + tremble * 0.4F;
        // head dips while guarding, then lifts for the shriek
        this.head.xRot = 0.28F * up - 0.55F * backSwing + tremble;
        this.head.yRot = netHeadYaw * 0.4F;

        // hands come up in front of the face: forward-up rotation plus an inward twist
        this.rightArm.xRot = -2.35F * up + 0.75F * backSwing + tremble;
        this.rightArm.yRot = 0.30F * up - 0.55F * backSwing;
        this.rightArm.zRot = -0.85F * up + 0.35F * backSwing;
        this.leftArm.xRot = -2.35F * up + 0.75F * backSwing - tremble;
        this.leftArm.yRot = -0.30F * up + 0.55F * backSwing;
        this.leftArm.zRot = 0.85F * up - 0.35F * backSwing;

        // legs brace slightly while charging
        this.rightLeg.xRot = 0.12F * up;
        this.leftLeg.xRot = -0.10F * up;
    }
}
