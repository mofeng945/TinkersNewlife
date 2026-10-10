package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.events.WatcherWillHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>守望意志 → 诡厄巫法吟唱速度 +15%/级</b>（虚空金属盔甲特性）。
 *
 * <h2>为什么只能这样挂</h2>
 * 诡厄<b>没有</b>公开的"吟唱速度"属性（它自己的"王冠"是硬编码减半 ✓）。反编译
 * {@code com.Polarice3.Goety.api.magic.ISpell#castDuration}：
 * <pre>
 * double duration = this.defaultCastDuration();
 * if (this.ReduceCastTime(caster)) duration /= 2.0;
 * return (int)(duration *= ModAttributes.getCastingSpeed(caster));   // ← ★ 这里
 * </pre>
 * ⇒ 施法时长 ∝ {@code ModAttributes.getCastingSpeed(caster)}（＝{@code 1 - 吟唱速度属性}) ✓，
 * 而且全仓字节搜确认 <b>只有 {@code ISpell} 调它</b> ✓ ⇒ 挂这一个静态方法就覆盖**所有**诡厄法术 ✓。
 *
 * <h2>做法</h2>
 * 在返回值上除以 {@code (1 + 15% × 等级)} ⇒ 时长按同样的比例变短 ✓（等级 = 身上所有护甲上守望意志之和 ✓，
 * 由 {@link WatcherWillHandler#levels} 算，mixin 里不写业务逻辑 ✓）。没穿 ⇒ 原样返回 ✓。
 *
 * <p>⚠ 对未装诡厄的整合包：{@code targets} 指定的类不存在 ⇒ 本 mixin 不应用 ✓（配置里 {@code required:false} ✓），
 * 不会有副作用 ✓。{@code require = 1} 是为了"万一方法名变了"能在日志里明确报错而不是静默失效 ✓。
 */
@Mixin(targets = "com.Polarice3.Goety.init.ModAttributes", remap = false)
public class GoetyCastSpeedMixin {

    @Inject(method = "getCastingSpeed", at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static void tinkersnewlife$watcherWillCastSpeed(LivingEntity caster,
                                                            CallbackInfoReturnable<Double> cir) {
        if (caster == null) return;
        int level = WatcherWillHandler.levels(caster);
        if (level <= 0) return;
        double base = cir.getReturnValue();
        // 施法时长 ∝ base ⇒ 想快 15%/级 就把 base 除以 (1 + 0.15×级) ✓；夹一个下限防止负数/除爆 ✓
        double divider = 1.0D + WatcherWillHandler.CAST_SPEED_PER_LEVEL * level;
        cir.setReturnValue(Math.max(0.05D, base / divider));
    }
}
