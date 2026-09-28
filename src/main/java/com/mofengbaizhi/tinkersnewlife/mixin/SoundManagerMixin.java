package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>音效嗅探</b>（§751 起，客户端）—— 把"到底在响哪个音效"记到日志里 ✓
 *
 * <h2>为什么需要它</h2>
 * 用户报告穿虚空金属巫师法袍时"一直在发出一些声音" ✓，而我这边：
 * <ul>
 *   <li>已核**我们自己的代码一行 {@code playSound} 都没有** ✓；</li>
 *   <li>诊断（§750）只抓到过一条"虚空抚摸挂虚蚀"（那是**工具**打的 ✓），
 *       盔甲那几条特性**一次都没触发** ✓ ⇒ 无法定性 ✗；</li>
 *   <li>诡厄那边的 {@code void_touched} 虽然自带 activate/loop/deactivate 三个音效 ✓，
 *       但它的语言文件里**没有字幕键** ✗ ⇒ 游戏字幕不会显示名字 ✗ ⇒ 只能我们自己抓 ✓。</li>
 * </ul>
 *
 * <h2>⚠⚠ §753：上一版（§751／§752）**根本一行都没抓到**，原因不是"过滤太窄"，而是 mixin 没生效 ✗</h2>
 * <ol>
 *   <li>本仓库<b>没有启用 Mixin 注解处理器</b> ✗（见 {@code build.gradle}：当前 ForgeGradle 环境缺混淆映射数据，
 *       改成<b>手写 refmap</b> {@code src/main/resources/tinkersnewlife.refmap.json} ✓）；</li>
 *   <li>⇒ 只有**手写进 refmap 的**「官方名 → SRG 名」才会被 remap ✓；
 *       而 {@code SoundManagerMixin} 从来**没进过 refmap** ✗（那个文件里只有 4 条：
 *       {@code EntityRenderDispatcherMixin} / {@code SlotMixin} / {@code ItemRendererMixin} /
 *       {@code HumanoidArmorLayerMixin} ✓）；</li>
 *   <li>⇒ 生产环境里类成员名是 <b>SRG</b>（{@code play} 实际叫 {@code m_120367_} ✓），
 *       {@code @Inject(method = "play")} <b>匹配不到任何方法</b> ✗，而 {@code defaultRequire = 0}
 *       又让它**完全静默地失败** ✗✗ ⇒ 所以测试时一条 {@code 🔊} 都没有 ✓ 一切都解释得通 ✓。</li>
 * </ol>
 * <b>正确写法＝直接写 SRG 名 ＋ {@code remap = false}</b> ✓（本仓库既有口径 ✓
 * 见 {@code SuperTierEffectMixin} / {@code ManaShieldEffectMixin} / {@code BlockDropsMixin} ✓），
 * 这样**不需要**动 refmap ✓ 也**不会**在开发／生产环境重复注入 ✗。
 *
 * <h2>SRG 名从哪来的（可复核 ✓）</h2>
 * ForgeGradle 缓存里的官方映射表：
 * {@code ~/.gradle/caches/forge_gradle/minecraft_user_repo/de/oceanlabs/mcp/mcp_config/1.20.1-20230612.114412/srg_to_official_1.20.1.tsrg}
 * 中 {@code net/minecraft/client/sounds/SoundManager} 段：
 * <pre>
 *   m_120367_ (Lnet/minecraft/client/resources/sounds/SoundInstance;)V        play
 *   m_120369_ (Lnet/minecraft/client/resources/sounds/SoundInstance;I)V       playDelayed
 *   m_120372_ (Lnet/minecraft/client/resources/sounds/TickableSoundInstance;)V queueTickingSound
 *   m_120389_ (Z)V                                                            tick
 * </pre>
 * （其中 {@code m_120372_} 与诡厄启示录自带 refmap 里的写法完全一致 ✓ 交叉验证过 ✓）
 *
 * <h2>钩哪几个入口</h2>
 * <ul>
 *   <li>{@code play} ✓ —— 普通一次性音（含脚步、方块、生物 ✓）；</li>
 *   <li>{@code playDelayed} ✓ —— 延迟播放;</li>
 *   <li><b>{@code queueTickingSound}</b> ✓✓ —— <b>循环音／持续音走这里</b> ✗ 不走 {@code play} ✗
 *       （诡厄的 {@code void_touched_loop} 正是这一类 ✓ 上一版**必然抓不到** ✗）；</li>
 *   <li>{@code tick} ✓ —— 只用来打<b>一行「嗅探已装载」</b> ✓：这是"mixin 到底有没有生效"的<b>铁证</b> ✓
 *       （{@code tick} 每 tick 必被调用 ✓ 一次性的 ✓ 不会刷屏 ✓）。</li>
 * </ul>
 * <p><b>不再按命名空间过滤</b> ✗ —— 所有音效都记 ✓（同一个 id 由 {@link VoidArmorDiag} 每 5 秒限流一行 ✓
 * 所以短时间测试不会刷屏 ✓，"一直在响"的那个 id 会**反复冒同一行** ✓ 一眼认出 ✓）。
 * <p>⚠ 排查完把 {@link VoidArmorDiag#ENABLED} 改成 {@code false} ⇒ 这个嗅探也一起静音 ✓（一行的事 ✓）。
 */
@Mixin(SoundManager.class)
public class SoundManagerMixin {

    /** 一次性"已装载"标记 ✓（{@link Unique} ＝ 这个字段只属于注入后的目标类 ✓ 不会撞名 ✓） */
    @Unique
    private static boolean tinkersnewlife$snifferArmed;

    /** ⭐ 铁证：{@code tick} 每 tick 必被调用 ⇒ 只要这行出现，就说明本 mixin 真的生效了 ✓ */
    @Inject(method = "m_120389_", at = @At("HEAD"), remap = false)
    private void tinkersnewlife$probeArmed(boolean paused, CallbackInfo ci) {
        if (tinkersnewlife$snifferArmed) return;
        tinkersnewlife$snifferArmed = true;
        VoidArmorDiag.log("sniff:armed", "🔊 音效嗅探已装载 ✓（tick 命中 ⇒ mixin 生效 ✓ SRG 名写法正确 ✓）");
    }

    /** 普通音效入口 ✓ */
    @Inject(method = "m_120367_", at = @At("HEAD"), remap = false)
    private void tinkersnewlife$sniffPlay(SoundInstance sound, CallbackInfo ci) {
        sniff("play", sound);
    }

    /** 延迟音效入口 ✓ */
    @Inject(method = "m_120369_", at = @At("HEAD"), remap = false)
    private void tinkersnewlife$sniffPlayDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        sniff("delayed", sound);
    }

    /** ⭐ 循环音走这里（不走 play ✗）—— 诡厄的 void_touched_loop 就是这一类 ✓ */
    @Inject(method = "m_120372_", at = @At("HEAD"), remap = false)
    private void tinkersnewlife$sniffTicking(TickableSoundInstance sound, CallbackInfo ci) {
        sniff("loop", sound);
    }

    /** 统一的记录逻辑 ✓（全都记 ✓ 由 {@link VoidArmorDiag} 按 id 限流 ✓） */
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
