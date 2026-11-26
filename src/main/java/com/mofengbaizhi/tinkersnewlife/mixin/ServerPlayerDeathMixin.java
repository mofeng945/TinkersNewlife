package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.curse.CurseDeath;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 咒力伤害致死 → 统一死亡信息「……被诅咒致死」。
 *
 * <p>只改<b>死亡信息</b>这一处取值：{@code ServerPlayer.die} 里
 * {@code this.getCombatTracker().getDeathMessage()} 的返回值（反编译确认该调用存在 ✓，
 * 它同时被塞进 {@code ClientboundPlayerCombatKillPacket}（死亡界面）与聊天栏广播 ✓，
 * 所以一个注入点两处都统一 ✓）。
 *
 * <p>判定看 {@link CurseDeath#wasCursedRecently}（本模组在自己造成咒力伤害时打的标记 ✓）——
 * 顺带说明为什么不能直接改伤害类型：那会连带改掉护甲/附魔/无敌帧的结算 ✗。
 *
 * <p>双注入 = 本模组既有写法（见 {@code HumanoidArmorLayerMixin}）：官方名给开发环境、
 * SRG 名（{@code die} = {@code m_6667_}、{@code getDeathMessage} = {@code m_19293_}）给生产环境；
 * 本模组未启用 Mixin 注解处理器、没有 refmap，所以两边各写一份、`defaultRequire=0` 保证
 * 另一份找不到也不会崩 ✓。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {

    /** 官方名注入（开发环境生效） */
    @Redirect(method = "die",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/CombatTracker;getDeathMessage()Lnet/minecraft/network/chat/Component;"))
    private Component tinkersnewlife$curseDeathMessage(CombatTracker tracker) {
        return CurseDeath.messageOrDefault((ServerPlayer) (Object) this, tracker.getDeathMessage());
    }

    /** 兜底注入：直连 SRG 名（生产环境生效），绕开 refmap */
    @Redirect(method = "m_6667_",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/CombatTracker;m_19293_()Lnet/minecraft/network/chat/Component;"),
            remap = false)
    private Component tinkersnewlife$curseDeathMessageSrg(CombatTracker tracker) {
        return CurseDeath.messageOrDefault((ServerPlayer) (Object) this, tracker.getDeathMessage());
    }
}
