package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

/**
 * <b>§1022 照抄 TiCEX 的 {@code IInputCommandEvent}</b> ✓ ——
 * 由 mixin {@code KatanaInputCommandEventMixin} 挂到拔刀剑的输入指令事件上 ✓，
 * 让我们的监听器能拿到事件里的玩家 ✓（那个字段是私有的 ✗，本体只公开了 {@code getEntity()} ✓）。
 */
public interface IInputCommandEvent {

    /** 取事件对应的服务端玩家 ✓（由 mixin 转发到本体的 {@code getEntity()} ✓）。 */
    net.minecraft.server.level.ServerPlayer tnl$getEntity();
}
