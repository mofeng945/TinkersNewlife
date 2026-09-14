package com.mofengbaizhi.tinkersnewlife.client.sound;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 「不可名状」的低语<b>音频</b>（循环播放的那条 {@code tinkersnewlife:effect.whispers}）。
 *
 * <h2>"获得效果就响、效果结束就停"是怎么保证的</h2>
 * 做成 {@link TickableSoundInstance}，让<b>声音自己</b>每 tick 检查玩家身上还有没有「不可名状」，
 * 没有就 {@code stop()}：
 * <ul>
 *   <li>{@code SoundEngine} 每 tick 都会遍历 {@code tickingSounds} 调 {@link #tick()}，
 *       紧接着检查 {@link #isStopped()}，为真就立刻关掉通道 —— 所以停止是"下一 tick 内"生效，不留尾音；</li>
 *   <li>这样<b>不需要</b>在外面到处挂"停止"逻辑：效果被 {@code /effect clear}、自然到期、
 *       玩家死亡清效果、被奶桶解毒……任何一种消失方式都会在 1 tick 内静音；</li>
 *   <li>断线/退出世界/换维度时声音引擎本身会清空，另外 {@code UnnameableClientHandler#forceShutdown}
 *       里还会显式停一次（见那里的调用点列表）。</li>
 * </ul>
 *
 * <p>非定位音（{@code relative = true} + {@link net.minecraft.client.resources.sounds.SoundInstance.Attenuation#NONE}）：
 * 不论玩家跑多远、朝哪看，音量都一样 —— 这是"贴在你耳边说话"而不是"世界上某处有声音"。
 * 音源用 {@link SoundSource#AMBIENT}，玩家可以用"环境音"滑条单独关掉它。
 */
@OnlyIn(Dist.CLIENT)
public class UnnameableWhisperSound extends AbstractTickableSoundInstance {

    public UnnameableWhisperSound(float volume, float pitch) {
        super(ModSounds.EFFECT_WHISPERS.get(), SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = volume;
        this.pitch = pitch;
    }

    /** 每 tick 一次：效果没了就停（引擎会在同一 tick 内关掉通道） */
    @Override
    public void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getEffect(ModEffects.UNNAMEABLE.get()) == null) {
            stop();
        }
    }
}
