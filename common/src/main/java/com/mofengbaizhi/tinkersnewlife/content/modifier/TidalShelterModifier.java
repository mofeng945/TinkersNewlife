package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;

@Mod.EventBusSubscriber(modid = "tinkersnewlife")
public final class TidalShelterModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(new ResourceLocation("tinkersnewlife", "tidal_shelter"));
    private static final String STATE = "tinkersnewlife:tidal_shelter_effects";
    private static final int CHECK_INTERVAL = 10;
    private static final int MAX_STRENGTH = 8;

    private static int rank(LivingEntity wearer, EquipmentSlot slot) {
        var stack = wearer.getItemBySlot(slot);
        boolean eligible = slot.getType() == EquipmentSlot.Type.HAND
                ? stack.is(TinkerTags.Items.SHIELDS)
                : stack.is(TinkerTags.Items.WORN_ARMOR) && LivingEntity.getEquipmentSlotForItem(stack) == slot;
        return eligible ? ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(stack), ID) : 0;
    }

    private static int total(LivingEntity wearer) {
        int levels = 0;
        for (var slot : EquipmentSlot.values()) levels += rank(wearer, slot);
        return levels;
    }

    private static boolean exposedToWaterWeather(LivingEntity wearer) {
        Level level = wearer.level();
        var pos = wearer.blockPosition();
        return wearer.isInWaterOrRain() || (level.isRaining() && level.canSeeSky(pos) && level.isRainingAt(pos));
    }

    private static String key(MobEffect effect) {
        return net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect).toString().replace(':', '_');
    }

    private static boolean isOwned(MobEffectInstance instance, int amplifier) {
        return instance != null && instance.isInfiniteDuration() && instance.getAmplifier() == amplifier
                && !instance.isAmbient() && instance.showIcon();
    }

    private static MobEffectInstance create(MobEffect effect, int amplifier, MobEffectInstance hidden) {
        return new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier,
                false, false, true, hidden == null ? null : new MobEffectInstance(hidden), java.util.Optional.empty());
    }

    /** Apply a hidden infinite effect while preserving/restoring any weaker pre-existing effect. */
    private static void maintain(LivingEntity wearer, MobEffect effect, int amplifier, boolean wanted) {
        var data = wearer.getPersistentData();
        var all = data.getCompound(STATE);
        String key = key(effect);
        boolean tracked = all.contains(key);
        var current = wearer.getEffect(effect);
        if (!wanted) {
            if (tracked) {
                var saved = all.getCompound(key);
                int ownedAmp = saved.getInt("amplifier");
                if (isOwned(current, ownedAmp)) {
                    wearer.removeEffect(effect);
                    if (saved.contains("previous")) wearer.addEffect(MobEffectInstance.load(saved.getCompound("previous")));
                }
                all.remove(key);
                if (all.isEmpty()) data.remove(STATE); else data.put(STATE, all);
            }
            return;
        }
        CompoundTag saved = tracked ? all.getCompound(key).copy() : new CompoundTag();
        if (tracked && isOwned(current, saved.getInt("amplifier"))) {
            if (current.getAmplifier() == amplifier) return;
            // Upgrade/downgrade our own instance while retaining its original hidden effect chain.
            saved.putInt("amplifier", amplifier);
            wearer.addEffect(create(effect, amplifier, current));
            all.put(key, saved);
            data.put(STATE, all);
            return;
        }
        if (current != null && current.getAmplifier() >= amplifier) {
            // Another source supplies an equal or stronger effect; do not replace or later remove it.
            if (tracked) {
                all.remove(key);
                if (all.isEmpty()) data.remove(STATE); else data.put(STATE, all);
            }
            return;
        }
        // Preserve a weaker effect that appeared while our own effect was absent/replaced.
        if (current != null && (!tracked || !isOwned(current, saved.getInt("amplifier")))) {
            saved.put("previous", current.save(new CompoundTag()));
        }
        saved.putInt("amplifier", amplifier);
        wearer.addEffect(create(effect, amplifier, current));
        all.put(key, saved);
        data.put(STATE, all);
    }

    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide || (wearer.tickCount % CHECK_INTERVAL) != 0) return;
        int levels = IntegrationLoader.isIceAndFire() && wearer.isAlive() && !wearer.isSpectator() ? total(wearer) : 0;
        boolean atLeastTwo = levels >= 2;
        boolean wet = atLeastTwo && exposedToWaterWeather(wearer);
        maintain(wearer, MobEffects.WATER_BREATHING, 0, atLeastTwo);
        maintain(wearer, MobEffects.NIGHT_VISION, 0, wet);
        maintain(wearer, MobEffects.DOLPHINS_GRACE, 2, wet);
        maintain(wearer, MobEffects.DAMAGE_BOOST, Math.min(MAX_STRENGTH, levels) - 1, wet);
    }
}
