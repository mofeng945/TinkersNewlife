package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LifeLampRingItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 命灯指轮的效果：**佩戴者打出的伤害永远不会致死**，目标最终总会留下一点血。
 *
 * <h2>只截"伤害"，不碰"死亡"</h2>
 * 判定口径就一条：**这次伤害的攻击者是不是戴着命灯指轮的玩家**（或其弹射物/仆从）。
 * 是 → 把致死的一击截成"只打到剩一点血"；不是 → 完全不介入。
 *
 * <p>这样一来：
 * <ul>
 *   <li><b>附加伤害</b>（词条/术式在 {@code LivingHurtEvent} 之后补的刀）也在口径内
 *       —— 所以 {@link LivingHurtEvent} 与 {@link LivingDamageEvent} <b>两关都要截</b>；</li>
 *   <li><b>处决 / 收服之类的机制不受影响</b>：它们要么走 {@code target.kill()}
 *       （伤害源是 {@code genericKill}，<b>没有攻击者</b>），要么直接改血量，
 *       我们的判定自然为 false，压根不会插手。咒灵操术的
 *       {@code capture()}（playerAttack 1e9 → magic 1e9 → {@code kill()} 兜底）
 *       正是靠最后那下无攻击者的 {@code kill()} 完成的。</li>
 * </ul>
 *
 * <h2>留多少血：和"百分比斩杀线"对齐</h2>
 * <pre>
 *   血量上限 ≥ 40 → 留 1 点        （1 ≤ 上限 × 2.5%，本来就在咒灵操术的收服线内）
 *   血量上限 &lt; 40 → 留 上限 × 2%   （僵尸上限 20 ⇒ 斩杀线 0.5，固定留 1 点会永远收不服）
 * </pre>
 * 结果恒 &gt; 0 且 ≤ 斩杀线，"打不死"与"收得服"同时成立。
 *
 * <p>两关都用 {@link EventPriority#LOWEST}：Forge 里 LOWEST <b>最后执行</b>，
 * 保证别人在事件里"加"的伤害都已经算进去了，我们截的才是最终值。
 *
 * <p>判定"凶手"：先看 {@code source.getEntity()}，否则看 {@code getDirectEntity()} 是否是
 * <b>弹射物/召唤物</b>并取它的 owner —— 飞剑、魔法弹、被指使的仆从打出的伤害同样受命灯约束。
 * <b>自己豁免</b>：只约束"攻击别人"，不干预佩戴者自身受伤。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LifeLampRingHandler {

    private LifeLampRingHandler() {
    }

    /** 大血量生物被截住时留下的生命值 */
    private static final float KEEP_HEALTH = 1.0F;

    /** 小血量生物改按这个比例留血（必须 < 咒灵操术的 2.5% 斩杀线） */
    private static final float KEEP_RATIO_FOR_TINY = 0.02F;

    /**
     * 这一击应该给目标留多少血（见类注释）：
     * {@code min(1.0, 上限 × 2%)}。公开出来方便别处（例如收服判定）引用同一套口径。
     */
    public static float keepHealth(LivingEntity target) {
        float max = Math.max(1.0F, target.getMaxHealth());
        return Math.min(KEEP_HEALTH, max * KEEP_RATIO_FOR_TINY);
    }

    /** 第 ① 关：主伤害（此时 amount 还没过护甲/附魔/吸收） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!byRingWearer(event.getEntity(), event.getSource())) return;
        clamp(event.getEntity(), event.getAmount(), event::setAmount);
    }

    /**
     * 第 ② 关：真正要扣的血。
     * <p>附加伤害通常是在 {@link LivingHurtEvent} 之后、这里之前加进来的，
     * 所以必须在这一关再截一次 —— 否则"主伤害被截到剩一点血 + 附加伤害补刀"依然会打死人。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!byRingWearer(event.getEntity(), event.getSource())) return;
        clamp(event.getEntity(), event.getAmount(), event::setAmount);
    }

    // ============================================================
    //  工具
    // ============================================================

    /** 这次伤害的攻击者是不是"戴着命灯指轮的玩家"（自己打自己不算） */
    private static boolean byRingWearer(LivingEntity target, DamageSource source) {
        if (target == null || target.level().isClientSide) return false;
        LivingEntity wearer = attackerOf(source);
        if (wearer == null || wearer == target) return false;
        return LifeLampRingItem.isWorn(wearer);
    }

    /** 把致死伤害截到"只留 {@link #keepHealth} 那么多血"；已经到那个血量则本次不扣血 */
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
