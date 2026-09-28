package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>让神秘遗物（Enigmatic Legacy）的饰品不再"吃掉"夜视</b>（§769，配置可关 ✓）
 *
 * <h2>问题</h2>
 * 神秘遗物的「海洋之石」（{@code OceanStone}）与「寻宝护符」（{@code MiningCharm}）在
 * {@code curioTick} 里每 tick 调一次：
 * <pre>
 * public void removeNightVisionEffect(Player player, int maxAmplifier) {          // javap 实核 ✓
 *     MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
 *     if (nv != null &amp;&amp; nv.getAmplifier() &lt;= maxAmplifier - 1)
 *         player.removeEffect(MobEffects.NIGHT_VISION);
 * }
 * </pre>
 * ⇒ 它只清"**等级不够高**"的夜视（amplifier ≤ maxAmplifier-1 ✓）；
 * 而**血族**的夜视是 amplifier 0（＝日志里的"等级 1"✓）⇒ 被它一直清掉 ✗，
 * 血族每 2.5 秒再补回来 ✗ ⇒ 玩家看到"夜视一开一关" ✗（§765／§767 调用栈实证 ✓）。
 *
 * <h2>为什么不去改配置</h2>
 * EL 的配置里只有 {@code MiningCharmEnableNightVision}（管"护符**给不给**夜视"✓），
 * 而**这条移除逻辑是写死在代码里的** ✗ ⇒ 配置改不到它 ✗。
 *
 * <h2>修法</h2>
 * 在**唯一入口** {@code removeNightVisionEffect(Player, int)} 的 HEAD 取消它 ✓
 * （⚠ 描述符写全 ✓ —— §767 就是因为只写方法名、回调类型又错而 `Mixin apply failed` ✗）。
 *
 * <p>⚠ 这会**改变神秘遗物的原设计**（戴着这两个饰品本来就没有低等级夜视 ✓）
 * ⇒ 因此挂了配置开关 {@code enigmatic_curio_keeps_night_vision}（默认 <b>true</b> ✓ 用户要求 ✓），
 * 想要原版行为就把配置改成 false ✓。
 *
 * <p>⚠ 纯兼容补丁 ✓ 不改我们自己的任何玩法逻辑 ✓。
 */
@Mixin(targets = "com.aizistral.enigmaticlegacy.items.MiningCharm", remap = false)
public class EnigmaticNightVisionMixin {

    @Inject(
            method = "removeNightVisionEffect(Lnet/minecraft/world/entity/player/Player;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void tinkersnewlife$keepNightVision(Player player, int maxAmplifier, CallbackInfo ci) {
        try {
            if (ModConfig.enigmaticCurioKeepsNightVision()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // 兼容补丁绝不许把别人的模组搞崩 ✗（配置取不到就按"不拦"处理 ✓）
        }
    }
}
