package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 领域·铁棺盖围山
 * <ul>
 *   <li>铁棺倾覆，山岳尽焚：领域内除施术者外所有目标持续被点燃
 *       （对免疫火焰的实体不重复点燃，避免视觉噪声）</li>
 *   <li>每 5 tick 施加一次<b>咒力灼烧</b>：无视无敌帧（每次命中前清零 invulnerableTime），
 *       伤害随咒力输出/亲和成长，但仍可被护甲/抗性等防御手段衰减</li>
 *   <li><b>可被新阴流三技巧抵挡</b>：带技巧且咒力足够的玩家免疫本领域点燃与灼烧
 *       （与胎藏遍野一致；仅伏诛赐死不可挡）</li>
 * </ul>
 */
public class TieGuanGaiWeiShanDomain extends BaseDomain {

    /** 灼烧间隔：5 tick */
    private static final int BURN_INTERVAL_TICKS = 5;
    /** 点燃时长（秒）：每次补火 1 秒，只要还在领域内就持续燃烧 */
    private static final int IGNITE_SECONDS = 1;

    private TieGuanGaiWeiShanDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 50.0);
    }

    /** 工厂：以施术者为中心展开（通用领域展开键） */
    public static TieGuanGaiWeiShanDomain tryCreate(ServerPlayer player) {
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 8;
        return new TieGuanGaiWeiShanDomain(player.getUUID(), player.position(), radius);
    }

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.tie_guan_gai_wei_shan";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.TIE_GUAN_GAI_WEI_SHAN.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.tie_guan_gai_wei_shan.open", radius), true);
        TinkersNewlife.LOGGER.info("[铁棺盖围山] {} 展开：半径 {}",
                player.getName().getString(), radius);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        ServerLevel level = player.serverLevel();
        double r = radius;
        boolean burnTick = now % BURN_INTERVAL_TICKS == 0;
        // 每 tick 圈选并补火；每 5 tick 额外施加咒力灼烧
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                        center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5))) {
            if (e.getUUID().equals(owner)) continue;
            if (e.position().distanceToSqr(center) > r * r) continue;
            // ⭐ 新阴流技巧抵御：带技巧且咒力足够 → 免疫本领域点燃与灼烧
            if (e instanceof ServerPlayer sp && SkillHandler.isProtected(sp, this)) continue;

            // 补火（免疫火焰的实体不点燃）
            if (!e.fireImmune() && e.getRemainingFireTicks() <= IGNITE_SECONDS * 20 - 5) {
                e.setSecondsOnFire(IGNITE_SECONDS);
            }

            // 咒力灼烧：每 5 tick 一次，无视无敌帧
            if (burnTick) {
                e.invulnerableTime = 0;
                e.hurt(level.damageSources().magic(), burnDamage(player));
            }
        }

        // 灼热粒子（每 4 tick，沿边界一圈火苗 + 顶部热浪）
        if (player.tickCount % 4 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = 2 * Math.PI * i / 6;
                level.sendParticles(ParticleTypes.FLAME,
                        center.x + Math.cos(angle) * r * 0.8,
                        center.y + level.random.nextDouble() * r,
                        center.z + Math.sin(angle) * r * 0.8,
                        1, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    /** 咒力灼烧单次伤害 = 0.6 + 咒力输出×0.35 + 咒力亲和×0.01（每 5 tick 结算，无视无敌帧） */
    private static float burnDamage(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return (float) (0.6 + output * 0.35 + affinity * 0.01);
    }
}
