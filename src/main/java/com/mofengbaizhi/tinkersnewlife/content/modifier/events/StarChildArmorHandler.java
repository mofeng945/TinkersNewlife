package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.StarChildArmorTrait;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 星之子护甲（ashen_ink 材料护甲部件自带）：穿戴者杀敌累积 → 每件护甲累计击杀数，
 * 按总击杀数给<b>穿戴者</b>（玩家或诡厄仆从等任意生物）最大生命加成。
 * 击杀计数存在护甲工具自身的 ModDataNBT 上，随装备走。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarChildArmorHandler {

    private static final ResourceLocation TAG_KILLS = new ResourceLocation(TinkersNewlife.MOD_ID, "star_child_kills");
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("b1c2d3e4-f5a6-7890-1234-567890abcdef");
    private static final int MAX_KILLS_PER_PIECE = 100;

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) return;
        if (killer.level().isClientSide) return;

        // 穿戴者（玩家或仆从等任意生物）穿着星之子护甲杀敌 → 护甲累积击杀
        for (ItemStack armor : killer.getArmorSlots()) {
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
                updateHealthBoost(killer);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide) {
            updateHealthBoost(entity);
        }
    }

    /** 定期兜底刷新（每 20 秒）：确保加成与穿戴/击杀同步 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity.tickCount % 400 != 0) return;
        updateHealthBoost(entity);
    }

    private static int[] getTotalKillsAndLevel(LivingEntity wearer) {
        int totalKills = 0;
        int totalLevel = 0;
        for (ItemStack armor : wearer.getArmorSlots()) {
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

    private static void updateHealthBoost(LivingEntity wearer) {
        int[] info = getTotalKillsAndLevel(wearer);
        int totalKills = info[0];
        int totalLevel = info[1];

        AttributeInstance healthAttr = wearer.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) return;

        healthAttr.removeModifier(HEALTH_MODIFIER_UUID);

        if (totalLevel > 0) {
            // ⭐ 引用 Trait 的公开常量，避免双份硬编码漂移
            float maxBonus = totalLevel * StarChildArmorTrait.MAX_HP_PER_LEVEL;
            float bonus = Math.min(totalKills * StarChildArmorTrait.HP_PER_KILL, maxBonus);
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
