package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.effect.AntiHealEffect;
import com.mofengbaizhi.tinkersnewlife.content.modifier.LaevatainModifier;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;
import java.util.UUID;

/**
 * 近战特性·莱万汀结算器（模仿启示录断曜流光）：
 * <ul>
 *   <li>命中改写为 true_pierce（真伤、无视伤害减免/无敌帧/各类无敌）——两段式（带归属 → 被挡则无主重打）；</li>
 *   <li>命中附加禁疗（anti_heal，LivingHealEvent 拦截）；</li>
 *   <li>概率砍血量上限（MAX_HEALTH 属性下调）；</li>
 *   <li>击中诡厄受限 Boss → 直接拆保护柱 + 抑制再生；</li>
 *   <li>潜行左键激流冲刺（位移，不瞬移）；</li>
 *   <li>持有时给周围仆从上抗性提升（光环）；</li>
 *   <li>致死不触发复活/锁血（打标记 + suppressApostleRegen，待验证）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LaevatainHandler {

    private static final ResourceKey<DamageType> PIERCE_TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "true_pierce"));
    private static volatile Holder<DamageType> cachedPierce = null;

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

    /** 激流冲刺（对齐原版 Valettein onEntitySwing）：shift+左键挥击时冲刺 */
    private static final double DASH_SPEED = 3.6;
    private static final int DASH_COOLDOWN_TICKS = 10;

    /** 禁疗时长 */
    private static final int ANTI_HEAL_DURATION = 160;

    private static final String KEY_NO_REVIVE = "tinkersnewlife.laevatain_no_revive";

    private LaevatainHandler() {
    }

    // ==================== 命中改写（真伤 + 无敌/限伤穿透 + 拆柱 + 禁疗 + 砍上限 + 防复活） ====================

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (!hasLaevatain(player.getMainHandItem())) return;

        // 潜行左键挥击（命中时）同样触发激流冲刺：对齐原版 onEntitySwing（挥击即冲刺）
        tryDash(player);

        event.setCanceled(true);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (damage <= 0) return;

        // ⭐ 拆柱 + 抑制再生（诡厄受限 Boss）
        if (target.isAlive() && !target.isRemoved() && GoetyBridge.isDamageLimitedBoss(target)) {
            GoetyBridge.shatterProtectingPillars(target);
            GoetyBridge.suppressApostleRegen(target);
        }
        // 防复活/锁血标记
        target.getPersistentData().putBoolean(KEY_NO_REVIVE, true);

        // 两段式穿透（带归属 → 被挡则无主重打），真伤源 = true_pierce
        boolean dealt = target.hurt(pierceSource(player), damage);
        if (!dealt && target.isAlive() && !target.isRemoved()) {
            target.hurt(GoetyBridge.truePierceSource(target.level()), damage);
        }

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

    // ==================== 潜行左键激流冲刺 ====================
    // 对齐原版 ValetteinItem.onEntitySwing：shift(潜行)+左键挥击时，
    // 取玩家视线方向归一化后 ×3.6 作为初速度 setDeltaMovement，产生高速冲刺；
    // 命中与否取决于朝向前方挥出的本体攻击（不额外造直线伤害），冷却 10 tick。
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!player.isShiftKeyDown()) return;
        if (!hasLaevatain(player.getMainHandItem())) return;
        event.setCanceled(true);
        tryDash(player);
    }

    /** 激流冲刺本体：对齐原版 ValetteinItem.onEntitySwing（shift+左键挥击）
     *  1) 设定视线方向 ×3.6 的速度（setDeltaMovement）
     *  2) startAutoSpinAttack(15)：触发激流(riptide)自旋动画
     *  3) 音效 SoundEvents.TRIDENT_RIPTIDE_1（激流音）+ hurtMarked=true 让客户端同步动画/速度
     *  冷却 10 tick（对齐原版）。 */
    private static void tryDash(Player player) {
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong("tinkersnewlife.laevatain_dash_cd");
        if (now - last < DASH_COOLDOWN_TICKS) return;
        player.getPersistentData().putLong("tinkersnewlife.laevatain_dash_cd", now + DASH_COOLDOWN_TICKS);

        // 视线方向 ×3.6 作为速度（原版用 yaw/pitch 构造、归一化后 *3.6；getLookAngle 等价）
        Vec3 dir = player.getLookAngle();
        player.setDeltaMovement(dir.x * DASH_SPEED, dir.y * DASH_SPEED, dir.z * DASH_SPEED);

        // 激流(riptide)自旋动画
        player.startAutoSpinAttack(15);

        // 挥击动作 + 音效反馈
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (player.level() instanceof ServerLevel server) {
            player.playNotifySound(net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.3f);
        }
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

    private static DamageSource pierceSource(LivingEntity attacker) {
        Holder<DamageType> cached = cachedPierce;
        if (cached == null) {
            cached = attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(PIERCE_TYPE);
            cachedPierce = cached;
        }
        return new DamageSource(cached, attacker, attacker);
    }
}
