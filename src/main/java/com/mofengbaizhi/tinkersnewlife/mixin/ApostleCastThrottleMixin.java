package com.mofengbaizhi.tinkersnewlife.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⭐⭐ §1212 <b>降低使徒的施法频率</b>（⭐ 用户口径 ✓ 2026-10-10：
 * 「**同步施法的设定不要取消，但是施法频率降低，不要移除我的法术goal**」✓）。
 *
 * <h2>⭐⭐ 为什么拦这一处就够</h2>
 * ⭐ 反编译实证 ✗：⭐ 使徒那 **8 个法术 goal**
 *（⭐ `CastingSpellGoal`／⭐ `FireballSpellGoal`／⭐ `DamnedSpellGoal`／⭐ `MonolithSpellGoal`／
 * ⭐ `FireRainSpellGoal`／⭐ `RangedSummonSpellGoal`／⭐ `FireTornadoSpellGoal`／⭐ `RoarSpellGoal` ✓）
 * ⭐ **全部 `extends Apostle$CastingGoal`** ✓ ✓
 * ⇒ ⭐ 只要拦 ⭐ 基类的 ⭐ **`canUse()`（⭐ SRG `m_8036_` ✓）** ✗
 * ⇒ ⭐ **一次覆盖全部 8 个** ✓ ✓ 而且 ⭐ **一个 goal 都没删** ✓ ⭐ **`StrafeCastGoal` 也没动** ✓。
 *
 * <h2>⭐ 节流规则</h2>
 * ⭐ 每个使徒各自记 ⭐ **"上次成功开始施法的 tick"** ✗
 * ⭐ 距上次不足 {@value #CAST_INTERVAL_TICKS} tick ⇒ ⭐ **`canUse` 返回 false** ✓
 * ⇒ ⭐ 原来的 AI 与目标选择**完全不变** ✗ ⭐ 只是**施法次数被拉稀** ✓ ✓。
 *
 * <p>⚠ 怎么拿到"那个使徒" ✗：⭐ `CastingGoal` 的构造器是
 * ⭐ `super((SpellCastingCultist) Apostle.this)` ✓ ⇒ ⭐ 它的父类 ⭐ `UseSpellGoal` 里
 * ⭐ **存着那个实体** ✓ ⇒ ⭐ 反射**遍历字段找一个 `SpellCastingCultist`** ✓ ✓（⭐ 不写死字段名 ✓）。
 *
 * <p>⚠ `require = 0` ✗ ⇒ ⭐ 名字变了**不注入也不崩** ✓；⭐ 反射失败 ⇒ ⭐ **不节流** ✓（⭐ 保持原行为 ✓）。
 */
@Mixin(targets = "com.Polarice3.Goety.common.entities.boss.Apostle$CastingGoal", remap = false)
public abstract class ApostleCastThrottleMixin {

    /** ⭐ 两次施法之间的最小间隔（⭐ tick ✓）。⭐ 调大 ⇒ ⭐ 法术更少 ✓ */
    private static final long CAST_INTERVAL_TICKS = 60L;

    /** ⭐ 每个使徒上次"开始施法"的时刻 ✗（⭐ 用 gameTime ✓ 免得 tickCount 被重置 ✓） */
    private static final java.util.Map<java.util.UUID, Long> TNL_LAST_CAST =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static java.lang.reflect.Field TNL_MOB_FIELD = null;
    private static boolean TNL_RESOLVED = false;

    /** ⭐ 反射找出"这个 goal 属于哪个实体" ✗（⭐ 遍历字段找 `SpellCastingCultist` ✓） */
    private static net.minecraft.world.entity.Mob tnl$owner(Object goal) {
        if (!TNL_RESOLVED) {
            TNL_RESOLVED = true;
            try {
                Class<?> c = goal.getClass();
                while (c != null && c != Object.class) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (net.minecraft.world.entity.Mob.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            TNL_MOB_FIELD = f;
                            break;
                        }
                    }
                    if (TNL_MOB_FIELD != null) {
                        break;
                    }
                    c = c.getSuperclass();
                }
            } catch (Throwable ignored) {
                TNL_MOB_FIELD = null;
            }
        }
        if (TNL_MOB_FIELD == null) {
            return null;
        }
        try {
            Object v = TNL_MOB_FIELD.get(goal);
            return v instanceof net.minecraft.world.entity.Mob m ? m : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Inject(method = "m_8036_", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void tinkersnewlife$throttleCast(CallbackInfoReturnable<Boolean> cir) {
        try {
            net.minecraft.world.entity.Mob owner = tnl$owner(this);
            if (owner == null || owner.level().isClientSide) {
                return;
            }
            long now = owner.level().getGameTime();
            Long last = TNL_LAST_CAST.get(owner.getUUID());
            if (last != null && now - last < CAST_INTERVAL_TICKS) {
                // ⭐ 冷却中 ⇒ ⭐ 这次不让它开始施法 ✓（⭐ 但 AI 该干嘛还干嘛 ✓）
                cir.setReturnValue(Boolean.FALSE);
                return;
            }
            // ⭐ 允许这一次 ⇒ ⭐ 记下时刻 ✓（⭐ 顺手清一下过期项 ✓）
            TNL_LAST_CAST.put(owner.getUUID(), now);
            if (TNL_LAST_CAST.size() > 512) {
                TNL_LAST_CAST.entrySet().removeIf(e -> now - e.getValue() > 20L * 600L);
            }
        } catch (Throwable ignored) {
            // ⭐ 节流出错 ⇒ ⭐ 放行（保持原行为 ✓）
        }
    }
}
