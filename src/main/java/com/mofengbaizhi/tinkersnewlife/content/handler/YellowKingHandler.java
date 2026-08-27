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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YellowKingHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(YellowKingHandler.class);

    // ==================== 事件参数 ====================
    private static final int START_Y = 400;
    private static final int END_Y = -50;
    private static final int EVENT_DURATION_TICKS = 20 * 20;
    private static final int DAMAGE_INTERVAL_TICKS = 3 * 20;
    private static final int TOTAL_DAMAGE_TIMES = 6;
    private static final float DAMAGE_AMOUNT = 3.0f;
    private static final double REWARD_CHANCE = 0.5;
    private static final int REWARD_COUNT = 16;
    private static final int PUNISH_DURATION_TICKS = 60 * 20;

    // ==================== 状态记录 ====================
    private static final Map<UUID, Boolean> FALLING_FROM_HIGH = new ConcurrentHashMap<>();
    private static final Map<UUID, EventData> ACTIVE_EVENTS = new ConcurrentHashMap<>();

    private static class EventData {
        final Player player;
        final int startTick;
        int damageCount;

        EventData(Player player, int startTick) {
            this.player = player;
            this.startTick = startTick;
            this.damageCount = 0;
        }

        int getElapsedTicks() {
            return player.tickCount - startTick;
        }

        boolean isExpired() {
            return getElapsedTicks() >= EVENT_DURATION_TICKS;
        }

        boolean shouldDamage() {
            int nextDamageTick = damageCount * DAMAGE_INTERVAL_TICKS;
            return damageCount < TOTAL_DAMAGE_TIMES && getElapsedTicks() >= nextDamageTick;
        }
    }

    // ==================== 检测下落触发 ====================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        Level level = player.level();
        if (level.isClientSide) return;

        UUID uuid = player.getUUID();

        if (!player.isAlive() || ACTIVE_EVENTS.containsKey(uuid)) return;

        double y = player.getY();
        double motionY = player.getDeltaMovement().y;

        if (y > START_Y && motionY < 0) {
            FALLING_FROM_HIGH.put(uuid, true);
        }

        if (FALLING_FROM_HIGH.getOrDefault(uuid, false) && y < END_Y) {
            FALLING_FROM_HIGH.remove(uuid);
            triggerEvent(player);
        }

        if (motionY >= 0) {
            FALLING_FROM_HIGH.remove(uuid);
        }
    }

    // ==================== 触发事件 ====================
    private static void triggerEvent(Player player) {
        UUID uuid = player.getUUID();
        if (ACTIVE_EVENTS.containsKey(uuid)) return;

        Level level = player.level();

        // 只需要施加不可名状（内部已集成失明/虚弱/缓慢/反胃/饥饿）
        if (ModEffects.UNNAMEABLE.get() != null) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.UNNAMEABLE.get(),
                    EVENT_DURATION_TICKS,
                    0,
                    false, false, true
            ));
        }

        // 音效 + 粒子
        Vec3 pos = player.position();
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5f, 0.3f);

        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SONIC_BOOM,
                    pos.x, pos.y + 0.5, pos.z,
                    30, 0.8, 0.8, 0.8, 0.1);
            server.sendParticles(ParticleTypes.PORTAL,
                    pos.x, pos.y + 0.5, pos.z,
                    50, 1.0, 1.0, 1.0, 0.2);
            server.sendParticles(ParticleTypes.WITCH,
                    pos.x, pos.y + 0.5, pos.z,
                    20, 0.5, 0.5, 0.5, 0.05);
        }

        ACTIVE_EVENTS.put(uuid, new EventData(player, player.tickCount));

        player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.yellow_king.start"),
                true
        );

        LOGGER.debug("[YellowKing] Player {} triggered the event at Y={}",
                player.getName().getString(), player.getY());
    }

    // ==================== 事件计时处理 ====================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Map.Entry<UUID, EventData> entry : ACTIVE_EVENTS.entrySet()) {
            UUID uuid = entry.getKey();
            EventData data = entry.getValue();
            Player player = data.player;

            if (!player.isAlive() || player.isRemoved()) {
                ACTIVE_EVENTS.remove(uuid);
                removeUnnameable(player);
                continue;
            }

            if (data.isExpired()) {
                ACTIVE_EVENTS.remove(uuid);
                removeUnnameable(player);
                completeEvent(player);
                continue;
            }

            if (data.shouldDamage()) {
                boolean hurt = player.hurt(player.damageSources().magic(), DAMAGE_AMOUNT);
                if (hurt) {
                    data.damageCount++;
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 1.0f);
                    if (player.level() instanceof ServerLevel server) {
                        Vec3 pos = player.position();
                        server.sendParticles(ParticleTypes.ENCHANT,
                                pos.x, pos.y + 0.5, pos.z,
                                15, 0.3, 0.3, 0.3, 0.1);
                        server.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                                pos.x, pos.y + 0.5, pos.z,
                                5, 0.2, 0.2, 0.2, 0);
                    }
                }
            }
        }
    }

    // ==================== 事件完成 ====================
    private static void completeEvent(Player player) {
        if (!player.isAlive()) {
            player.displayClientMessage(
                    Component.translatable("event.tinkersnewlife.yellow_king.failed"),
                    true
            );
            LOGGER.debug("[YellowKing] Player {} failed the event (dead)",
                    player.getName().getString());
            return;
        }

        boolean reward = player.level().random.nextDouble() < REWARD_CHANCE;

        if (reward) {
            applyReward(player);
            LOGGER.debug("[YellowKing] Player {} received reward",
                    player.getName().getString());
        } else {
            applyPunishment(player);
            LOGGER.debug("[YellowKing] Player {} received punishment",
                    player.getName().getString());
        }
    }

    // ==================== 奖励与惩罚 ====================
    private static void applyReward(Player player) {
        ItemStack reward = new ItemStack(ModItems.YELLOW_KING_REMNANT.get(), REWARD_COUNT);
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }

        if (player.level() instanceof ServerLevel server) {
            Vec3 pos = player.position();
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.x, pos.y + 1, pos.z,
                    40, 0.8, 0.8, 0.8, 0.2);
            server.sendParticles(ParticleTypes.FIREWORK,
                    pos.x, pos.y + 1, pos.z,
                    30, 0.5, 0.5, 0.5, 0.2);
            server.sendParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 1, pos.z,
                    20, 0.5, 0.5, 0.5, 0.1);
        }

        player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.yellow_king.reward"),
                true
        );
    }

    private static void applyPunishment(Player player) {
        player.addEffect(new MobEffectInstance(
                MobEffects.WITHER,
                PUNISH_DURATION_TICKS,
                2,
                false, true, true
        ));

        if (player.level() instanceof ServerLevel server) {
            Vec3 pos = player.position();
            server.sendParticles(ParticleTypes.SMOKE,
                    pos.x, pos.y + 1, pos.z,
                    40, 0.8, 0.8, 0.8, 0.2);
            server.sendParticles(ParticleTypes.SOUL,
                    pos.x, pos.y + 1, pos.z,
                    30, 0.5, 0.5, 0.5, 0.2);
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y + 1, pos.z,
                    20, 0.5, 0.5, 0.5, 0.1);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WITHER_AMBIENT,
                SoundSource.PLAYERS, 1.0f, 0.5f);

        player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.yellow_king.punishment"),
                true
        );
    }

    // ==================== 清理 ====================
    private static void removeUnnameable(Player player) {
        if (ModEffects.UNNAMEABLE.get() != null) {
            player.removeEffect(ModEffects.UNNAMEABLE.get());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        FALLING_FROM_HIGH.remove(uuid);
        EventData data = ACTIVE_EVENTS.remove(uuid);
        if (data != null) {
            removeUnnameable(data.player);
            LOGGER.debug("[YellowKing] Player {} logged out during event, cleaned up",
                    event.getEntity().getName().getString());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        UUID uuid = event.getEntity().getUUID();
        ACTIVE_EVENTS.remove(uuid);
        FALLING_FROM_HIGH.remove(uuid);
        removeUnnameable(event.getEntity());
    }
}