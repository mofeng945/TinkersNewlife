package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RlyehCallHandler {

    private static final double TRIGGER_CHANCE = 0.5;
    private static final int UNNAMEABLE_DURATION = 5 * 20;
    private static final int MIN_WATER_DEPTH = 30;

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) return;

        // 1. 夜晚（使用游戏时间判定）
        long dayTime = level.getDayTime() % 24000;
        if (dayTime < 13000 || dayTime > 23000) return;

        // 2. 满月
        if (level.getMoonPhase() != 0) return;

        // 3. 钓上鱼
        boolean hasFish = false;
        for (ItemStack drop : event.getDrops()) {
            if (isFishItem(drop)) {
                hasFish = true;
                break;
            }
        }
        if (!hasFish) return;

        // 4. 检测浮标下方水域深度
        FishingHook hook = event.getHookEntity();
        boolean isDeep = false;

        if (hook != null) {
            int depth = getWaterDepth(level, hook.blockPosition());
            if (depth > MIN_WATER_DEPTH) {
                isDeep = true;
            }
        }

        // 备选：浮标不可用时检测玩家位置
        if (!isDeep) {
            int depth = getWaterDepth(level, player.blockPosition());
            if (depth > MIN_WATER_DEPTH) {
                isDeep = true;
            }
        }

        if (!isDeep) {
            player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.rlyeh_call.shallow_whisper"),
                true
            );
            return;
        } else {
            player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.rlyeh_call.deep_whisper"),
                true
            );
        }

        // 5. 二选一
        if (level.random.nextDouble() < TRIGGER_CHANCE) {
            ItemStack reward = new ItemStack(ModItems.RLYEH_CALL.get(), 1);
            if (!player.getInventory().add(reward)) {
                player.drop(reward, false);
            }

            if (ModEffects.UNNAMEABLE.get() != null) {
                player.addEffect(new MobEffectInstance(
                    ModEffects.UNNAMEABLE.get(),
                    UNNAMEABLE_DURATION,
                    0,
                    false, false, true
                ));
            }

            playWhisperSound(player);
            player.displayClientMessage(
                Component.translatable("event.tinkersnewlife.rlyeh_call.success"),
                true
            );
        }
    }

    private static int getWaterDepth(Level level, BlockPos pos) {
        int waterDepth = 0;
        BlockPos checkPos = pos.below();
        while (checkPos.getY() > level.getMinBuildHeight()) {
            if (level.getBlockState(checkPos).getFluidState().isSource()) {
                waterDepth++;
                checkPos = checkPos.below();
            } else {
                break;
            }
        }
        return waterDepth;
    }

    private static boolean isFishItem(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.COD ||
               item == Items.SALMON ||
               item == Items.TROPICAL_FISH ||
               item == Items.PUFFERFISH;
    }

    private static void playWhisperSound(Player player) {
        SoundEvent caveSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("ambient.cave"));
        SoundEvent enderSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.enderman.ambient"));

        if (caveSound != null) {
            player.playSound(caveSound, 0.3f, 0.6f);
        }
        if (enderSound != null) {
            player.playSound(enderSound, 0.2f, 0.7f);
        }
    }
}