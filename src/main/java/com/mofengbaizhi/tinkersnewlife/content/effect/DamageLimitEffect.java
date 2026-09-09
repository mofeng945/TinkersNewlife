package com.mofengbaizhi.tinkersnewlife.content.effect;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伤害限幅效果（Damage Limit Effect）
 *
 * <p>口径 = <b>0.5 秒（10 tick）内实际血量减少不超过 30 点</b>：
 * 以实体真实血量为准记账（窗口起点血量 - 当前血量 = 窗口内已减少量），
 * 而不是只按伤害事件金额累加——这样即使有绕开事件记录/多源/异常路径的伤害
 * （如亚波伦灼烧类持续伤害），只要它反映在血量上，就会被后续事件按剩余额度拦截。
 * 事件在伤害结算前执行：额度耗尽 → 取消；超出 → 裁剪到剩余额度。
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

        /** 每个实体的窗口数据：窗口起点 tick 与起点血量 */
        private static final Map<UUID, DamageData> DAMAGE_DATA = new ConcurrentHashMap<>();

        /** 每 tick 维护窗口：实体带限伤效果时，窗口过期则重置（记录窗口起点血量） */
        @SubscribeEvent
        public static void onLivingTick(LivingEvent.LivingTickEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide()) return;
            MobEffectInstance effect = entity.getEffect(ModEffects.DAMAGE_LIMIT.get());
            if (effect == null) {
                DAMAGE_DATA.remove(entity.getUUID());
                return;
            }
            DamageData data = DAMAGE_DATA.computeIfAbsent(entity.getUUID(), k -> new DamageData());
            if (entity.tickCount - data.cycleStartTick >= CYCLE_DURATION) {
                data.cycleStartTick = entity.tickCount;
                data.healthAtCycleStart = entity.getHealth();
            }
        }

        @SubscribeEvent
        public static void onLivingDamage(LivingDamageEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide()) return;

            // ⭐ 穿透（ex_pierce 命中改写 / 天逆鉾）使用的 true_pierce 伤害类型可突破限伤：
            //   限伤是"目标方保护"，穿透应能贯穿我方限伤（魔虚罗/恐钢穿戴者）。
            net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> typeKey =
                    event.getSource().typeHolder().unwrapKey().orElse(null);
            if (typeKey != null && typeKey.location().equals(
                    new net.minecraft.resources.ResourceLocation(TinkersNewlife.MOD_ID, "true_pierce"))) {
                return;
            }

            // 没有限伤效果则不做任何处理（数据由每 tick 的 onLivingTick 清理）
            if (!entity.hasEffect(ModEffects.DAMAGE_LIMIT.get())) return;

            DamageData data = DAMAGE_DATA.computeIfAbsent(entity.getUUID(), k -> new DamageData());
            if (entity.tickCount - data.cycleStartTick >= CYCLE_DURATION) {
                // 事件先于 tick 管理器到达（新窗口首次伤害）：以当前血量为窗口起点
                data.cycleStartTick = entity.tickCount;
                data.healthAtCycleStart = entity.getHealth();
            }

            // 窗口内已减少的血量 = 窗口起点血量 - 当前血量（含各种来源/路径，只要反映在血量上）
            float dropped = Math.max(0.0f, data.healthAtCycleStart - entity.getHealth());
            float remaining = DAMAGE_CAP - dropped;
            float original = event.getAmount();

            if (remaining <= 0) {
                // 窗口内血量已减少 ≥30：本次伤害全额抵消
                event.setCanceled(true);
                return;
            }
            if (original > remaining) {
                // 只允许扣到窗口剩余额度
                event.setAmount(remaining);
            }
            // original <= remaining：全额放行（本次扣完后窗口总量仍 ≤30）
        }

        /**
         * 清理数据（当效果被移除时）
         */
        public static void cleanup(LivingEntity entity) {
            DAMAGE_DATA.remove(entity.getUUID());
        }

        private static class DamageData {
            int cycleStartTick = 0;
            float healthAtCycleStart = 0.0f;
        }
    }
}
