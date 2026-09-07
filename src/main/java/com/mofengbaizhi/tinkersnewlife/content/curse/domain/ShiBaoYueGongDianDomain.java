package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.binding.BindingStateHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 领域·时胞月宫殿
 * <ul>
 *   <li>月华凝结为时胞：领域内除施术者外所有目标被命中"投射咒法未成功"的<b>罚站定身</b>
 *       ——无法移动/攻击/使用物品/转视角（每 5 tick 刷新 60 tick 定身）。</li>
 *   <li><b>天与暴君豁免</b>：天与咒缚·暴君体质的玩家（失去咒力、纯肉体强化者）
 *       免疫本领域定身——月华无法束缚无咒之躯。</li>
 *   <li>通用抵抗生效（首次进入先抵抗一会）；新阴流三技巧可抵挡本领域定身
 *       （同胎藏遍野——仅伏诛赐死不可挡）。</li>
 *   <li>关闭领域：领域内定身立即解除。</li>
 * </ul>
 */
public class ShiBaoYueGongDianDomain extends BaseDomain {
    /** config: 领域 modifier path（系数键） */
    @Override
    protected String configScaleId() { return "shi_bao_yue_gong_dian"; }

    /** 定身刷新间隔：5 tick */
    private static final int STUN_REFRESH_TICKS = 5;
    /** 定身时长：60 tick（3 秒，持续刷新） */
    private static final int STUN_DURATION_TICKS = 60;

    private ShiBaoYueGongDianDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 55.0);
    }

    /** 工厂：以施术者为中心展开（通用领域展开键） */
    public static ShiBaoYueGongDianDomain tryCreate(ServerPlayer player) {
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 8;
        return new ShiBaoYueGongDianDomain(player.getUUID(), player.position(), radius);
    }

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.shi_bao_yue_gong_dian";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.SHI_BAO_YUE_GONG_DIAN.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.shi_bao_yue_gong_dian.open", radius), true);
        TinkersNewlife.LOGGER.info("[时胞月宫殿] {} 展开：半径 {}",
                player.getName().getString(), radius);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        if (now % STUN_REFRESH_TICKS != 0) return;
        ServerLevel level = player.serverLevel();
        double r = radius;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                        center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5))) {
            if (e.getUUID().equals(owner)) continue;
            if (e.position().distanceToSqr(center) > r * r) continue;

            // ⭐ 通用领域抵抗：首次进入先抵抗一会，期满后才进入罚站
            if (registerResistAndCheck(e, now)) continue;

            if (e instanceof ServerPlayer sp) {
                // ⭐ 新阴流技巧抵挡：带技巧且咒力足够 → 免疫本领域定身
                if (SkillHandler.isProtected(sp, this)) continue;
                // ⭐ 天与暴君豁免：无咒之躯不受月华束缚
                if (BindingStateHandler.isRestricted(sp)) continue;
            }

            // 命中投射咒法未成功式罚站定身：无法移动/攻击/使用物品/转视角
            e.addEffect(new MobEffectInstance(ModEffects.STUN.get(), STUN_DURATION_TICKS, 0, false, false));
            if (e instanceof Mob mob) {
                StunHandler.onStunApplied(mob);
            }
        }

        // 月华粒子（每 10 tick）
        if (now % 10 == 0) {
            for (int i = 0; i < 10; i++) {
                double angle = 2 * Math.PI * i / 10;
                level.sendParticles(ParticleTypes.END_ROD,
                        center.x + Math.cos(angle) * r * 0.8,
                        center.y + 1.0 + level.random.nextDouble() * r * 0.8,
                        center.z + Math.sin(angle) * r * 0.8,
                        1, 0.1, 0.2, 0.1, 0.01);
            }
        }
    }

    @Override
    public void onClose(ServerPlayer player, String messageKey) {
        // 领域关闭：解除领域内目标的定身（不留 3 秒尾巴）
        ServerLevel level = player != null ? player.serverLevel() : null;
        if (level != null) {
            double r = radius;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                            center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5))) {
                if (e.getUUID().equals(owner)) continue;
                if (e.position().distanceToSqr(center) > r * r) continue;
                e.removeEffect(ModEffects.STUN.get());
            }
        }
        clearResist();
    }
}
