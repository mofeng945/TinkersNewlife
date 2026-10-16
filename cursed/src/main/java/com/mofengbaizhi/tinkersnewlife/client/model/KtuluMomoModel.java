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

    /** TNL1156 true only on the frames where the Discordant Chord is being cast */
    public static boolean CHORD = false;
    /** 0 = just started, 1 = about to shriek */
    public static float PROGRESS = 0.0F;
    /** TNL1108 the chord keeps the hands up for a while after the sync flag drops, so the pose does
     *  not snap down while the rings are still travelling outwards. */
    public static long CHORD_HOLD_UNTIL = 0L;

    public KtuluMomoModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(KtuluMomo entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // TNL1101 the hair IS this hat part (hiding it during the charge made it vanish), and the old
        // code set visible = !CHARGING *after* an early return, so the hat never came back. Restore it
        // unconditionally before anything can return.
        // TNL1188 reset the arm offsets every frame; the chord pose shifts them and a stale shift
        // would leak into every other pose.
        this.rightArm.x = 0.0F;
        this.leftArm.x = 0.0F;
        // TNL1187 user: "thin arms". Scale the arm parts across their cross section only, so the arms
        // get slimmer without getting shorter, and do it before any early return so every pose has it.
        float tnl$thin = 0.62F;
        this.rightArm.xScale = tnl$thin;
        this.rightArm.zScale = tnl$thin;
        this.leftArm.xScale = tnl$thin;
        this.leftArm.zScale = tnl$thin;
        this.hat.visible = true;
        this.hat.skipDraw = false;
        this.hat.yScale = 1.0F;
        if (!CHARGING) {
            return;
        }
        // TNL1096 user: "the hat layer of the momo_common skin is the one that does not follow".
        // In HumanoidModel the hat part is a CHILD of the head, so it should follow for free. This
        // forced twist is a decisive test: if the visible hat does not turn, then the skin we see is
        // not drawn by this model instance at all.
        // TNL1097 The user confirmed it is the live boss (B) and that the body tremble IS visible, so
        // this model really is the one drawn. The hat twist not showing then means the hat part is not
        // being drawn at all for this skin. Force it visible, mark it, and log its state once.
        this.hat.visible = true;
        this.hat.skipDraw = false;
        // TNL1098 unmistakable identity test: if the model we pose is the one drawn, the momo has to
        // visibly inflate while charging. The hat state probe says visible=true yRot=1.0 yet nothing
        // moves, so either this model is not what is rendered, or the outer hair is not this part.
        this.body.xScale = CHARGING ? 1.45F : 1.0F;
        this.body.zScale = CHARGING ? 1.45F : 1.0F;
        float t = Math.max(0.0F, Math.min(1.0F, PROGRESS));
        // TNL1092 user: "only the tremble shows, the skeletal animation is gone". The tremble rides on
        // body.xRot, so the hook does run. Log the numbers to see whether t is stuck at an endpoint.
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
        this.body.xRot = 0.22F * Math.min(1.0F, t * 1.6F) - 0.40F * backSwing + tremble * 0.4F;   // TNL1101
        // head dips while guarding, then lifts for the shriek
        this.head.xRot = 0.52F * up - 0.85F * backSwing + tremble;   // TNL1101 stronger, so the hair visibly follows
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
        // TNL1105 Discordant Chord: evoker style casting, both hands held high and waved.
        long nowMs = System.currentTimeMillis();

        // TNL1156 the pose is chosen per frame by the renderer: the chord pose only when
        // the Discordant Chord is the skill that is casting. The old CHORD_HOLD_UNTIL
        // check kept the chord pose alive for 20 s after ANY skill charged, so the
        // sonic boom wind up looked like the chord and the chord itself lost its pose.
        if (CHORD) {
            // TNL1188 taken straight from Goety's CultistModel SPELLCASTING case (verified look):
            // zRot +/-135 degrees raises the hands, a cos wave at 0.6662 gives the chant its swaying,
            // and the shoulder x of +/-5 spreads the arms so the hands do not overlap at the centre.
            float wave = (float) Math.cos(ageInTicks * 0.6662F) * 0.25F;
            float wave2 = (float) Math.cos(ageInTicks * 0.6662F + 0.6F) * 0.25F;
            this.rightArm.x = 5.0F;
            this.leftArm.x = -5.0F;
            this.body.xRot = 0.06F;
            this.head.xRot = -0.18F + wave * 0.05F;      // chin up, chanting
            // TNL1109 user: "the arms are pointing the wrong way again". xRot is the front/back axis,
            // which is why they went forwards. Raising the arms overhead is the zRot (frontal) axis.
            this.rightArm.xRot = 0.0F;
            this.rightArm.yRot = wave;
            this.rightArm.zRot = 2.3561945F + wave;      // 135 degrees, Goety SPELLCASTING
            this.leftArm.xRot = 0.0F;
            this.leftArm.yRot = wave2;
            this.leftArm.zRot = -2.3561945F - wave2;
            this.rightLeg.xRot = 0.0F;
            this.leftLeg.xRot = 0.0F;
            this.hat.xRot = this.head.xRot;
            this.hat.yRot = this.head.yRot;
            this.hat.zRot = this.head.zRot;
            return;
        }

        // TNL1102 user: "you tied the hat to the body, I want it tied to the head". The hat is a root
        // level part in this baked mesh, so copy the head rotation onto it explicitly. If it ends up
        // doubled (because it also inherits from the head), halve these three lines.
        this.hat.xRot = this.head.xRot;
        this.hat.yRot = this.head.yRot;
        this.hat.zRot = this.head.zRot;

        // TNL1099 probes now run AFTER the pose is applied, they used to log the rest values
        if (entity.tickCount % 10 == 0) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL1099 applied t={} bodyX={} headX={} hatY={} rightArmX={}",
                    t, this.body.xRot, this.head.xRot, this.hat.yRot, this.rightArm.xRot);
        }

    }
}
