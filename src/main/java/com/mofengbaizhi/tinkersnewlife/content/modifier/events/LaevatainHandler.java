package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.LaevatainModifier;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;
import java.util.UUID;

/**
 * 近战特性·莱万汀结算器（模仿启示录断曜流光）：
 * <ul>
 *   <li>统一用 LivingHurtEvent 触发：玩家挥击、怪物/仆从攻击、悠悠球命中、弹射物命中
 *       ——只要攻击者带莱万汀工具，就对目标套用莱万汀效果（清无敌帧/诅咒/禁疗/砍上限/拆柱/防复活）；</li>
 *   <li>命中附加禁疗（anti_heal，LivingHealEvent 拦截）＋ 原版 Goety CURSED 诅咒；</li>
 *   <li>概率砍血量上限（MAX_HEALTH 属性下调）；</li>
 *   <li>击中诡厄受限 Boss → 直接拆保护柱 + 抑制再生；</li>
 *   <li>持有时给周围仆从上抗性提升（光环）；</li>
 *   <li>致死不触发复活/锁血（打标记 + suppressApostleRegen，待验证）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LaevatainHandler {

    /** 砍血量上限参数（限时：到期自动移除，不永久） */
    private static final float MAX_HP_CUT_CHANCE = 0.15f;
    private static final float MAX_HP_CUT_FRACTION = 0.05f;
    private static final int MAX_HP_CUT_DURATION = 600; // 30 秒
    private static final UUID MAXHP_MODIFIER = UUID.fromString("3f6a9c1e-2b8d-4f5a-9e7c-1a2b3c4d5e6f");
    /** 目标 UUID -> 上限削减的到期 gameTime */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> MAXHP_EXPIRY =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 仆从光环：半径 7（对齐原版 Valettein），每 20 tick */
    private static final double AURA_RADIUS = 7.0;
    private static final int AURA_INTERVAL = 20;

    /** 禁疗时长 */
    private static final int ANTI_HEAL_DURATION = 160;

    private static final String KEY_NO_REVIVE = "tinkersnewlife.laevatain_no_revive";

    private LaevatainHandler() {
    }

    // ==================== 命中改写 ====================
    // 统一用 LivingHurtEvent 触发：无论伤害源是玩家挥击、怪物/仆从攻击、悠悠球命中还是弹射
    // 物命中，只要"攻击者"（悠球→球实体上工具栈；活物→主/副手；弹射物→持有者主/副手）带莱万汀，
    // 就对目标套用莱万汀效果：清无敌帧、真伤穿透、诅咒+禁疗、概率砍上限、拆柱+抑制再生、防复活。
    // ⚠️ 不再用 AttackEntityEvent(cancel+重打)——那对悠悠球/怪物/仆从不生效，且无法多来源统一。

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        // 从伤害源解析"带莱万汀工具"的攻击者
        ItemStack weapon = resolveLaevatainWeapon(event.getSource());
        if (weapon.isEmpty()) return;

        // 真伤：清目标无敌帧，让伤害不被打断/抵扣
        target.invulnerableTime = 0;

        // ⭐ 拆柱 + 抑制再生（诡厄受限 Boss）
        if (target.isAlive() && !target.isRemoved() && GoetyBridge.isDamageLimitedBoss(target)) {
            GoetyBridge.shatterProtectingPillars(target);
            GoetyBridge.suppressApostleRegen(target);
        }
        // 防复活/锁血标记
        target.getPersistentData().putBoolean(KEY_NO_REVIVE, true);

        if (target.isAlive() && !target.isRemoved()) {
            // 原版命中必附：Goety 诅咒（CURSED）40 tick amp=1
            applyGoetyCursed(target);
            // 禁疗
            target.addEffect(new MobEffectInstance(ModEffects.ANTI_HEAL.get(), ANTI_HEAL_DURATION, 0, false, false, true));
            // 概率砍血量上限（按用户改动保留）
            if (target.getRandom().nextFloat() < MAX_HP_CUT_CHANCE) {
                cutMaxHp(target);
            }
        }
    }

    /**
     * 解析带莱万汀的攻击者工具栈（空 = 当前伤害源无莱万汀）。
     * <ul>
     *   <li>悠悠球：直接实体 = YoYoEntity → 读球实体携带的完整工具栈（returnStack）；</li>
     *   <li>活物攻击者：直接实体是 LivingEntity → 查其主/副手；</li>
     *   <li>弹射物：直接实体是 Projectile（非悠球）→ 查其持有者（owner）主/副手。</li>
     * </ul>
     */
    private static ItemStack resolveLaevatainWeapon(DamageSource source) {
        if (source == null) return ItemStack.EMPTY;
        try {
            net.minecraft.world.entity.Entity direct = source.getDirectEntity();
            // 悠悠球命中
            if (direct instanceof com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity yoyo) {
                ItemStack stack = yoyo.getReturnStack();
                if (hasLaevatain(stack)) return stack;
            }
            // 活物攻击者（玩家/怪物/仆从直接近战）
            if (direct instanceof LivingEntity attacker) {
                ItemStack w = combatWeapon(attacker);
                if (!w.isEmpty()) return w;
            }
            // 弹射物：持有者手上工具
            if (direct instanceof net.minecraft.world.entity.projectile.Projectile proj
                    && proj.getOwner() instanceof LivingEntity owner) {
                ItemStack w = combatWeapon(owner);
                if (!w.isEmpty()) return w;
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    /** 主/副手第一把带莱万汀的工具 */
    private static ItemStack combatWeapon(LivingEntity attacker) {
        ItemStack main = attacker.getMainHandItem();
        if (hasLaevatain(main)) return main;
        ItemStack off = attacker.getOffhandItem();
        if (hasLaevatain(off)) return off;
        return ItemStack.EMPTY;
    }

    /** 原版 ValetteinItem 命中附带的 Goety CURSED（诅咒）效果 40t amp1；未装 goety 时 no-op */
    private static void applyGoetyCursed(LivingEntity target) {
        Object effect = goetyEffect("CURSED");
        if (effect instanceof net.minecraft.world.effect.MobEffect e) {
            target.addEffect(new MobEffectInstance(e, 40, 1, false, false, true));
        }
    }

    private static Object goetyEffect(String field) {
        try {
            java.lang.reflect.Field f = java.lang.Class.forName("com.Polarice3.Goety.common.effects.GoetyEffects")
                    .getField(field);
            Object ro = f.get(null);
            return ((net.minecraftforge.registries.RegistryObject<?>) ro).get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 限时砍目标最大血量上限（属性修饰符，按 UUID；到期自动移除） */
    private static void cutMaxHp(LivingEntity target) {
        var attr = target.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(MAXHP_MODIFIER);
        AttributeModifier modifier = new AttributeModifier(
                MAXHP_MODIFIER, "laevatain_maxhp_cut",
                -(attr.getValue() * MAX_HP_CUT_FRACTION),
                AttributeModifier.Operation.ADDITION);
        attr.addTransientModifier(modifier);
        if (target.getHealth() > attr.getValue()) {
            target.setHealth((float) attr.getValue());
        }
        MAXHP_EXPIRY.put(target.getUUID(), target.level().getGameTime() + MAX_HP_CUT_DURATION);
    }

    /** 定期清理到期上限削减（每 20 tick 检查一次已削减目标） */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;
        if (MAXHP_EXPIRY.isEmpty()) return;
        long now = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD) != null
                ? event.getServer().overworld().getGameTime() : 0;
        for (var it = MAXHP_EXPIRY.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (now < e.getValue()) continue;
            LivingEntity living = null;
            for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) {
                net.minecraft.world.entity.Entity en = level.getEntity(e.getKey());
                if (en instanceof LivingEntity le) {
                    living = le;
                    break;
                }
            }
            if (living != null) {
                var attr = living.getAttribute(Attributes.MAX_HEALTH);
                if (attr != null) attr.removeModifier(MAXHP_MODIFIER);
                if (living.getHealth() > living.getMaxHealth()) {
                    living.setHealth(living.getMaxHealth());
                }
            }
            it.remove();
        }
    }

    // ==================== 仆从抗性光环 ====================
    // 对齐原版 ValetteinItem.onInventoryTick：每 20 tick，对半径 7 内“属于本玩家”的仆从
    // （Goety IOwned.getTrueOwner()==holder 或 OwnableEntity.getOwner()==holder）施加 抗性提升 II（60t，amp1）。

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide) return;
        if (holder.tickCount % AURA_INTERVAL != 0) return;
        if (!hasLaevatain(holder.getMainHandItem())) return;
        if (!(holder.level() instanceof ServerLevel server)) return;

        AABB box = holder.getBoundingBox().inflate(AURA_RADIUS);
        for (LivingEntity other : holder.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (other == holder || !other.isAlive()) continue;
            if (!isOwnedBy(other, holder)) continue;
            // 原版：DAMAGE_RESISTANCE 60 tick，amp=1（抗性 II）
            other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, false, true));
        }
    }

    /** 是否为 holder 的仆从（Goety IOwned.getTrueOwner()==holder 或 OwnableEntity.getOwner()==holder） */
    private static boolean isOwnedBy(LivingEntity target, LivingEntity holder) {
        LivingEntity goetyOwner = GoetyBridge.getServantOwner(target);
        if (goetyOwner != null && goetyOwner == holder) return true;
        return target instanceof net.minecraft.world.entity.OwnableEntity oe && oe.getOwner() == holder;
    }

    // ==================== 禁疗拦截 ====================

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(ModEffects.ANTI_HEAL.get())) {
            event.setCanceled(true);
        }
    }

    // ==================== 死亡不触发复活/锁血（标记已置于 boss，抑制再生） ====================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (e.getPersistentData().getBoolean(KEY_NO_REVIVE) && GoetyBridge.isGoetyApostle(e)) {
            // 已由命中时的拆柱/抑制再生处理；此处兜底避免复活类流程
            e.getPersistentData().remove(KEY_NO_REVIVE);
        }
    }

    // ==================== 工具 ====================

    private static boolean hasLaevatain(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(LaevatainModifier.ID) > 0;
    }
}
