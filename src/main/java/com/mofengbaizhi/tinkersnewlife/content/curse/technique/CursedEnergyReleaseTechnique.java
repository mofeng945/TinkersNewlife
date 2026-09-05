package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.CurseBoltEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 术式「咒力外放」：
 * <p>
 * 顺转（C）：消耗咒力，把咒力以纯粹能量形式压缩成光弹朝视线方向发射，
 * 命中第一个非友方实体造成高额伤害（无视无敌帧）。
 * <p>
 * 反转（F）：冰沙冲击波（Granite Blast）——咒力以激光炮形式持续外放：
 * 每 2 tick 沿玩家当前视线对命中实体造成无视无敌帧的持续伤害，持续 3 秒
 * （60 tick），期间可自由转动视角调整激光方向。结束时顺转与反转一同进入
 * 6 秒（120 tick）冷却。
 * <p>
 * 本术式不受术式熔断影响（反转术式同源设定），覆写 {@link #isBurnoutExempt()}。
 */
public final class CursedEnergyReleaseTechnique extends BaseTechnique {

    public static final CursedEnergyReleaseTechnique INSTANCE = new CursedEnergyReleaseTechnique();

    /** 反转激光持续 tick（3 秒） */
    public static final int BLAST_TICKS = 60;
    /** 反转结束后的共同冷却 tick（6 秒） */
    public static final int COOLDOWN_TICKS = 120;
    /** 顺转能量弹自身短冷却（防连发刷伤害，0.5 秒） */
    public static final int SHORT_CD_TICKS = 10;
    /** 激光伤害间隔 tick（每 2 tick 一跳） */
    public static final int HIT_INTERVAL = 2;

    /** 持久数据键 */
    private static final String KEY_GRANITE_END = "tinkersnewlife.curse_release_granite_end"; // 激光结束 gameTime
    private static final String KEY_COOLDOWN_END = "tinkersnewlife.curse_release_cooldown_end"; // 共同冷却结束 gameTime
    private static final String KEY_SHORT_CD_END = "tinkersnewlife.curse_release_short_cd_end"; // 顺转短冷却结束 gameTime

    private CursedEnergyReleaseTechnique() {
        super(Modifiers.CURSED_ENERGY_RELEASE.getId());
    }

    // ============================================================
    //  状态查询
    // ============================================================

    /** 反转激光是否进行中 */
    public static boolean isBlasting(ServerPlayer player) {
        return player.getPersistentData().getLong(KEY_GRANITE_END) > player.serverLevel().getGameTime();
    }

    /** 共同冷却剩余 tick（>0 表示冷却中） */
    private static long cooldownLeft(ServerPlayer player) {
        return player.getPersistentData().getLong(KEY_COOLDOWN_END) - player.serverLevel().getGameTime();
    }

    private static boolean onCooldown(ServerPlayer player) {
        return cooldownLeft(player) > 0;
    }

    /** 顺转短冷却剩余 tick */
    private static long shortCdLeft(ServerPlayer player) {
        return player.getPersistentData().getLong(KEY_SHORT_CD_END) - player.serverLevel().getGameTime();
    }

    /** 熔断豁免：咒力外放（含反转）不受术式熔断影响 */
    @Override
    public boolean isBurnoutExempt() {
        return true;
    }

    // ============================================================
    //  顺转（C）：能量弹直射
    // ============================================================

    @Override
    public void onKeyPress(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        var data = player.getPersistentData();
        // 共同冷却（反转触发过）
        if (onCooldown(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_release.cooldown",
                    cooldownLeft(player) / 20 + 1), true);
            return;
        }
        // 顺转自身短冷却（防连点 spam）
        long shortLeft = data.getLong(KEY_SHORT_CD_END) - now;
        if (shortLeft > 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_release.short_cd",
                    shortLeft / 20 + 1), true);
            return;
        }
        // 消耗：中高费用（纯粹能量弹）
        int cost = (int) Math.ceil(getCost(player) * 1.5);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        data.putLong(KEY_SHORT_CD_END, now + SHORT_CD_TICKS);

        // 伤害 = (1 + 亲和/100) × (10 + 输出×6) × 2.4，可被魔杖增幅
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        double base = (1.0 + affinity / 100.0) * (10.0 + output * 6.0);
        base = amplifyTechniqueDamage(player, base * 2.4);

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 pos = eye.add(look.scale(0.9));
        CurseBoltEntity bolt = new CurseBoltEntity(player.serverLevel(), pos, player.getUUID(), (float) base, look);
        player.serverLevel().addFreshEntity(bolt);
        player.serverLevel().playSound(null, pos.x, pos.y, pos.z,
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    // ============================================================
    //  反转（F）：冰沙冲击波 / Granite Blast 激光
    // ============================================================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        var data = player.getPersistentData();
        if (onCooldown(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_release.cooldown",
                    cooldownLeft(player) / 20 + 1), true);
            return;
        }
        if (isBlasting(player)) {
            // 激光进行中再按 F → 提前终止（进入共同冷却）
            endBlast(player, now);
            return;
        }
        // 消耗：高费用（持续 3 秒的外放）
        int cost = (int) Math.ceil(getCost(player) * 6.0);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        data.putLong(KEY_GRANITE_END, now + BLAST_TICKS);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /** 结束激光：进入顺转+反转共同冷却（6 秒） */
    private static void endBlast(ServerPlayer player, long now) {
        var data = player.getPersistentData();
        data.remove(KEY_GRANITE_END);
        data.putLong(KEY_COOLDOWN_END, now + COOLDOWN_TICKS);
        data.putLong(KEY_SHORT_CD_END, now + COOLDOWN_TICKS); // 顺转一并冷却
    }

    /** 取消进行中的激光（登出/死亡时，无冷却残留——重进后自然冷却由时间驱动） */
    public static void cancelBlast(ServerPlayer player) {
        var data = player.getPersistentData();
        data.remove(KEY_GRANITE_END);
    }

    // ============================================================
    //  每 tick 驱动（服务端，由主类对所有在线玩家调用）
    // ============================================================

    /**
     * 每 tick：激光进行中 → 每 2 tick 沿玩家当前视线对命中实体造成
     * 无视无敌帧伤害（可转动视角调整激光方向）；到期自动结束并进入共同冷却。
     */
    public static void tickBlast(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        var data = player.getPersistentData();
        long end = data.getLong(KEY_GRANITE_END);
        if (end <= now) {
            if (data.contains(KEY_GRANITE_END)) {
                // 自然到期 → 进入共同冷却
                endBlast(player, now);
            }
            return;
        }
        if (!player.isAlive() || player.isRemoved()) {
            cancelBlast(player);
            return;
        }
        // 伤害间隔
        if (now % HIT_INTERVAL == 0) {
            INSTANCE.beamHit(level, player);
        }
        spawnBeamParticles(level, player);
    }

    /** 单跳伤害：从眼睛沿视线找第一个非友方实体，造成无视无敌帧伤害 */
    private void beamHit(ServerLevel level, ServerPlayer player) {
        double range = 40.0;
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator()
                        && !PuppetUtil.isAllyOf(le, player),
                range * range);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) return;

        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        // 单跳伤害 = (1 + 亲和/100) × (2.5 + 输出×1.6)（每 2 tick，持续 3 秒约 30 跳）
        double dmg = (1.0 + affinity / 100.0) * (2.5 + output * 1.6);
        dmg = amplifyTechniqueDamage(player, dmg);
        dmg = com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                .applyCurseCoreTraits(player, target, dmg);
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().mobAttack(player), (float) dmg);
        com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper.afterCurseCoreHit(player, target, dmg);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 3, 0.2, 0.2, 0.2, 0);
    }

    /** 激光视觉：沿玩家视线散布白蓝光束粒子 */
    private static void spawnBeamParticles(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        float dist = 40.0F;
        // 每 tick 只画 3 段粒子，配合帧间移动形成连续光束观感
        for (int i = 0; i < 3; i++) {
            double t = (level.random.nextDouble() * 0.8 + 0.1) * dist;
            Vec3 p = eye.add(look.scale(t));
            level.sendParticles(new DustParticleOptions(
                            new Vector3f(0.75F, 0.9F, 1.0F), 1.5F),
                    p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0);
        }
        // 光束起点（施术者手上）亮度
        level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 2.0F),
                eye.x + look.x * 0.4, eye.y + look.y * 0.4, eye.z + look.z * 0.4, 2, 0.05, 0.05, 0.05, 0);
    }
}
