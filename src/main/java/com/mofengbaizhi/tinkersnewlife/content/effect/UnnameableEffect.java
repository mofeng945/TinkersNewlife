package com.mofengbaizhi.tinkersnewlife.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 不可名状效果 - 集成所有负面效果
 * 状态栏只显示本效果图标，不显示子效果
 */
public class UnnameableEffect extends MobEffect {

    // ========== 饥饿消耗参数 ==========
    private static final int HUNGER_INTERVAL_TICKS = 50;
    private static final float HUNGER_DRAIN = 1.0f;

    // ========== 缓慢/虚弱参数 ==========
    private static final UUID SLOW_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID WEAKNESS_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f23456789012");
    private static final double SLOW_AMOUNT = -0.3;      // 减少 30% 速度
    private static final double WEAKNESS_AMOUNT = -2.0;  // 减少 2 点攻击力

    private final ConcurrentHashMap<LivingEntity, Integer> lastHungerTick = new ConcurrentHashMap<>();

    public UnnameableEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;

        // ========== 饥饿消耗 ==========
        if (entity instanceof Player player) {
            int currentTick = entity.tickCount;
            Integer lastTick = lastHungerTick.get(entity);
            if (lastTick == null) {
                lastHungerTick.put(entity, currentTick);
                return;
            }

            if (currentTick - lastTick >= HUNGER_INTERVAL_TICKS) {
                FoodData foodData = player.getFoodData();
                float currentFood = foodData.getFoodLevel();
                float newFood = Math.max(0, currentFood - HUNGER_DRAIN);
                foodData.setFoodLevel((int) newFood);

                if (newFood <= 0) {
                    player.hurt(player.damageSources().starve(), 1.0f);
                }

                lastHungerTick.put(entity, currentTick);
            }
        }
    }

    @Override
    public void addAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.addAttributeModifiers(entity, attributeMap, amplifier);

        // ========== 缓慢（减少移动速度） ==========
        AttributeInstance speedInstance = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (speedInstance != null && speedInstance.getModifier(SLOW_MODIFIER_UUID) == null) {
            speedInstance.addTransientModifier(
                    new AttributeModifier(
                            SLOW_MODIFIER_UUID,
                            "unnameable_slow",
                            SLOW_AMOUNT,
                            AttributeModifier.Operation.ADDITION
                    )
            );
        }

        // ========== 虚弱（减少攻击力） ==========
        AttributeInstance damageInstance = attributeMap.getInstance(Attributes.ATTACK_DAMAGE);
        if (damageInstance != null && damageInstance.getModifier(WEAKNESS_MODIFIER_UUID) == null) {
            damageInstance.addTransientModifier(
                    new AttributeModifier(
                            WEAKNESS_MODIFIER_UUID,
                            "unnameable_weakness",
                            WEAKNESS_AMOUNT,
                            AttributeModifier.Operation.ADDITION
                    )
            );
        }

        lastHungerTick.put(entity, entity.tickCount);
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributeMap, int amplifier) {
        super.removeAttributeModifiers(entity, attributeMap, amplifier);

        // ========== 移除缓慢修饰 ==========
        AttributeInstance speedInstance = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (speedInstance != null) {
            speedInstance.removeModifier(SLOW_MODIFIER_UUID);
        }

        // ========== 移除虚弱修饰 ==========
        AttributeInstance damageInstance = attributeMap.getInstance(Attributes.ATTACK_DAMAGE);
        if (damageInstance != null) {
            damageInstance.removeModifier(WEAKNESS_MODIFIER_UUID);
        }

        lastHungerTick.remove(entity);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // 每 tick 触发
    }
}