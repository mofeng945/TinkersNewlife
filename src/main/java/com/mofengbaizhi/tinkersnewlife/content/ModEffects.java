package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.effect.DisarmEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.FrostEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.DamageLimitEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.UnnameableEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.CharmEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.StunEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TinkersNewlife.MOD_ID);

    public static final RegistryObject<DisarmEffect> DISARM =
            EFFECTS.register("disarm", () -> new DisarmEffect(MobEffectCategory.HARMFUL, 0xFFAA00));

    public static final RegistryObject<FrostEffect> FROST =
            EFFECTS.register("frost", () -> new FrostEffect(MobEffectCategory.HARMFUL, 0x00BFFF));

    public static final RegistryObject<DamageLimitEffect> DAMAGE_LIMIT =
            EFFECTS.register("damage_limit", () -> new DamageLimitEffect(MobEffectCategory.BENEFICIAL, 0x66FF66));

    public static final RegistryObject<UnnameableEffect> UNNAMEABLE =
            EFFECTS.register("unnameable", () -> new UnnameableEffect(MobEffectCategory.HARMFUL, 0x4A0E4E));

    public static final RegistryObject<CharmEffect> CHARM =
            EFFECTS.register("charm", () -> new CharmEffect(MobEffectCategory.HARMFUL, 0xFF69B4));

    /** 静止（无量空处）：完全定身 */
    public static final RegistryObject<StunEffect> STUN =
            EFFECTS.register("stun", () -> new StunEffect(MobEffectCategory.HARMFUL, 0x000000));
}