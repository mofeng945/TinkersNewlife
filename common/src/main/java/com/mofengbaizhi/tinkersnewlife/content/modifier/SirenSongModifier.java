package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class SirenSongModifier extends NoLevelsModifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("siren_song"));
    private static final ResourceLocation SONG = new ResourceLocation("iceandfire", "siren_song");
    private static final String READY = "tinkersnewlife:siren_song_ready";
    private static final int DURATION = 200;
    private record Lock(WeakReference<LivingEntity> target, long until) {}
    private static final Map<Mob, Lock> LOCKS = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hit(LivingDamageEvent event) {
        var target = event.getEntity();
        var source = event.getSource();
        if (!(target.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker)
                || attacker == target || !target.isAlive() || event.getAmount() <= 0 || !IntegrationLoader.isIceAndFire()) return;
        long now = level.getServer().overworld().getGameTime();
        var data = attacker.getPersistentData();
        if (now < data.getLong(READY)) return;
        if (source.getDirectEntity() instanceof Projectile projectile) {
            if (EntityModifierCapability.getOrEmpty(projectile).getLevel(ID) <= 0) return;
        } else {
            if (source.getDirectEntity() != attacker) return;
            var tool = ToolHelper.getToolStack(attacker.getMainHandItem());
            if (ToolHelper.getActiveModifierLevel(tool, ID) <= 0) return;
        }
        data.putLong(READY, now + DURATION);
        var sound = ForgeRegistries.SOUND_EVENTS.getValue(SONG);
        if (sound != null) level.playSound(null, attacker.blockPosition(), sound, SoundSource.PLAYERS, 1, 1);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(8),
                mob -> mob != target && mob != attacker && mob.isAlive() && !mob.isNoAi()
                        && mob.distanceToSqr(target) <= 64)) {
            var previous = LOCKS.remove(mob);
            mob.setTarget(target);
            if (mob.getTarget() == target) LOCKS.put(mob, new Lock(new WeakReference<>(target), now + DURATION));
            else if (previous != null) LOCKS.put(mob, previous);
        }
    }

    private static LivingEntity lockedTarget(Mob mob, Lock lock) {
        if (lock == null || !(mob.level() instanceof ServerLevel level)) return null;
        var target = lock.target().get();
        return target != null && target.isAlive() && !target.isRemoved() && target.level() == level
                && !target.isSpectator() && level.getServer().overworld().getGameTime() < lock.until() ? target : null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void changeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide) {
            var target = lockedTarget(mob, LOCKS.get(mob));
            if (target != null) event.setNewTarget(target);
        }
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        var lock = LOCKS.get(mob);
        if (lock == null) return;
        var target = lockedTarget(mob, lock);
        if (target == null) {
            LOCKS.remove(mob);
            if (mob.getTarget() != null && mob.getTarget() == lock.target().get()) mob.setTarget(null);
        } else if (mob.getTarget() != target) mob.setTarget(target);
    }
}
