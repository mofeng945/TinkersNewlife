package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 术式处理（服务端）
 * <p>
 * 术式是佩戴在咒力核心术式槽上的主动技能，由"释放术式"按键触发。
 * 目前实现第一个术式——「解」：对看向的实体释放一次斩击，
 * 伤害 = (1+(咒力输出等级+咒力亲和/10)/10) × (当前攻击伤害+咒力输出等级×5) × 70%，
 * 类似伏魔御厨子的小斩击（无视无敌帧、正常护甲结算、横扫粒子）。
 * 术式熔断期间无法使用术式。
 */
public final class TechniqueHandler {

    private static final ModifierId KAI_ID = new ModifierId(
            new net.minecraft.resources.ResourceLocation(TinkersNewlife.MOD_ID, "kai"));

    /** 术式索敌距离（格） */
    private static final double REACH = 16.0;

    private TechniqueHandler() {}

    /** 释放术式「解」：对看向的指定实体释放一次斩击 */
    public static void tryUseKai(ServerPlayer player) {
        // 术式熔断：期间无法使用术式
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        // 需要佩戴拥有「解」术式的咒力核心
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_core"), true);
            return;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null || tool.getModifierLevel(KAI_ID) <= 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_trait"), true);
            return;
        }
        // 看向的目标实体
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }

        // 伤害 = (1+(咒力输出等级+咒力亲和/10)/10) × (当前攻击伤害+咒力输出等级×5) × 70%
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double playerDmg = player.getAttributeValue(Attributes.ATTACK_DAMAGE) + output * 5.0;
        double damage = (1.0 + (output + affinity / 10.0) / 10.0) * playerDmg * 0.70;

        // 类似伏魔御厨子小斩击：无视无敌帧，正常伤害结算（护甲/盾牌等仍可衰减）
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().mobAttack(player), (float) damage);

        // 横扫粒子：路径暴击粒 + 命中处横扫弧
        spawnSlashParticles(player.serverLevel(), player.getEyePosition(), target.position());
    }

    /** 射线检测：玩家视线方向上的第一个生物/玩家 */
    private static LivingEntity findTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(REACH));
        AABB box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && (e instanceof LivingEntity), REACH * REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** 横扫粒子：从施法者到目标的斩击轨迹 + 命中处的横扫弧 */
    private static void spawnSlashParticles(ServerLevel level, Vec3 from, Vec3 to) {
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
