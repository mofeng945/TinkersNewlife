package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class DragonSpirit {
    private static final String ACTIVE = "tnlDragonSpirit";
    private DragonSpirit() {}

    public static void mark(Entity dragon, boolean active) {
        if (active) dragon.getPersistentData().putBoolean(ACTIVE, true);
        else dragon.getPersistentData().remove(ACTIVE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preventGrief(EntityMobGriefingEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(ACTIVE)) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void preventSuffocation(LivingAttackEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(ACTIVE) && (event.getSource().is(DamageTypes.IN_WALL)
                || event.getSource().is(DamageTypes.CRAMMING))) event.setCanceled(true);
    }
}
