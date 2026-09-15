package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LifeLampRingItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 命灯指轮的效果：**佩戴者打出的伤害永远不会致死**，目标最终总会剩 1 点生命。
 *
 * <h2>为什么需要这么多道保险（原版事件顺序）</h2>
 * <pre>
 *   玩家攻击 → hurt()
 *     ① LivingAttackEvent        可取消（护甲/附魔都还没算）
 *     ② LivingHurtEvent          amount 还要再过护甲/附魔/吸收 —— <b>附加伤害常在这一步之后才加进来</b>
 *     ③ LivingDamageEvent        amount 就是"真正要扣的血"（护甲/附魔/吸收全算完）
 *     ④ setHealth(getHealth() - amount)   ← 真扣血
 * </pre>
 *
 * <ul>
 *   <li>{@link LivingHurtEvent}：拦截主要伤害（此时 amount 是"过护甲前"的，拦到 血量-1 就够，
 *       因为护甲只会让最终伤害更小）；</li>
 *   <li>{@link LivingDamageEvent}：<b>关键的第二道</b>——词条/术式类"附加伤害"是在 ② 之后才加进来的
 *       （例如堕落词条、雷法增伤、穿刺补刀），只拦 ② 会被它们补刀超杀，所以这里再按"真正要扣的血"截一次；</li>
 *   <li>{@link LivingDeathEvent}：兜底，任何绕过伤害的死亡（处决、{@code kill()} 等）只要凶手是佩戴者就取消并拉回 1 血；</li>
 *   <li>{@link LivingEvent.LivingTickEvent}：最后兜底 —— 有些效果直接 {@code setHealth(0)}，
 *       连 hurt() 都不走（两个伤害事件、甚至死亡事件都收不到）。这里只在"血量已经 ≤ 0"时才检查
 *       （一次浮点比较，开销可忽略），并且要求"最后攻击者是 5 秒内打过它的命灯佩戴者"，才把血拉回 1。</li>
 * </ul>
 *
 * <h2>判定"凶手"</h2>
 * 先看 {@code source.getEntity()}，否则看 {@code getDirectEntity()} 是否是**弹射物/召唤物**并取它的 owner
 * —— 飞剑、魔法弹、被指使的仆从打出的伤害同样受命灯约束。**自己豁免**：只约束"攻击别人"，不干预佩戴者自身受伤。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LifeLampRingHandler {

    private LifeLampRingHandler() {
    }

    /** 伤害被截住时至少留下的生命值（大血量生物按这个留） */
    private static final float KEEP_HEALTH = 1.0F;

    /** 咒灵操术的斩杀线：血量 ≤ 上限的 2.5% 才能收服（见 CursedSpiritTechnique#capture） */
    private static final float CAPTURE_LINE = 0.025F;

    /**
     * 小血量生物（1 点就已经超过斩杀线）改按这个比例留血。
     *
     * <p>为什么需要：僵尸/骷髅上限只有 20，斩杀线 = 0.5 点 —— 固定留 1 点的话
     * **永远进不了收服线**（玩家实测反馈）。取 2%（&lt; 2.5%）既能被收服，又保证还活着。
     */
    private static final float KEEP_RATIO_FOR_TINY = 0.02F;

    /**
     * 这一击应该给目标留多少血：
     * <pre>
     *   上限 ≥ 40 → 留 1 点（1 ≤ 上限×2.5%，本来就在收服线内）
     *   上限 &lt; 40 → 留 2% 上限（此时 1 点已经超出 2.5% 的收服线，必须按比例留才收得服）
     * </pre>
     * 两种情况下结果都 &gt; 0 且 ≤ 斩杀线，所以"打不死"与"能收服"同时成立。
     */
    public static float keepHealth(LivingEntity target) {
        float max = Math.max(1.0F, target.getMaxHealth());
        return Math.min(KEEP_HEALTH, max * KEEP_RATIO_FOR_TINY);
    }

    /** 直杀兜底的追溯窗口（tick）：最后攻击者必须是这么久以内打过它的命灯佩戴者 */
    private static final long DIRECT_KILL_WINDOW = 100L;

    /**
     * 「故意处决」豁免：实体 UUID → 豁免截止 tick。
     *
     * <p><b>为什么必须有</b>：有些机制本来就靠"杀死目标"生效 —— 最典型的是
     * <b>咒灵操术收服</b>（{@code CursedSpiritTechnique#capture}：先 playerAttack 1e9、再 magic 1e9、
     * 最后 {@code kill()} 兜底）与 <b>领域处决</b>（{@code ExecutionDomain#execute}）。
     * 命灯指轮要是把这些也拦下，收服就完不成（目标留在原地、记录却已经进了灵册）。
     *
     * <p>窗口 40 tick（2 秒）：足够覆盖"伤害 + 兜底 kill + 下一 tick 的静默致死救回"整条链；
     * 过期自动清理，不会永久豁免某个实体。
     */
    private static final java.util.Map<java.util.UUID, Long> DELIBERATE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DELIBERATE_TICKS = 40L;

    /** 模组的"主动击杀"调用它登记豁免（见 {@link #DELIBERATE}） */
    public static void markDeliberateKill(LivingEntity target) {
        if (target == null) return;
        DELIBERATE.put(target.getUUID(), target.level().getGameTime() + DELIBERATE_TICKS);
    }

    /** 该目标当前是否处于"故意处决"豁免期 */
    private static boolean isDeliberate(LivingEntity target) {
        Long until = DELIBERATE.get(target.getUUID());
        if (until == null) return false;
        if (target.level().getGameTime() > until) {
            DELIBERATE.remove(target.getUUID());
            return false;
        }
        return true;
    }

    /** 第 ② 道：主伤害（过护甲前） */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity wearer = wearerFor(event.getEntity(), event.getSource());
        if (wearer == null) return;
        clamp(event.getEntity(), event.getAmount(), event::setAmount);
    }

    /**
     * 第 ③ 道（关键）：真正要扣的血。
     * <p>附加伤害（词条/术式）通常是在 {@link LivingHurtEvent} 之后、这里之前加进来的，
     * 所以必须在这一关再截一次 —— 否则"主伤害被截到剩 1 血 + 附加伤害补刀"依然会打死人。
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity wearer = wearerFor(event.getEntity(), event.getSource());
        if (wearer == null) return;
        clamp(event.getEntity(), event.getAmount(), event::setAmount);
    }

    /** 第 ④ 道：绕过伤害的死亡（处决 / kill() / 某些 mod 特效）直接取消并拉回 1 血 */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity wearer = wearerFor(event.getEntity(), event.getSource());
        if (wearer == null) return;
        revive(event.getEntity());
        event.setCanceled(true);
    }

    /**
     * 第 ⑤ 道：静默致死（有人直接 {@code setHealth(0)}，不走 hurt()）。
     * <p>只在血量已经 ≤ 0 时才做判断，且要求最后攻击者是近期打过它的命灯佩戴者。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) return;
        if (entity.getHealth() > 0.0F) return;          // 99.9% 的情况在这里就返回
        if (isDeliberate(entity)) return;               // 故意处决（收服/领域处决）不救
        LivingEntity last = entity.getLastHurtByMob();
        if (last == null || !LifeLampRingItem.isWorn(last)) return;
        long dt = entity.level().getGameTime() - entity.getLastHurtByMobTimestamp();
        if (dt < 0 || dt > DIRECT_KILL_WINDOW) return;  // 不是"刚才被灯下之人打的"
        revive(entity);
    }

    // ============================================================
    //  工具
    // ============================================================

    /** 取"该为这次伤害负责的命灯佩戴者"；不适用（无凶手/自己打自己/没戴戒指/故意处决）返回 null */
    private static LivingEntity wearerFor(LivingEntity target, DamageSource source) {
        if (target == null || target.level().isClientSide) return null;
        if (isDeliberate(target)) return null;
        LivingEntity wearer = attackerOf(source);
        if (wearer == null || wearer == target) return null;
        return LifeLampRingItem.isWorn(wearer) ? wearer : null;
    }

    /**
     * 把致死伤害截到"只留 {@link #keepHealth} 那么多血"；已经到那个血量则本次不扣血。
     * <p>大血量生物留 1 点；上限 &lt; 40 的小生物留 2% 上限 —— 否则 1 点会高出咒灵操术的
     * 2.5% 斩杀线，导致"打不死但永远收不服"。
     */
    private static void clamp(LivingEntity target, float amount, java.util.function.Consumer<Float> setter) {
        float keep = keepHealth(target);
        float hp = target.getHealth();
        if (hp <= keep) {
            setter.accept(0.0F);
            return;
        }
        if (amount >= hp) {
            setter.accept(hp - keep);
        }
    }

    /** 把"已经死了/正在死"的目标拉回到 {@link #keepHealth} 的血量，并清掉致死状态 */
    private static void revive(LivingEntity target) {
        target.setHealth(keepHealth(target));
        target.deathTime = 0;
        target.clearFire();
        target.hurtTime = 0;
    }

    /** 伤害来源的"责任者"：直接攻击者，或弹射物/召唤物的主人 */
    private static LivingEntity attackerOf(DamageSource source) {
        if (source == null) return null;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof LivingEntity living) return living;
        if (direct instanceof LivingEntity livingDirect) return livingDirect;
        return null;
    }
}
