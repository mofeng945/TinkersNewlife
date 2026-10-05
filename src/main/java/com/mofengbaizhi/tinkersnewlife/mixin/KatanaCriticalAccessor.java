package com.mofengbaizhi.tinkersnewlife.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;

/**
 * <b>§1020 访问器：给匠魂的 {@code ToolAttackContext} 写暴击倍率</b>
 * —— <b>逐行照抄 TiCEX</b> 的 {@code moffy.ticex.mixin.CriticalAccessor} ✓
 * （它那边是给 {@link KatanaAttackHelperMixin} 用的 ✓：本体算出暴击后要把倍率塞进上下文 ✓，
 * 匠魂的"近战伤害"修饰符会读它 ✓）。
 */
@Mixin(value = ToolAttackContext.class, remap = false)
public interface KatanaCriticalAccessor {

    @Mutable
    @Accessor("criticalModifier")
    void setCriticalModifier(float criticalModifier);
}
