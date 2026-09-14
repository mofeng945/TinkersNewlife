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
