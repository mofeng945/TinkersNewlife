package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GorgonImmunityHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GorgonImmunityHandler.class);
    private static final String MODIFIER_ID = "gorgon_immunity";
    private static final String GORGON_DAMAGE_TYPE = "gorgon";
    private static final ResourceLocation STONE_STATUE_ID =
            new ResourceLocation("iceandfire", "stone_statue");
    private static final double SCAN_RADIUS = 4.0;
    private static final boolean DEBUG = false;

    /**
     * 在 LivingHurtEvent 中免疫伤害，这样反伤（LivingAttackEvent）可以先触发
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        if (target.level().isClientSide) return;
        if (!GORGON_DAMAGE_TYPE.equals(source.getMsgId())) return;
        // 墨默（武器商人）天生免疫蛇发女妖石化凝视
        if (target instanceof com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant) {
            event.setCanceled(true);
            removeNearbyStoneStatues(target);
            return;
        }
        if (!ArmorModifierHelper.hasModifierOnArmor(target, MODIFIER_ID)) return;

        event.setCanceled(true);
        removeNearbyStoneStatues(target);

        if (DEBUG) {
            LOGGER.debug("🛡️ {} 免疫了蛇发女妖的石化凝视（伤害已取消）！",
                    target.getName().getString());
        }
    }

    /**
     * 在 LivingAttackEvent 中仅处理石像移除，不取消事件
     * 这样反伤逻辑可以正常触发
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        if (target.level().isClientSide) return;
        if (!GORGON_DAMAGE_TYPE.equals(source.getMsgId())) return;
        if (target instanceof com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant) {
            removeNearbyStoneStatues(target);
            return;
        }
        if (!ArmorModifierHelper.hasModifierOnArmor(target, MODIFIER_ID)) return;

        // 不取消事件，让反伤逻辑可以执行；仅移除已生成的石像
        removeNearbyStoneStatues(target);
    }

    private static void removeNearbyStoneStatues(LivingEntity entity) {
        List<Entity> statues = entity.level().getEntitiesOfClass(
                Entity.class,
                entity.getBoundingBox().inflate(SCAN_RADIUS),
                e -> {
                    var registryName = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
                    return registryName != null && STONE_STATUE_ID.equals(registryName);
                }
        );

        for (Entity statue : statues) {
            statue.remove(Entity.RemovalReason.DISCARDED);
            if (DEBUG) {
                LOGGER.debug("🗿 已移除石像 at {}", statue.blockPosition());
            }
        }
    }
}