package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class HydraCurseModifier extends NoLevelsModifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("hydra_curse"));
    private static final String FIRE_UNTIL = "tnlHydraFireUntil";
    private static final String REVIVE_AT = "tnlHydraReviveAt";

    private static boolean active(LivingEntity wearer) {
        return IntegrationLoader.isIceAndFire() && !wearer.isSpectator()
                && ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(wearer.getItemBySlot(EquipmentSlot.CHEST)), ID) > 0;
    }

    private static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    public static boolean canRegenerate(LivingEntity wearer) {
        return wearer.level() instanceof ServerLevel level && wearer.isAlive() && active(wearer)
                && !wearer.isOnFire() && !wearer.isInLava() && !wearer.isEyeInFluid(FluidTags.LAVA)
                && !wearer.isInFluidType((type, depth) -> depth > 0 && type.getTemperature() >= 1000, false)
                && now(level) >= wearer.getPersistentData().getLong(FIRE_UNTIL);
    }

    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        var wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        var regeneration = ModEffects.HYDRA_REGENERATION.get();
        if (canRegenerate(wearer)) {
            if (!wearer.hasEffect(regeneration)) {
                wearer.addEffect(new MobEffectInstance(regeneration, MobEffectInstance.INFINITE_DURATION, 3, true, false, true));
            }
        } else if (wearer.hasEffect(regeneration)) wearer.removeEffect(regeneration);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fire(LivingHurtEvent event) {
        var wearer = event.getEntity();
        if (!(wearer.level() instanceof ServerLevel level) || !event.getSource().is(DamageTypeTags.IS_FIRE)
                || event.getAmount() <= 0) return;
        // Record fire hits even if the chestplate is equipped only after the hit.
        wearer.getPersistentData().putLong(FIRE_UNTIL, now(level) + 100);
        if (active(wearer)) event.setAmount(event.getAmount() * 2);
        wearer.removeEffect(ModEffects.HYDRA_REGENERATION.get());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void revive(LivingDeathEvent event) {
        var wearer = event.getEntity();
        if (!(wearer.level() instanceof ServerLevel level) || !active(wearer)
                || event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || wearer.isOnFire() || wearer.isInLava() || wearer.isEyeInFluid(FluidTags.LAVA)
                || wearer.isInFluidType((type, depth) -> depth > 0 && type.getTemperature() >= 1000, false)) return;
        long currentTime = now(level);
        var data = wearer.getPersistentData();
        if (currentTime < data.getLong(FIRE_UNTIL) || currentTime < data.getLong(REVIVE_AT)) return;
        data.putLong(REVIVE_AT, currentTime + 4000);
        event.setCanceled(true);
        wearer.setHealth(wearer.getMaxHealth());
        wearer.setAirSupply(wearer.getMaxAirSupply());
        wearer.fallDistance = 0;
        level.broadcastEntityEvent(wearer, (byte)35);
    }

    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        var previous = event.getOriginal().getPersistentData();
        var current = event.getEntity().getPersistentData();
        if (previous.contains(REVIVE_AT)) current.putLong(REVIVE_AT, previous.getLong(REVIVE_AT));
        if (previous.contains(FIRE_UNTIL)) current.putLong(FIRE_UNTIL, previous.getLong(FIRE_UNTIL));
    }
}
