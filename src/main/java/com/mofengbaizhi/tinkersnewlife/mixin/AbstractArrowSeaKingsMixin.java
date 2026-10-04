package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 海王之力（§960）：**远程武器射出的弹射物在水中无视阻力衰减**（§968）。
 *
 * <p>机制（实查 ✓ 不是猜 ✗）：原版 {@code AbstractArrow#tick} 里就是
 * <pre>f = this.getWaterInertia();</pre>
 * ⇒ 让被标记过的弹射物返回 {@code 1.0F} ＝ **水里不减速** ✓（默认是 0.6 那档 ✓）。
 *
 * <p>标记由 {@code SeaKingsPowerModifier#onProjectileLaunch} 在**发射那一刻**打上 ✓
 * （TC 现成钩子 ✓ 见 §965 的钩子清单 ✓ ⇒ 不需要 mixin 发射流程 ✓ 只需要这一个 getter ✓）。
 * ⚠ 双注解：SRG 名实查 {@code getWaterInertia→m_6882_} ✓。
 */
@Mixin(AbstractArrow.class)
public class AbstractArrowSeaKingsMixin {

    /** 标记键（写在弹射物的持久数据里 ✓ Forge 的 getPersistentData ✓） */
    public static final String TNL_SEA_KINGS = "tnl_sea_kings";

    @Inject(method = "getWaterInertia()F", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$waterInertia(CallbackInfoReturnable<Float> cir) {
        AbstractArrow self = (AbstractArrow) (Object) this;
        if (self.getPersistentData().getBoolean(TNL_SEA_KINGS)) cir.setReturnValue(1.0F);
    }

    @Inject(method = "m_6882_()F", remap = false, at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$waterInertiaSrg(CallbackInfoReturnable<Float> cir) {
        tinkersnewlife$waterInertia(cir);
    }
}