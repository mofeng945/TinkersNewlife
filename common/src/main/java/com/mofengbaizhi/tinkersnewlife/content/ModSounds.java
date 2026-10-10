package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组自注册音效（墨默语音等）。
 * 语音占位文件位于 assets/tinkersnewlife/sounds/entity/momo/（后续用真实语音同名覆盖即可）。
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TinkersNewlife.MOD_ID);

    // ===== 墨默（武器商人）语音 =====
    public static final RegistryObject<SoundEvent> MOMO_AMBIENT = reg("entity.momo.ambient");
    public static final RegistryObject<SoundEvent> MOMO_HURT = reg("entity.momo.hurt");
    public static final RegistryObject<SoundEvent> MOMO_DEATH = reg("entity.momo.death");
    public static final RegistryObject<SoundEvent> MOMO_TRADE = reg("entity.momo.trade");
    /** 交易成功：固定播放空闲语音 2（momo_ambient2.ogg），替换自带的村民高兴语音 */
    public static final RegistryObject<SoundEvent> MOMO_TRADE_SUCCESS = reg("entity.momo.trade_success");

    // ===== 「不可名状」低语音频（客户端循环播放，见 client/sound/UnnameableWhisperSound）=====
    /** 低语音频：assets/tinkersnewlife/sounds/effects/whispers.ogg */
    public static final RegistryObject<SoundEvent> EFFECT_WHISPERS = reg("effect.whispers");

    // ===== 领域展开音频（两层叠加播放，见 DomainRegistry#playExpandSounds）=====
    /**
     * 领域展开·底层轰鸣：assets/tinkersnewlife/sounds/domain/base.ogg
     *
     * <p>⚠ 这两个用 {@link #reg(String, float)}（固定传播距离 64 格）而不是
     * {@link #reg(String)}（默认 16 格）：领域半径可以到 40+ 格，
     * 站在球壳边上的玩家离球心就有 40 格，用默认距离他根本听不到展开声。
     */
    public static final RegistryObject<SoundEvent> DOMAIN_BASE = reg("domain.base", 64.0F);
    /** 领域展开·展开爆音：assets/tinkersnewlife/sounds/domain/open.ogg（与 base 同时播放 → 叠加） */
    public static final RegistryObject<SoundEvent> DOMAIN_OPEN = reg("domain.open", 64.0F);

    // ===== §1096 唱片「墨封白织的唱片」=====
    /**
     * 唱片音效 id：{@code tinkersnewlife:music_doll} ✓
     *
     * <p>⚠ 这个 id 有<b>三个地方必须同名</b> ✓：
     * <ul>
     *   <li>{@code assets/tinkersnewlife/sounds.json} 里的键 {@code "music_doll"} ✓
     *       （键＝事件 id，值里的 {@code tinkersnewlife:music/doll_music} 才是 ogg 的路径 ✓）；</li>
     *   <li>这里注册的 {@link SoundEvent} ✓（{@code RecordItem} 拿它去放 ✓）；</li>
     *   <li>客户端 {@code FumoMoDanceHandler} 判"这是我们的唱片"就用它 ✓。</li>
     * </ul>
     * <p>用 16 格的默认传播距离（{@link #reg(String)}）就够 ✓ —— 原版唱片机放音时自己会传
     * {@code volume=4.0F} ⇒ 实际可听范围约 64 格 ✓（跳舞范围另有 16 格硬判 ✓ 见 FumoMoBlockEntity ✓）。
     */
    public static final RegistryObject<SoundEvent> MUSIC_DOLL = reg("music_doll");

    /** {@link #MUSIC_DOLL} 的 id（客户端判定用 ✓ 注册前就能拿到 ✓） */
    public static final ResourceLocation MUSIC_DOLL_ID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "music_doll");

    // ===== §1241 新唱片「Tell Me Tell Me」=====
    /** 新唱片音效 id：{@code tinkersnewlife:music_tell_me} ✓（⭐ 用户口径 ✓「不是替换，是新舞蹈和新唱片」✓） */
    public static final RegistryObject<SoundEvent> MUSIC_TELL_ME = reg("music_tell_me");

    /** {@link #MUSIC_TELL_ME} 的 id（客户端判定用 ✓ 注册前就能拿到 ✓） */
    public static final ResourceLocation MUSIC_TELL_ME_ID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "music_tell_me");

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TinkersNewlife.MOD_ID, name)));
    }

    /** 固定传播距离的音效（用于领域这种"范围远大于 16 格"的场合） */
    private static RegistryObject<SoundEvent> reg(String name, float range) {
        return SOUNDS.register(name,
                () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(TinkersNewlife.MOD_ID, name), range));
    }
}
