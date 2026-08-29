package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 伏魔御厨子领域
 * <p>
 * - 咒力消耗为坐杀搏徒的 2 倍（每秒 半径×40）
 * - 每 2 tick 对领域内除开启者以外的所有生物与玩家释放斩击，带横扫粒子特效
 * - 无视无敌帧（每次命中前清零 invulnerableTime），但非真实伤害：
 *   仍可被护甲、盾牌、抗性提升、创造/旁观无敌等防御手段衰减甚至免疫
 * - 每次斩击尝试在实体脚下放置红石粉外观的"血液"（已有红石/血液则不放置，
 *   该方块无战利品表，挖掉不掉落任何东西）
 * - 每道斩击伤害 = (1 + (咒力输出等级 + 咒力亲和/10)/10) × 玩家当前攻击伤害 × 5%
 */
public class FuMoYuChuZiDomain extends BaseDomain {

    private static final ModifierId FUMO_YUCHUZI_ID = new ModifierId(
            new net.minecraft.resources.ResourceLocation(com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, "fumo_yuchuzi"));

    /** 斩击间隔：2 tick */
    private static final int SLASH_INTERVAL_TICKS = 2;

    private FuMoYuChuZiDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 40.0); // 2× 坐杀搏徒（半径×20）
    }

    /** 尝试创建伏魔御厨子领域（工厂）：校验佩戴咒力核心、拥有该特性、咒力 > 0 */
    public static FuMoYuChuZiDomain tryCreate(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            sendMessage(player, "message.tinkersnewlife.domain.no_core");
            return null;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null || tool.getModifierLevel(FUMO_YUCHUZI_ID) <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_trait");
            return null;
        }
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_trait");
            return null;
        }
        if (!CursePowerHelper.isCurseInfinite(player) && CursePowerHelper.getCurse(player) <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_curse");
            return null;
        }
        return new FuMoYuChuZiDomain(player.getUUID(), player.position(), radius);
    }

    // ============================================================
    //  生命周期
    // ============================================================

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.fumo_yuchuzi";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(FUMO_YUCHUZI_ID) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.open", radius), true);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        if (now % SLASH_INTERVAL_TICKS != 0) return;
        ServerLevel level = player.serverLevel();
        double r = radius;
        double dmg = computeDamage(player);
        DamageSource source = player.damageSources().mobAttack(player);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                        center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5))) {
            if (entity.getUUID().equals(owner)) continue;
            if (entity.position().distanceToSqr(center) > r * r) continue;

            // ⭐ 无视无敌帧：清零受伤间隔，确保每次斩击全额结算；
            // 伤害仍走正常结算（护甲/盾牌/抗性等照常衰减，非真实伤害）
            entity.invulnerableTime = 0;
            entity.hurt(source, (float) dmg);

            // 横扫粒子特效
            spawnSlashParticles(level, center, entity.position());
            // 脚下红石粉模仿血液（已有红石/血液则不放置）
            placeBlood(level, entity);
        }
    }

    // ============================================================
    //  斩击伤害
    // ============================================================

    /** 每道斩击伤害 = (1+(咒力输出等级+咒力亲和/10)/10) × 玩家当前攻击伤害 × 5% */
    private double computeDamage(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double playerDmg = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return (1.0 + (output + affinity / 10.0) / 10.0) * playerDmg * 0.05;
    }

    // ============================================================
    //  粒子与血液
    // ============================================================

    /** 横扫粒子：斩击路径上的暴击粒 + 命中处的多重横扫弧 */
    private void spawnSlashParticles(ServerLevel level, Vec3 from, Vec3 to) {
        // 路径粒子（斩击轨迹）
        Vec3 delta = to.subtract(from);
        double dist = delta.length();
        if (dist > 1e-4) {
            Vec3 step = delta.normalize().scale(dist / 5.0);
            for (int i = 1; i <= 4; i++) {
                Vec3 p = from.add(step.scale(i));
                level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.5, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
        // 命中处横扫弧（两个高度）
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y + 0.4, to.z, 1, 0.3, 0.2, 0.3, 0);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y + 0.8, to.z, 1, 0.3, 0.2, 0.3, 0);
    }

    /**
     * 在实体脚下放置"血液"（红石粉外观，无掉落）。
     * 已有红石粉/血液则不放置；目标格需为空气且有支撑，否则跳过。
     */
    private void placeBlood(ServerLevel level, LivingEntity entity) {
        BlockPos pos = entity.blockPosition();
        BlockState at = level.getBlockState(pos);
        if (at.is(Blocks.REDSTONE_WIRE) || at.is(ModBlocks.BLOOD_REDSTONE.get())) return; // 已有血/红石
        if (!at.isAir()) return;
        if (!Block.canSupportCenter(level, pos.below(), Direction.UP)) return; // 无支撑会立即掉落
        level.setBlock(pos, ModBlocks.BLOOD_REDSTONE.get().defaultBlockState(), 2);
    }

    private static void sendMessage(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
