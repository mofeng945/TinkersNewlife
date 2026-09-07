package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.AntiGravityTechnique;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 领域·胎藏遍野
 * <p>
 * 展开后，领域内除施术者外的所有目标持续承受"压力"——压力大小 =
 * 反重力机构（反转压力场）压力的 2 倍（随咒力亲和/输出成长）。
 * 压力判定复用反重力体系：按目标体型与血量上限计算压力阈值，
 * 未超阈值 → 迟缓（越接近阈值等级越高 I~V）；超出阈值 → 脚下非基岩方块被压碎、
 * 目标被定身并在压力中持续受到咒术伤害（超出越多单次越高）。
 * <p>
 * 与伏诛赐死相反：本领域压力<b>可被新阴流三技巧抵挡</b>（带技巧且咒力足够的目标免疫压力）。
 */
public class TaizangBianyeDomain extends BaseDomain {
    /** config: 领域 modifier path（系数键） */
    @Override
    protected String configScaleId() { return "taizang_bianye"; }

    private TaizangBianyeDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 30.0);
    }

    /** 工厂：以施术者为中心展开（通用领域展开键） */
    public static TaizangBianyeDomain tryCreate(ServerPlayer player) {
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 8;
        return new TaizangBianyeDomain(player.getUUID(), player.position(), radius);
    }

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.taizang_bianye";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.TAIZANG_BIANYE.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.open", radius), true);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        ServerLevel level = player.serverLevel();
        double r = radius;
        // 压力 = 反重力反转压力 × 2
        double pr = AntiGravityTechnique.pressure(player) * 2.0;
        AABB box = new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (t.getUUID().equals(owner)) continue;
            if (t.position().distanceToSqr(center) > r * r) continue;
            // ⭐ 技巧抵挡：新阴流三技巧（咒力足够时）免疫本领域压力
            if (t instanceof ServerPlayer sp && SkillHandler.isProtected(sp, this)) continue;

            double thr = AntiGravityTechnique.thresholdFor(t);
            if (pr < thr) {
                // 阈值内：迟缓，离阈值越近等级越高（I~V）
                double ratio = pr / thr;
                int amp = Math.max(0, Math.min(4, (int) Math.ceil(ratio * 5.0) - 1));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, amp, false, false));
            } else {
                // 超出阈值：定身 + 压碎 + 持续伤害
                t.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 25, 0, false, false));
                if (t instanceof Mob mob) {
                    StunHandler.onStunApplied(mob);
                }
                if (player.tickCount % 4 == 0) {
                    crushBelow(level, t);
                }
                if (player.tickCount % 12 == 0) {
                    double over = pr - thr;
                    float dmg = (float) (1.5 + over * 0.35);
                    t.invulnerableTime = 0;
                    t.hurt(level.damageSources().magic(), dmg);
                }
            }
        }
        // 重压尘埃粒子（边界一圈，每 6 tick）
        if (player.tickCount % 6 == 0) {
            for (int i = 0; i < 8; i++) {
                double angle = 2 * Math.PI * i / 8;
                level.sendParticles(ParticleTypes.ASH,
                        center.x + Math.cos(angle) * r * 0.7,
                        center.y + r * 0.5,
                        center.z + Math.sin(angle) * r * 0.7,
                        1, 0.2, 0.3, 0.2, 0.0);
            }
        }
    }

    /** 压碎目标脚下非基岩方块（无掉落） */
    private static void crushBelow(ServerLevel level, LivingEntity t) {
        var pos = t.blockPosition().below();
        var state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() == Blocks.BEDROCK || state.getBlock() instanceof LiquidBlock) {
            return;
        }
        level.destroyBlock(pos, false);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.02);
    }
}
