package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.SoundSniffer;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>音效嗅探 · 第二层</b>（§754 起）—— 直接钩 <b>{@code SoundEngine#play}</b> ✓
 *
 * <h2>为什么要多加这一层</h2>
 * §753 的 {@code SoundManagerMixin} 实测只有 {@code tick} 那条打出"已装载" ✓，三条音效钩子一行都没打 ✗ ——
 * 可是同一场会话里日志明明有（`SoundEngine` 自己打的 ✓）：
 * <pre>
 * [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]:
 *     Missing sound for event: minecraft:item.goat_horn.play
 * </pre>
 * ⇒ 说明"声音真的在放" ✓，那就只能是钩子那一层的问题 ✓ ⇒ 再加这一层兜底 ✓
 * （{@code SoundEngine#play} 是**所有声音最终的落点** ✓）。
 *
 * <h2>§754 结论：这一层**确实命中**了 ✓（`require = 1` 没报错 ✓、异常行也打得出来 ✓）</h2>
 * 当时两层都在打：
 * <pre>
 * 🐞 嗅探异常(SoundManager) / 🐞 嗅探异常(SoundEngine#play)
 *   java.lang.NullPointerException: Cannot invoke "…Sound.m_235146_()" because "this.f_119570_" is null
 * </pre>
 * ⇒ 钩子没问题 ✗，问题在**取音量／音调会抛异常** ✗ ⇒ §755 把记录逻辑抽到
 * {@link SoundSniffer} 并改成"先打 id、其余各自单独 try" ✓。
 *
 * <h2>SRG 名（出自官方映射表 ✓）</h2>
 * {@code net/minecraft/client/sounds/SoundEngine} → {@code m_120312_ (SoundInstance)V play} ✓
 *
 * <p>⚠ 这个方法运行在<b>声音线程</b>上 ✓，所以记录走 {@link SoundSniffer} → {@code VoidArmorDiag}
 * 的 {@code ConcurrentHashMap} ✓ 线程安全 ✓，而且**绝不静默吞异常** ✗。
 */
@Mixin(targets = "net.minecraft.client.sounds.SoundEngine")
public class SoundEngineHookMixin {

    @Inject(method = "m_120312_", at = @At("HEAD"), remap = false, require = 1)
    private void tinkersnewlife$sniffEnginePlay(SoundInstance sound, CallbackInfo ci) {
        SoundSniffer.sniff("engine", sound);
    }
}
