package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.client.event.sound.PlayStreamingSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * §1083 <b>让我们的 fumo 跟着「朋友的酒」({@code friendswine}) 的音乐一起转／挤压</b> ✓
 * —— 用户口径：「<b>联动一下我新加的朋友的酒模组，让我的玩偶也跟随音乐播放一块转动挤压</b>」✓
 *
 * <p>§1096 起<b>多了一条完全独立的来源</b> ✓：<b>我们自己的唱片</b>
 * （{@code tinkersnewlife:music_disc_doll_music} ✓ 见 {@code ModItems}／{@code ModSounds} ✓）
 * 放进<b>原版唱片机</b>播放时 —— 用户口径：「<b>cd播放时，周围16格范围内的所有fumo会挤压着转起来
 * （无朋友的酒模组情况下）</b>」✓ ⇒ 这一条<b>不依赖</b> {@code friendswine} ✓
 * （本类对它仍然只有"命名空间字符串"这一层软依赖 ✓ 没装就永不命中 ✓）。
 *
 * <h2>为什么要"听声音"而不是调它的 API</h2>
 * 反编译看过 ✓：{@code friendswine} 自己就有一套玩偶跳舞（{@code com.friendswine.DollBlockEntity} ✓
 * 客户端音乐 {@code com.friendswine.client.DollMusicSound} ✓，配置项 {@code rotationSpeed}/{@code orbitSpeed}/
 * {@code kasumiMinScale} ✓ 见它自己的语言文件 ✓）。但它**没有对外 API** ✗ ——
 * ⇒ 我们**不碰它的类** ✗、**不加 mixin** ✗、**不写死它的内部字段** ✗；
 * 改为听 Forge 的**客户端声音事件** ✓：
 * <ul>
 *   <li>{@link PlaySoundSourceEvent} ✓ ＋ {@link PlayStreamingSourceEvent} ✓ —— 这两个是
 *       <b>每 tick、每条正在播放的声音</b>都会触发 ✓ ⇒ 长曲子也能**持续**知道"还在放" ✓
 *       （{@code PlaySoundEvent} 只在开始那一下 ✓ 不够用 ✗）；</li>
 *   <li>判据＝声音 id 的**命名空间是 {@code friendswine}** ✓（它所有音乐都在这个命名空间 ✓）；</li>
 *   <li>范围＝音乐坐标 {@link FumoMoBlockEntity#DANCE_RANGE} 格内的**我们的**玩偶才跳 ✓
 *       （远处的不动 ✓ 多人也不串 ✓）。</li>
 * </ul>
 *
 * <h2>§1096 我们的唱片那条怎么走</h2>
 * 原版唱片机放音时走的是 {@code LevelRenderer#playStreamingMusic} ⇒
 * {@code SimpleSoundInstance.forRecord(...)} ✓ ⇒ 声音实例的 id 就是我们的 {@code tinkersnewlife:music_doll} ✓、
 * 坐标就是唱片机那一格 ✓ ⇒ 这里把它记给 {@link FumoMoBlockEntity#noteDiscSource} ✓
 * （"到底还在不在放"由玩偶每 tick 自己去看那格唱片机 ✓ 见 {@code maintainDance} ✓）。
 * <p>另：<b>唱片机换成别的唱片</b>（原版唱片也是 {@code SoundSource.RECORDS} ✓）⇒
 * 立刻把那一格的"我们的唱片"作废 ✓ —— 否则那台唱片机 {@code HAS_RECORD} 仍为真 ✗
 * 玩偶会跟着别人的唱片继续跳 ✗。
 *
 * <h2>软依赖 ✓</h2>
 * 没装 {@code friendswine} ⇒ ③号分支永不命中 ⇒ **零开销、零报错** ✓；
 * 本类只依赖原版/Forge 的公开事件 ✓ 不引用它的任何类型 ✓ ⇒ 两个整合包都能正常启动 ✓
 *（该模组目前只装在**测试包** ✓）。§1096 那条更是纯原版机制 ✓。</p>
 *
 * <p>⚠ 舞蹈是**纯客户端视觉** ✓（时间戳只存在方块实体的内存里 ✓ 不进 NBT、不发包 ✓）——
 * 与 §908 的"抚摸挤压"同一路子 ✓；音乐一停 ⇒ 时间戳过期 ⇒ 玩偶**自动回正** ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class FumoMoDanceHandler {

    /** 认定"这是朋友在放音乐"的命名空间 ✓（它全部音乐都在 {@code friendswine:*} ✓） */
    private static final String MUSIC_NAMESPACE = "friendswine";
    /**
     * 每听到一次音乐，给玩偶续多久（tick ✓）。
     *
     * <p>⚠ <b>为什么是 2 分钟这么长</b> ✗——用户实测（§1085）：「联动玩偶只转了一下下就不转了」✓
     * ⇒ 说明 {@code friendswine} 的音乐**并不是每 tick 都发这两个声音事件** ✗
     * （我原先按"每 tick 都发"设计 ⇒ 1.25 秒窗口 ⇒ 转一下就停 ✗）。
     * ⇒ 改成**一次听到就续 2 分钟**（2400 tick ✓，盖得住它最长那几首 ✓），
     * 期间再听到（换曲/重放 ✓）继续往后延 ✓；音乐停了至多 2 分钟后自动回正 ✓。
     * <p>想更准地"跟着曲子起停"得去读它 {@code DollMusicSound} 的内部字段 ✗（版本一变就崩 ✗）
     * —— 除非你要求 ✓，否则不走那条 ✓。
     * <p>⚠ §1096：这条窗口<b>只管</b>朋友的酒那条分支 ✓ —— 我们自己的唱片那条不看它 ✓
     * （那条由唱片机本身的曲子长度兜底 ✓ 见 {@code FumoMoBlockEntity} ✓）。
     */
    public static final int DANCE_REFRESH_TICKS = 2400;

    private FumoMoDanceHandler() {
    }

    @SubscribeEvent
    public static void onSoundSource(PlaySoundSourceEvent event) {
        mark(event.getSound());
    }

    @SubscribeEvent
    public static void onStreamingSource(PlayStreamingSourceEvent event) {
        mark(event.getSound());
    }

    /** 声音在放 ⇒ 把它附近的我们的玩偶点着 ✓（任何异常都吞掉 ✓ 绝不干扰声音系统本身 ✓） */
    private static void mark(SoundInstance sound) {
        try {
            if (sound == null) return;
            net.minecraft.resources.ResourceLocation id = sound.getLocation();
            if (id == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            // ── §1096 ①我们自己的唱片（原版唱片机在放）⇒ 只记下"那台唱片机在哪" ✓
            //    这条路**完全不碰** friendswine ✓（没装它也照样跳 ✓）
            // ⭐ §1241 **新唱片 ⇒ ⭐ 新舞蹈** ✗（⭐ 用户口径 ✓「不是替换，是新舞蹈和新唱片」✓）
            if (com.mofengbaizhi.tinkersnewlife.content.ModSounds.MUSIC_TELL_ME_ID.equals(id)) {
                FumoMoBlockEntity.noteDiscSource(mc.level, sound.getX(), sound.getY(), sound.getZ(),
                        1, com.mofengbaizhi.tinkersnewlife.content.ModItems.MUSIC_DISC_TELL_ME_LENGTH_TICKS);
                return;
            }
            if (com.mofengbaizhi.tinkersnewlife.content.ModSounds.MUSIC_DOLL_ID.equals(id)) {
                FumoMoBlockEntity.noteDiscSource(mc.level, sound.getX(), sound.getY(), sound.getZ(),
                        0, com.mofengbaizhi.tinkersnewlife.content.ModItems.MUSIC_DISC_DOLL_MUSIC_LENGTH_TICKS);
                return;
            }
            // ── §1096 ②同一格唱片机改放**别的**唱片（原版唱片走的是 SoundSource.RECORDS ✓）⇒
            //    把我们那张立刻作废 ✓（不然后面 HAS_RECORD 仍为真 ⇒ 会跟着别人的唱片跳 ✗）
            if (sound.getSource() == net.minecraft.sounds.SoundSource.RECORDS) {
                FumoMoBlockEntity.clearDiscSourceAt(sound.getX(), sound.getY(), sound.getZ());
                return;
            }
            // ── §1083~§1090 ③朋友的酒（软依赖 ✓ 没装 ⇒ 这里永不命中 ✓）
            if (!MUSIC_NAMESPACE.equals(id.getNamespace())) return;
            // §1086 只记"音乐从哪儿放的" ✓ —— 真正"在不在放"由方块实体每 tick 反射问它自己的
            //   DollBlockEntity#isPlaying() 决定 ✓（用户实测：这样音乐停就真的停 ✓ 见 maintainDance ✓）
            FumoMoBlockEntity.noteMusicSource(mc.level, sound.getX(), sound.getY(), sound.getZ());
        } catch (Throwable ignored) {
            // fail-safe：联动失败最多是"玩偶不跳" ✓ 绝不影响音乐播放 ✓
        }
    }
}
