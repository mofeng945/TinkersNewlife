package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.TruePierce;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⭐⭐ §1208 <b>"方法修改"：⭐ 血量锁定期间让 {@code getHealth()} 直接返回我们的值</b>
 * （⭐ 用户口径 ✓ 2026-10-10：「**压血加方法修改，然后就先放着吧**」✓）。
 *
 * <h2>⭐⭐ 为什么这条能治本</h2>
 * ⭐ 前面的"锁"是 ⭐ **改真身字段** ✗ ⚠ 而 ⭐ 目标自己的回血逻辑 ⭐ **每 tick 都会跑一次** ✓
 * ⭐ 且 ⭐ 它可能在 ⭐ **我们两个写入点之后**再改 ✓（⭐ 实测校验日志就是这么显示的 ✓）
 * ⇒ ⚠ 只靠"更晚写"永远有被插队的风险 ✓。
 * <p>⭐ 而 ⭐ **所有"读血量"的人都走 `getHealth()`** ✗：
 * ⭐ `isAlive()` ✗ ⭐ 血条同步 ✗ ⭐ 阶段判定 ✗ ⭐ 死亡判定 ✗ ⭐ 别的模组的伤害计算 ✓
 * ⇒ ⭐ 让它在锁定期内 ⭐ **直接返回我们的值** ✗
 * ⇒ ⭐ **对外界来说血量就锁死了** ✓ ✓（⭐ 真身被谁改都无所谓 ✓）。
 *
 * <h2>⚠ 三个代价（⭐ 已评估 ✓ 用户明确要 ✓）</h2>
 * <ol>
 *   <li>⚠ ⭐ **真身与读数会不一致** ✗ —— ⭐ 直接读字段的模组会看到差异 ✓；</li>
 *   <li>⚠ ⭐ `getHealth()` 是**热点方法** ✗ ⇒ ⭐ 所以实现里 ⭐ **先查一个空的 map 就返回** ✓
 *       （⭐ `lockedHealthFor` 内部 `PENDING.isEmpty()` 时零开销 ✓ ⭐ 绝大多数时候都是这样 ✓）；</li>
 *   <li>⚠ ⭐ 别人也可能 mixin 它 ✗ ⇒ ⭐ 我们取 ⭐ `priority = 3000` ✓ ⭐ 且 ⭐ **只在锁定期内**返回 ✓。</li>
 * </ol>
 *
 * <p>⚠ 两道注入 ✗（⭐ 照仓库既有写法 ✓）：⭐ 方法名 `getHealth` ✗ ＋ ⭐ 直连 SRG ⭐ `m_21223_`
 * （⭐ `remap = false` ✓ 兜底 ✓）。
 */
@Mixin(value = LivingEntity.class, priority = 3000)
public abstract class LivingEntityGetHealthMixin {

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$lockedHealth(CallbackInfoReturnable<Float> cir) {
        float locked = TruePierce.lockedHealthFor((LivingEntity) (Object) this);
        if (locked >= 0.0F) {
            cir.setReturnValue(locked);
        }
    }

    /** ⭐ 兜底注入：直连 SRG 方法名 ✗（⭐ 绕开 refmap ✓ 照仓库既有写法 ✓） */
    @Inject(method = "m_21223_", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinkersnewlife$lockedHealthSrg(CallbackInfoReturnable<Float> cir) {
        float locked = TruePierce.lockedHealthFor((LivingEntity) (Object) this);
        if (locked >= 0.0F) {
            cir.setReturnValue(locked);
        }
    }
}
