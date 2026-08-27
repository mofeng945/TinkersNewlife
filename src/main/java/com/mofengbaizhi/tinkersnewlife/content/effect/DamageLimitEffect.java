package com.mofengbaizhi.tinkersnewlife.content.effect;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伤害限幅效果（Damage Limit Effect）
 * 每 0.5 秒内受到的所有伤害总和不超过 30 点
 */
public class DamageLimitEffect extends MobEffect {

    public static final float DAMAGE_CAP = 30.0f;
    public static final int CYCLE_DURATION = 10; // 0.5 秒 = 10 tick
    public static final int DEFAULT_DURATION = 200;

    public DamageLimitEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    // ==================== 事件处理器 ====================
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class DamageLimitHandler {

        // 存储每个实体的周期累计伤害和周期开始时间
        private static final Map<UUID, DamageData> DAMAGE_DATA = new ConcurrentHashMap<>();

        @SubscribeEvent
        public static void onLivingDamage(LivingDamageEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide()) return;

            // ⭐ 命令击杀(/kill)、虚空等"无法避免"的伤害不受限伤影响
            DamageSource source = event.getSource();
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return;
            }

            // 检查是否有伤害限幅效果
            MobEffectInstance effect = entity.getEffect(ModEffects.DAMAGE_LIMIT.get());
            if (effect == null) {
                // 没有效果时清理数据
                DAMAGE_DATA.remove(entity.getUUID());
                return;
            }

            UUID uuid = entity.getUUID();
            DamageData data = DAMAGE_DATA.computeIfAbsent(uuid, k -> new DamageData());

            // 检查是否进入新周期
            int currentTick = entity.tickCount;
            if (currentTick - data.cycleStartTick >= CYCLE_DURATION) {
                // 重置周期
                data.cycleDamage = 0f;
                data.cycleStartTick = currentTick;
            }

            float originalDamage = event.getAmount();
            float remainingCap = DAMAGE_CAP - data.cycleDamage;

            if (remainingCap <= 0) {
                // 周期内已达上限，完全抵消伤害
                event.setCanceled(true);
                return;
            }

            if (originalDamage > remainingCap) {
                // 只造成剩余上限的伤害
                event.setAmount(remainingCap);
                data.cycleDamage = DAMAGE_CAP;
            } else {
                // 正常造成伤害，累计
                data.cycleDamage += originalDamage;
            }
        }

        /**
         * 清理数据（当效果被移除时）
         */
        public static void cleanup(LivingEntity entity) {
            DAMAGE_DATA.remove(entity.getUUID());
        }

        private static class DamageData {
            float cycleDamage = 0f;
            int cycleStartTick = 0;
        }
    }
}