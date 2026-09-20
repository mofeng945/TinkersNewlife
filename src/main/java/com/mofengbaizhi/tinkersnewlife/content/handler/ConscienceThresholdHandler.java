package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「心」三期②：<b>善恶阈值效果</b>（用户口径 ✓ 恶 5 档 + 善 5 档 = 10 条 ✓）。
 *
 * <h2>恶意（善恶值 ≤ −20% 起 ✓）</h2>
 * <table border="1">
 *   <tr><th>阈值</th><th>效果</th><th>落点</th></tr>
 *   <tr><td>−20%</td><td><b>命灯指轮的"七咒解除"不再适用</b>（伤害照旧被七咒之戒加倍 ✗）</td>
 *       <td>{@code LifeLampRingHandler.snapshot()} 里加的一道闸门 ✓（本类给常量 ✓）</td></tr>
 *   <tr><td>−30%</td><td><b>幸运值 −50%</b></td><td>{@link Attributes#LUCK} 的 {@code MULTIPLY_TOTAL} 修饰符 −0.5 ✓</td></tr>
 *   <tr><td>−40%</td><td><b>时运等级归 0</b></td><td>挖掘前临时把工具上的<b>时运附魔摘掉</b> ✓ 下一 tick 还原 ✓（见下 ✓）</td></tr>
 *   <tr><td>−45%</td><td><b>每 200s，40% 概率附加「不可名状」5s</b> ✓ 且 <b>村民涨价 90%</b>（实际价 ×1.9）</td>
 *       <td>{@link ModEffects#UNNAMEABLE}（我们自己的效果 ✓ 5s = 100 tick ✓）；价格同下方"1 折"那套 ✓ 只是系数反过来 ✓</td></tr>
 *   <tr><td>−50%</td><td><b>所有生物与你为敌</b>（怪物/中立生物主动打你 ✓ <b>被动生物主动逃跑</b> ✓）</td>
 *       <td>每秒：怪物&中立 {@code setTarget(player)} ✓ 动物 {@code setLastHurtByMob(player)} ✓（触发原版 {@code PanicGoal} ✓）</td></tr>
 * </table>
 *
 * <h2>善意（善恶值 ≥ +20% 起 ✓）</h2>
 * <table border="1">
 *   <tr><td>+20%</td><td><b>持续生命恢复 II</b></td><td>{@link MobEffects#REGENERATION} 等级 II ✓ 每秒续 3s ✓</td></tr>
 *   <tr><td>+30%</td><td><b>幸运值 +50%</b></td><td>同上的 LUCK 修饰符 +0.5 ✓</td></tr>
 *   <tr><td>+40%</td><td><b>时运等级 +1</b></td><td>同上，临时给工具加一级时运 ✓</td></tr>
 *   <tr><td>+45%</td><td><b>村民交易打 1 折</b></td><td>打开村民界面时把每条报价的实际价格压到 10% ✓（{@code specialPriceDiff} ✓ 每秒自纠 ✓）</td></tr>
 *   <tr><td>+50%</td><td><b>所有生物不再主动攻击你</b> + <b>免死一次</b>（死亡即回满血原地复活 ✓ 冷却 200s ✓）</td>
 *       <td>{@link LivingChangeTargetEvent} 拦目标 ✓ + 每秒清已有目标 ✓；{@link LivingDeathEvent} 取消死亡 ✓</td></tr>
 * </table>
 *
 * <h2>「时运等级」怎么实现（✗ 没有用 mixin ✓）</h2>
 * 原版时运是在<b>掉落表计算时</b>读工具附魔的 ✓ 所以只要在 {@link BlockEvent.BreakEvent}（<b>掉落计算之前</b> ✓）
 * 把主手工具的<b>附魔表临时改掉</b>（恶 ⇒ 摘掉时运 ✓ 善 ⇒ +1 级 ✓），
 * 掉落算完后的<b>下一个 tick 再还原</b> ✓ ⇒ 不需要 mixin ✗ 也精确等于原版公式 ✓。
 * 同一 tick 挖多块（连锁/范围挖掘 ✓）只改一次 ✓ 不会把"已改过的表"当成原表 ✓。
 *
 * <p>⚠ 口径照实写：这条只管<b>原版时运附魔</b> ✓；匠魂的"时运"强化、其它 mod 的等价物<b>不跟</b> ✗。
 * 幸运那条同理：{@code ATTRIBUTES.LUCK} 本身如果是 0（没有任何幸运来源）✓ 乘 0.5 仍是 0 ✓ 看不出变化 ✓ 属正常 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConscienceThresholdHandler {

    private ConscienceThresholdHandler() {}

    // ============================================================
    //  阈值（单位 = 百分点 ✓ 与 ConscienceHandler 的 ±50 同尺 ✓）
    // ============================================================

    /** 恶意：命灯指轮的七咒解除失效 ✓（由 LifeLampRingHandler 读 ✓） */
    public static final int LAMP_AT = -20;
    /** 恶意 / 善意：幸运值 ∓50% ✓ */
    public static final int LUCK_AT = 30;
    /** 恶意 / 善意：时运等级 0 / +1 ✓ */
    public static final int FORTUNE_AT = 40;
    /** 恶意：不可名状抽奖 ✓（每 200s 40% ✓） */
    public static final int UNSPEAKABLE_AT = -45;
    /** 善意：村民 1 折 ✓ */
    public static final int DISCOUNT_AT = 45;
    /** 恶意：村民涨价 90%（实际价 ×1.9 ✓ 用户后补口径 ✓） */
    public static final int MARKUP_AT = -45;
    /** 善意 1 折 / 恶意 ×1.9 的倍率 ✓ */
    public static final double PRICE_DOWN = 0.1D;
    public static final double PRICE_UP = 1.9D;
    /** 善意：生命恢复 II ✓ */
    public static final int REGEN_AT = 20;
    /** 满值（恶 −50 / 善 +50 ✓） */
    public static final int FULL = 50;

    private static final int UNSPEAKABLE_PERIOD_TICKS = 200 * 20;   // 200s
    private static final float UNSPEAKABLE_CHANCE = 0.4F;
    private static final int UNSPEAKABLE_DURATION = 5 * 20;         // 5s
    private static final int REVIVE_COOLDOWN_TICKS = 200 * 20;      // 200s
    private static final int REGEN_REFRESH = 60;                    // 每次给 3s ✓
    private static final double MOB_RANGE = 32.0D;

    private static final String KEY_UNSPEAKABLE_NEXT = "tn_unspeakable_next";
    private static final String KEY_REVIVE_NEXT = "tn_revive_next";

    private static final UUID LUCK_MOD_ID = UUID.nameUUIDFromBytes(
            "tinkersnewlife:conscience_luck".getBytes(StandardCharsets.UTF_8));
    private static final String LUCK_MOD_NAME = "tn_conscience_luck";

    // ============================================================
    //  阈值表（**游戏逻辑与 tooltip 共用这一份** ✓ 免得两边写岔 ✗）
    // ============================================================

    /**
     * 一个档位 = 阈值 + 描述 lang 键 ✓。
     * <b>用户口径：同一档多条效果也写成一行</b>（用「 · 」连 ✓ 文案里就已经连好了 ✓）。
     */
    public record Tier(int threshold, String key) {}

    /** 善侧 5 档（+20 / +30 / +40 / +45 / +50 ✓ 由低到高 ✓） */
    public static final List<Tier> TIERS_GOOD = List.of(
            new Tier(REGEN_AT, "item.tinkersnewlife.conscience.good.20"),
            new Tier(LUCK_AT, "item.tinkersnewlife.conscience.good.30"),
            new Tier(FORTUNE_AT, "item.tinkersnewlife.conscience.good.40"),
            new Tier(DISCOUNT_AT, "item.tinkersnewlife.conscience.good.45"),
            new Tier(FULL, "item.tinkersnewlife.conscience.good.50"));

    /** 恶侧 5 档（−20 / −30 / −40 / −45 / −50 ✓ 由轻到重 ✓） */
    public static final List<Tier> TIERS_EVIL = List.of(
            new Tier(LAMP_AT, "item.tinkersnewlife.conscience.evil.20"),         // −20（命灯失效 ✓）
            new Tier(-LUCK_AT, "item.tinkersnewlife.conscience.evil.30"),        // −30（幸运 −50% ✓）
            new Tier(-FORTUNE_AT, "item.tinkersnewlife.conscience.evil.40"),     // −40（时运归零 ✓）
            new Tier(MARKUP_AT, "item.tinkersnewlife.conscience.evil.45"),       // −45（不可名状 · 涨价 ✓）
            new Tier(-FULL, "item.tinkersnewlife.conscience.evil.50"));          // −50（万物为敌 ✓）

    /** 该档是否已达成（善侧阈值正 ✓ 恶侧阈值负 ✓ 统一比较 ✓） */
    public static boolean reached(int alignment, Tier tier) {
        return tier.threshold() >= 0 ? alignment >= tier.threshold() : alignment <= tier.threshold();
    }

    /** 当前侧"下一个还没达成的档"（没有就 null ⇒ 已至极限 ✓） */
    public static Tier nextTier(int alignment) {
        for (Tier tier : alignment >= 0 ? TIERS_GOOD : TIERS_EVIL) {
            if (!reached(alignment, tier)) return tier;
        }
        return null;
    }

    /** 还差几个百分点到该档 ✓（绝对值之差 ✓） */
    public static int gapTo(int alignment, Tier tier) {
        return Math.abs(tier.threshold()) - Math.abs(alignment);
    }

    // ============================================================
    //  每秒：幸运 / 再生 / 不可名状 / 生物态度 / 村民折扣
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;

        restoreTool(sp);                                  // 每 tick：还原被临时改过附魔的工具 ✓

        if (sp.tickCount % 20 != 0) return;               // 其余每秒一次 ✓ 够用且便宜 ✓
        int a = ConscienceHandler.getAlignment(sp);
        refreshLuck(sp, a);
        refreshRegen(sp, a);
        tickUnspeakable(sp, a);
        tickMobAttitude(sp, a);
        tickVillagerDiscount(sp, a);
    }

    /** 幸运值 ±50%（{@link Attributes#LUCK} ✓ 固定 UUID ✓ 值没变就不重加 ✓） */
    private static void refreshLuck(ServerPlayer sp, int a) {
        try {
            AttributeInstance attr = sp.getAttribute(Attributes.LUCK);
            if (attr == null) return;
            double desired = a >= LUCK_AT ? 0.5D : (a <= -LUCK_AT ? -0.5D : 0.0D);
            AttributeModifier old = attr.getModifier(LUCK_MOD_ID);
            if (old != null && Math.abs(old.getAmount() - desired) < 1.0E-6D) return;
            if (old != null) attr.removeModifier(LUCK_MOD_ID);
            if (desired != 0.0D) {
                attr.addTransientModifier(new AttributeModifier(LUCK_MOD_ID, LUCK_MOD_NAME, desired,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 善意 ≥20%：持续生命恢复 II ✓（每秒续 3s ✓ 离开阈值自然到期 ✓） */
    private static void refreshRegen(ServerPlayer sp, int a) {
        try {
            if (a < REGEN_AT) return;
            MobEffectInstance cur = sp.getEffect(MobEffects.REGENERATION);
            if (cur != null && cur.getAmplifier() >= 1 && cur.getDuration() > REGEN_REFRESH + 20) return;
            sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_REFRESH, 1, true, false, true));
        } catch (Throwable ignored) {
        }
    }

    /** 恶意 ≤−45%：每 200s 掷一次，40% 给「不可名状」5s ✓ */
    private static void tickUnspeakable(ServerPlayer sp, int a) {
        try {
            if (a > UNSPEAKABLE_AT) return;
            long now = sp.level().getGameTime();
            long next = sp.getPersistentData().getLong(KEY_UNSPEAKABLE_NEXT);
            if (now < next) return;
            sp.getPersistentData().putLong(KEY_UNSPEAKABLE_NEXT, now + UNSPEAKABLE_PERIOD_TICKS);
            if (sp.getRandom().nextFloat() >= UNSPEAKABLE_CHANCE) return;
            sp.addEffect(new MobEffectInstance(ModEffects.UNNAMEABLE.get(), UNSPEAKABLE_DURATION, 0, false, true, true));
            sp.displayClientMessage(Component.translatable("event.tinkersnewlife.conscience.unspeakable")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        } catch (Throwable ignored) {
        }
    }

    /** 满恶：生物与你为敌（动物逃跑 ✓ 怪物/中立打你 ✓）；满善：不再锁定你 ✓ */
    private static void tickMobAttitude(ServerPlayer sp, int a) {
        try {
            if (a > -FULL && a < FULL) return;
            List<Mob> mobs = sp.level().getEntitiesOfClass(Mob.class,
                    new AABB(sp.blockPosition()).inflate(MOB_RANGE));
            for (Mob mob : mobs) {
                if (a >= FULL) {
                    if (mob.getTarget() == sp) mob.setTarget(null);          // 满善：不再主动攻击 ✓
                } else if (mob instanceof Animal animal) {
                    animal.setLastHurtByMob(sp);                              // 满恶：被动生物主动逃跑 ✓（触发原版 PanicGoal ✓）
                } else if (mob instanceof Monster || mob instanceof NeutralMob) {
                    mob.setTarget(sp);                                        // 满恶：怪物 / 中立生物与你为敌 ✓
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 满善拦"把满善玩家设为目标" ✓（照 CharmHandler 的写法 ✓） */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        try {
            if (!(event.getNewTarget() instanceof ServerPlayer sp)) return;
            if (ConscienceHandler.getAlignment(sp) < FULL) return;
            event.setNewTarget(null);
        } catch (Throwable ignored) {
        }
    }

    /** 满恶：动物视线里立刻逃 ✓（{@code getLastHurtByMob} 会被原版慢慢淡掉 ⇒ 每秒续 ✓ 上面已做 ✓） */
    @SubscribeEvent
    public static void onAnimalHurtByAlignedPlayer(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof Animal animal)) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
            if (ConscienceHandler.getAlignment(sp) > -FULL) return;
            animal.setLastHurtByMob(sp);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  时运等级（恶 0 / 善 +1）：临时改主手工具附魔 ✓ 下一 tick 还原 ✓
    // ============================================================

    private record PendingTool(ItemStack stack, Map<Enchantment, Integer> original, long tick) {}

    private static final Map<UUID, PendingTool> PENDING = new HashMap<>();

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
            int a = ConscienceHandler.getAlignment(sp);
            if (a < FORTUNE_AT && a > -FORTUNE_AT) return;
            if (PENDING.containsKey(sp.getUUID())) return;             // 同 tick 多块：只改一次 ✓
            ItemStack tool = sp.getMainHandItem();
            if (tool.isEmpty()) return;

            Map<Enchantment, Integer> original = EnchantmentHelper.getEnchantments(tool);
            Map<Enchantment, Integer> modified = new HashMap<>(original);
            if (a <= -FORTUNE_AT) {
                modified.remove(Enchantments.BLOCK_FORTUNE);            // 恶：时运归 0 ✓
            } else {
                modified.put(Enchantments.BLOCK_FORTUNE,
                        original.getOrDefault(Enchantments.BLOCK_FORTUNE, 0) + 1);   // 善：时运 +1 ✓
            }
            if (modified.equals(original)) return;
            EnchantmentHelper.setEnchantments(modified, tool);
            PENDING.put(sp.getUUID(), new PendingTool(tool, original, sp.level().getGameTime()));
        } catch (Throwable ignored) {
        }
    }

    /** 把临时改过的附魔还原 ✓（至少等过完整个 tick ✓ 掉落早就算完了 ✓） */
    private static void restoreTool(ServerPlayer sp) {
        PendingTool p = PENDING.get(sp.getUUID());
        if (p == null) return;
        if (sp.level().getGameTime() <= p.tick()) return;
        PENDING.remove(sp.getUUID());
        try {
            EnchantmentHelper.setEnchantments(p.original(), p.stack());
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  村民价格：善 ≥45% ⇒ 1 折 ✓ 恶 ≤−45% ⇒ ×1.9（涨价 90% ✓）
    // ============================================================

    private static final String KEY_PRICE_MOD = "tn_price_mod_on";

    /** 每秒修正一次（只在村民界面开着时 ✓）：把实际价格压到 10% / 抬到 190% ✓；掉出阈值就恢复原价 ✓ */
    private static void tickVillagerDiscount(ServerPlayer sp, int a) {
        try {
            if (!(sp.containerMenu instanceof MerchantMenu menu)) return;
            MerchantOffers offers = menu.getOffers();

            double factor = a >= DISCOUNT_AT ? PRICE_DOWN : (a <= MARKUP_AT ? PRICE_UP : 1.0D);
            if (factor == 1.0D) {
                // 掉出阈值 ⇒ 只复原一次 ✓（用持久标记防每秒乱重置 ✓ 免得把原版"需求涨价"也一直清掉 ✗）
                if (sp.getPersistentData().getBoolean(KEY_PRICE_MOD)) {
                    for (MerchantOffer offer : offers) offer.resetSpecialPriceDiff();
                    sp.getPersistentData().putBoolean(KEY_PRICE_MOD, false);
                }
                return;
            }
            sp.getPersistentData().putBoolean(KEY_PRICE_MOD, true);
            for (MerchantOffer offer : offers) {
                int base = offer.getBaseCostA().getCount();
                // ⚠ 上限必须跟原版一样夹在 maxStackSize ✓ 否则被原版夹住后"当前 ≠ 目标"⇒ 每秒无限累加差值 ✗
                int cap = Math.max(1, offer.getBaseCostA().getMaxStackSize());
                int desired = Math.max(1, Math.min(cap, (int) Math.round(base * factor)));
                int current = offer.getCostA().getCount();
                if (current != desired) offer.addToSpecialPriceDiff(desired - current);
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  免死一次（满善 ✓ 冷却 200s ✓）
    // ============================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (ConscienceHandler.getAlignment(sp) < FULL) return;
            long now = sp.level().getGameTime();
            if (now < sp.getPersistentData().getLong(KEY_REVIVE_NEXT)) return;

            sp.getPersistentData().putLong(KEY_REVIVE_NEXT, now + REVIVE_COOLDOWN_TICKS);
            event.setCanceled(true);                                     // 取消死亡 ✓
            sp.setHealth(sp.getMaxHealth());                             // 回满血 ✓
            sp.clearFire();
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, true, false, false));
            sp.displayClientMessage(Component.translatable("event.tinkersnewlife.conscience.revive")
                    .withStyle(ChatFormatting.AQUA), false);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  给别处用的小查询
    // ============================================================

    /** 命灯指轮的"七咒解除"是否被恶意挡掉了 ✓（{@code LifeLampRingHandler} 读 ✓） */
    public static boolean lampCancelDisabled(Entity entity) {
        return entity instanceof Player p && ConscienceHandler.getAlignment(p) <= LAMP_AT;
    }
}
