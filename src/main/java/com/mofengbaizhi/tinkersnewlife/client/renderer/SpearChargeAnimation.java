package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * <b>长矛蓄力/冲刺动画</b>（§844）—— 照搬 MC 1.21.11 原版 {@code SpearAnimations} 的数学 ✓。
 *
 * <h2>来源（官方未混淆客户端反编译 ✓）</h2>
 * 原版在 {@code net.minecraft.client.model.effects.SpearAnimations} 里做这件事 ✓：
 * <ul>
 *   <li>{@code UseParams.fromKineticWeapon(kineticWeapon, time)}：把**按住时长**映射成
 *       raise / sway / lower / raiseBack 四段进度 ✓（阶段时刻＝
 *       {@code delayTicks}、{@code delay+dismountMax}、{@code delay+knockbackMax}、{@code delay+damageMax} ✓）；</li>
 *   <li>{@code firstPersonUse}：第一人称的位移/旋转（本类 {@link #firstPersonUse} ✓ 逐行照抄 ✓）；</li>
 *   <li>{@code thirdPersonHandUse}：第三人称的手臂姿势（本类 {@link #thirdPersonArm} ✓）。</li>
 * </ul>
 * ⚠ 1.20.1 没有 {@code Ease} 这个工具类 ✗ 也没有 kinetic_weapon 组件 ✗ ⇒
 * 缓和函数按 easings.net 的标准式**自己实现**（{@link Ease} ✓ 与 MC 的 Ease 同源 ✓），
 * 阶段时刻改用**我们自己的常量**（下面那组 ✓ 与原版铁矛的推导完全一致 ✓ 见 §842/§844 ✓）。
 */
public final class SpearChargeAnimation {

    // ============================================================
    //  阶段时刻（tick ✓）—— 照原版对"铁矛那一档"的推导 ✓
    //    原版：finishRaising = delayTicks
    //          finishSwaying = delay + dismountMax      (12 + 50  = 62)
    //          startSwaying  = finishSwaying − 20       (42)
    //          finishLowering = delay + knockbackMax    (12 + 135 = 147)
    //          startLowering  = finishLowering − 40     (107)
    //          finishRaisingBack = delay + damageMax    (12 + 225 = 237)  start = −5
    // ============================================================

    // §911 用户口径：「**右键蓄力并刺出，松手收回**」✓ ⇒ 时间轴按这个重排（单位 tick ✓）：
    //   0 ~ RAISE_END    蓄力（把矛端起来 ✓）
    //   RAISE_END ~ THRUST_END  刺出（矛往前送 ✓ 新增 thrust 项 ✓）
    //   > THRUST_END     保持刺出（按住不放就一直端着 ✓ —— 原版那套 42~237 的 sway/lower 全不用了 ✗）
    // 松手后的"收回"由 §911 的短冷却驱动（retractProgress ✓ 见下面两个入口 ✓）
    public static final float RAISE_END = 8.0F;      // 蓄力结束
    public static final float THRUST_END = 16.0F;    // 刺出结束（之后保持 ✓）

    private SpearChargeAnimation() {}

    // ============================================================
    //  进度与缓和（照原版语义 ✓）
    // ============================================================

    private static float progress(float time, float start, float end) {
        return Mth.clamp(Mth.inverseLerp(time, start, end), 0.0F, 1.0F);
    }

    /** 原版 {@code net.minecraft.util.Ease} 的子集 ✓（公式取自 easings.net ✓ 与 MC 同源 ✓） */
    private static final class Ease {
        static float inQuad(float t) { return t * t; }

        static float outQuart(float t) { float u = 1.0F - t; return 1.0F - u * u * u * u; }

        static float outCirc(float t) { float u = t - 1.0F; return (float) Math.sqrt(Math.max(0.0D, 1.0D - u * u)); }

        static float inCirc(float t) { return 1.0F - (float) Math.sqrt(Math.max(0.0D, 1.0D - t * t)); }

        static float inOutSine(float t) { return (float) (-(Math.cos(Math.PI * t) - 1.0D) / 2.0D); }

        static float inOutExpo(float t) {
            if (t <= 0.0F) return 0.0F;
            if (t >= 1.0F) return 1.0F;
            return t < 0.5F
                    ? (float) (Math.pow(2.0D, 20.0D * t - 10.0D) / 2.0D)
                    : (float) ((2.0D - Math.pow(2.0D, -20.0D * t + 10.0D)) / 2.0D);
        }

        static float outCubic(float t) { float u = 1.0F - t; return 1.0F - u * u * u; }

        static float outBack(float t) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float u = t - 1.0F;
            return 1.0F + c3 * u * u * u + c1 * u * u;
        }

        static float inOutBack(float t) {
            float c1 = 1.70158F;
            float c2 = c1 * 1.525F;
            if (t < 0.5F) {
                float u = 2.0F * t;
                return u * u * ((c2 + 1.0F) * u - c2) / 2.0F;
            }
            float u = 2.0F * t - 2.0F;
            return (u * u * ((c2 + 1.0F) * u + c2) + 2.0F) / 2.0F;
        }

        static float inOutElastic(float t) {
            float c5 = (float) (2.0D * Math.PI / 3.0D);
            if (t <= 0.0F) return 0.0F;
            if (t >= 1.0F) return 1.0F;
            return t < 0.5F
                    ? (float) (-(Math.pow(2.0D, 20.0D * t - 10.0D) * Math.sin((20.0D * t - 11.125D) * c5)) / 2.0D)
                    : (float) (Math.pow(2.0D, -20.0D * t + 10.0D) * Math.sin((20.0D * t - 11.125D) * c5) / 2.0D + 1.0D);
        }
    }

    /** 原版 {@code UseParams} ✓ —— 只留我们需要的几项 ✓ */
    private record UseParams(float raiseProgress, float raiseProgressStart, float raiseProgressMiddle,
                             float raiseProgressEnd, float swayProgress, float lowerProgress,
                             float raiseBackProgress, float swayIntensity,
                             float swayScaleSlow, float swayScaleFast, float thrustProgress) {

        static UseParams at(float time) {
            float raiseProgress = progress(time, 0.0F, RAISE_END);
            float raiseProgressStart = progress(raiseProgress, 0.0F, 0.5F);
            float raiseProgressMiddle = progress(raiseProgress, 0.5F, 0.8F);
            float raiseProgressEnd = progress(raiseProgress, 0.8F, 1.0F);
            // §911：刺出进度（新增 ✓）；原版那三段 sway/lower/raiseBack 不再参与（置 0 ✓）
            float thrustProgress = Ease.outCubic(progress(time, RAISE_END, THRUST_END));
            float swayProgress = 0.0F;
            float lowerProgress = 0.0F;
            float raiseBackProgress = 0.0F;
            float swayIntensity = 0.0F;
            float swayScaleSlow = 0.0F;
            float swayScaleFast = 0.0F;
            return new UseParams(raiseProgress, raiseProgressStart, raiseProgressMiddle, raiseProgressEnd,
                    swayProgress, lowerProgress, raiseBackProgress, swayIntensity, swayScaleSlow, swayScaleFast,
                    thrustProgress);
        }
    }

    /** 原版 {@code hitFeedbackAmount}（命中反馈的抖动 ✓ 我们暂用 0 ＝ 不抖 ✓） */
    private static float hitFeedbackAmount(float ticksSinceFeedback) {
        return 0.4F * (Ease.outQuart(progress(ticksSinceFeedback, 1.0F, 3.0F))
                - Ease.inOutSine(progress(ticksSinceFeedback, 3.0F, 10.0F)));
    }

    // ============================================================
    //  第一人称（原版 {@code firstPersonUse} 逐行照抄 ✓）
    // ============================================================

    public static void firstPersonUse(float ticksSinceHitFeedback, PoseStack poseStack, float timeHeld,
                                      float retractProgress, HumanoidArm arm) {
        // §911 收回（松手后 ✓）：把"有效时间"从刺出末端往 0 倒着走 ✓ ⇒ 姿势自然 unwind 回静止 ✓
        //   （不用再写一套反向公式 ✓ 也不引入任何客户端状态 ✓）
        float t = timeHeld > 0.0F ? timeHeld
                : THRUST_END * (1.0F - Mth.clamp(retractProgress, 0.0F, 1.0F));
        UseParams p = UseParams.at(t);
        int invert = arm == HumanoidArm.RIGHT ? 1 : -1;

        poseStack.translate(
                (double) ((float) invert * (p.raiseProgress() * 0.15F + p.raiseProgressEnd() * -0.05F
                        + p.swayProgress() * -0.1F + p.swayScaleSlow() * 0.005F)),
                (double) (p.raiseProgress() * -0.075F + p.raiseProgressMiddle() * 0.075F + p.swayScaleFast() * 0.01F),
                (double) p.raiseProgressStart() * 0.05D + (double) p.raiseProgressEnd() * -0.05D
                        + (double) (p.swayScaleSlow() * 0.005F));
        poseStack.rotateAround(Axis.XP.rotationDegrees(-65.0F * Ease.inOutBack(p.raiseProgress())
                        - 35.0F * p.lowerProgress() + 100.0F * p.raiseBackProgress() - 0.5F * p.swayScaleFast()),
                0.0F, 0.1F, 0.0F);
        poseStack.rotateAround(Axis.YN.rotationDegrees((float) invert * (-90.0F * progress(p.raiseProgress(), 0.5F, 0.55F)
                        + 90.0F * p.swayProgress() + 2.0F * p.swayScaleSlow())),
                (float) invert * 0.15F, 0.0F, 0.0F);
        // §911 刺出：往前送（第一人称里 -Z 是"前"✓）＋ 一点点下压 ✓
        poseStack.translate(0.0D, (double) (p.thrustProgress() * 0.02F), (double) (p.thrustProgress() * -0.28F));
        poseStack.rotateAround(Axis.XP.rotationDegrees(12.0F * p.thrustProgress()), 0.0F, 0.1F, 0.0F);
        poseStack.translate(0.0F, -hitFeedbackAmount(ticksSinceHitFeedback), 0.0F);
    }

    // ============================================================
    //  第三人称手臂姿势（原版 {@code thirdPersonHandUse} 的简化版 ✓）
    // ============================================================

    /**
     * 让手臂摆成"端矛"的样子 ✓（原版还叠了 sway/raise 的细微抖动 ✓ 我们保留同一套公式 ✓）。
     *
     * @param armRotX 手臂 xRot（弧度 ✓ 用 float[] 传出，调用方写回模型 ✓）
     */
    public static void thirdPersonArm(float[] armRotX, float[] armRotY, float[] armRotZ,
                                      boolean rightArm, float headRotX, float headRotY,
                                      float timeHeld, float retractProgress) {
        // §911 收回同样用"有效时间倒着走"这一招 ✓
        if (timeHeld <= 0.0F) timeHeld = THRUST_END * (1.0F - Mth.clamp(retractProgress, 0.0F, 1.0F));
        int invert = rightArm ? 1 : -1;
        float yRot = -0.1F * (float) invert + headRotY;
        float xRot = -1.5707964F + headRotX + 0.8F;
        yRot = (float) Math.PI / 180.0F * Mth.clamp(57.295776F * yRot, -60.0F, 60.0F);
        xRot = (float) Math.PI / 180.0F * Mth.clamp(57.295776F * xRot, -120.0F, 30.0F);
        if (timeHeld > 0.0F) {
            UseParams p = UseParams.at(timeHeld);
            yRot += (float) (-invert) * p.swayScaleFast() * ((float) Math.PI / 180.0F) * p.swayIntensity();
            float zRot = (float) (-invert) * p.swayScaleSlow() * ((float) Math.PI / 180.0F) * p.swayIntensity() * 0.5F;
            xRot += (float) Math.PI / 180.0F * (-40.0F * p.raiseProgressStart() + 30.0F * p.raiseProgressMiddle()
                    - 20.0F * p.raiseProgressEnd() + 20.0F * p.lowerProgress() + 10.0F * p.raiseBackProgress()
                    + 0.6F * p.swayScaleSlow() * p.swayIntensity());
            armRotZ[0] = zRot;
        }
        // §911 刺出：手臂再往前压一点（第三人称就是"把矛捅出去"那下 ✓）
        xRot += (float) Math.PI / 180.0F * (-18.0F * UseParams.at(timeHeld).thrustProgress());
        armRotX[0] = xRot;
        armRotY[0] = yRot;
    }

    /** §911 手里拿着长矛（不管在不在使用中 ✓ 收回阶段也用它 ✓） */
    public static boolean holdingSpear(LivingEntity entity) {
        return entity != null
                && entity.getMainHandItem().getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
    }

    /** 这个生物手上"正在使用"的是不是我们的长矛 ✓ */
    public static boolean usingSpear(LivingEntity entity) {
        ItemStack use = entity.getUseItem();
        return !use.isEmpty()
                && use.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
    }
}
