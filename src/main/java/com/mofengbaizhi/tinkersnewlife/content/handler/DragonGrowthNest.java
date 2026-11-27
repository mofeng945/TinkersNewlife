package com.mofengbaizhi.tinkersnewlife.content.handler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Dragon staff growth and nest mechanics; the staff owns slot eligibility and timing. */
public final class DragonGrowthNest {
    private static final String GROWTH_MARKER = "tinkersnewlife:dragon_staff_growth";
    private static final int RELEASE_BUFF_TICKS = 20 * 20;

    private DragonGrowthNest() {}

    /** Updates the persistent bonus marker for a successfully released, registered dragon. */
    public static void markReleased(LivingEntity dragon, int growthLevel) {
        if (dragon.level().isClientSide) return;
        if (growthLevel > 0) {
            dragon.getPersistentData().putBoolean(GROWTH_MARKER, true);
        } else {
            dragon.getPersistentData().remove(GROWTH_MARKER);
        }
    }

    /** Used only at Ice and Fire's regular Dragon Meal growth call site. */
    public static int regularMealGrowthDays(LivingEntity dragon, int days) {
        return !dragon.level().isClientSide && dragon.getPersistentData().getBoolean(GROWTH_MARKER)
                ? days * 2 : days;
    }

    /**
     * Heals a living dragon's stored entity NBT. Pass the stored dragon's effective max health
     * and only the newly elapsed storage ticks (20 ticks heal 2% of max health).
     * Returns whether the stored health changed.
     */
    public static boolean healStored(CompoundTag data, float maxHealth, long elapsedTicks) {
        if (elapsedTicks <= 0 || !Float.isFinite(maxHealth) || maxHealth <= 0
                || !data.contains("Health", Tag.TAG_ANY_NUMERIC)) return false;
        float health = data.getFloat("Health");
        if (!Float.isFinite(health) || health <= 0 || health >= maxHealth) return false;
        float healed = (float) Math.min(maxHealth, health + maxHealth * (elapsedTicks / 1000.0));
        if (healed <= health) return false;
        data.putFloat("Health", healed);
        return true;
    }

    /** Applies the nest bonus to a successfully released dragon, only if already at full health. */
    public static void applyNestRelease(LivingEntity dragon) {
        applyNestRelease(dragon, dragon.getHealth() >= dragon.getMaxHealth());
    }

    /** Uses the full-health snapshot taken before modifiers change the dragon's max health on release. */
    public static void applyNestRelease(LivingEntity dragon, boolean wasFull) {
        if (!wasFull || dragon.level().isClientSide || !dragon.isAlive()) return;
        dragon.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RELEASE_BUFF_TICKS, 0));
        dragon.addEffect(new MobEffectInstance(MobEffects.REGENERATION, RELEASE_BUFF_TICKS, 0));
    }
}
