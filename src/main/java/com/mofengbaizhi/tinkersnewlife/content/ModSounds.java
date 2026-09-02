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

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(TinkersNewlife.MOD_ID, name)));
    }
}
