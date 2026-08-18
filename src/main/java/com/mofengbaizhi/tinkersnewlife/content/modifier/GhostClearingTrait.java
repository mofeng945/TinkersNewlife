package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.Modifier;

import java.util.List;

/**
 * 幽灵清除强化（Ghost Clearing Trait）
 * <p>
 * 功能说明：
 * 当玩家穿戴具有此强化的盔甲时，会持续检测周围 16 格范围内的幽灵（iceandfire:ghost），
 * 一旦发现，立即将其清除（移除实体）。
 * </p>
 */
public class GhostClearingTrait extends Modifier {

    private static final int DETECTION_RADIUS = 16;
    private static final int SCAN_INTERVAL = 20;

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
    public static class ArmorHandler {
        private static int tickCounter = 0;

        @SubscribeEvent
        public static void onLivingTick(LivingEvent.LivingTickEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide) return;
            if (!(entity instanceof Player player)) return;
            if (!ArmorModifierHelper.hasModifierOnArmor(player, "ghost_clearing")) return;

            tickCounter++;
            if (tickCounter < SCAN_INTERVAL) return;
            tickCounter = 0;

            clearGhostsAround(player);
        }

        private static void clearGhostsAround(Player player) {
            AABB range = new AABB(
                    player.getX() - DETECTION_RADIUS,
                    player.getY() - DETECTION_RADIUS,
                    player.getZ() - DETECTION_RADIUS,
                    player.getX() + DETECTION_RADIUS,
                    player.getY() + DETECTION_RADIUS,
                    player.getZ() + DETECTION_RADIUS
            );

            List<Entity> entities = player.level().getEntities(player, range);
            for (Entity entity : entities) {
                if (isGhost(entity)) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }

        private static boolean isGhost(Entity entity) {
            var registryName = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
            if (registryName == null) return false;
            return "iceandfire".equals(registryName.getNamespace())
                    && "ghost".equals(registryName.getPath());
        }
    }
}