package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §813 被缴械丢出的地物**不会被销毁**（★用户口径 ✓）。
 *
 * <pre>
 *   雷劈 / 爆炸 / 火焰 / 岩浆 / 仙人掌  ⇒ ★一律不会消失或被烧毁 ✓
 * </pre>
 *
 * <p>认领依据：{@code DisarmEffect.DisarmEventHandler.DROP_TAG} 存在于该地物的持久数据里 ✓。
 * （★直接写字符串而不 import：避免 common 依赖 imagination 的编译顺序问题 ✓）
 *
 * <p>★目标是**原版类** ⇒ 不能写 {@code remap = false}（★详见 §1352 的计时插桩教训 ✓）。
 */
@Mixin(ItemEntity.class)
public class DisarmDropProtectionMixin {

    private static final String DROP_TAG = "tnl_disarm_drop";

    private static boolean tnl$protected(ItemEntity self) {
        try {
            return self.getPersistentData().getBoolean(DROP_TAG);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** ★爆炸/仙人掌/火焰 等都走 `hurt` ⇒ 一律拦下 ✓ */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void tnl$blockHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (tnl$protected((ItemEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    /** ★岩浆：原版直接把物品 `discard()` ✗ 不经过 hurt ⇒ 单独拦 ✓ */
    @Inject(method = "lavaHurt", at = @At("HEAD"), cancellable = true)
    private void tnl$blockLava(CallbackInfo ci) {
        if (tnl$protected((ItemEntity) (Object) this)) {
            ci.cancel();
        }
    }

    /** ★着火：每 tick 清掉火苗（★避免雷劈/火焰 慢慢烧毁 ✗） */
    @Inject(method = "tick", at = @At("HEAD"))
    private void tnl$clearFire(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (tnl$protected(self) && self.isOnFire()) {
            self.clearFire();
        }
    }
}
