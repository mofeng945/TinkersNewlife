package com.mofengbaizhi.tinkersnewlife.content.curse;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 领域通用基类
 * <p>
 * 每个具体领域（坐杀搏徒等）继承本类并覆写生命周期钩子即可：
 * <ul>
 *   <li>{@link #isValid(ServerPlayer)}：领域能否维持（如是否仍佩戴核心/特性）</li>
 *   <li>{@link #onOpen(ServerPlayer)}：展开时逻辑（如提示消息）</li>
 *   <li>{@link #onTick(ServerPlayer, long)}：每 tick 扩展逻辑（如坐杀搏徒的抽奖）</li>
 *   <li>{@link #onClose(ServerPlayer, String)}：关闭时逻辑</li>
 * </ul>
 * 通用外壳（咒力消耗、生物困锁、黑色空心球视觉）由本类提供，无需子类重复实现。
 * 构造参数即配置：球心、半径、每秒咒力消耗等。
 */
public abstract class BaseDomain {

    /** 领域主人 */
    protected final UUID owner;
    /** 领域球心（通常为展开瞬间玩家位置） */
    protected final Vec3 center;
    /** 领域半径（格） */
    protected final int radius;
    /** 每秒咒力消耗量（可配置） */
    protected final double curseCostPerSecond;

    protected BaseDomain(UUID owner, Vec3 center, int radius, double curseCostPerSecond) {
        this.owner = owner;
        this.center = center;
        this.radius = radius;
        this.curseCostPerSecond = curseCostPerSecond;
    }

    public UUID getOwner() { return owner; }
    public Vec3 getCenter() { return center; }
    public int getRadius() { return radius; }
    public double getCurseCostPerSecond() { return curseCostPerSecond; }

    // ============================================================
    //  生命周期钩子（子类覆写）
    // ============================================================

    /** 领域是否可维持（如咒力核心被取下/特性丢失则返回 false，领域被破坏） */
    public abstract boolean isValid(ServerPlayer player);

    /** 展开时调用 */
    public void onOpen(ServerPlayer player) {}

    /** 每 tick 调用（子类扩展逻辑，如抽奖计时） */
    public void onTick(ServerPlayer player, long now) {}

    /** 关闭时调用 */
    public void onClose(ServerPlayer player, String messageKey) {}

    // ============================================================
    //  通用外壳
    // ============================================================

    /** 每 tick 消耗咒力，返回 false 表示咒力耗尽（应关闭领域）；咒力无限状态下不消耗 */
    protected final boolean spendCurse(ServerPlayer player) {
        if (!CursePowerHelper.isCurseInfinite(player)) {
            CursePowerHelper.spendCurse(player, curseCostPerSecond / 20.0);
            if (CursePowerHelper.getCurse(player) <= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 困锁领域内生物：被笼罩的生物无法离开领域范围
     * （球面边界拉回 + 防卡墙 + 消除外冲速度），创造/旁观玩家豁免。
     */
    protected final void clampEntities(Level level) {
        double r = radius;
        AABB box = new AABB(
                center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getUUID().equals(owner)) continue;
            if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) continue;

            Vec3 delta = entity.position().subtract(center);
            double dist = delta.length();
            if (dist <= r) continue;

            Vec3 dir = dist < 1e-4 ? new Vec3(0, 0, 0) : delta.normalize();
            double edge = r - 0.35;
            Vec3 target = findSafeSpot(level, dir, edge);

            // 竖直偏移后重新水平缩放，让落点始终在球面边界上
            double dy = target.y - center.y;
            double hRemain = Math.sqrt(Math.max(0, r * r - dy * dy));
            double h = Math.hypot(dir.x, dir.z);
            if (h > 1e-4 && hRemain < h) {
                double scale = hRemain / h;
                target = new Vec3(center.x + dir.x * scale, target.y, center.z + dir.z * scale);
            }

            entity.teleportTo(target.x, target.y, target.z);

            // 消除朝外速度，防止立刻被推/冲出
            Vec3 motion = entity.getDeltaMovement();
            double outward = motion.dot(dir);
            if (outward > 0) {
                entity.setDeltaMovement(motion.subtract(dir.scale(outward)));
            }
            entity.fallDistance = 0;
        }
    }

    /** 在球面候选点附近寻找不卡进方块的安全落点（上下最多偏移 6 格） */
    private Vec3 findSafeSpot(Level level, Vec3 dir, double edge) {
        double tx = center.x + dir.x * edge;
        double ty = center.y + dir.y * edge;
        double tz = center.z + dir.z * edge;
        BlockPos pos = BlockPos.containing(tx, ty, tz);
        if (isSafe(level, pos)) return new Vec3(tx, ty, tz);
        for (int dy = 1; dy <= 6; dy++) {
            if (isSafe(level, pos.above(dy))) return new Vec3(tx, ty + dy, tz);
        }
        for (int dy = 1; dy <= 6; dy++) {
            if (isSafe(level, pos.below(dy))) return new Vec3(tx, ty - dy, tz);
        }
        return new Vec3(tx, ty, tz);
    }

    private static boolean isSafe(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }
}
