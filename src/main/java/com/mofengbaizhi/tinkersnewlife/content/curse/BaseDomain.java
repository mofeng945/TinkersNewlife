package com.mofengbaizhi.tinkersnewlife.content.curse;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 实体进出状态：实体 UUID → 上一检测 tick 是否在领域内（用于判定"试图进入/试图离开"） */
    private final Map<UUID, Boolean> entityInside = new ConcurrentHashMap<>();
    /** 阻挡墙方块位置（关闭领域时移除） */
    private final java.util.List<net.minecraft.core.BlockPos> barrierPositions = new java.util.ArrayList<>();

    /** 领域对抗中的对手（对方咒力核心主人 UUID）；null = 未在对抗 */
    protected UUID clashOpponent = null;
    /** 对抗期间自身咒力消耗倍率（由对方输出/亲和决定），默认 1（无对抗） */
    protected double clashCostMultiplier = 1.0;
    /** 是否已提示过"咒力耗尽改耗灵魂能量"（每个领域实例只提示一次） */
    private boolean soulFallbackNotified = false;

    protected BaseDomain(UUID owner, Vec3 center, int radius, double curseCostPerSecond) {
        this.owner = owner;
        this.center = center;
        this.radius = radius;
        this.curseCostPerSecond = curseCostPerSecond;
    }

    // ============================================================
    //  阻挡墙：生成隐形物理墙（任何生物进不来也出不去）
    // ============================================================

    /** 生成隐形阻挡墙：在球壳表面放置领域阻挡方块（1 格厚球壳） */
    protected final void buildBarrier(ServerLevel level) {
        barrierPositions.clear();
        int r = radius;
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);
        for (int y = -r; y <= r; y++) {
            double rH = Math.sqrt(Math.max(0, r * r - y * y));
            int rhi = (int) Math.ceil(rH);
            for (int x = -rhi; x <= rhi; x++) {
                for (int z = -rhi; z <= rhi; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d < r - 0.5 || d > r + 0.5) continue;
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(cx + x, cy + y, cz + z);
                    var state = level.getBlockState(pos);
                    // ⭐ 已存在的本领域墙块（如对抗结束后重建）也要补记，否则关闭时无法移除
                    if (state.is(com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get())) {
                        barrierPositions.add(pos);
                        continue;
                    }
                    if (!state.isAir()) continue;
                    // 避免在生物站立的方块上放置（防窒息），留出的缺口由每 tick 位置钳制兜底
                    if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                            new AABB(pos)).isEmpty()) continue;
                    level.setBlock(pos, com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get().defaultBlockState(), 2);
                    barrierPositions.add(pos);
                }
            }
        }
    }

    /** 移除阻挡墙 */
    protected final void removeBarrier(ServerLevel level) {
        for (net.minecraft.core.BlockPos pos : barrierPositions) {
            if (level.getBlockState(pos).is(com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get())) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            }
        }
        barrierPositions.clear();
    }

    /**
     * 领域对抗：移除本领域阻挡墙中落入对方领域球体内的部分（打通两个领域空间）。
     * 返回移除的方块数量。
     */
    protected final int removeBarrierOverlap(ServerLevel level, Vec3 otherCenter, double otherRadius) {
        int removed = 0;
        java.util.Iterator<net.minecraft.core.BlockPos> it = barrierPositions.iterator();
        while (it.hasNext()) {
            net.minecraft.core.BlockPos pos = it.next();
            if (Vec3.atCenterOf(pos).distanceToSqr(otherCenter) <= otherRadius * otherRadius) {
                if (level.getBlockState(pos).is(com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get())) {
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                }
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    // ============================================================
    //  领域对抗（两个领域球体相交时触发，见 DomainRegistry）
    // ============================================================

    public boolean isClashing() { return clashOpponent != null; }

    public UUID getClashOpponent() { return clashOpponent; }

    public double getClashCostMultiplier() { return clashCostMultiplier; }

    /** 进入对抗：记录对手并设置本领域消耗倍率（倍率由对方输出/亲和决定） */
    public void setClash(UUID opponentOwner, double costMultiplier) {
        this.clashOpponent = opponentOwner;
        this.clashCostMultiplier = costMultiplier;
    }

    /** 对抗结束：清除对抗状态（领域效果恢复） */
    public void clearClash() {
        this.clashOpponent = null;
        this.clashCostMultiplier = 1.0;
    }

    /** 对抗开始钩子（子类可暂停自身效果，如无量空处解除静止） */
    public void onClashStart(ServerPlayer player, BaseDomain opponent) {}

    /** 对抗结束钩子（本领域胜出，领域效果恢复） */
    public void onClashEnd(ServerPlayer player, BaseDomain opponent) {}

    /** 对抗结束时把败者拉入本领域：球心附近的可站安全点 */
    public Vec3 getClashPullTarget(ServerLevel level) {
        return findSafeSpot(level, new Vec3(0, 0, 0), radius * 0.6);
    }

    public UUID getOwner() { return owner; }
    public Vec3 getCenter() { return center; }
    public int getRadius() { return radius; }
    public double getCurseCostPerSecond() { return curseCostPerSecond; }

    // ============================================================
    //  生命周期钩子（子类覆写）
    // ============================================================

    /** 领域名称翻译键（展开时以标题形式展示给领域内所有玩家） */
    public abstract String getDomainNameKey();

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

    /** 每 tick 消耗咒力，返回 false 表示咒力耗尽（应关闭领域）；咒力无限状态下不消耗。
     *  领域对抗期间消耗按 clashCostMultiplier 倍率放大（对方输出/亲和越高，消耗越猛）。
     *  ⭐ 咒力耗尽时自动改为消耗诡厄巫法（Goety）灵魂能量兜底：咒力:灵魂能量 = 1:3，
     *  即 3 点灵魂能量相当于 1 点咒力；灵魂能量也不足时才判定领域关闭。 */
    protected final boolean spendCurse(ServerPlayer player) {
        if (CursePowerHelper.isCurseInfinite(player)) return true;
        double cost = curseCostPerSecond * clashCostMultiplier / 20.0;
        double curse = CursePowerHelper.getCurse(player);
        if (curse >= cost) {
            CursePowerHelper.spendCurse(player, cost);
            return true;
        }
        // 咒力不足本 tick 消耗：先用光剩余咒力，差额由灵魂能量按 1:3 补足
        double deficit = cost - curse;
        CursePowerHelper.spendCurse(player, curse); // 咒力清零
        int soulsNeeded = (int) Math.ceil(deficit * 3.0);
        int souls = com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge.getSouls(player);
        if (souls < soulsNeeded) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[TinkersNewlife] 灵魂兜底失败: 咒力={}, tick消耗={}, 差额={}, 所需灵魂={}, 实际灵魂={}",
                    curse, cost, deficit, soulsNeeded, souls);
            return false; // 灵魂能量也不足 → 领域关闭
        }
        if (!com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge.decreaseSouls(player, soulsNeeded)) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "[TinkersNewlife] 灵魂扣减失败: 所需={}, 当时持有={}", soulsNeeded, souls);
            return false;
        }
        if (!soulFallbackNotified) {
            soulFallbackNotified = true;
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.tinkersnewlife.soul_fallback"), true);
        }
        return true;
    }

    /**
     * 双向封锁：不区分创造/玩家/主人，任何生物（含领域主人）一律
     * - 领域内生物试图离开 → 拉回球面内侧（出不去）
     * - 领域外生物试图进入 → 挡在球面外侧并反向推回（进不来）
     */
    protected final void clampEntities(Level level) {
        double r = radius;
        AABB box = new AABB(
                center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5);
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            seen.add(entity.getUUID());

            Vec3 delta = entity.position().subtract(center);
            double dist = delta.length();
            boolean inside = dist <= r;
            Boolean prevInside = entityInside.put(entity.getUUID(), inside);
            if (prevInside == null) continue; // 首次出现：按当前位置定性（在内部即被困，外部即保持在外）

            Vec3 dir = dist < 1e-4 ? new Vec3(0, 0, 0) : delta.normalize();
            if (inside && !prevInside) {
                // 外界生物试图进入：挡在球面外侧（r+0.4）并反向推回，记录为"外界"
                edgeTo(level, entity, dir, r + 0.4, true);
                entityInside.put(entity.getUUID(), false);
            } else if (!inside && prevInside) {
                // 内部生物试图离开：拉回球面内侧（r-0.35），记录为"内部"
                edgeTo(level, entity, dir, r - 0.35, false);
                entityInside.put(entity.getUUID(), true);
            } else {
                entityInside.put(entity.getUUID(), inside);
            }
        }
        // 清理已离开追踪范围的实体记录
        entityInside.keySet().removeIf(id -> !seen.contains(id));
    }

    /** 把实体传送到距球心 edge 处（防卡墙 + 水平缩放保持距离），并按需处理朝内/朝外速度 */
    private void edgeTo(Level level, LivingEntity entity, Vec3 dir, double edge, boolean pushOut) {
        Vec3 target = findSafeSpot(level, dir, edge);
        double dy = target.y - center.y;
        double hRemain = Math.sqrt(Math.max(0, edge * edge - dy * dy));
        double h = Math.hypot(dir.x, dir.z);
        if (h > 1e-4 && hRemain < h) {
            double scale = hRemain / h;
            target = new Vec3(center.x + dir.x * scale, target.y, center.z + dir.z * scale);
        }
        entity.teleportTo(target.x, target.y, target.z);

        Vec3 motion = entity.getDeltaMovement();
        double along = motion.dot(dir);
        if (pushOut) {
            // 外界生物：朝内速度分量反射为朝外（推回外界）
            if (along < 0) {
                entity.setDeltaMovement(motion.subtract(dir.scale(2.0 * along)));
            }
        } else {
            // 内部生物：消除朝外速度（防止立刻被推/冲出）
            if (along > 0) {
                entity.setDeltaMovement(motion.subtract(dir.scale(along)));
            }
        }
        entity.fallDistance = 0;
    }

    /** 在球面候选点附近寻找安全落点（优先落在地面实心方块上，避免被悬在半空与重力打架） */
    private Vec3 findSafeSpot(Level level, Vec3 dir, double edge) {
        double tx = center.x + dir.x * edge;
        double ty = center.y + dir.y * edge;
        double tz = center.z + dir.z * edge;
        BlockPos pos = BlockPos.containing(tx, ty, tz);
        // 候选点本身可站（空气 + 脚下实心）→ 直接用
        if (isSafe(level, pos) && isGroundBelow(level, pos)) return new Vec3(tx, ty, tz);
        // 向上找 6 格内的可站点
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos up = pos.above(dy);
            if (isSafe(level, up) && isGroundBelow(level, up)) return new Vec3(tx, ty + dy, tz);
        }
        // 向下找 24 格内落在地面（防止钳制把生物悬空导致"飘天上"）
        for (int dy = 1; dy <= 24; dy++) {
            BlockPos down = pos.below(dy);
            if (isSafe(level, down) && isGroundBelow(level, down)) return new Vec3(tx, ty - dy, tz);
        }
        // 兜底：原候选点
        return new Vec3(tx, ty, tz);
    }

    private static boolean isSafe(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static boolean isGroundBelow(Level level, BlockPos pos) {
        return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }
}
