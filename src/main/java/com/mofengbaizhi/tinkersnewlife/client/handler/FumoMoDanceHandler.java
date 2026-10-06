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
 *   <li>范围＝音乐坐标 {@link #RANGE} 格内的**我们的**玩偶才跳 ✓（远处的不动 ✓ 多人也不串 ✓）。</li>
 * </ul>
 *
 * <h2>软依赖 ✓</h2>
 * 没装 {@code friendswine} ⇒ 事件永不命中 ⇒ **零开销、零报错** ✓；
 * 本类只依赖原版/Forge 的公开事件 ✓ 不引用它的任何类型 ✓ ⇒ 两个整合包都能正常启动 ✓
 *（该模组目前只装在**测试包** ✓）。
 *
 * <p>⚠ 舞蹈是**纯客户端视觉** ✓（时间戳只存在方块实体的内存里 ✓ 不进 NBT、不发包 ✓）——
 * 与 §908 的"抚摸挤压"同一路子 ✓；音乐一停 ⇒ 时间戳过期 ⇒ 玩偶**自动回正** ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class FumoMoDanceHandler {

    /** 认定"这是朋友在放音乐"的命名空间 ✓（它全部音乐都在 {@code friendswine:*} ✓） */
    private static final String MUSIC_NAMESPACE = "friendswine";
    /** 音乐离玩偶多近才一起跳（格 ✓） */
    private static final double RANGE = 16.0D;
    /**
     * 每听到一次音乐，给玩偶续多久（tick ✓）——
     * 声音事件**每 tick 都来** ✓ ⇒ 25 只是留点余量 ✓（音乐停 ⇒ 至多 1.25 秒后就回正 ✓）。
     */
    public static final int DANCE_REFRESH_TICKS = 25;

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
            if (id == null || !MUSIC_NAMESPACE.equals(id.getNamespace())) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;
            FumoMoBlockEntity.markDancingNear(mc.level,
                    sound.getX(), sound.getY(), sound.getZ(), RANGE, DANCE_REFRESH_TICKS);
        } catch (Throwable ignored) {
            // fail-safe：联动失败最多是"玩偶不跳" ✓ 绝不影响音乐播放 ✓
        }
    }
}
