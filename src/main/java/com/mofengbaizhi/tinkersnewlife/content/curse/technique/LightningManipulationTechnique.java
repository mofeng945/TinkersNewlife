package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「雷电操术」。
 * <p>
 * 顺转（释放键）：对视线内目标降下一道闪电（纯视觉），实际伤害 = 咒术伤害
 * （共享伤害基底 × 80%，套魔杖增幅与核心材料特性；被标记的视觉闪电本身不造成任何伤害/转化）。
 * 消耗沿用通用公式。
 * <p>
 * 反转（反转键 F）：术式解放「幻兽琥珀」——开关式：
 * 开启期间自身每次近战/远程伤害额外附带咒术闪电
 * （加成 = round((1+亲和/100) × (5 + 输出×5))），并伴随小型闪电粒子；
 * 每 tick 持续消耗 咒力 ceil(max(1,(1-亲和/100)×(2+输出))) 与 生命 (0.15+输出×0.05)，
 * 咒力/生命耗尽自动解除。
 */
public final class LightningManipulationTechnique extends BaseTechnique {

    public static final LightningManipulationTechnique INSTANCE = new LightningManipulationTechnique();

    private static final String BOLT_FLAG = "tnl_visual_only";

    /** 幻兽琥珀解放中的玩家 */
    private static final Set<UUID> RELEASED = ConcurrentHashMap.newKeySet();

    private LightningManipulationTechnique() {
        super(Modifiers.LIGHTNING_MANIPULATION.getId());
    }

    public static boolean isReleased(ServerPlayer player) {
        return RELEASED.contains(player.getUUID());
    }

    /** 关闭解放（登出/死亡/耗尽等） */
    public static void deactivate(ServerPlayer player) {
        if (RELEASED.remove(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.lightning.deactivated"), true);
        }
    }

    // ================= 顺转：视觉闪电 + 咒术伤害 =================

    @Override
    public void onKeyPress(ServerPlayer player) {
        LivingEntity target = findEnemyTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        // 咒术伤害（无视无敌帧、套魔杖增幅与核心材料特性）
        double raw = amplifyTechniqueDamage(player, computeBaseDamage(player) * 0.8);
        raw = CurseCoreTraitHelper.applyCurseCoreTraits(player, target, raw);
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().mobAttack(player), (float) raw);
        CurseCoreTraitHelper.afterCurseCoreHit(player, target, raw);
        // 纯视觉闪电（标记后不产生原版闪电伤害/转化）
        spawnVisualBolt(level, target);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.lightning.strike"), true);
    }

    private static void spawnVisualBolt(ServerLevel level, LivingEntity target) {
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.moveTo(target.getX(), target.getY() + 1.0, target.getZ());
        bolt.getPersistentData().putBoolean(BOLT_FLAG, true);
        level.addFreshEntity(bolt);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                24, 0.6, 0.6, 0.6, 0.05);
    }

    /** 视线内第一个敌对目标（同队除外） */
    private LivingEntity findEnemyTarget(ServerPlayer player) {
        var eye = player.getEyePosition(1.0F);
        var look = player.getLookAngle();
        var end = eye.add(look.scale(REACH));
        var box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity le
                        && le.isAlive() && !PuppetUtil.isAllyOf(le, player),
                REACH * REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ================= 反转：幻兽琥珀 =================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (RELEASED.contains(player.getUUID())) {
            deactivate(player);
            return;
        }
        RELEASED.add(player.getUUID());
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.lightning.released"), true);
    }

    /** 登出/死亡清理 */
    public static void cleanup(ServerPlayer player) {
        RELEASED.remove(player.getUUID());
    }

    // ================= 事件 =================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class LightningEvents {

        /** 视觉闪电不产生原版闪电伤害/附带伤害 */
        @SubscribeEvent
        public static void onLightningHurt(LivingHurtEvent event) {
            if (event.getSource().getDirectEntity() instanceof LightningBolt bolt
                    && bolt.getPersistentData().getBoolean(BOLT_FLAG)) {
                event.setCanceled(true);
            }
        }

        /** 幻兽琥珀：自身每次造成伤害（近战/远程，含箭等投射物）附带咒术闪电 */
        @SubscribeEvent
        public static void onDamageDealt(LivingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (event.getAmount() <= 0) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
            if (!RELEASED.contains(attacker.getUUID())) return;
            LivingEntity victim = event.getEntity();
            if (PuppetUtil.isAllyOf(victim, attacker)) return;
            if (victim == attacker) return;
            int output = CursePowerHelper.getCurseOutputLevel(attacker);
            int affinity = CursePowerHelper.getCurseAffinity(attacker);
            float bonus = Math.round((1.0F + affinity / 100.0F) * (5.0F + output * 5.0F));
            bonus = com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier
                    .getSpellAmplification(attacker, bonus);
            event.setAmount(event.getAmount() + bonus);
            if (attacker.serverLevel() != null) {
                attacker.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                        10, 0.5, 0.5, 0.5, 0.03);
            }
        }

        /** 解放持续消耗：每 tick 扣咒力 + 生命，耗尽自动解除 */
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (RELEASED.isEmpty()) return;
            net.minecraft.server.MinecraftServer server = event.getServer();
            if (server == null) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!RELEASED.contains(p.getUUID())) continue;
                if (p.isDeadOrDying() || !p.isAlive()) {
                    RELEASED.remove(p.getUUID());
                    continue;
                }
                int output = CursePowerHelper.getCurseOutputLevel(p);
                int affinity = CursePowerHelper.getCurseAffinity(p);
                double curseCost = Math.max(1.0, (1.0 - affinity / 100.0) * (2.0 + output));
                double hpCost = 0.15 + output * 0.05;
                if (!CursePowerHelper.isCurseInfinite(p)) {
                    if (CursePowerHelper.getCurse(p) < curseCost) {
                        deactivate(p);
                        continue;
                    }
                    CursePowerHelper.spendCurse(p, curseCost);
                }
                if (p.getHealth() - hpCost <= 1.0F) {
                    deactivate(p);
                    continue;
                }
                p.setHealth(p.getHealth() - (float) hpCost);
                // 雷光随行特效
                if (p.tickCount % 8 == 0 && p.serverLevel() != null) {
                    p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            p.getX(), p.getY() + p.getBbHeight() * 0.8, p.getZ(),
                            6, 0.8, 0.8, 0.8, 0.02);
                    p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.3F, 1.5F);
                }
            }
        }
    }
}
