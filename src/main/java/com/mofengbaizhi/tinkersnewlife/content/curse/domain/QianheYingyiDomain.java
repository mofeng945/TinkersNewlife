package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiHandler;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 领域·嵌合影翳庭
 * <ul>
 *   <li>强控：领域内除施术者外所有目标每 tick 定身（无法移动/反击）；
 *       <b>新阴流三技巧可抵挡本领域定身</b>（与胎藏遍野一致，仅伏诛赐死不可挡）</li>
 *   <li>召唤：展开瞬间召唤"全体十影式神 ×2"（全已调伏，数值随施术者亲和/输出调幅），
 *       围绕施术者索敌并攻击领域内目标</li>
 *   <li>关闭：领域关闭（手动/耗尽/破坏）时所有领域式神立即消失</li>
 * </ul>
 */
public class QianheYingyiDomain extends BaseDomain {
    /** config: 领域 modifier path（系数键） */
    @Override
    protected String configScaleId() { return "qianhe_yingyi"; }

    /** 领域式神实体 id（关闭时清除） */
    private final List<Integer> summonedIds = new ArrayList<>();

    private QianheYingyiDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 60.0);
    }

    /** 工厂：以施术者为中心展开（通用领域展开键） */
    public static QianheYingyiDomain tryCreate(ServerPlayer player) {
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 8;
        return new QianheYingyiDomain(player.getUUID(), player.position(), radius);
    }

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.qianhe_yingyi";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.QIANHE_YINGYI.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        summonedIds.addAll(ShikigamiHandler.summonDomainSet(player));
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.qianhe.open", summonedIds.size()), true);
        TinkersNewlife.LOGGER.info("[嵌合影翳庭] {} 展开：召唤 {} 只领域式神",
                player.getName().getString(), summonedIds.size());
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        // 每 5 tick 定身领域内除施术者外所有目标（不给通用抵抗；新阴流技巧可抵挡）
        if (now % 5 != 0) return;
        ServerLevel level = player.serverLevel();
        double r = radius;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                        center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5))) {
            if (e.getUUID().equals(owner)) continue;
            if (e.position().distanceToSqr(center) > r * r) continue;
            // ⭐ 新阴流技巧抵御：被包裹玩家带技巧且咒力足够 → 本领域定身对其无效
            if (e instanceof ServerPlayer sp
                    && com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler.isProtected(sp, this)) {
                continue;
            }
            e.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 60, 0, false, false));
            if (e instanceof Mob mob) {
                net.minecraft.world.entity.ai.navigation.PathNavigation nav = mob.getNavigation();
                if (nav != null) nav.stop();
            }
        }
        // 影翳粒子
        if (player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.SMOKE,
                    center.x, center.y + 1.0, center.z, 8, r * 0.6, 1.0, r * 0.6, 0.01);
        }
    }

    @Override
    public void onClose(ServerPlayer player, String messageKey) {
        // 关闭领域：领域式神全部消失
        ServerLevel level = player != null ? player.serverLevel() : null;
        if (level == null) return;
        for (int id : summonedIds) {
            Entity e = level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        summonedIds.clear();
    }
}
