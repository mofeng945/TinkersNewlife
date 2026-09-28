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
 * <h2>只记"可疑的"（不然满屏环境音 ✗）</h2>
 * 只记这三类：
 * <ul>
 *   <li>命名空间是 {@code goety} / {@code goety_ladder} / {@code tinkersnewlife} ✓；</li>
 *   <li>或者路径里含 {@code armor} / {@code void} / {@code soul} ✓（顺带能抓到
 *       "护甲被反复穿上"这类原版音效 ✓ —— 那也是一种可能 ✗）。</li>
 * </ul>
 * <p>同一个音效 id 最多每 5 秒一行 ✓（{@link VoidArmorDiag} 统一限流 ✓），
 * 所以"一直在响"的声音会**反复出现同一行** ✓ 一眼就能认出来 ✓。
 * <p>⚠ 排查完把 {@link VoidArmorDiag#ENABLED} 改成 false ⇒ 这个嗅探也一起静音 ✓（一行的事 ✓）。
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void tinkersnewlife$sniffSound(SoundInstance sound, CallbackInfo ci) {
        try {
            if (!VoidArmorDiag.ENABLED || sound == null) return;
            ResourceLocation id = sound.getLocation();
            if (id == null) return;
            String namespace = id.getNamespace();
            String path = id.getPath();
            boolean interesting = "goety".equals(namespace)
                    || "goety_ladder".equals(namespace)
                    || "tinkersnewlife".equals(namespace)
                    || path.contains("armor")
                    || path.contains("void")
                    || path.contains("soul");
            if (!interesting) return;
            VoidArmorDiag.log("sound:" + id, "🔊 播放音效 {} ✓（音量 {} / 音调 {}）",
                    id, sound.getVolume(), sound.getPitch());
        } catch (Throwable ignored) {
            // 嗅探本身绝不许影响游戏 ✗
        }
    }
}
