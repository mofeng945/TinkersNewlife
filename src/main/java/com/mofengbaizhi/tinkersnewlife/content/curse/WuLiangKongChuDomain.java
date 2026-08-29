package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无量空处领域
 * <p>
 * - 咒力消耗为坐杀搏徒的 4 倍（每秒 半径×80）
 * - 领域内除开启者以外的所有生物/玩家陷入静止：无法攻击/移动/使用物品/切换物品栏/打开背包
 * - 领域关闭后，静止状态按公式继续：
 *   (领域内状态时间/10 + 咒力输出等级) × (1 + 咒力亲和/100) × 10 tick 后结束
 */
public class WuLiangKongChuDomain extends BaseDomain {

    /** 静止效果刷新间隔（tick） */
    private static final int STUN_REFRESH_TICKS = 5;
    /** 领域内静止效果的"持续"时长（期间反复刷新，领域关闭时改为延续时长） */
    private static final int STUN_DURATION_TICKS = 100000;

    /** 每个实体在领域内停留的 tick 数（用于关闭后的延续时长计算） */
    private final Map<UUID, Long> insideTicks = new ConcurrentHashMap<>();

    private WuLiangKongChuDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 80.0); // 4× 坐杀搏徒（半径×20）
    }

    /** 尝试创建无量空处领域（工厂）：校验佩戴咒力核心、拥有无量空处特性、咒力 > 0 */
    public static WuLiangKongChuDomain tryCreate(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            sendMessage(player, "message.tinkersnewlife.domain.no_core");
            return null;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null || tool.getModifierLevel(Modifiers.WULIANG_KONGCHU.getId()) <= 0) {
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
        return new WuLiangKongChuDomain(player.getUUID(), player.position(), radius);
    }

    // ============================================================
    //  生命周期
    // ============================================================

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.WULIANG_KONGCHU.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.open", radius), true);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        if (now % STUN_REFRESH_TICKS != 0) return;
        ServerLevel level = player.serverLevel();
        double r = radius;
        AABB box = new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getUUID().equals(owner)) continue;
            if (entity.position().distanceToSqr(center) > r * r) continue;

            // 记录领域内停留时间
            insideTicks.merge(entity.getUUID(), (long) STUN_REFRESH_TICKS, Long::sum);

            // 施加/刷新静止效果（持续期间几乎不结束）
            entity.addEffect(new MobEffectInstance(ModEffects.STUN.get(), STUN_DURATION_TICKS, 0, false, false));
            if (entity instanceof Mob mob) {
                StunHandler.onStunApplied(mob);
            }
        }

        // 领域边界粒子提示（少量，不干扰视野）
        if (now % 20 == 0) {
            for (int i = 0; i < 12; i++) {
                double angle = 2 * Math.PI * i / 12;
                level.sendParticles(ParticleTypes.SMOKE,
                        center.x + Math.cos(angle) * r, center.y + 1.0, center.z + Math.sin(angle) * r,
                        1, 0, 0, 0, 0);
            }
        }
    }

    @Override
    public void onClose(ServerPlayer player, String messageKey) {
        // 领域关闭：静止状态按公式继续延续
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        ServerLevel level = player.serverLevel();
        for (Map.Entry<UUID, Long> entry : insideTicks.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living)) continue;
            long duration = (long) ((entry.getValue() / 10.0 + output) * (1 + affinity / 100.0) * 10);
            duration = Math.min(duration, STUN_DURATION_TICKS);
            living.addEffect(new MobEffectInstance(ModEffects.STUN.get(), (int) Math.max(1, duration), 0, false, false));
        }
        insideTicks.clear();
    }

    private static void sendMessage(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
