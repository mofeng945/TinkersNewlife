package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>音效嗅探的公共实现</b>（§755）—— 给 {@code SoundManagerMixin} 与 {@code SoundEngineHookMixin} 共用 ✓
 *
 * <h2>为什么要单独抽出来 ＋ 为什么这么写（§754 的实测教训 ✗）</h2>
 * §754 那一轮，两层钩子**其实都命中了** ✓（`require = 1` 没报错 ✓、`🐞 嗅探异常` 一行行地打 ✓），
 * 但打出来的全是：
 * <pre>
 * java.lang.NullPointerException:
 *     Cannot invoke "net.minecraft.client.resources.sounds.Sound.m_235146_()" because "this.f_119570_" is null
 * </pre>
 * ⇒ <b>取"音量／音调"这一步会抛异常</b> ✗（个别 {@code SoundInstance} 内部那个 {@code Sound} 还是空的 ✓），
 * 而我把 id 和音量写在**同一个 try** 里 ✗ ⇒ 异常一抛，**连 id 都没来得及打** ✗✗
 * ⇒ 日志上看起来就像"什么都没有" ✗。
 *
 * <p>所以这一版铁律：<b>先拿 id、先打出来</b> ✓；音量／音调／循环标记一律**各自单独 try** ✓ 拿不到就写"取不到" ✗
 * —— **任何一项失败都不许把整条记录吃掉** ✓。
 *
 * <h2>还会打"来源调用栈"</h2>
 * 每个音效 id **第一次出现**时，额外打一条调用栈 ✓（只留**非**原版／非 mixin／非我们自己的帧 ✓），
 * 这样"到底是谁在放这个声音"一目了然 ✓ —— 这是本轮排查的关键判据 ✓。
 */
public final class SoundSniffer {

    /** 已经打过调用栈的 {@code 入口|id} ✓ 只打一次 ✓ */
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private SoundSniffer() {
    }

    /**
     * 记一条音效 ✓（任何一步失败都不会吞掉整条记录 ✓）。
     *
     * @param from  入口标记（{@code play} / {@code loop} / {@code engine} …）
     * @param sound 音效实例
     */
    public static void sniff(String from, SoundInstance sound) {
        if (!VoidArmorDiag.ENABLED || sound == null) return;

        // ① id 必须先拿到、必须先打出来 ✓（§754 就是死在这一步之后 ✗）
        ResourceLocation id;
        try {
            id = sound.getLocation();
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:id", "🐞 嗅探异常(取音效 id) {} ／ 实例类={}",
                    String.valueOf(t), sound.getClass().getName());
            return;
        }
        if (id == null) return;

        // ② 实例类名：谁是循环音／谁不是一眼看出 ✓（循环音不走 play ✗ 走 queueTickingSound ✓）
        String className = sound.getClass().getSimpleName();

        // ③ 音量／音调：单独 try ✓ 拿不到就写"取不到" ✓ 绝不影响 id 的记录 ✓
        String volPitch;
        try {
            volPitch = "音量 " + sound.getVolume() + " / 音调 " + sound.getPitch();
        } catch (Throwable t) {
            volPitch = "音量音调取不到（" + t.getClass().getSimpleName() + "）";
        }

        // ④ 循环标记：单独 try ✓
        String loop;
        try {
            loop = (sound instanceof TickableSoundInstance tsi)
                    ? ("循环=" + tsi.isLooping())
                    : "非循环音类";
        } catch (Throwable t) {
            loop = "循环标记取不到（" + t.getClass().getSimpleName() + "）";
        }

        VoidArmorDiag.log("sound:" + id, "🔊 播放音效 {} ✓（入口 {} ／ 类 {} ／ {} ／ {}）",
                id, from, className, loop, volPitch);

        // ⑤ 首次出现 ⇒ 打"谁在放它"的调用栈 ✓
        if (SEEN.add(from + "|" + id)) {
            StringBuilder sb = new StringBuilder();
            int kept = 0;
            try {
                for (StackTraceElement e : new Throwable().getStackTrace()) {
                    String cn = e.getClassName();
                    if (cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("net.minecraft.")
                            || cn.startsWith("org.spongepowered") || cn.startsWith("com.mojang")
                            || cn.startsWith("com.mofengbaizhi")) {
                        continue;
                    }
                    sb.append(" ⇐ ").append(cn).append('#').append(e.getMethodName())
                            .append(':').append(e.getLineNumber());
                    if (++kept >= 6) break;
                }
            } catch (Throwable ignored) {
                // 调用栈本身失败无所谓 ✓
            }
            VoidArmorDiag.log("src:" + id, "🧭 音效 {} 的来源调用栈（首次出现）{}",
                    id, kept == 0 ? "（只有原版／我们自己的帧）" : sb.toString());
        }
    }
}
