package com.mofengbaizhi.tinkersnewlife.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ⭐⭐ §1210 <b>修"使徒转阶段时一直试图瞬移"</b>
 * （⭐ 用户口径 ✓ 2026-10-10：「**使徒在转阶段时会试图一直向玩家瞬移**」✓）。
 *
 * <h2>⭐⭐ 根因（⭐ 反编译实证 ✓）</h2>
 * ⭐ {@code Apostle.tick} 里有这么一条 ✗：
 * <pre>
 *   if (this.isInWater() || this.isInLava() || this.isInFluidType() || this.isStuck()) {
 *       this.teleport();          // ⚠⚠ 这条【没有】检查 isSettingUpSecond ✗
 *   }
 * </pre>
 * ⚠ ⭐ 对比它上面那条 ⭐ **有**检查 ✓：
 * <pre>
 *   if ((dist &gt; 1024 || !nav.canReach(target)) &amp;&amp; target.onGround() &amp;&amp; !this.isSettingUpSecond()) { … }
 * </pre>
 * ⭐ 而 ⭐ **转阶段时** ✗：⭐ `Apostle.tick` 里会调 ⭐ `getNavigation().stop()` ＋ ⭐ 它**站着不动** ✓
 * ⇒ ⭐ **`isStuck()` 恒为真** ✓ ⇒ ⭐ **每 tick 都调 `teleport()`** ✓ ✓。
 * <p>⚠ ⭐ `teleport()` 内部**确实**有 `isSettingUpSecond` 早退 ✗（⭐ 所以它**没真传走** ✓）
 * ⚠ 但它**已经**：⭐ 播了 `APOSTLE_PRE_TELEPORT` **音效** ✓ ＋ ⭐ **开了一个 `CompletableFuture` 异步搜索** ✓
 * ⇒ ⭐ 玩家**听到/看到的就是"一直试图瞬移"** ✓ ⚠ **而且疯狂开线程** ✓。

 * <h2>⭐ 修法</h2>
 * ⭐ 在 ⭐ `teleport()` 与 ⭐ `teleportTowards(Entity)` 的 **HEAD** ✗
 * ⭐ 若 ⭐ `Apostle#isSettingUpSecond()` 为真 ⇒ ⭐ **直接 `ci.cancel()`** ✓ ✓
 * ⇒ ⭐ **连音效和异步线程都不再产生** ✓ ✓（⭐ 比"内部早退"更早一步 ✓）。
 *
 * <p>⚠ 用 ⭐ `require = 0` ✗ —— ⭐ 万一诡厄改了这个方法名 ✗ ⭐ **不注入也不崩** ✓
 * （⭐ 与仓库里其它 mixin 一样的保守口径 ✓）。
 * ⚠ 反射读 ⭐ `isSettingUpSecond` ✗ —— ⭐ 它是 ⭐ `public` ✓ ⚠ 但 ⭐ 用反射更稳 ✓
 * （⭐ 拿不到 ⇒ ⭐ **不取消** ✓ ⭐ 保持原行为 ✓ ⭐ 绝不误伤 ✓）。
 */
@Mixin(targets = "com.Polarice3.Goety.common.entities.boss.Apostle", remap = false)
public abstract class ApostlePhaseTeleportMixin {

    /** ⭐ 缓存 ⭐ `isSettingUpSecond()` 方法 ✗（⭐ 拿不到 ⇒ ⭐ 永久禁用本 mixin ✓） */
    private static java.lang.reflect.Method TNL_IS_SETTING_UP = null;
    private static boolean TNL_RESOLVED = false;

    private static boolean tnl$isSettingUp(Object self) {
        if (!TNL_RESOLVED) {
            TNL_RESOLVED = true;
            try {
                TNL_IS_SETTING_UP = self.getClass().getMethod("isSettingUpSecond");
                TNL_IS_SETTING_UP.setAccessible(true);
            } catch (Throwable ignored) {
                TNL_IS_SETTING_UP = null;
            }
        }
        if (TNL_IS_SETTING_UP == null) {
            return false;
        }
        try {
            Object r = TNL_IS_SETTING_UP.invoke(self);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tinkersnewlife$noTeleportWhilePhasing(CallbackInfo ci) {
        if (tnl$isSettingUp(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "teleportTowards", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tinkersnewlife$noTeleportTowardsWhilePhasing(
            net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
        if (tnl$isSettingUp(this)) {
            ci.cancel();
        }
    }
}
