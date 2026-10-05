package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.integration.slashblade.IInputCommandEvent;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/**
 * <b>§1022 照抄 TiCEX 的 {@code InputCommandEventMixin$Older}</b> ✓ ——
 * 给拔刀剑的输入指令事件加一个接口 ✓，好让我们的同步监听器拿到玩家 ✓。
 *
 * <p>⚠ 拔刀剑有两个事件类名（版本差异 ✓）：TiCEX 用插件按版本二选一 ✗；
 * 我们的整合包固定 1.9.65 ✓，其类是 {@code event.handler.InputCommandEvent} 且**有** {@code getEntity()} ✓
 * （已 javap 核对 ✓）⇒ 只保留这一个变体 ✓。{@code @Pseudo} ⇒ 类不存在时静默跳过 ✓。
 */
@Pseudo
@Mixin(targets = "mods.flammpfeil.slashblade.event.handler.InputCommandEvent", remap = false)
public abstract class KatanaInputCommandEventMixin implements IInputCommandEvent {

    @Shadow
    public abstract ServerPlayer getEntity();

    @Override
    public ServerPlayer tnl$getEntity() {
        return this.getEntity();
    }
}
