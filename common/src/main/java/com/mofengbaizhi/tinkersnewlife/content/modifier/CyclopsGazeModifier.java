package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class CyclopsGazeModifier extends NoLevelsModifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("cyclops_gaze"));

    private static boolean active(LivingEntity wearer) {
        return !wearer.level().isClientSide && wearer.isAlive() && !wearer.isSpectator()
                && IntegrationLoader.isIceAndFire() && ToolHelper.getActiveModifierLevel(
                ToolHelper.getToolStack(wearer.getItemBySlot(EquipmentSlot.HEAD)), ID) > 0;
    }

    @SubscribeEvent public static void aura(LivingEvent.LivingTickEvent event) {
        var wearer = event.getEntity();
        if (wearer.tickCount % 5 != 0 || !active(wearer)) return;
        for (var target : wearer.level().getEntitiesOfClass(LivingEntity.class, wearer.getBoundingBox().inflate(8),
                e -> e != wearer && e.isAlive() && !e.isSpectator() && wearer.distanceToSqr(e) <= 64)) {
            var weakness = target.getEffect(MobEffects.WEAKNESS);
            if (weakness == null || weakness.getAmplifier() == 0 && weakness.endsWithin(6)) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 11, 0, true, false, true), wearer);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void retaliate(LivingAttackEvent event) {
        var wearer = event.getEntity();
        if (event.getAmount() > 0 && event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker != wearer && active(wearer) && wearer.distanceToSqr(attacker) <= 64) {
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2), wearer);
        }
    }
}
