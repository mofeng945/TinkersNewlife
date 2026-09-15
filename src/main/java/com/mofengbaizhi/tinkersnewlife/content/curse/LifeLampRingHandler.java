package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LifeLampRingItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
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

    // ============================================================
    //  命灯指轮 × 七咒之戒：无效化"第一诅咒：任何来源受到的伤害加倍"
    // ============================================================

    /** 神秘遗物的七咒之戒（curios 戒指槽饰品）。只按物品 id 匹配，没装该 mod 时永不命中。 */
    private static final net.minecraft.resources.ResourceLocation EL_CURSED_RING =
            new net.minecraft.resources.ResourceLocation("enigmaticlegacy", "cursed_ring");

    /** 一次受击的原始伤害快照（带 tick，避免事件被取消后残留到下一次） */
    private record PreCurse(float amount, long tick) {
    }

    private static final java.util.Map<java.util.UUID, PreCurse> PRE_CURSE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 第 ① 步（{@link EventPriority#HIGHEST} = <b>最先</b>跑）：记下"七咒之戒放大之前"的伤害。
     * <p>只在<b>同时</b>戴着命灯指轮与七咒之戒时才记，平时零开销。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSnapshotHurt(LivingHurtEvent event) {
        snapshot(event.getEntity(), event.getAmount());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSnapshotDamage(LivingDamageEvent event) {
        snapshot(event.getEntity(), event.getAmount());
    }

    /**
     * 第 ② 步（{@link EventPriority#LOWEST} = <b>最后</b>跑）：把伤害还原成快照值 ——
     * 也就是把七咒之戒"受伤加倍"这条诅咒<b>抵消掉</b>。
     *
     * <p><b>为什么"还原"而不是"除以 2"</b>：倍率是神秘遗物的配置项（默认 200%，整合包可改），
     * 写死 2 会在改过配置的包里算错；记下放大前的值再还原，则与配置无关、恒等于"没有这条诅咒"。
     *
     * <p>Hurt 与 Damage 两关都做（哪一关被放大都兜得住）；对同一击是<b>幂等</b>的。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUndoCurseHurt(LivingHurtEvent event) {
        restore(event.getEntity(), event::setAmount);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUndoCurseDamage(LivingDamageEvent event) {
        restore(event.getEntity(), event::setAmount);
    }

    private static void snapshot(LivingEntity target, float amount) {
        if (target == null || target.level().isClientSide) return;
        if (!(target instanceof ServerPlayer player)) return;
        if (!LifeLampRingItem.isWorn(player)) return;
        if (!carriesCursedRing(player)) return;
        PRE_CURSE.put(player.getUUID(), new PreCurse(amount, player.level().getGameTime()));
    }

    private static void restore(LivingEntity target, java.util.function.Consumer<Float> setter) {
        if (!(target instanceof ServerPlayer player)) return;
        PreCurse pre = PRE_CURSE.remove(player.getUUID());
        if (pre == null) return;
        if (pre.tick() != player.level().getGameTime()) return;   // 隔了 tick 的残留 → 丢弃
        if (pre.amount() >= 0.0F) setter.accept(pre.amount());
    }

    /** 玩家身上（饰品槽或背包）是否带着七咒之戒；没装神秘遗物时永远 false */
    private static boolean carriesCursedRing(ServerPlayer player) {
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isPresent()) {
            for (var handler : curios.get().getCurios().values()) {
                var stacks = handler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    if (isCursedRing(stacks.getStackInSlot(i))) return true;
                }
            }
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isCursedRing(inv.getItem(i))) return true;
        }
        return false;
    }

    private static boolean isCursedRing(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().builtInRegistryHolder().key().location().equals(EL_CURSED_RING);
    }

    /** 第 ① 关：主伤害（此时 amount 还没过护甲/附魔/吸收） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        // ⭐ 穿透（真伤）不受慈悲约束：带穿透的近战/投射物应当能照常杀死目标
        if (com.mofengbaizhi.tinkersnewlife.util.TruePierce.isTruePierce(event.getSource())) return;
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
        // ⭐ 穿透（真伤）不受慈悲约束：带穿透的近战/投射物应当能照常杀死目标
        if (com.mofengbaizhi.tinkersnewlife.util.TruePierce.isTruePierce(event.getSource())) return;
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
