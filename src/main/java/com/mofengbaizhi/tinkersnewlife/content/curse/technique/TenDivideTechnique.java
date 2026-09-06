package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「十划咒法」（七海建人式 Ratio）：
 * <p>
 * 顺转（术式键 C，需视线目标）：
 * <ul>
 *   <li>目标尚无弱点 → 将其"十等分"，在 7:3 分界（第七划，目标身高的 70% 处）
 *       刻下一个弱点（金色粒子标记，持续 20 秒）</li>
 *   <li>目标已有你的弱点 → 立即对该弱点发动一次<b>精准斩击</b>（主动引爆，必中弱点）：
 *       伤害 = 共享咒术基底 × 暴击倍率，并移除弱点</li>
 * </ul>
 * 带弱点期间，该玩家对目标造成的<b>每次伤害</b>都会尝试命中弱点（命中率随数值成长，
 * 非必暴）：命中则这次伤害变成"暴击"（伤害 × 暴击倍率）并消耗弱点；
 * 打偏则弱点保留，可继续尝试直到 20 秒到期。
 * <p>
 * 暴击倍率 = 1.4 + 咒力输出×0.1 + 咒力亲和×0.008（满配约 3.2 倍）；
 * 弱点命中率 = 30% + 输出×2% + 亲和×0.2%（上限 85%）。
 */
public final class TenDivideTechnique extends BaseTechnique {

    public static final TenDivideTechnique INSTANCE = new TenDivideTechnique();

    /** 弱点持续 tick（20 秒） */
    public static final int MARK_DURATION = 20 * 20;

    /** owner UUID → (victim UUID → 弱点到期时刻) */
    private static final Map<UUID, Map<UUID, Long>> MARKS = new ConcurrentHashMap<>();

    private TenDivideTechnique() {
        super(Modifiers.TEN_DIVIDE.getId());
    }

    /** 目标当前是否带该玩家的有效弱点 */
    public static boolean isMarked(ServerPlayer owner, LivingEntity target) {
        Map<UUID, Long> marks = MARKS.get(owner.getUUID());
        if (marks == null) return false;
        Long until = marks.get(target.getUUID());
        if (until == null) return false;
        if (until <= owner.serverLevel().getGameTime()) {
            marks.remove(target.getUUID());
            return false;
        }
        return true;
    }

    /** 暴击倍率 = 1.4 + 输出×0.1 + 亲和×0.008 */
    public static double critMultiplier(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return 1.4 + output * 0.1 + affinity * 0.008;
    }

    /**
     * 弱点命中率 = 30% + 输出×2% + 亲和×0.2%（最高 85%）。
     * 标记后每次对该目标的伤害都会尝试命中弱点：命中才暴击并消耗弱点，
     * 打偏则弱点保留（可继续尝试，直到 20 秒到期）。
     */
    public static double weakPointHitRate(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return Math.min(0.85, 0.30 + output * 0.02 + affinity * 0.002);
    }

    /** 登出/死亡清理：移除该玩家所有弱点标记 */
    public static void cleanup(ServerPlayer player) {
        MARKS.remove(player.getUUID());
    }

    // ================= 顺转：刻下弱点 / 精准斩击 =================

    @Override
    protected void onCast(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        if (!isMarked(player, target)) {
            // 第一次：十等分并刻下第七划弱点
            MARKS.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                    .put(target.getUUID(), level.getGameTime() + MARK_DURATION);
            level.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                    18, 0.35, 0.05, 0.35, 0.01);
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.ten_divide.marked",
                    target.getDisplayName()), true);
            return;
        }
        // 已有弱点 → 精准斩击（暴击结算一次）
        double damage = amplifyTechniqueDamage(player,
                computeBaseDamage(player) * critMultiplier(player));
        damage = CurseCoreTraitHelper.applyCurseCoreTraits(player, target, damage);
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().mobAttack(player), (float) damage);
        CurseCoreTraitHelper.afterCurseCoreHit(player, target, damage);
        spawnSlashParticles(level, player.getEyePosition(), target.position());
        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                30, 0.4, 0.1, 0.4, 0.05);
        consumeMark(player, target);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.ten_divide.crit",
                String.format("%.1f", critMultiplier(player))), true);
    }

    private static void consumeMark(ServerPlayer owner, LivingEntity target) {
        Map<UUID, Long> marks = MARKS.get(owner.getUUID());
        if (marks != null) {
            marks.remove(target.getUUID());
        }
    }

    // ================= 事件：命中弱点自动暴击 + 标记维护 =================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class TenDivideEvents {

        /** 带弱点目标受到该玩家造成的伤害 → 尝试命中弱点（概率暴击并消耗弱点，打偏则保留） */
        @SubscribeEvent
        public static void onDamage(LivingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (event.getAmount() <= 0) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
            if (event.getEntity() instanceof Player) return; // 不对玩家触发，防 PvP 白嫖暴击
            LivingEntity victim = event.getEntity();
            Map<UUID, Long> marks = MARKS.get(attacker.getUUID());
            if (marks == null) return;
            Long until = marks.get(victim.getUUID());
            if (until == null) return;
            if (until <= attacker.serverLevel().getGameTime()) {
                marks.remove(victim.getUUID());
                return;
            }
            // 命中弱点判定（非必暴）
            double rate = weakPointHitRate(attacker);
            if (attacker.getRandom().nextDouble() >= rate) {
                return; // 打偏：弱点保留，可继续尝试
            }
            marks.remove(victim.getUUID());
            double mul = critMultiplier(attacker);
            event.setAmount(event.getAmount() * (float) mul);
            attacker.serverLevel().sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.7, victim.getZ(),
                    24, 0.4, 0.1, 0.4, 0.05);
            attacker.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.ten_divide.crit",
                    String.format("%.1f", mul)), true);
        }

        /** 弱点标记维护：过期清理 + 弱点金色粒子提示 */
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (MARKS.isEmpty()) return;
            MinecraftServer server = event.getServer();
            if (server == null) return;
            long now = server.getTickCount();
            for (Map.Entry<UUID, Map<UUID, Long>> entry : MARKS.entrySet()) {
                ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
                if (owner == null || !owner.isAlive()) {
                    // owner 离线/死亡 → 标记随 owner 失效
                    entry.getValue().clear();
                    continue;
                }
                ServerLevel level = owner.serverLevel();
                entry.getValue().entrySet().removeIf(mark -> mark.getValue() <= now);
                if (owner.tickCount % 8 != 0) continue;
                for (UUID victimId : entry.getValue().keySet()) {
                    if (!(level.getEntity(victimId) instanceof LivingEntity victim)
                            || !victim.isAlive()) {
                        entry.getValue().remove(victimId);
                        continue;
                    }
                    // 弱点位置：目标身高的 70%（7:3 分界）
                    level.sendParticles(ParticleTypes.CRIT,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.7, victim.getZ(),
                            2, 0.25, 0.02, 0.25, 0.0);
                }
            }
            MARKS.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }
}
