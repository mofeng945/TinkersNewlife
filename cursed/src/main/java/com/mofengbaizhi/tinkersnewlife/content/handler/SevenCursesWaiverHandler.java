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
            // ③ 盔甲效力：把七咒之戒加在 ARMOR / ARMOR_TOUGHNESS 上的减益修饰符摘掉 ✓
            //（⚠ 必须**定期**摘 ✗ —— 戒指重新戴上/Curios 刷新时它会被重新加上 ✓）
            stripRingArmorDebuff(player);
            // ⑥ 灵魂破裂：碎片归零 ＋ 摘掉那层最大生命减益 ✓
            //（⚠ 也要定期做 ✗ —— 它在"死亡 + 重新进服（updatePlayerSoulMap）"时会重新算 ✓）
            clearSoulFragments(player);
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

    // ---------------------------------------------------------------- ③ 盔甲效力降低（按 UUID 摘属性修饰符）

    /**
     * ③「盔甲效力降低 30%」✓ —— ⭐ **反编译实证**：它是挂在**七咒之戒的 Curios 属性**上的 ✓
     * （{@code CursedRing#getAttributeModifiers} ✓）：
     * <pre>
     * Attributes.ARMOR           ← AttributeModifier(UUID "457d0ac3-69e4-482f-b636-22e0802da6bd", …)
     * Attributes.ARMOR_TOUGHNESS ← AttributeModifier(UUID "95e70d83-3d50-4241-a835-996e1ef039bb", …)
     * </pre>
     * ⇒ ⭐ 所以**按这两个固定 UUID 直接摘掉**即可 ✓（它每次戴上戒指会重新加 ✓ 所以**定期摘**就行 ✓）
     * —— ⚠ 这是"撤掉对方的属性修饰符"而不是"改数值"✗ ⇒ 与它配置里写 30% 还是别的数**无关** ✓ 恒等于"没有这条诅咒" ✓。
     */
    private static final java.util.UUID RING_ARMOR_UUID =
            java.util.UUID.fromString("457d0ac3-69e4-482f-b636-22e0802da6bd");
    private static final java.util.UUID RING_TOUGHNESS_UUID =
            java.util.UUID.fromString("95e70d83-3d50-4241-a835-996e1ef039bb");

    /** 摘掉七咒之戒加在护甲/韧性上的减益修饰符 ✓（幂等 ✓ 摘不到也无害 ✓） */
    public static void stripRingArmorDebuff(ServerPlayer player) {
        try {
            var armor = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifier(RING_ARMOR_UUID);
            }
            var toughness = player.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
            if (toughness != null) {
                toughness.removeModifier(RING_TOUGHNESS_UUID);
            }
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }

    // ---------------------------------------------------------------- ② 中立生物激怒

    /**
     * ②「中立生物会主动攻击你」✓ —— ⭐ **反编译实证**：实现在 {@code CursedRing#curioTick} 里 ✓
     * （每 tick 按 {@code neutralAngerRange}／{@code neutralXRayRange} 扫范围内的生物 ✓
     * 然后 `neutral.setTarget(player)` ✓ / `PiglinAi.setAngerTarget` ✓；
     * 末影人另有 `endermenRandomportRange` 的随机传送 ✓）。
     *
     * <p>⇒ 对治 ✓：**在每只生物自己 tick 的开头**（`LivingEvent.LivingTickEvent` ✓ 早于它的 AI 决策 ✓）
     * 检查"它当前的目标是不是一个合格的受咒豁免者" ⇒ 是就把目标**清掉** ✓
     * （`Mob#setTarget(null)` ✓ ＋ 中立生物的 `setPersistentAngerTarget(null)` ＋ `setRemainingPersistentAngerTime(0)` ✓）。
     * ⚠ 只清"目标正好是合格玩家"的那些 ✓ ⇒ 不打搅正常战斗 ✓ 也几乎不吃性能 ✓（每只生物 O(1) ✓）。
     */
    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        try {
            if (!(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) {
                return;
            }
            if (mob.level().isClientSide) {
                return;
            }
            net.minecraft.world.entity.LivingEntity target = mob.getTarget();
            if (target instanceof Player player && qualifies(player)) {
                mob.setTarget(null);
                if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
                    neutral.setPersistentAngerTarget(null);
                    neutral.setRemainingPersistentAngerTime(0);
                }
            }
        } catch (Throwable ignored) {
            // 绝不干扰生物 AI ✓
        }
    }

    // ---------------------------------------------------------------- ⑥ 灵魂破裂（灵魂水晶被撕下来）

    /** 神秘遗物的灵魂水晶（⚠ 按**注册名**判定 ✓ 不 import 它的类 ✓） */
    private static final String SOUL_CRYSTAL = "enigmaticlegacy:soul_crystal";

    /**
     * ⑥「每次死亡都会使你的灵魂破裂」✓ —— ⭐ **反编译实证**（`SoulCrystal` ✓）：
     * <pre>
     * public ItemStack createCrystalFrom(Player player) {
     *     int lostFragments = getLostCrystals(player);
     *     setLostCrystals(player, lostFragments + 1);   // ← ⭐ 每死一次 +1 ＝"破裂一格"
     *     return new ItemStack(this);
     * }
     * // 读/写：SuperpositionHandler.setPersistentInteger(player, "enigmaticlegacy.lostsoulfragments", n)
     * //        ⇒ player.getPersistentData() → "PlayerPersisted" → 该键（IntTag）
     * // 后果  ：updatePlayerSoulMap 按碎片数给 Attributes.MAX_HEALTH 挂
     * //        AttributeModifier(UUID "66a2aa2d-7e3c-4af4-882f-bd2b2ded8e7b", "Lost Soul Health Modifier")
     * // 找回  ：SoulCrystal.retrieveSoulFromCrystal ⇒ 碎片 −1（所以水晶只是"找回那一格"的凭证）
     * </pre>
     * ⚠ 而**物品掉落根本没被它碰** ✓ —— `EnigmaticEventHandler` 里灵魂水晶那一支
     * （约 2246~2254 行 ✓）**没有** `event.getDrops().clear()` ✗（会清空的是"护身符储物水晶"那一支 ✓ 见下 ✓）。
     *
     * <p>⭐⭐ <b>对治（照用户口径 ✓「直接不生成水晶，物品全走正常死亡逻辑」✓）</b>：
     * ① **不让灵魂水晶生成** ✓（取消它 ✓ **不给任何东西** ✗）；
     * ② 把碎片数**归零** ✓（`PlayerPersisted.enigmaticlegacy.lostsoulfragments` ✓ 纯**原版 NBT** ✓ 不 import 它的类 ✓）；
     * ③ 把那层 `Lost Soul` 的 **`MAX_HEALTH` 减益按固定 UUID 摘掉** ✓
     * ⇒ **灵魂永不破裂** ✓ 而物品**本来就照常爆在地上** ✓（本来就没被搬到水晶里 ✓）。
     *
     * <p>⚠ **刻意不管**「护身符储物水晶」那一支 ✓（约 2234~2245 行 ✓：需要**佩戴谜团护身符** ✓
     * 才会把掉落物抄进水晶并 `getDrops().clear()` ✓ 还会 `drainPlayerXP` 吸走经验 ✓）
     * —— ⚠ 那是**护身符自己的功能** ✗ 不是七咒条目 ✓ ⇒ 不在本次豁免范围 ✓（要一并去掉说一声 ✓）。
     */
    @SubscribeEvent
    public static void onSoulCrystalJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        try {
            if (event.getLevel().isClientSide) {
                return;
            }
            if (!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)) {
                return;
            }
            if (item.getItem().isEmpty()) {
                return;
            }
            String id = item.getItem().getItem().builtInRegistryHolder().key().location().toString();
            if (!SOUL_CRYSTAL.equals(id)) {
                return;
            }
            // ⚠ `ItemEntity#getOwner()` 返回的是 `Entity` ✗ 不是 UUID ✓（EL 那边 setOwnerId(UUID) ✓ 服务端解析回玩家 ✓）
            net.minecraft.world.entity.Entity ownerEntity = item.getOwner();
            if (!(ownerEntity instanceof net.minecraft.server.level.ServerPlayer player)) {
                return;
            }
            if (!qualifies(player)) {
                return;
            }
            // ① 不让它生成 ✓（⚠ 不给玩家任何东西 ✗ —— 物品本就走正常掉落 ✓）
            event.setCanceled(true);
            // ② 碎片归零 ✓ ＋ ③ 摘掉那层最大生命减益 ✓
            clearSoulFragments(player);
        } catch (Throwable ignored) {
            // 出问题就让它照原样走 ✓（绝不吞东西 ✗）
        }
    }

    /** 灵魂碎片键 ✓（EL 存在 `player.getPersistentData()` 下的 `PlayerPersisted` 里 ✓ 纯原版 NBT ✓） */
    private static final String PERSISTED_ROOT = "PlayerPersisted";
    private static final String LOST_SOUL_KEY = "enigmaticlegacy.lostsoulfragments";
    /** `Lost Soul Health Modifier` 的固定 UUID ✓（`SoulCrystal:146` ✓） */
    private static final java.util.UUID LOST_SOUL_HEALTH_UUID =
            java.util.UUID.fromString("66a2aa2d-7e3c-4af4-882f-bd2b2ded8e7b");

    /** 把"灵魂破裂"整个抹掉 ✓：碎片归零 ＋ 摘掉最大生命减益 ✓（幂等 ✓） */
    public static void clearSoulFragments(ServerPlayer player) {
        try {
            var data = player.getPersistentData();
            if (data.contains(PERSISTED_ROOT)) {
                net.minecraft.nbt.CompoundTag persisted = data.getCompound(PERSISTED_ROOT);
                if (persisted.contains(LOST_SOUL_KEY)) {
                    persisted.putInt(LOST_SOUL_KEY, 0);
                }
            }
            var health = player.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (health != null) {
                health.removeModifier(LOST_SOUL_HEALTH_UUID);
            }
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }
}
