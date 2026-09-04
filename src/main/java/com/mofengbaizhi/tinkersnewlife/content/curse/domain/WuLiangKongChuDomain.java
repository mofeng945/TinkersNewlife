package com.mofengbaizhi.tinkersnewlife.content.curse.domain;
import com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.BaseDomain;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;

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
import net.minecraft.world.entity.player.Player;
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
 * - 领域内除开启者以外的所有生物/玩家陷入静止：无法自主移动/攻击/使用物品/切换物品栏/打开背包，
 *   但仍受物理引擎（重力/击退）与碰撞挤压影响
 * - 抵抗机制：生物按血量抵抗 (血量/100+1)×5 tick，玩家按咒力亲和抵抗 (咒力亲和/100+1)×5 tick，
 *   抵抗期内不会被定身，之后才进入静止
 * - 领域关闭后，静止按公式继续：
 *   (领域内状态时间/10 + 咒力输出等级) × (1 + 咒力亲和/100) × 10 tick 后结束
 */
public class WuLiangKongChuDomain extends BaseDomain {

    /** 静止效果刷新间隔（tick） */
    private static final int STUN_REFRESH_TICKS = 5;
    /** 领域内静止效果的"持续"时长（期间反复刷新，领域关闭时改为延续时长） */
    private static final int STUN_DURATION_TICKS = 100000;

    /** 每个实体在领域内停留的 tick 数（用于关闭后的延续时长计算） */
    private final Map<UUID, Long> insideTicks = new ConcurrentHashMap<>();
    /** 抵抗截止时刻：实体 UUID → 服务器 tick，此前不会被定身 */
    private final Map<UUID, Long> resistUntil = new ConcurrentHashMap<>();

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
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.wuliang_kongchu";
    }

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

            // 记录领域内停留时间（含抵抗期）
            insideTicks.merge(entity.getUUID(), (long) STUN_REFRESH_TICKS, Long::sum);

            // 抵抗期：生物按血量、玩家按咒力亲和抵抗，期满后才进入静止
            long resist = resistUntil.computeIfAbsent(entity.getUUID(), id -> now + computeResist(entity));
            if (now < resist) continue;

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
        // 领域关闭：已被定身的实体按公式继续延续静止
        int output = player != null ? CursePowerHelper.getCurseOutputLevel(player) : 1;
        int affinity = player != null ? CursePowerHelper.getCurseAffinity(player) : 0;
        ServerLevel level = player != null ? player.serverLevel() : null;
        for (Map.Entry<UUID, Long> entry : insideTicks.entrySet()) {
            Entity entity = level != null ? level.getEntity(entry.getKey()) : null;
            if (!(entity instanceof LivingEntity living)) continue;
            if (!living.hasEffect(ModEffects.STUN.get())) continue; // 抵抗期内未被定身的实体不延续
            long duration = (long) ((entry.getValue() / 10.0 + output) * (1 + affinity / 100.0) * 10);
            duration = Math.min(duration, STUN_DURATION_TICKS);
            // ⭐ 先移除再施加：原版 addEffect 在新效果更短时不替换，直接施加会导致永久定身
            living.removeEffect(ModEffects.STUN.get());
            living.addEffect(new MobEffectInstance(ModEffects.STUN.get(), (int) Math.max(1, duration), 0, false, false));
        }
        insideTicks.clear();
        resistUntil.clear();
    }

    /** 抵抗时长（tick）：生物按血量 (血量/100+1)×5，玩家按咒力亲和 (亲和/100+1)×5 */
    private long computeResist(LivingEntity entity) {
        if (entity instanceof Player p) {
            return Math.max(1, (long) ((CursePowerHelper.getCurseAffinity(p) / 100.0 + 1) * 5));
        }
        return Math.max(1, (long) ((entity.getMaxHealth() / 100.0 + 1) * 5));
    }

    /**
     * 领域对抗开始：效果暂停 —— 解除领域内所有实体的静止（含被拉入/闯入者），
     * 重置停留计时与抵抗计时；对抗结束后由 onTick 重新施加静止。
     */
    @Override
    public void onClashStart(ServerPlayer player, BaseDomain opponent) {
        ServerLevel level = player.serverLevel();
        double r = radius;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                        center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5))) {
            if (entity.getUUID().equals(owner)) continue;
            if (entity.position().distanceToSqr(center) > r * r) continue;
            entity.removeEffect(ModEffects.STUN.get());
        }
        insideTicks.clear();
        resistUntil.clear();
    }

    private static void sendMessage(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
