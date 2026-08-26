package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class YogSothothKeyHandler {

    private static final Map<UUID, ExpTask> ACTIVE_TASKS = new HashMap<>();

    private static class ExpTask {
        final Player player;
        int ticksRemaining;
        final int expPerTick;
        final int remainder;

        ExpTask(Player player, int totalExp, int totalTicks) {
            this.player = player;
            this.ticksRemaining = totalTicks;
            this.expPerTick = totalExp / totalTicks;
            this.remainder = totalExp % totalTicks;
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != ModItems.YOG_SOTHOTH_GATE_KEY.get()) return;

        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        if (ACTIVE_TASKS.containsKey(player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("event.tinkersnewlife.yog_sothoth.busy"),
                    true
            );
            return;
        }

        int currentLevel = player.experienceLevel;
        if (currentLevel >= 300) {
            player.displayClientMessage(
                    Component.translatable("event.tinkersnewlife.yog_sothoth.max_level"),
                    true
            );
            return;
        }

        int expNeeded = getExpToLevel(300) - getExpToLevel(currentLevel);
        if (expNeeded <= 0) return;

        if (ModEffects.UNNAMEABLE.get() != null) {
            player.addEffect(new MobEffectInstance(ModEffects.UNNAMEABLE.get(), 20 * 20, 0, false, false, true));
        }

        player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.yog_sothoth.start")
                        .withStyle(ChatFormatting.ITALIC, ChatFormatting.LIGHT_PURPLE),
                true
        );

        ACTIVE_TASKS.put(player.getUUID(), new ExpTask(player, expNeeded, 20 * 20));

        stack.shrink(1);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ACTIVE_TASKS.entrySet().removeIf(entry -> {
            ExpTask task = entry.getValue();
            Player player = task.player;

            if (!player.isAlive() || player.isRemoved()) return true;

            int expThisTick = task.expPerTick;
            if (task.ticksRemaining <= 1) expThisTick += task.remainder;

            if (expThisTick > 0 && player instanceof ServerPlayer sp) {
                sp.giveExperiencePoints(expThisTick);
            }

            task.ticksRemaining--;

            if (task.ticksRemaining <= 0) {
                player.displayClientMessage(
                        Component.translatable("event.tinkersnewlife.yog_sothoth.complete")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                        true
                );
                return true;
            }
            return false;
        });
    }

    // ⭐ 玩家登出时清理进行中的经验灌输任务，防止任务残留到下次 tick（busy 状态卡死）
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_TASKS.remove(event.getEntity().getUUID());
    }

    private static int getExpToLevel(int level) {
        if (level <= 0) return 0;
        int total = 0;
        for (int i = 0; i < level; i++) {
            if (i <= 15) total += 2 * i + 7;
            else if (i <= 30) total += 5 * i - 38;
            else total += 9 * i - 158;
        }
        return total;
    }
}