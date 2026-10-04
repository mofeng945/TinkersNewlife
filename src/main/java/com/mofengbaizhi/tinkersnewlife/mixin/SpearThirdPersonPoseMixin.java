package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.renderer.SpearChargeAnimation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>长矛蓄力：第三人称手臂姿势</b>（§848）。
 *
 * <h2>问题（用户实测 ✓）</h2>
 * 「**手持时正常，蓄力时（第三人称）矛尖朝后**」✗ —— 原因是 1.20.1 把 {@code UseAnim.SPEAR}
 * 映射成了**三叉戟那套"预备投掷"姿势**（{@code ArmPose.THROW_TRIDENT} ✗ 矛尖朝后/朝上 ✗），
 * 与物品模型无关（所以平时手持正常 ✓）。
 *
 * <h2>做法</h2>
 * 用官方 1.21.11 的第三人称持矛姿势覆盖它：{@code SpearAnimations.thirdPersonHandUse}
 * （数学已搬到 {@link SpearChargeAnimation#thirdPersonArm} ✓ 逐行照抄 ✓）。
 * <ul>
 *   <li>注入点选 {@code setupAnim} 的 <b>TAIL</b> ✓ —— vanilla 先把 {@code THROW_TRIDENT} 摆好 ✓，
 *       我们在最后**改掉手臂三个旋转** ✓ ⇒ 一定生效 ✓（{@code RenderPlayerEvent.Pre} 那种太早的钩子会被覆盖 ✗
 *       这正是 §845 踩过的坑 ✓）；</li>
 *   <li>只对"**正在使用我们的长矛**"的角色生效 ✓ 其它人类模型/其它物品一概不动 ✓。</li>
 * </ul>
 *
 * <h2>⚠ 双份注解（本仓惯例 ✓ 因为没有 Mixin 注解处理器 ✗ 不生成 refmap）</h2>
 * named（开发用 ✓）＋ SRG 字面量 {@code remap = false}（生产用 ✓）。
 * SRG 名来源：{@code setupAnim (LivingEntity;FFFFF)V → m_6973_} ✓
 * （Mojang official → obf → {@code obf_to_srg} 两步查得 ✓ 与 §837 验证 {@code aiStep} 时同一套流程 ✓）。
 * 本仓 {@code defaultRequire = 0} ⇒ 万一没注入上也**不会崩** ✓ 只是姿势恢复原样 ✓。
 */
@Mixin(HumanoidModel.class)
public abstract class SpearThirdPersonPoseMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void tinkersnewlife$spearChargePose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                float ageInTicks, float netHeadYaw, float headPitch,
                                                CallbackInfo ci) {
        tinkersnewlife$applySpearPose(entity);
    }

    @Inject(method = "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", remap = false, at = @At("TAIL"))
    private void tinkersnewlife$spearChargePoseSrg(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                   float ageInTicks, float netHeadYaw, float headPitch,
                                                   CallbackInfo ci) {
        tinkersnewlife$applySpearPose(entity);
    }

    /** 共用：正在蓄力 ⇒ 把主手那条手臂摆成"端矛"的样子 ✓（原版 thirdPersonHandUse ✓） */
    private void tinkersnewlife$applySpearPose(LivingEntity entity) {
        // §911：使用中 ✓ 或"刚松手正在收回"（主手仍拿着长矛且冷却在跑 ✓）都摆这个姿势 ✓
        boolean using = SpearChargeAnimation.usingSpear(entity);
        if (!using && !SpearChargeAnimation.holdingSpear(entity)) return;
        if (!using && !(entity instanceof net.minecraft.world.entity.player.Player p
                && p.getCooldowns().isOnCooldown(entity.getMainHandItem().getItem()))) return;

        HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
        boolean rightArm = entity.getMainArm() == HumanoidArm.RIGHT;
        int invert = rightArm ? 1 : -1;

        // §911 收回进度 = 冷却百分比（松手那一刻是 1 ✓ 冷却走完是 0 ✓）
        float retract = 0.0F;
        if (!SpearChargeAnimation.usingSpear(entity)
                && entity instanceof net.minecraft.world.entity.player.Player p) {
            retract = p.getCooldowns().getCooldownPercent(entity.getMainHandItem().getItem(), 0.0F);
        }

        // ★ §923：**优先播动画文件里的 `arm` 轨道** ✓
        //   （用户口径：「**第三人称重画动画**」✓ —— 他在 Blockbench 里把手臂骨骼命名为 `arm` ✓
        //    导出的同一个 json 里带上 arm 的 rotation ✓ 这里就按**有效时间**取那一帧 ✓
        //    有效时间 = 蓄力时用已蓄 tick ✓ 松手后倒着走 ✓ 和第一人称同一套口径 ✓）
        // ★ §928 修正：动画文件里的 `arm` 是**增量** ✓ 叠在下面那套「端矛」基准姿势上 ✓
        //   ⚠ §927 我错误地让它**整个替换**基准 ✗ ⇒ 用户实测「**你怎么侧向旋转了，我是说手向前伸
        //     然后矛尖指向前方**」✗ —— 因为第一人称那条是 Z 轴**侧滚** ✗，而第三人称要的是
        //     **手臂前伸＋矛尖朝前**的基准姿势 ✓（= §912 从原版 1.21.11 搬来的 `thirdPersonArm` ✓）。
        //   ⇒ 基准**永远**由 `thirdPersonArm` 给 ✓，文件里的 arm 只作为**增量**相加 ✓（没给就是纯基准 ✓）。
        float[] xRot = new float[1];
        float[] yRot = new float[1];
        float[] zRot = new float[1];
        SpearChargeAnimation.thirdPersonArm(xRot, yRot, zRot, rightArm,
                self.head.xRot, self.head.yRot, entity.getTicksUsingItem(), retract);

        com.mofengbaizhi.tinkersnewlife.client.anim.AnimationClip clip =
                com.mofengbaizhi.tinkersnewlife.client.anim.AnimationClip.spearAnimation();
        if (clip != null) {
            float effective = SpearChargeAnimation.usingSpear(entity)
                    ? entity.getTicksUsingItem()
                    : SpearChargeAnimation.ATTACK_END
                        * (1.0F - net.minecraft.util.Mth.clamp(retract, 0.0F, 1.0F));
            float[] d = clip.armRot(effective);
            if (d != null) {
                xRot[0] += d[0];
                yRot[0] += (invert == 1 ? d[1] : -d[1]);   // 左手镜像 ✓
                zRot[0] += (invert == 1 ? d[2] : -d[2]);
            }
        }

        ModelPart arm = invert == 1 ? self.rightArm : self.leftArm;
        arm.xRot = xRot[0];
        arm.yRot = yRot[0];
        arm.zRot = zRot[0];
    }
}
