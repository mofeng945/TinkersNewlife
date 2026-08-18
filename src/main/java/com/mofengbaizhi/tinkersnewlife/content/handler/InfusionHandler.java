package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.DragonBloodTankTrait;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InfusionHandler {

    private static final ModifierId DRAGON_BLOOD_INFUSION = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_blood_infusion"));
    private static final ModifierId DRAGON_BLOOD_TANK = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_blood_tank"));

    private static final int BLOOD_COST = 50;
    private static final int FIRE_DURATION_BASE = 5;
    private static final int DURATION_PER_LEVEL_FIRE = 2;
    private static final int FROST_DURATION_BASE = 100;
    private static final int DURATION_PER_LEVEL_FROST = 50;
    private static final int DISARM_DURATION_BASE = 60;
    private static final int DURATION_PER_LEVEL_DISARM = 20;

    // ==================== 近战 ====================
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;
        if (tool.getStats().getContainedStats().isEmpty()) return;

        int level = tool.getModifierLevel(DRAGON_BLOOD_INFUSION);
        if (level > 0) applyInfusionEffect(tool, target, level);
    }

    // ==================== 弹射物 ====================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getProjectile().getOwner() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;
        if (tool.getStats().getContainedStats().isEmpty()) return;

        int level = tool.getModifierLevel(DRAGON_BLOOD_INFUSION);
        if (level > 0) applyInfusionEffect(tool, target, level);
    }

    private static void applyInfusionEffect(ToolStack tool, LivingEntity target, int level) {
        int tankLevel = tool.getModifierLevel(DRAGON_BLOOD_TANK);
        if (tankLevel <= 0) return;

        int capacity = tankLevel * DragonBloodTankTrait.CAPACITY_PER_LEVEL;
        DragonBloodTankTrait.DragonBloodTankData data = DragonBloodTankTrait.getTankData(tool, capacity);
        if (data == null) return;

        DragonBloodTankTrait.DragonBloodType consumedType = null;
        for (DragonBloodTankTrait.DragonBloodType type : DragonBloodTankTrait.DragonBloodType.values()) {
            if (data.getAmount(type) >= BLOOD_COST) {
                consumedType = type;
                data.setAmount(type, data.getAmount(type) - BLOOD_COST);
                DragonBloodTankTrait.setTankData(tool, data);
                break;
            }
        }

        if (consumedType == null) return;

        // ========== 新增：消耗成功 → 播放粒子特效 ==========
        if (target.level() instanceof ServerLevel server) {
            Vec3 pos = target.position().add(0, target.getBbHeight() / 2, 0);
            switch (consumedType) {
                case FIRE -> {
                    server.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 20, 0.5, 0.5, 0.5, 0.1);
                    server.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 10, 0.5, 0.5, 0.5, 0.1);
                }
                case ICE -> {
                    server.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 20, 0.5, 0.5, 0.5, 0.1);
                    server.sendParticles(ParticleTypes.ITEM_SNOWBALL, pos.x, pos.y, pos.z, 10, 0.3, 0.3, 0.3, 0.1);
                }
                case LIGHTNING -> {
                    server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 20, 0.5, 0.5, 0.5, 0.1);
                    server.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 10, 0.5, 0.5, 0.5, 0.1);
                }
                default -> {}
            }
        }

        // ========== 应用效果（与原逻辑一致） ==========
        switch (consumedType) {
            case FIRE -> {
                int duration = FIRE_DURATION_BASE + (level - 1) * DURATION_PER_LEVEL_FIRE;
                target.setSecondsOnFire(duration);
            }
            case ICE -> {
                int duration = FROST_DURATION_BASE + (level - 1) * DURATION_PER_LEVEL_FROST;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 3));
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, 1));
            }
            case LIGHTNING -> {
                int duration = DISARM_DURATION_BASE + (level - 1) * DURATION_PER_LEVEL_DISARM;
                if (ModEffects.DISARM.get() != null) {
                    target.addEffect(new MobEffectInstance(ModEffects.DISARM.get(), duration, 0, false, true));
                }
            }
            default -> {}
        }
    }
}