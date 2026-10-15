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
        // TNL1096 user: "the hat layer of the momo_common skin is the one that does not follow".
        // In HumanoidModel the hat part is a CHILD of the head, so it should follow for free. This
        // forced twist is a decisive test: if the visible hat does not turn, then the skin we see is
        // not drawn by this model instance at all.
        this.hat.yRot = CHARGING ? 1.0F : 0.0F;
        float t = Math.max(0.0F, Math.min(1.0F, PROGRESS));
        // TNL1092 user: "only the tremble shows, the skeletal animation is gone". The tremble rides on
        // body.xRot, so the hook does run. Log the numbers to see whether t is stuck at an endpoint.
        if (entity.tickCount % 10 == 0) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL1092 pose t={} bodyX={} headX={} rightArmX={} leftArmX={}",
                    t, this.body.xRot, this.head.xRot, this.rightArm.xRot, this.leftArm.xRot);
        }
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

        // TNL1094 user: "the guard is reversed, the arms open forward instead of covering the face,
        // and the throw back never happens". yRot and zRot are flipped so the hands converge inward
        // onto the face, and the release now interpolates to a clearly swung back pose.
        float guardX = -1.35F * up;                       // raise the hands forward and up
        float guardY = -0.45F * up;                       // bring the right hand toward the centre
        float guardZ = 0.90F * up;                        // adduct it in front of the face
        float openX = 1.40F;                              // release: arms swung behind the body
        float openY = 0.50F;
        float openZ = -0.35F;

        this.rightArm.xRot = guardX * (1.0F - backSwing) + openX * backSwing + tremble;
        this.rightArm.yRot = guardY * (1.0F - backSwing) + openY * backSwing;
        this.rightArm.zRot = guardZ * (1.0F - backSwing) + openZ * backSwing;
        this.leftArm.xRot = guardX * (1.0F - backSwing) + openX * backSwing - tremble;
        this.leftArm.yRot = -guardY * (1.0F - backSwing) - openY * backSwing;
        this.leftArm.zRot = -guardZ * (1.0F - backSwing) - openZ * backSwing;

        // legs brace slightly while charging
        this.rightLeg.xRot = 0.12F * up;
        this.leftLeg.xRot = -0.10F * up;
    }
}
