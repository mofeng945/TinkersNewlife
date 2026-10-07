package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⭐ §1118y <b>七咒全免</b>（超越维度 ≥4 时 ✓ 用户口径 ✓）—— 效果层 ✓。
 *
 * <h2>⚠ 前提：神秘遗物**没有**"关闭诅咒"的接口（已查证 ✓）</h2>
 * 翻遍它的 {@code api} 包（capabilities / events / generic / items / materials / quack ✓ 六类 ✓）：
 * <ul>
 *   <li>**没有**任何 curse toggle ✗；capability 只有 {@code IPlaytimeCounter} ✓；</li>
 *   <li>{@code items/ICursed} 只是**物品标记**（"此物带诅咒" ✓）不是玩家状态 ✗；</li>
 *   <li>「唯配者」只是 {@code helpers/ItemLoreHelper} 里的**提示文案** ✓（是个门槛 ✗ 不是豁免 ✓）；</li>
 *   <li>⭐ 它倒是有 **quack** 包（{@code IAbyssalHeartBearer}/{@code IProperShieldUser} ✓）＝ 给别模组留的
 *       **鸭子类型**扩展口 ✓ —— ⚠ 但**"豁免诅咒"那个口并不存在** ✗。</li>
 * </ul>
 * ⇒ 诅咒实现在它自己的 {@code EnigmaticEventHandler} 里 ✗ ⇒ **只能逐条拦截** ✓。
 *
 * <h2>⭐ 采用的路线：**事件优先级夹击**（你仓库已验证 ✓ 见 {@code content/curse/LifeLampRingHandler}）</h2>
 * <pre>
 * HIGHEST（最先跑）：记下"七咒放大之前"的数值
 * LOWEST （最后跑）：把数值还原成快照
 * </pre>
 * ⚠ 为什么"还原"而不是"除以 2 / 乘回 50%"✗：**倍率是神秘遗物的配置项**（默认 200% ✓ 整合包可改 ✓）
 * ⇒ 写死倍数会在改过配置的包里算错 ✗；记原值再还原则**与配置无关** ✓ 恒等于"没有这条诅咒" ✓。
 * ⚠ 而且 {@code LivingHurtEvent} 与 {@code LivingDamageEvent} **两关都要截** ✓
 * （别的词条/术式会在 {@code LivingHurtEvent} 之后继续补刀 ✓ 只截一关会漏 ✗）。
 *
 * <p>本期做到 ✓：<b>① 伤害加倍</b>（受伤侧 ✓）✓、<b>④ 对怪伤害降低</b>（出手侧 ✓）✓、
 * <b>⑤ 火焰永远灼烧</b>（每 tick 清火 ✓）✓。
 * ⚠ **没做到** ✗（如实列出 ✓）：② 中立生物主动攻击 ⚠（要改生物的敌对判定/目标 ✓ 钩子很脏 ✗）、
 * ③ 盔甲效力降低 ⚠（多半是**属性修饰符**而不是事件 ✗ 夹击手法对它无效 ✗）、
 * ⑥ 灵魂破裂 ⚠（是它自己的一套机制 ✗）、⑦ 失眠 ⚠（需要另找睡眠钩子 ✓ 待做 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SevenCursesWaiverHandler {

    private SevenCursesWaiverHandler() {
    }

    /** key ＝ 受击者实体 id ✓（同一 tick 内够用 ✓ 记原值用 ✓） */
    private static final Map<Integer, Float> HURT_SNAPSHOT = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> DAMAGE_SNAPSHOT = new ConcurrentHashMap<>();

    /** 这名玩家是否达到"七咒全免"资格 ✓（全身超越维度总等级 ≥4 ✓ 用户口径 ✓） */
    private static boolean qualifies(Player player) {
        return TranscendentDimensionHandler.totalLevel(player)
                >= com.mofengbaizhi.tinkersnewlife.content.modifier.BeyondDimensionTrait.WAIVER_TOTAL_LEVEL;
    }

    /** 这一击是否在我们的豁免口径内 ✓（自己挨打 ✓ 或 自己打怪 ✓） */
    private static boolean inScope(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        if (victim instanceof Player player && qualifies(player)) {
            return true;   // ① 伤害加倍：自己受伤 ✓
        }
        return source.getEntity() instanceof Player attacker && qualifies(attacker)
                && victim instanceof Mob;   // ④ 对怪伤害降低：自己出手打怪 ✓
    }

    // ---------------------------------------------------------------- ① / ④ 夹击

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurtSnapshot(LivingHurtEvent event) {
        try {
            if (!inScope(event.getEntity(), event.getSource())) {
                return;
            }
            HURT_SNAPSHOT.put(event.getEntity().getId(), event.getAmount());
        } catch (Throwable ignored) {
            // 绝不干扰伤害结算 ✓
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurtRestore(LivingHurtEvent event) {
        try {
            Float snapshot = HURT_SNAPSHOT.remove(event.getEntity().getId());
            if (snapshot != null) {
                event.setAmount(snapshot);
            }
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamageSnapshot(LivingDamageEvent event) {
        try {
            if (!inScope(event.getEntity(), event.getSource())) {
                return;
            }
            DAMAGE_SNAPSHOT.put(event.getEntity().getId(), event.getAmount());
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamageRestore(LivingDamageEvent event) {
        try {
            Float snapshot = DAMAGE_SNAPSHOT.remove(event.getEntity().getId());
            if (snapshot != null) {
                event.setAmount(snapshot);
            }
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }

    // ---------------------------------------------------------------- ⑤ 火焰永远灼烧

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 5 != 0) {
            return;   // 每 5 tick 一次就够 ✓（不用每 tick 白跑 ✗）
        }
        try {
            if (!qualifies(player)) {
                return;
            }
            // ⑤「一旦着火，火焰将会永远灼烧着你」⇒ 我们让它**烧不着** ✓
            if (player.getRemainingFireTicks() > 0) {
                player.clearFire();
                player.setRemainingFireTicks(0);
            }
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }

    // ---------------------------------------------------------------- ⑧ 承受击退（配置里那条"没写进提示"的诅咒）

    /** 承受击退倍率 ✓ 神秘遗物配置项 {@code CursedRingKnockbackDebuff} ✓（默认 **200%** ✓） */
    private static final Map<Integer, Float> KNOCKBACK_SNAPSHOT = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockbackSnapshot(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player) || !qualifies(player)) {
                return;
            }
            KNOCKBACK_SNAPSHOT.put(player.getId(), event.getStrength());
        } catch (Throwable ignored) {
            // 绝不干扰击退结算 ✓
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKnockbackRestore(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        try {
            if (!(event.getEntity() instanceof Player player)) {
                return;
            }
            Float snapshot = KNOCKBACK_SNAPSHOT.remove(player.getId());
            if (snapshot != null) {
                event.setStrength(snapshot);
            }
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }

    // ---------------------------------------------------------------- ⑦ 失眠

    /**
     * ⑦「你患有无法治愈的失眠症」✓ ⇒ 合格者**可以照常睡觉** ✓。
     *
     * <p>⚠ 做法说明 ✓：神秘遗物配置里有 {@code CursedRingdisableInsomnia} ✓
     * （「Set to true to prevent curse of insomnia from actually doing anything」✓）——
     * ⚠ 但那是**全局开关** ✗（一改对所有人生效 ✗，而且改玩家配置不是我该擅自做的事 ✗）
     * ⇒ 所以这里走**逐人**的路子 ✓：在 `LOWEST`（最后跑 ✓）如果这床睡被拦下了 ✓ 且该玩家合格 ✓
     * ⇒ 把结果**清空**（＝放行 ✓）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSleep(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        try {
            if (event.getResultStatus() == null) {
                return;   // 本来就没被拦 ⇒ 不动 ✓
            }
            if (!qualifies(event.getEntity())) {
                return;
            }
            // ⚠ 必须显式转型 ✗：`Event#setResult(Result)` 与 `PlayerSleepInBedEvent#setResult(BedSleepingProblem)`
            // 同名 ⇒ 直接 `setResult(null)` 会"引用不明确"✗（这就是编译器告诉我的 ✓）
            event.setResult((Player.BedSleepingProblem) null);   // ⚠ 置空 ⇒ 可睡 ✓
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }
}
