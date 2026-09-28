package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>音效嗅探</b>（§751，客户端）—— 把"到底在响哪个音效"记到日志里 ✓
 *
 * <h2>为什么需要它</h2>
 * 用户报告穿虚空金属巫师法袍时"一直在发出一些声音" ✓，而我这边：
 * <ul>
 *   <li>已核**我们自己的代码一行 `playSound` 都没有** ✓；</li>
 *   <li>第一次诊断（§750）只抓到一条"虚空抚摸挂虚蚀"（那是**工具**打的 ✓），
 *       盔甲那几条特性**一次都没触发** ✓ ⇒ 无法定性 ✗；</li>
 *   <li>诡厄那边的 `void_touched` 虽然自带 activate/loop/deactivate 三个音效 ✓，
 *       但它的语言文件里**没有字幕键** ✗ ⇒ 游戏字幕不会显示名字 ✗ ⇒ 只能我们自己抓 ✓。</li>
 * </ul>
 * ⇒ 直接在 {@link SoundManager#play} 的 HEAD 插一脚 ✓ 把声音 id、音量、音调打到日志里 ✓。
 *
 * <h2>⚠ §752 起改成"全记"（前一版过滤太窄，测试时一条都没抓到 ✗）</h2>
 * <ul>
 *   <li><b>钩三个入口</b>：{@code play} ✓、{@code playDelayed} ✓、
 *       <b>{@code queueTickingSound}</b> ✓✓ —— 最后这个是关键 ✗：
 *       <b>循环音（loop）不走 {@code play}</b> ✗ 而是走 {@code queueTickingSound} ✓
 *       （诡厄的 {@code void_touched_loop} 就是循环音 ✓ ⇒ 上一版**必然抓不到** ✗）；</li>
 *   <li>不再按命名空间过滤 ✗ —— <b>所有音效都记</b> ✓（同一个 id 仍每 5 秒最多一行 ✓
 *       所以短时间测试不会刷屏 ✓，读完我再自己筛 ✓）。</li>
 * </ul>
 * <p>同一个音效 id 最多每 5 秒一行 ✓（{@link VoidArmorDiag} 统一限流 ✓），
 * 所以"一直在响"的声音会**反复出现同一行** ✓ 一眼就能认出来 ✓。
 * <p>⚠ 排查完把 {@link VoidArmorDiag#ENABLED} 改成 false ⇒ 这个嗅探也一起静音 ✓（一行的事 ✓）。
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void tinkersnewlife$sniffPlay(SoundInstance sound, CallbackInfo ci) {
        sniff("play", sound);
    }

    @Inject(method = "playDelayed", at = @At("HEAD"))
    private void tinkersnewlife$sniffPlayDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        sniff("delayed", sound);
    }

    /** ⭐ 循环音走这里（不走 play ✗）—— 诡厄的 void_touched_loop 就是这一类 ✓ */
    @Inject(method = "queueTickingSound", at = @At("HEAD"))
    private void tinkersnewlife$sniffTicking(net.minecraft.client.resources.sounds.TickableSoundInstance sound,
                                             CallbackInfo ci) {
        sniff("loop", sound);
    }

    /** 统一的记录逻辑 ✓（§752 起**全都记** ✓ 由 {@link VoidArmorDiag} 按 id 限流 ✓） */
    private static void sniff(String from, SoundInstance sound) {
        try {
            if (!VoidArmorDiag.ENABLED || sound == null) return;
            ResourceLocation id = sound.getLocation();
            if (id == null) return;
            VoidArmorDiag.log("sound:" + id, "🔊 播放音效 {} ✓（入口 {} / 音量 {} / 音调 {}）",
                    id, from, sound.getVolume(), sound.getPitch());
        } catch (Throwable ignored) {
            // 嗅探本身绝不许影响游戏 ✗
        }
    }
}
