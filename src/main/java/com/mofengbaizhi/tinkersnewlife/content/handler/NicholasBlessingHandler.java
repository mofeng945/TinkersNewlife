package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NicholasBlessingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NicholasBlessingHandler.class);

    private static final double TRIGGER_CHANCE = 0.25;
    private static final int EVENT_DURATION_TICKS = 60 * 20;
    private static final double REWARD_CHANCE = 0.5;
    private static final int REWARD_ITEM_COUNT = 32;
    private static final int REGENERATION_DURATION = 10 * 60 * 20;
    private static final int WITHER_DURATION = 1 * 60 * 20;

    private static final Map<UUID, EventData> ACTIVE_EVENTS = new ConcurrentHashMap<>();

    private static class EventData {
        final Player player;
        final int startTick;

        EventData(Player player) {
            this.player = player;
            this.startTick = player.tickCount;
        }

        int getElapsedTicks() {
            return player.tickCount - startTick;
        }

        boolean isExpired() {
            return getElapsedTicks() >= EVENT_DURATION_TICKS;
        }
    }

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getParentA() instanceof Animal animalA) ||
            !(event.getParentB() instanceof Animal animalB)) {
            return;
        }

        Player player = animalA.getLoveCause();
        if (player == null) {
            player = animalB.getLoveCause();
        }
        if (player == null) return;

        Level level = player.level();
        if (level.isClientSide) return;

        if (level.random.nextDouble() >= TRIGGER_CHANCE) return;
        if (ACTIVE_EVENTS.containsKey(player.getUUID())) return;

        triggerEvent(player);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        UUID uuid = player.getUUID();
        ACTIVE_EVENTS.remove(uuid);
        removeUnnameable(player);
        LOGGER.info("[NicholasBlessing] Player {} died during event", player.getName().getString());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        EventData data = ACTIVE_EVENTS.remove(uuid);
        if (data != null) {
            removeUnnameable(data.player);
            LOGGER.info("[NicholasBlessing] Player {} logged out during event, cleaned up",
                    event.getEntity().getName().getString());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Map.Entry<UUID, EventData> entry : ACTIVE_EVENTS.entrySet()) {
            EventData data = entry.getValue();
            Player player = data.player;

            if (!player.isAlive() || player.isRemoved()) {
                ACTIVE_EVENTS.remove(entry.getKey());
                removeUnnameable(player);
                continue;
            }

            ensureUnnameableEffect(player);

            if (data.isExpired()) {
                ACTIVE_EVENTS.remove(entry.getKey());
                removeUnnameable(player);
                completeEvent(player);
            }
        }
    }

    private static void triggerEvent(Player player) {
        ACTIVE_EVENTS.put(player.getUUID(), new EventData(player));
        ensureUnnameableEffect(player);

        Level level = player.level();
        Vec3 pos = player.position();
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2.0f, 0.3f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.PORTAL,
                    pos.x, pos.y + 0.5, pos.z,
                    30, 1.0, 1.0, 1.0, 0.2);
        }

        player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.nicholas_blessing.start"), true);
        LOGGER.info("[NicholasBlessing] Player {} triggered event", player.getName().getString());
    }

    private static void ensureUnnameableEffect(Player player) {
        if (ModEffects.UNNAMEABLE.get() == null) return;
        MobEffectInstance current = player.getEffect(ModEffects.UNNAMEABLE.get());
        if (current == null || current.getDuration() < 100) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.UNNAMEABLE.get(),
                    EVENT_DURATION_TICKS,
                    0,
                    false, true, true
            ));
        }
    }

    private static void removeUnnameable(Player player) {
        if (ModEffects.UNNAMEABLE.get() != null) {
            player.removeEffect(ModEffects.UNNAMEABLE.get());
        }
    }

    private static void completeEvent(Player player) {
        Level level = player.level();
        boolean reward = level.random.nextDouble() < REWARD_CHANCE;

        if (reward) {
            applyReward(player);
            LOGGER.info("[NicholasBlessing] Player {} received reward", player.getName().getString());
        } else {
            applyPunishment(player);
            LOGGER.info("[NicholasBlessing] Player {} received punishment", player.getName().getString());
        }
    }

    private static void applyReward(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_DURATION, 1, false, true, true));
        ItemStack stack = new ItemStack(ModItems.NICHOLAS_BLESSING.get(), REWARD_ITEM_COUNT);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        if (player.level() instanceof ServerLevel server) {
            Vec3 pos = player.position();
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 1, pos.z,
                    30, 0.5, 0.5, 0.5, 0.2);
        }
        player.displayClientMessage(Component.translatable("event.tinkersnewlife.nicholas_blessing.reward"), true);
    }

    private static void applyPunishment(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION, 2, false, true, true));
        if (player.level() instanceof ServerLevel server) {
            Vec3 pos = player.position();
            server.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 1, pos.z,
                    30, 0.5, 0.5, 0.5, 0.2);
        }
        player.displayClientMessage(Component.translatable("event.tinkersnewlife.nicholas_blessing.punishment"), true);
    }
}