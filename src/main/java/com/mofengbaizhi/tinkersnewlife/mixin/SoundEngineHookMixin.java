package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>音效嗅探 · 第二层</b>（§754）—— 直接钩 <b>{@code SoundEngine#play}</b> ✓
 *
 * <h2>为什么要多加这一层</h2>
 * §753 的 {@code SoundManagerMixin}（钩 {@code play} / {@code playDelayed} / {@code queueTickingSound}）
 * 实测**只有 {@code tick} 那条打出了"已装载"** ✓，而三条音效钩子**一行都没打** ✗ ——
 * 可是同一场会话里日志明明有：
 * <pre>
 * [16:38:37] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]:
 *                    Missing sound for event: minecraft:item.goat_horn.play
 * </pre>
 * 这句话是 <b>{@code SoundEngine#play} 内部</b>打的 ✓ ⇒ <b>那个时刻 {@code SoundEngine.play} 确实被调用了</b> ✓
 * ⇒ 说明"声音真的在放" ✓ 那么没抓到就只能是**钩子那一层的问题** ✓。
 * <p>⇒ 于是再加这一层：{@code SoundEngine#play} 是**所有声音最终的落点** ✓
 * （{@code SoundManager#play} 和 {@code SoundManager#tick} 里处理循环音最终都会走到它 ✓），
 * 钩住它就**跑不掉** ✓。
 *
 * <h2>SRG 名（同样出自官方映射表 ✓）</h2>
 * {@code net/minecraft/client/sounds/SoundEngine} → {@code m_120312_ (SoundInstance)V play} ✓
 *
 * <p>⚠ 这个方法运行在<b>声音线程</b>（{@code SoundEngineExecutor}）上 ✓ 所以记录走的是
 * {@link VoidArmorDiag} 的 {@code ConcurrentHashMap} ✓ 线程安全 ✓；
 * 而且**绝不再静默吞异常** ✗ —— 出错会打一行 {@code 🐞 嗅探异常} ✓（这是 §753 的教训 ✓）。
 */
@Mixin(targets = "net.minecraft.client.sounds.SoundEngine")
public class SoundEngineHookMixin {

    @Inject(method = "m_120312_", at = @At("HEAD"), remap = false, require = 1)
    private void tinkersnewlife$sniffEnginePlay(SoundInstance sound, CallbackInfo ci) {
        try {
            if (!VoidArmorDiag.ENABLED || sound == null) return;
            ResourceLocation id = sound.getLocation();
            if (id == null) return;
            VoidArmorDiag.log("engine:" + id, "🔊[引擎层] 播放音效 {} ✓（音量 {} / 音调 {}）",
                    id, sound.getVolume(), sound.getPitch());
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:engine", "🐞 嗅探异常(SoundEngine#play) {}", String.valueOf(t));
        }
    }
}
