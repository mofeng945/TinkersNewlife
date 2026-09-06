package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.WeakPointEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 术式「十划咒法」（七海建人式 Ratio）：
 * <p>
 * 顺转（术式键 C，需视线目标）：
 * <ul>
 *   <li>目标尚无弱点 → 将其"十等分"，在 7:3 分界（第七划，目标身高的 70% 处）
 *       生成一颗<b>弱点实体</b>（金色小球，悬在目标身前 0.3 格并跟随目标，持续 20 秒）</li>
 *   <li>目标已有你的弱点 → 立即对该弱点发动一次<b>精准斩击</b>（必中弱点）：
 *       伤害 = 共享咒术基底 × 暴击倍率，并移除弱点</li>
 * </ul>
 * 弱点实体会被"物理击中"：施术者对弱点实体造成的任何伤害（近战挥击/箭矢/术式）
 * 都视为精准命中第七划 → 对本体目标结算一次暴击（伤害 × 暴击倍率），弱点随即消散
 * （一击即碎）。打不中弱点球就永远没有暴击——真正的"精准"。
 * <p>
 * 暴击倍率 = 1.4 + 咒力输出×0.1 + 咒力亲和×0.008（满配约 3.2 倍）。
 */
public final class TenDivideTechnique extends BaseTechnique {

    public static final TenDivideTechnique INSTANCE = new TenDivideTechnique();

    private TenDivideTechnique() {
        super(Modifiers.TEN_DIVIDE.getId());
    }

    /** 目标当前是否带该玩家的弱点实体 */
    public static boolean isMarked(ServerPlayer owner, LivingEntity target) {
        return WeakPointEntity.find(owner, target) != null;
    }

    /** 暴击倍率 = 1.4 + 输出×0.1 + 亲和×0.008 */
    public static double critMultiplier(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return 1.4 + output * 0.1 + affinity * 0.008;
    }

    /**
     * 对本体目标结算一次"弱点暴击"（伤害 = 共享咒术基底 × 暴击倍率，套核心特性）。
     * 供精准斩击与弱点实体被击中时共用。
     */
    public static void strikeCrit(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        double damage = INSTANCE.amplifyTechniqueDamage(player,
                INSTANCE.computeBaseDamage(player) * critMultiplier(player));
        damage = CurseCoreTraitHelper.applyCurseCoreTraits(player, target, damage);
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().mobAttack(player), (float) damage);
        CurseCoreTraitHelper.afterCurseCoreHit(player, target, damage);
        INSTANCE.spawnSlashParticles(level, player.getEyePosition(), target.position());
        level.sendParticles(ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(),
                30, 0.4, 0.1, 0.4, 0.05);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.ten_divide.crit",
                String.format("%.1f", critMultiplier(player))), true);
    }

    /**
     * 登出/死亡清理：无需主动处理——弱点实体每 tick 自检 owner 离线/死亡/目标消失，
     * 会在下一个 tick 自行消散。
     */
    public static void cleanup(ServerPlayer player) {
    }

    // ================= 顺转：生成弱点实体 / 精准斩击 =================

    @Override
    protected void onCast(ServerPlayer player, LivingEntity target) {
        if (target instanceof Player) {
            // 不对玩家刻印弱点（防 PvP 白嫖必暴），仅可普通斩击
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        WeakPointEntity existing = WeakPointEntity.find(player, target);
        if (existing == null) {
            // 第一次：生成弱点实体
            WeakPointEntity wp = new WeakPointEntity(player.serverLevel(), target, player);
            player.serverLevel().addFreshEntity(wp);
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.ten_divide.marked",
                    target.getDisplayName()), true);
            return;
        }
        // 已有弱点 → 精准斩击（必中）
        strikeCrit(player, target);
        existing.discard();
    }
}
