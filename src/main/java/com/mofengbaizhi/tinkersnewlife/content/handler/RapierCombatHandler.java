package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.RapierItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>西洋剑 · 穿甲（§943 阶段 2）</b>—— 匠魂2 那条"**无视目标护甲**"。
 *
 * <h2>为什么不用伤害类型</h2>
 * 1.20.1 的"无视护甲"是**伤害类型标签** {@code minecraft:bypasses_armor} 驱动的 ✓
 * ⇒ 给武器换伤害类型会**波及所有用该类型的攻击** ✗（整合包里别的模组也吃这套 ✗）
 * ⇒ 用户口径选**方案 B** ✓：只在"护甲这一段"做补偿 ✓ 影响面最小 ✓。
 *
 * <h2>为什么能这么补（事件顺序，1.20.1 实查）</h2>
 * <pre>LivingEntity#hurt(...)：
 *   amount = ForgeHooks.onLivingHurt(this, source, amount);      // ← LivingHurtEvent ✓ 护甲【前】
 *   amount = applyArmorCalculations(source, amount);             // ← 护甲在这里减的
 *   amount = applyPotionDamageCalculations(source, amount);      // ← 抗性提升/保护附魔
 *   ...
 *   f1 = ForgeHooks.onLivingDamage(this, source, amount);        // ← LivingDamageEvent ✓ 护甲【后】+药水后
 * </pre>
 * ⇒ 在 {@link LivingHurtEvent} 记下**护甲前**的伤害 ✓ 在 {@link LivingDamageEvent} 里
 * 用**原版同一条公式**（{@link CombatRules#getDamageAfterAbsorb}）算出"护甲本来要吃掉多少" ✓
 * 再把这部分**加回去** ✓。
 * <ul>
 *   <li>✅ 只穿**护甲** ✓ —— 抗性提升/保护附魔那部分**保留** ✗（它们在上面的第二步 ✓ 我们没动它 ✓）；</li>
 *   <li>✅ 只对**主手拿西洋剑的玩家攻击**生效 ✓ 其它来源一概不动 ✓；</li>
 *   <li>✅ 源本来就带 {@code bypasses_armor} ⇒ 直接跳过 ✓（否则会重复加 ✓）；</li>
 *   <li>✅ 目标没护甲 ⇒ 跳过 ✓；记录带 gameTime ✓ 跨 tick 的陈旧记录自动作废 ✓。</li>
 * </ul>
 *
 * <p>⚠ §942 的教训适用：任何"自己算/自己补"的伤害逻辑都要**显式考虑创造模式** ✓ —— 这里不涉及耐久 ✓
 * 但**不额外排除创造** ✓（创造玩家打出的伤害本来就该正常生效 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class RapierCombatHandler {

    private RapierCombatHandler() {}

    /** 受击者 UUID → [{@code gameTime}, 护甲前伤害] ✓（同一 tick 内 {@code LivingHurt → LivingDamage} 传递 ✓） */
    private static final Map<UUID, float[]> PRE_ARMOR = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRapierHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity victim = event.getEntity();
        if (!rapierAttack(event.getSource(), victim)) return;
        PRE_ARMOR.put(victim.getUUID(),
                new float[]{victim.level().getGameTime(), event.getAmount()});
    }

    @SubscribeEvent
    public static void onRapierDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity victim = event.getEntity();
        float[] record = PRE_ARMOR.remove(victim.getUUID());
        if (record == null) return;
        if ((long) record[0] != victim.level().getGameTime()) return;      // 陈旧记录 ✗（那次伤害被取消/免疫了 ✓）
        if (!rapierAttack(event.getSource(), victim)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)) return;   // 本来就穿甲 ✓ 别重复加 ✓
        float preArmor = record[1];
        float armor = victim.getArmorValue();
        if (armor <= 0.0F || preArmor <= 0.0F) return;                     // 没护甲 ⇒ 没得穿 ✓
        float toughness = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float absorbed = preArmor - CombatRules.getDamageAfterAbsorb(preArmor, armor, toughness);
        if (absorbed <= 0.0F) return;
        event.setAmount(event.getAmount() + absorbed);
    }

    /** 这一发是不是"**主手西洋剑的玩家**"打出来的 ✓（含直接攻击与弹射物主人 ✓ 自伤排除 ✓） */
    private static boolean rapierAttack(DamageSource source, LivingEntity victim) {
        if (!(source.getEntity() instanceof Player player)) return false;
        if (player == victim) return false;
        return player.getMainHandItem().getItem() instanceof RapierItem;
    }

    /**
     * <b>§950 阶段 4：手持西洋剑时，副手盾牌等"无法使用"</b> ✓（匠魂2 原版特征之一 ✓）。
     *
     * <p>为什么还需要这一手：我们右键**后跳**已经吃掉了右键 ✓（`RapierItem#use` 返回 success ✓）
     * ⇒ 绝大多数情况下副手盾牌根本起不来 ✓ —— **但潜行时我们故意放行** ✗（潜行右键留给放置/交互 ✓）
     * ⇒ 那条缝里盾牌还是能举起来 ✗ ⇒ 用这个事件把缝堵上 ✓。
     *
     * <p>口径选择（两档）：**这里选"完全不格挡"** ✓（{@code setCanceled(true)} ✓）
     * —— 匠魂2 的原文是"**无法使用**" ✓ 不是"能举但挡不住" ✗；
     * 想温和一点就改成只把 {@code setBlockedDamage(0)} ✓（保动画、不挡伤害 ✓）一行之差 ✓。
     *
     * <p>判定看的是**主手** ✓（盾牌在副手 ✓ 与哪只手举盾无关 ✓）：主手是 {@link RapierItem} ⇒ 取消格挡 ✓。
     */
    @SubscribeEvent
    public static void onShieldBlock(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player.getMainHandItem().getItem() instanceof RapierItem)) return;
        event.setCanceled(true);              // 完全不格挡 ✓（盾牌"用不了" ✓）
        event.setShieldTakesDamage(false);    // 保险：别白扣盾的耐久 ✓
    }
}
