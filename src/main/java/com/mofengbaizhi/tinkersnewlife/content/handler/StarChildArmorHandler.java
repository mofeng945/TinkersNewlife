package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.StarChildArmorTrait;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarChildArmorHandler {

    private static final ResourceLocation TAG_KILLS = new ResourceLocation(TinkersNewlife.MOD_ID, "star_child_kills");
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("b1c2d3e4-f5a6-7890-1234-567890abcdef");
    private static final int MAX_KILLS_PER_PIECE = 100;

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        for (ItemStack armor : player.getArmorSlots()) {
            if (armor.isEmpty()) continue;
            // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
            ToolStack tool = ToolHelper.getToolStack(armor);
            if (tool == null) continue;
            int level = tool.getModifierLevel(StarChildArmorTrait.ID);
            if (level <= 0) continue;

            ModDataNBT persistent = tool.getPersistentData();
            int kills = persistent.getInt(TAG_KILLS);
            int maxKills = MAX_KILLS_PER_PIECE * level;
            if (kills < maxKills) {
                persistent.putInt(TAG_KILLS, kills + 1);
                tool.updateStack(armor);
                updateHealthBoost(player);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!player.level().isClientSide) {
                updateHealthBoost(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (event.player.tickCount % 400 == 0) {
            updateHealthBoost(event.player);
        }
    }

    private static int[] getTotalKillsAndLevel(Player player) {
        int totalKills = 0;
        int totalLevel = 0;
        for (ItemStack armor : player.getArmorSlots()) {
            if (armor.isEmpty()) continue;
            // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
            ToolStack tool = ToolHelper.getToolStack(armor);
            if (tool == null) continue;
            int level = tool.getModifierLevel(StarChildArmorTrait.ID);
            if (level > 0) {
                totalLevel += level;
                totalKills += tool.getPersistentData().getInt(TAG_KILLS);
            }
        }
        return new int[]{totalKills, totalLevel};
    }

    private static void updateHealthBoost(Player player) {
        int[] info = getTotalKillsAndLevel(player);
        int totalKills = info[0];
        int totalLevel = info[1];

        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) return;

        healthAttr.removeModifier(HEALTH_MODIFIER_UUID);

        if (totalLevel > 0) {
            float maxBonus = totalLevel * 50.0f;
            float bonus = Math.min(totalKills * 0.5f, maxBonus);
            if (bonus > 0) {
                AttributeModifier modifier = new AttributeModifier(
                        HEALTH_MODIFIER_UUID,
                        "star_child_armor_health",
                        bonus,
                        AttributeModifier.Operation.ADDITION
                );
                healthAttr.addTransientModifier(modifier);
            }
        }
    }
}