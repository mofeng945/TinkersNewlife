package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NyarlathotepDesireHandler {

    // 存储攻击记录：村民UUID -> (攻击者玩家UUID, 攻击时间tick)
    private static final Map<UUID, AttackRecord> ATTACK_RECORDS = new HashMap<>();
    private static final long EXPIRE_TIME_TICKS = 5 * 20; // 5 秒

    // 待处理事件
    private static final Map<UUID, EventData> PENDING_EVENTS = new HashMap<>();

    private static class AttackRecord {
        final UUID playerId;
        final long attackTick;
        AttackRecord(UUID playerId, long attackTick) {
            this.playerId = playerId;
            this.attackTick = attackTick;
        }
    }

    private static class EventData {
        final UUID playerId;
        final long startTick;
        EventData(UUID playerId, long startTick) {
            this.playerId = playerId;
            this.startTick = startTick;
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        ATTACK_RECORDS.put(event.getEntity().getUUID(),
                new AttackRecord(player.getUUID(), event.getEntity().level().getGameTime()));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        UUID villagerId = villager.getUUID();
        AttackRecord record = ATTACK_RECORDS.remove(villagerId);
        if (record == null) return;

        long currentTick = villager.level().getGameTime();
        if (currentTick - record.attackTick > EXPIRE_TIME_TICKS) return;

        if (event.getSource().getEntity() instanceof Player) return;

        UUID playerId = record.playerId;
        Player player = villager.level().getPlayerByUUID(playerId);
        if (player == null) return;

        if (ModEffects.UNNAMEABLE.get() != null) {
            player.addEffect(new MobEffectInstance(ModEffects.UNNAMEABLE.get(), 10 * 20, 0, false, false, true));
        }

        player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.nyarlathotep_desire.start"),
                true
        );

        PENDING_EVENTS.put(playerId, new EventData(playerId, currentTick));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // ✅ 使用世界时间，与存储时保持一致
        long currentTick = event.getServer().overworld().getGameTime();
        Iterator<Map.Entry<UUID, EventData>> iterator = PENDING_EVENTS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, EventData> entry = iterator.next();
            EventData data = entry.getValue();

            if (currentTick - data.startTick >= 10 * 20) {
                iterator.remove();

                Player player = event.getServer().getPlayerList().getPlayer(data.playerId);
                if (player == null) continue;

                // 二选一判定
                if (player.level().random.nextDouble() < 0.5) {
                    ItemStack reward = new ItemStack(ModItems.NYARLATHOTEP_DESIRE.get(), 1);
                    if (!player.getInventory().add(reward)) {
                        player.drop(reward, false);
                    }
                    player.displayClientMessage(
                            Component.translatable("event.tinkersnewlife.nyarlathotep_desire.reward"),
                            true
                    );
                } else {
                    player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60 * 20, 2, false, true, true));
                    player.displayClientMessage(
                            Component.translatable("event.tinkersnewlife.nyarlathotep_desire.punishment"),
                            true
                    );
                }
            }
        }
    }
}