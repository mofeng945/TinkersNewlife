package com.mofengbaizhi.tinkersnewlife.content.effect;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 冰冻效果（Frost Effect）
 * 使目标逐渐被冻结在冰块中
 */
public class FrostEffect extends MobEffect {

    public FrostEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        int current = entity.getTicksFrozen();
        entity.setTicksFrozen(current + 50 + 10 * amplifier);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class FrostEffectEventHandler {

        @SubscribeEvent
        public static void onEffectRemoved(MobEffectEvent.Remove event) {
            handleEffectEnd(event.getEntity(), event.getEffectInstance());
        }

        @SubscribeEvent
        public static void onEffectExpired(MobEffectEvent.Expired event) {
            handleEffectEnd(event.getEntity(), event.getEffectInstance());
        }

        private static void handleEffectEnd(LivingEntity entity, MobEffectInstance effectInstance) {
            if (entity == null || effectInstance == null) return;
            if (!effectInstance.getEffect().equals(ModEffects.FROST.get())) return;
            entity.setTicksFrozen(100);
        }
    }
}