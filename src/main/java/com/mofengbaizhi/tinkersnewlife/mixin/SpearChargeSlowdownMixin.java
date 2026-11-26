package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>长矛蓄力不减速</b>（§837 ＋ TNL1303 冲突修复 ＋ TNL1304 保住功能）
 *
 * <h2>TNL1303 为什么不能再用 {@code @Redirect}</h2>
 * 玩家日志：`irons_lib.mixins.json:LocalPlayerMixin` 与本模组**抢同一个
 * {@code LocalPlayer#aiStep} 里的 {@code isUsingItem()} 调用点**（都是 `@Redirect`、都 priority 1000）
 * ⇒ 后者被跳过、`@Redirect` 默认 `require = 1` ⇒ `InjectionError` ⇒ **启动就崩**。
 * 上一版（TNL1303）用"priority 900 ＋ require = 0"让路止血，
 * 但代价是**有 irons_lib 时这个能力没了** —— 用户 TNL1304 明确要求：
 * 「我不想在冲突时失去长矛蓄力不减速能力」。
 *
 * <h2>TNL1304 新做法：不碰那个调用点，改"乘数常量"</h2>
 * 减速那段源码（1.20.1 `LocalPlayer#aiStep`）：
 * <pre>
 * if (isUsingItem() &amp;&amp; !isPassenger()) {
 *    input.leftImpulse *= 0.2F;      // ← occ#0
 *    input.forwardImpulse *= 0.2F;   // ← occ#1
 *    sprintTriggerTime = 0;
 * }
 * </pre>
 * ⇒ 用 {@link ModifyConstant} 只把**这两个 `0.2F`** 在"手上正在蓄长矛"时改成 `1.0F` ✓
 * <ul>
 *   <li>不注入 `isUsingItem()` 调用点 ⇒ **与 irons_lib 的 Redirect 完全不冲突** ✓；</li>
 *   <li>无论 irons_lib 让不让那段执行，长矛都不减速 ✓（★正好满足用户要求 ✓）</li>
 * </ul>
 * 起跑：`canStartSprinting()` 里有 `!isUsingItem()` ⇒ 蓄力时无法起跑 ✗
 * ⇒ 用 {@code @Inject(at = RETURN)} 手动 `setSprinting(true)`（回调点也不是 Redirect，不冲突 ✓），
 * 语义等同原版 {@code use_effects(canSprint = true)} ✓
 *
 * <h2>⚠ 每条注入写两份（本仓既有写法 ✓ 没有 Mixin 注解处理器 ✗ 不生成 refmap）</h2>
 * official 名 ＋ SRG 字面量（`remap = false`）；全部 `require = 0`：
 * 真找不到就静默跳过，**绝不再把游戏拖崩** ✓
 */
@Mixin(value = LocalPlayer.class, priority = 900)
public abstract class SpearChargeSlowdownMixin {

    /** 只打一次日志 ✓ */
    @Unique
    private static boolean tinkersnewlife$loggedApplied = false;

    // ============================================================
    //  ① 减速乘数：0.2F -> 1.0F（蓄长矛时）
    // ============================================================

    @ModifyConstant(method = "aiStep", require = 0,
            constant = @Constant(floatValue = 0.2F, ordinal = 0))
    private float tinkersnewlife$slowMulA$named(float original) {
        return tinkersnewlife$mul(original, (LocalPlayer) (Object) this);
    }

    @ModifyConstant(method = "aiStep", require = 0,
            constant = @Constant(floatValue = 0.2F, ordinal = 1))
    private float tinkersnewlife$slowMulB$named(float original) {
        return tinkersnewlife$mul(original, (LocalPlayer) (Object) this);
    }

    @ModifyConstant(method = "m_8107_", remap = false, require = 0,
            constant = @Constant(floatValue = 0.2F, ordinal = 0))
    private float tinkersnewlife$slowMulA$srg(float original) {
        return tinkersnewlife$mul(original, (LocalPlayer) (Object) this);
    }

    @ModifyConstant(method = "m_8107_", remap = false, require = 0,
            constant = @Constant(floatValue = 0.2F, ordinal = 1))
    private float tinkersnewlife$slowMulB$srg(float original) {
        return tinkersnewlife$mul(original, (LocalPlayer) (Object) this);
    }

    /** 蓄长矛 ⇒ 把 0.2 换成 1.0（其它情况原样返回） */
    @Unique
    private static float tinkersnewlife$mul(float original, LocalPlayer self) {
        if (tinkersnewlife$chargingSpear(self)) {
            tinkersnewlife$logOnce();
            return 1.0F;
        }
        return original;
    }

    // ============================================================
    //  ② 起跑：aiStep 末尾手动 setSprinting(true)（等价 canSprint = true）
    // ============================================================

    @Inject(method = "aiStep", at = @At("RETURN"), require = 0)
    private void tinkersnewlife$canSprint$named(CallbackInfo ci) {
        tinkersnewlife$tryStartSprint((LocalPlayer) (Object) this);
    }

    @Inject(method = "m_8107_", remap = false, at = @At("RETURN"), require = 0)
    private void tinkersnewlife$canSprint$srg(CallbackInfo ci) {
        tinkersnewlife$tryStartSprint((LocalPlayer) (Object) this);
    }

    @Unique
    private static void tinkersnewlife$tryStartSprint(LocalPlayer self) {
        try {
            if (!tinkersnewlife$chargingSpear(self)) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) {
                return;
            }
            if (!mc.options.keySprint.isDown() || self.isSprinting()) {
                return;
            }
            if (!self.input.hasForwardImpulse() || !self.onGround() || self.isPassenger()
                    || self.isFallFlying() || self.getFoodData().getFoodLevel() <= 6) {
                return;      // 对应原版 canStartSprinting 的那几条前置
            }
            self.setSprinting(true);
            tinkersnewlife$logOnce();
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  共用判定 / 日志
    // ============================================================

    @Unique
    private static boolean tinkersnewlife$chargingSpear(LocalPlayer self) {
        try {
            return self.isUsingItem() && self.getUseItem().getItem() instanceof SpearItem;
        } catch (Throwable t) {
            return false;
        }
    }

    @Unique
    private static void tinkersnewlife$logOnce() {
        if (!tinkersnewlife$loggedApplied) {
            tinkersnewlife$loggedApplied = true;
            TinkersNewlife.LOGGER.info("[长矛] 蓄力减速已跳过（常量改写通道生效 §1304）");
        }
    }
}
