package com.mofengbaizhi.tinkersnewlife.content.curse.technique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.modifiers.ModifierId;

/**
 * 术式基类：所有术式（解、……）的公共骨架
 * <p>
 * 模板方法 {@link #tryUse(ServerPlayer)} 统一处理：
 * 术式熔断拦截 → 咒力消耗（不足时诡厄巫法灵魂能量 1:3 兜底）→ 视线索敌 →
 * 调用子类 {@link #onCast}。子类只需实现具体效果，并可复用：
 * <ul>
 *   <li>{@link #computeBaseDamage}：共享成长伤害基底 (1+(输出+亲和/10)/10) × (当前攻击伤害+输出×5)</li>
 *   <li>{@link #spawnSlashParticles}：横扫粒子（斩击轨迹 + 命中处横扫弧）</li>
 * </ul>
 */
public abstract class BaseTechnique {

    /** 术式索敌距离（格） */
    protected static final double REACH = 16.0;

    private final ModifierId modifierId;

    protected BaseTechnique(ModifierId modifierId) {
        this.modifierId = modifierId;
    }

    public ModifierId getModifierId() {
        return modifierId;
    }

    /**
     * 按键按下：默认直接释放（解/捌等即时术式）；
     * 蓄力型术式（如灶·开）覆写为"生成蓄力箭"。
     */
    public void onKeyPress(ServerPlayer player) {
        tryUse(player);
    }

    /**
     * 按键松开：默认无动作；蓄力型术式覆写为"向当前朝向发射"。
     */
    public void onKeyRelease(ServerPlayer player) {}

    /**
     * 术式反转按键（F）按下：默认无动作。
     * 拥有术式反转的术式（如无下限·苍 → 赫）覆写此方法。
     */
    public void onReverseKeyPress(ServerPlayer player) {}

    /**
     * 术式反转按键（F）松开：默认无动作。
     */
    public void onReverseKeyRelease(ServerPlayer player) {}

    /** 模板方法：释放本术式（子类无需覆写，只需实现 onCast） */
    public boolean tryUse(ServerPlayer player) {
        // 术式熔断：期间无法使用术式
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return false;
        }
        // 消耗咒力（创造模式免费；不足时灵魂能量兜底）
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return false;
        }
        // 看向的目标实体
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return false;
        }
        // 距离判定（子类可收紧，如「捌」需 3 格内接触目标）
        if (!isTargetInRange(player, target)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.too_far"), true);
            return false;
        }
        onCast(player, target);
        return true;
    }

    /** 具体术式效果（子类实现；蓄力型术式如灶·开不需要，覆写按键钩子即可） */
    protected void onCast(ServerPlayer player, LivingEntity target) {}

    /** 目标距离判定钩子：默认无限制（解等远程术式）；「捌」覆写为 3 格内 */
    protected boolean isTargetInRange(ServerPlayer player, LivingEntity target) {
        return true;
    }

    /**
     * 本术式每次释放的咒力消耗：(1 - 咒力亲和/100) × (10 + 咒力输出×5) 点，最低 1 点。
     * 咒力亲和越高消耗越低。
     */
    protected int getCost(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double cost = (1.0 - affinity / 100.0) * (10 + output * 5);
        return Math.max(1, (int) Math.ceil(cost));
    }

    /** 支付咒力；创造模式免费；不足时差额按 1:3 由诡厄巫法灵魂能量兜底 */
    protected boolean payCost(ServerPlayer player) {
        if (CursePowerHelper.isCurseInfinite(player)) return true;
        return CursePowerHelper.payCurseWithSoulFallback(player, getCost(player)) >= 0;
    }

    /** 射线检测：玩家视线方向上的第一个生物/玩家 */
    protected LivingEntity findTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(REACH));
        AABB box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && (e instanceof LivingEntity), REACH * REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** 共享伤害基底：(1+(咒力输出等级+咒力亲和/10)/10) × (当前攻击伤害 + 咒力输出等级×5) */
    protected double computeBaseDamage(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double playerDmg = player.getAttributeValue(Attributes.ATTACK_DAMAGE) + output * 5.0;
        return (1.0 + (output + affinity / 10.0) / 10.0) * playerDmg;
    }

    /**
     * 咒术增幅：玩家主手/副手持有效模块化魔杖时，按魔杖法术强度公式放大伤害
     * （与法术增幅同一公式，见 {@code ModularStaffModifier.getSpellAmplification}）。
     */
    protected double amplifyTechniqueDamage(ServerPlayer player, double damage) {
        return com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier
                .getSpellAmplification(player, (float) damage);
    }

    /** 横扫粒子：施法者→目标的斩击轨迹 + 命中处双重横扫弧 */
    protected void spawnSlashParticles(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double dist = delta.length();
        if (dist > 1e-4) {
            Vec3 step = delta.normalize().scale(dist / 4.0);
            for (int i = 1; i <= 3; i++) {
                Vec3 p = from.add(step.scale(i));
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y + 0.4, to.z, 1, 0.3, 0.2, 0.3, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y + 0.8, to.z, 1, 0.3, 0.2, 0.3, 0);
    }
}
