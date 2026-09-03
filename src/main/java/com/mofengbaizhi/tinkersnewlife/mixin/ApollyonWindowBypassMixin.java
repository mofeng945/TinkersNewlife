package com.mofengbaizhi.tinkersnewlife.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import z1gned.goetyrevelation.util.ApollyonAbilityHelper;

/**
 * 穿透启示录（Goety: Revelation）下界亚波伦的 30tick 受击免疫窗：
 * <p>
 * 启示录的 LivingEntityMixin.canHurt 在 LivingEntity.hurt() 的 HEAD 注入——
 * 当目标是"下界 + Apollyon 状态"且其 hitCooldown &gt; 0 时直接取消伤害（每次成功受伤后置 30，
 * 即每 1.5s 只能受一次伤）。此窗口不看伤害类型/来源，从外部无法用任何伤害源绕过。
 * <p>
 * 本 mixin 打进启示录的 LivingEntityMixin 模板，把 canHurt 里读取冷却的
 * {@code ApollyonAbilityHelper.allTitlesApostle_1_20_1$getHitCooldown()} 调用重定向为恒 0
 * → 免疫窗判定永远不成立，多段穿透可连续全额命中。
 * <p>
 * 仅在启示录存在时生效：目标类缺失会被 mixin 系统跳过（config required=false，不崩溃）。
 */
@Mixin(targets = "z1gned.goetyrevelation.mixin.LivingEntityMixin")
public abstract class ApollyonWindowBypassMixin {

    @Redirect(
            method = "canHurt",
            at = @At(value = "INVOKE",
                    target = "Lz1gned/goetyrevelation/util/ApollyonAbilityHelper;allTitlesApostle_1_20_1$getHitCooldown()I"),
            require = 0)
    private int tinkersnewlife$noHitCooldown(ApollyonAbilityHelper helper) {
        return 0; // 免疫窗穿透：canHurt 永远读到冷却 0
    }
}
