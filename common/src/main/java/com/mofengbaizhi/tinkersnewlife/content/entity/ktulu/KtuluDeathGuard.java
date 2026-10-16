package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL1139 用户口径："加保护，还有血就拦住死亡"。
 *
 * 无尽贪婪的寰宇支配剑是**三连**：hurt(Float.MAX_VALUE) ⇒ setHealth(0) ⇒ 紧接着
 * die(source)。血量 mixin 已经把 setHealth 顶在下限上，所以血量不会归零 —— 但
 * die() 完全不看血量，照样判死。这里取消那次死亡，并且因为"取消后的死亡"仍会留下
 * 半死状态，所以同时补回血量、清零死亡计时，让 Boss 继续打。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class KtuluDeathGuard {

    /** TNL1147 篡改探针的限流时间戳 */
    private static long TNL_STAMP = 0L;

    private KtuluDeathGuard() {
    }

    /**
     * TNL1165 用户："施法动作时不是本来就不能攻击吗"。对 —— 这是施法自带的属性。
     * 我们**不清 AI**（清 AI 会粘住），而是把墨默在施法中打出的伤害**一律取消**，
     * 这样它的 goal 一个不动、事后也不会留下任何后遗症。
     */
    @SubscribeEvent
    public static void onMomoAttack(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getSource().getEntity() instanceof KtuluMomo attacker)
                    || attacker.level().isClientSide) {
                return;
            }
            if (attacker.tnl$isBusy()) {
                event.setCanceled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** TNL1143 探针：打印一次命中前后墨默的血量变化 */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo momo) || momo.level().isClientSide) {
                return;
            }
            String src;
            try {
                src = event.getSource().typeHolder().unwrapKey()
                        .map(k -> k.location().toString()).orElse("?");
            } catch (Throwable t) {
                src = String.valueOf(event.getSource().getMsgId());
            }
            TinkersNewlife.LOGGER.warn(
                    "TNL1143 hit on momo src={} amount={} before={} auth={} max={}",
                    src, event.getAmount(), momo.getHealth(), com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a.qF(momo), momo.getMaxHealth());

            // TNL1145 日志证实了真正的问题：字段血量只有 28，而镜像权威值是 1365，
            // 于是一发 43 点普通伤害就把它打死了，血条看着也是空的。
            // 现在把字段同步回镜像，并让单次伤害不超过"一个 tick 的预算"。
            float tnl$auth = com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a.qF(momo);
            if (tnl$auth > 0.0F) {
                if (momo.getHealth() < tnl$auth - 0.5F) {
                    momo.setHealth(tnl$auth);
                    TinkersNewlife.LOGGER.warn("TNL1145 momo health resynced to {}",
                            String.format(java.util.Locale.ROOT, "%.1f", tnl$auth));
                }
                float tnl$budget = Math.max(1.0F, momo.getMaxHealth()
                        * com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a.C0);
                if (event.getAmount() > tnl$budget) {
                    event.setAmount(tnl$budget);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // TNL1180 the once a second boss bar watchdog is gone: KtuluMomo.tick() maintains the bar every
    // tick now (progress from the authoritative health, forced remove+add once a second), so this
    // whole handler had become a second source of truth for the same thing.

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo momo)) {
                return;
            }
            TinkersNewlife.LOGGER.warn("TNL1143 death of momo src={} health={} auth={}",
                    event.getSource().getMsgId(), momo.getHealth(), com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a.qF(momo));
            if (momo.level().isClientSide) {
                return;
            }
            // TNL1142 用户："一剑就把血条事件清了，第二剑满血直接秒"。
            // 取消**所有**死亡会破坏正常流程（血条被摘、实体留在半死状态）。
            // 现在只拦"作弊秒杀"：寰宇支配剑那条 infinity 伤害源，它的动作是
            // hurt(最大浮点) ＋ setHealth(0) ＋ die() 三连。
            String id;
            try {
                id = event.getSource().typeHolder().unwrapKey()
                        .map(k -> k.location().toString()).orElse("");
            } catch (Throwable t) {
                id = String.valueOf(event.getSource().getMsgId());
            }
            boolean cheatKill = id.contains("infinity");
            if (cheatKill && momo.getHealth() > 0.0F) {
                event.setCanceled(true);
                momo.deathTime = 0;
                momo.hurtTime = 0;
                momo.setHealth(Math.max(1.0F, momo.getHealth()));
                momo.tnl$unDead();       // TNL1146 undo the latch so the protection keeps working
                TinkersNewlife.LOGGER.warn(
                        "TNL1139 death blocked for the momo, it still had {} health",
                        String.format(java.util.Locale.ROOT, "%.1f", momo.getHealth()));
            }
        } catch (Throwable ignored) {
        }
    }
}
