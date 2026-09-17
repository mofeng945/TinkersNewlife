package com.mofengbaizhi.tinkersnewlife.content.curse.domain;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;

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
    /**
     * 展开瞬间"本该放墙却放不下"的位置（被生物占位）——留给每 tick 的补墙
     * （见 {@link #refillBarrierGaps}）：既不把生物闷死在墙里，也不在壳上留永久缺口。
     */
    private final java.util.Set<net.minecraft.core.BlockPos> barrierGaps = new java.util.LinkedHashSet<>();

    /** 领域对抗中的对手（对方咒力核心主人 UUID）；null = 未在对抗 */
    protected UUID clashOpponent = null;
    /** 对抗期间自身咒力消耗倍率（由对方输出/亲和决定），默认 1（无对抗） */
    protected double clashCostMultiplier = 1.0;
    /** 是否已提示过"咒力耗尽改耗灵魂能量"（每个领域实例只提示一次） */
    private boolean soulFallbackNotified = false;
    /** 领域展开时的 gameTime（用于防刷：展开不足 5 秒被破坏不掉碎片） */
    private long createdAtGameTime = -1;

    /** 领域所在维度（展开时由 DomainRegistry 注入；用于判定"玩家被他人领域包裹"） */
    private net.minecraft.resources.ResourceKey<Level> dimension = null;

    public void setDimension(net.minecraft.resources.ResourceKey<Level> dimension) {
        this.dimension = dimension;
    }

    public net.minecraft.resources.ResourceKey<Level> getDimension() {
        return dimension;
    }

    /** 领域通用抵抗：实体 → 抵抗截止时刻（服务器 tick）。抵抗期内本领域负面效果不生效 */
    private final Map<UUID, Long> resistUntil = new ConcurrentHashMap<>();

    // ============================================================
    //  ⭐ 空间查询缓存（性能）
    //  <p>领域效果每 tick 都要圈选球内生物：若每个调用点各写一遍
    //  {@code level.getEntitiesOfClass(LivingEntity.class, new AABB(...))}，同一个领域在同一 tick
    //  内会重复扫描同一片区域（弹射物导引更是<b>每颗子弹一次</b>），并把 AABB 反复分配。
    //  这里把"球外接盒 + 盒内生物表"按 gameTime 缓存一份，同一 tick 内所有调用点共用，
    //  跨 tick 自动失效——效果与逐点扫描完全一致，只是不再重复劳动。
    // ============================================================

    /** 缓存的球外接盒（随领域实例存活，跨 tick 复用同一对象） */
    private AABB cachedSphereBox;
    /** 缓存的盒内生物表（只读使用，勿改动） */
    private java.util.List<LivingEntity> cachedInsideBox = java.util.List.of();
    /** 缓存的"已排除同心戒同伴"的球内生物表（只读使用，勿改动） */
    private java.util.List<LivingEntity> cachedAffected = java.util.List.of();
    /** 缓存所属的 gameTime（±1 让同一次 tick 内的多次调用都命中） */
    private long cachedEntitiesTime = Long.MIN_VALUE;

    /**
     * 领域球体外接盒（缓存复用）。语义为"至少覆盖半径 {@link #radius} 的球"——
     * 各调用点原本的 ±1.5 缓冲不再需要（那只是为了防止实体正好卡在球面上被判漏），
     * 调用点仍保留自己的 {@code distanceToSqr(center) > r*r} 精确判定。
     */
    protected final AABB sphereBox() {
        AABB box = cachedSphereBox;
        if (box == null) {
            double r = radius;
            box = new AABB(center.x - r, center.y - r, center.z - r,
                    center.x + r, center.y + r, center.z + r);
            cachedSphereBox = box;
        }
        return box;
    }

    /**
     * 原始球内生物（<b>含</b>同心戒同伴）：仅供"生物占位"判定使用 ——
     * 若在这里把同伴剔掉，阻挡墙就会长在同伴身上 ✗。
     */
    private java.util.List<LivingEntity> rawInSphere(Level level) {
        long t = level.getGameTime();
        long dt = t - cachedEntitiesTime;
        if (dt < 0 || dt > 1) {
            cachedEntitiesTime = t;
            cachedInsideBox = level.getEntitiesOfClass(LivingEntity.class, sphereBox());
            cachedAffected = null;   // 同一 tick 内作废
        }
        return cachedInsideBox;
    }

    /**
     * 领域球外接盒内的生物（含玩家；不含领域主人判定，由调用点自行处理）。同一 tick 内共享缓存。
     *
     * <p>⭐ <b>已剔除"领域主人的同心戒同伴"</b>：戴着同一对同心戒的两人互相免疫对方的
     * <b>领域效果</b> —— 领域的所有效果循环都走本方法，因此不必逐个领域改 ✓
     * （占位判定走 {@link #rawInSphere}，同伴仍算占位，墙不会长在他身上 ✓）。
     */
    public final java.util.List<LivingEntity> entitiesInSphere(Level level) {
        java.util.List<LivingEntity> all = rawInSphere(level);
        if (cachedAffected != null) return cachedAffected;
        net.minecraft.world.entity.player.Player twin = ownerPlayer(level);
        if (twin == null) {
            cachedAffected = all;
            return all;
        }
        java.util.List<LivingEntity> out = new java.util.ArrayList<>(all.size());
        for (LivingEntity e : all) {
            if (!com.mofengbaizhi.tinkersnewlife.content.curse.TwinRingLink.arePaired(e, twin)) out.add(e);
        }
        cachedAffected = out;
        return out;
    }

    /** 领域主人的实体（用来判定"谁是主人的同心戒同伴"）；主人离线/不在本维度 = null */
    private net.minecraft.world.entity.player.Player ownerPlayer(Level level) {
        return owner == null ? null : level.getPlayerByUUID(owner);
    }

    /** 球壳体积内的"生物占位方块"：一次盒选 → 逐生物登记方块坐标（替代逐方块盒查） */
    private static java.util.Set<net.minecraft.core.BlockPos> occupancyOf(java.util.List<LivingEntity> entities) {
        java.util.Set<net.minecraft.core.BlockPos> occupied = new java.util.HashSet<>();
        for (LivingEntity e : entities) {
            int x0 = net.minecraft.util.Mth.floor(e.getBoundingBox().minX);
            int y0 = net.minecraft.util.Mth.floor(e.getBoundingBox().minY);
            int z0 = net.minecraft.util.Mth.floor(e.getBoundingBox().minZ);
            int x1 = net.minecraft.util.Mth.floor(e.getBoundingBox().maxX);
            int y1 = net.minecraft.util.Mth.floor(e.getBoundingBox().maxY);
            int z1 = net.minecraft.util.Mth.floor(e.getBoundingBox().maxZ);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        occupied.add(new net.minecraft.core.BlockPos(x, y, z));
                    }
                }
            }
        }
        return occupied;
    }

    protected BaseDomain(UUID owner, Vec3 center, int radius, double curseCostPerSecond) {
        this.owner = owner;
        this.center = center;
        // 配置缩放（每领域 radius/cost 系数，见 config/ModConfig；未覆写 configScaleId = 无缩放）
        double rScale = com.mofengbaizhi.tinkersnewlife.config.ModConfig.domainRadius(configScaleId());
        double cScale = com.mofengbaizhi.tinkersnewlife.config.ModConfig.domainCost(configScaleId());
        this.radius = rScale <= 0 ? radius : Math.max(1, (int) Math.ceil(radius * rScale));
        this.curseCostPerSecond = cScale <= 0 ? curseCostPerSecond : curseCostPerSecond * cScale;
    }

    /** 本领域 modifier path（config 系数键）；子类覆写返回自身领域 modifier 的 path（如 "zuosha_botu"）；默认 null=无缩放 */
    protected String configScaleId() {
        return null;
    }

    // ============================================================
    //  通用领域抵抗（提取自无量空处）
    // ============================================================

    /**
     * 实体首次出现在领域内时登记抵抗期（只登记一次，重复调用不重置）；
     * 返回 true 表示该实体当前仍处于"抵抗期"——领域负面效果（定身/斩击等）应跳过本 tick。
     * 所有领域统一接入：目标进入领域后先"抵抗一会"，期间不会被立刻定身/打击。
     */
    protected final boolean registerResistAndCheck(LivingEntity entity, long now) {
        resistUntil.computeIfAbsent(entity.getUUID(), id -> now + computeResistTicks(entity));
        return now < resistUntil.get(entity.getUUID());
    }

    /** 抵抗时长（tick）：玩家按咒力亲和 (亲和/100+1)×10，生物按血量上限 (血量/100+1)×10 */
    private long computeResistTicks(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player p) {
            return Math.max(1L, (long) ((CursePowerHelper.getCurseAffinity(p) / 100.0 + 1.0) * 10.0));
        }
        return Math.max(1L, (long) ((entity.getMaxHealth() / 100.0 + 1.0) * 10.0));
    }

    /** 领域关闭/对抗重置时清空抵抗登记（领域实例随之销毁，防御性清理） */
    protected final void clearResist() {
        resistUntil.clear();
    }

    /** 领域展开时由 DomainRegistry 记录展开时刻（gameTime） */
    public void markCreated(long gameTime) {
        this.createdAtGameTime = gameTime;
    }

    /** 领域已存在时长（tick）；未记录展开时刻返回 0 */
    public long getAgeTicks(long nowGameTime) {
        return createdAtGameTime < 0 ? 0 : nowGameTime - createdAtGameTime;
    }

    // ============================================================
    //  阻挡墙：生成隐形物理墙（任何生物进不来也出不去）
    // ============================================================

    /**
     * 生成隐形阻挡墙：以「方块中心在球内 + 六面邻里至少一个方块中心在球外」判定球壳。
     * <p>
     * ⭐ 为什么不能再用「|距离 - 半径| ≤ 0.5」那种写法：那个判定在<b>极点</b>会跳层——
     * 半径在球顶/球底变化极快（r=20 时 y=±19 层的水平半径已经是 6.2，下一层就变 0），
     * 于是球顶只剩"一个方块 + 6 格外一圈环"，中间一圈全是空洞，可以从上下两处钻出去/
     * 射出去（实测现象："领域上下还是有空洞，没有被外壳完全封闭"）。
     * <p>
     * 换成"在球内、且贴着球外"的判定后，壳是逐层封闭的阶梯：每一层只要中心在球内、
     * 且上一格中心在球外，就必须放墙 → 顶点自然被封成实心圆盘，整球无孔。
     */
    protected final void buildBarrier(ServerLevel level) {
        barrierPositions.clear();
        barrierGaps.clear();
        // 一次盒选拿到球壳体积内的生物占位（原先每个壳块各查一次实体）
        // ⚠ 用 rawInSphere：同伴要算占位，否则墙会长在他身上
        java.util.Set<net.minecraft.core.BlockPos> occupied = occupancyOf(rawInSphere(level));
        int r = radius;
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);
        double rSq = (double) r * r;
        int lim = r + 1;
        for (int y = -lim; y <= lim; y++) {
            for (int x = -lim; x <= lim; x++) {
                for (int z = -lim; z <= lim; z++) {
                    if (!isShellBlock(cx, cy, cz, x, y, z, rSq)) continue;
                    placeBarrier(level, new net.minecraft.core.BlockPos(cx + x, cy + y, cz + z), occupied);
                }
            }
        }
        cachedEntitiesTime = Long.MIN_VALUE; // 建墙期间生物可能被传送 → 作废本 tick 缓存
    }

    /** 方块中心到球心的距离平方（方块中心 = floor(center) + 偏移 + 0.5） */
    private double centerDistSq(int cx, int cy, int cz, double ox, double oy, double oz) {
        double dx = cx + ox - center.x;
        double dy = cy + oy - center.y;
        double dz = cz + oz - center.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 该方块是否为壳块：方块中心在球内，且六面邻中存在方块中心在球外的邻居 */
    private boolean isShellBlock(int cx, int cy, int cz, int x, int y, int z, double rSq) {
        if (centerDistSq(cx, cy, cz, x + 0.5, y + 0.5, z + 0.5) > rSq) return false;
        return centerDistSq(cx, cy, cz, x + 1.5, y + 0.5, z + 0.5) > rSq
                || centerDistSq(cx, cy, cz, x - 0.5, y + 0.5, z + 0.5) > rSq
                || centerDistSq(cx, cy, cz, x + 0.5, y + 1.5, z + 0.5) > rSq
                || centerDistSq(cx, cy, cz, x + 0.5, y - 0.5, z + 0.5) > rSq
                || centerDistSq(cx, cy, cz, x + 0.5, y + 0.5, z + 1.5) > rSq
                || centerDistSq(cx, cy, cz, x + 0.5, y + 0.5, z - 0.5) > rSq;
    }

    /** 放一块墙：已有墙块直接登记；非空气（实心地形/水）跳过；生物占位则记账等补墙 */
    private void placeBarrier(ServerLevel level, net.minecraft.core.BlockPos pos,
                              java.util.Set<net.minecraft.core.BlockPos> occupied) {
        var state = level.getBlockState(pos);
        if (state.is(com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get())) {
            barrierPositions.add(pos);
            return;
        }
        if (!state.isAir()) return;
        // 避免在生物站立的方块上放置（防窒息）：记入待补清单，生物走开后由每 tick 补墙补上
        if (occupied.contains(pos)) {
            barrierGaps.add(pos);
            return;
        }
        level.setBlock(pos, com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get().defaultBlockState(), 2);
        barrierPositions.add(pos);
    }

    /**
     * 补墙（每 5 tick 与困锁一起调用）：把展开瞬间因生物占位而跳过的壳块补上，
     * 保证外壳最终一定是封闭的（否则那几格就是永久缺口）。
     */
    public final void refillBarrierGaps(ServerLevel level) {
        if (barrierGaps.isEmpty()) return;
        // 合并中：占位按并集盒查（外壳是并集的 ✓）
        java.util.Set<net.minecraft.core.BlockPos> occupied = occupancyOf(
                allyCenter == null ? rawInSphere(level)
                        : level.getEntitiesOfClass(LivingEntity.class, unionBox()));
        java.util.Iterator<net.minecraft.core.BlockPos> it = barrierGaps.iterator();
        while (it.hasNext()) {
            net.minecraft.core.BlockPos pos = it.next();
            var state = level.getBlockState(pos);
            if (state.is(com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get())) {
                barrierPositions.add(pos);
                it.remove();
                continue;
            }
            if (!state.isAir()) {
                it.remove();
                continue;
            }
            if (occupied.contains(pos)) continue;
            level.setBlock(pos, com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get().defaultBlockState(), 2);
            barrierPositions.add(pos);
            it.remove();
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
        barrierGaps.clear();
    }

    /** 当前阻挡墙位置列表（供天逆鉾等咒具破坏领域时统计碎片掉落） */
    public java.util.List<net.minecraft.core.BlockPos> getBarrierPositions() {
        return new java.util.ArrayList<>(barrierPositions);
    }

    /** 该位置是否属于本领域的阻挡墙 */
    public boolean containsBarrier(net.minecraft.core.BlockPos pos) {
        return barrierPositions.contains(pos);
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
        // ⭐ "待补墙"里落在合并区的也要一起剔除：敌对对抗期间不补墙，所以旧代码没暴露这个问题；
        //    但同心戒的**友好合并照常补墙**（效果不停摆 → 每 5 tick 仍会 refill），
        //    不剔除就会把墙又砌回合并区、把打通的空间重新堵上 ✗
        barrierGaps.removeIf(pos -> Vec3.atCenterOf(pos).distanceToSqr(otherCenter) <= otherRadius * otherRadius);
        return removed;
    }

    // ============================================================
    //  领域对抗（两个领域球体相交时触发，见 DomainRegistry）
    // ============================================================

    /** ⭐ 多方对抗：本领域当前的所有对手（两两相交 + 通过中间领域连通进来的） */
    protected final java.util.Set<UUID> clashOpponents = new java.util.LinkedHashSet<>();

    public boolean isClashing() { return !clashOpponents.isEmpty(); }

    /** 主要对手（胜出/拉人这类只需要一个目标的旧逻辑用）：取最后加入的那个 */
    public UUID getClashOpponent() { return clashOpponent; }

    /** 全部对手（多方混战） */
    public java.util.Set<UUID> getClashOpponents() {
        return java.util.Collections.unmodifiableSet(clashOpponents);
    }

    public boolean isClashingWith(UUID ownerId) { return clashOpponents.contains(ownerId); }

    public double getClashCostMultiplier() { return clashCostMultiplier; }

    /** 进入对抗：记录对手并设置本领域消耗倍率（倍率由对方输出/亲和决定） */
    public void setClash(UUID opponentOwner, double costMultiplier) {
        addClash(opponentOwner, costMultiplier);
    }

    /** 加入一个对手（多方混战可多次调用）；消耗倍率取最"贵"的对手 */
    public void addClash(UUID opponentOwner, double costMultiplier) {
        if (opponentOwner == null) return;
        boolean first = clashOpponents.isEmpty();
        this.clashOpponents.add(opponentOwner);
        this.clashOpponent = opponentOwner;
        this.clashCostMultiplier = first ? costMultiplier
                : Math.max(this.clashCostMultiplier, costMultiplier);
    }

    /** 移除一个对手（其领域已关闭）；没有对手了就自动结束对抗 */
    public void removeClash(UUID opponentOwner) {
        this.clashOpponents.remove(opponentOwner);
        if (clashOpponents.isEmpty()) {
            this.clashOpponent = null;
            this.clashCostMultiplier = 1.0;
        } else if (opponentOwner != null && opponentOwner.equals(this.clashOpponent)) {
            this.clashOpponent = clashOpponents.iterator().next();
        }
    }

    /** 对抗结束：清除对抗状态（领域效果恢复） */
    public void clearClash() {
        this.clashOpponents.clear();
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

    // ============================================================
    //  ⭐ 同心戒同伴：领域**友好合并**（与"对抗"是两套东西，别混）
    //
    //  对抗 = 敌对：空间合并但**双方效果停摆**、消耗加剧、分胜负。
    //  友好合并 = 同伴：空间同样打通，但**两套效果都照常生效** ——
    //  领域内其他目标同时吃两个领域的效果，两位主人则互相免疫（{@link #entitiesInSphere} 已剔除同伴）✓
    //  所以这里**绝不能**借用 clashOpponents：一旦进了那张表，DomainRegistry 的
    //  `if (clashing) continue;` 就会把效果停掉、败者拉入等逻辑也会跟着跑 ✗。
    // ============================================================

    /** 同伴（同心戒同对）的领域主人；null = 未合并 */
    private UUID mergedAlly = null;
    /** 同伴领域球心（合并时缓存，避免每 tick 反查注册表） */
    private Vec3 allyCenter = null;
    /** 同伴领域半径 */
    private double allyRadius = 0;

    /**
     * 合并后由谁负责造墙（host）。
     * <p>⭐ 合并区**共用一圈"并集外壳"**：只有 host 建墙（外壳 = 并集的边界 ✓ 不规则 ✓），
     * guest 把自己的球壳拆掉 ✓ —— 于是合并区内部没有墙、外圈是一整圈可被天逆鉾砸碎的墙 ✓。
     * （旧做法是"两人各建球壳 + 互相拆掉嵌入对方球内的部分"：两球几乎重合时整圈墙都会被拆光，
     * 出现"没有墙、无处可敲、只能被别的领域覆盖掉"的开放领域 ✗ —— 用户实测。）
     */
    private boolean wallHost = true;

    /** 是否处于"同心戒同伴领域友好合并"状态 */
    public boolean isMerged() { return mergedAlly != null; }

    /** 是否与指定领域主人友好合并 */
    public boolean isMergedWith(UUID ownerId) { return ownerId != null && ownerId.equals(mergedAlly); }

    /** 友好合并的同伴领域主人（未合并 = null） */
    public UUID getMergedAlly() { return mergedAlly; }

    /** 建立友好合并（记录同伴球体；"拆掉互相嵌入的那部分墙"由 DomainRegistry 做） */
    public void setMergedAlly(UUID ownerId, Vec3 center, double radius) {
        this.mergedAlly = ownerId;
        this.allyCenter = center;
        this.allyRadius = radius;
    }

    /**
     * 解除友好合并（同伴领域关闭 / 主人下线 / 两人不再成对）。
     * <p>同时清掉"实体进出追踪"：解除瞬间正站在同伴球内（其实在本领域外）的实体会被当作
     * "首次出现"，从而不会被"试图离开"的逻辑一把拽回球内 ✗→✓。
     */
    public void clearMergedAlly() {
        this.mergedAlly = null;
        this.allyCenter = null;
        this.allyRadius = 0;
        this.wallHost = true;   // 拆伙后自己重新负责造墙 ✓
        entityInside.clear();
    }

    /** 合并后是否由本领域负责造墙（见 {@link #wallHost}） */
    public boolean isWallHost() { return wallHost; }

    /** 设定合并后的造墙责任（由 DomainRegistry 在建立合并时指定 host/guest） */
    public void setWallHost(boolean host) { this.wallHost = host; }

    /**
     * 重建外壳（口径自适应）：
     * <ul>
     *   <li>不是 host（合并中的 guest）→ 只把自己的墙拆掉，不建 ✓；</li>
     *   <li>合并中且是 host → 建**并集外壳**（不规则大领域 ✓）；</li>
     *   <li>其余（单体领域）→ 建自己的球壳 ✓。</li>
     * </ul>
     * ⚠ 先 {@link #removeBarrier} 再建：{@code buildBarrier} 只清列表、不拆方块，
     * 旧墙不拆会留在原地变成"内部墙" ✗。
     */
    public final void rebuildBarrier(ServerLevel level) {
        removeBarrier(level);
        if (!wallHost) return;
        if (isMerged()) {
            buildMergedBarrier(level);
        } else {
            buildBarrier(level);
        }
    }

    /**
     * 点（世界坐标）是否在"共享空间"里 = 自己的球 ∪ 同伴的球 ✓。
     * <p>作为并集外壳的成员判定。
     */
    private boolean inUnion(double x, double y, double z) {
        double dx = x - center.x, dy = y - center.y, dz = z - center.z;
        if (dx * dx + dy * dy + dz * dz <= (double) radius * radius) return true;
        if (allyCenter == null) return false;
        double ax = x - allyCenter.x, ay = y - allyCenter.y, az = z - allyCenter.z;
        return ax * ax + ay * ay + az * az <= allyRadius * allyRadius;
    }

    /** 该方块是不是"并集外壳"：方块中心在并集内，且六邻中存在方块中心在并集外的邻居 ✓ */
    private boolean isUnionShellBlock(int x, int y, int z) {
        if (!inUnion(x + 0.5, y + 0.5, z + 0.5)) return false;
        return !inUnion(x + 1.5, y + 0.5, z + 0.5)
                || !inUnion(x - 0.5, y + 0.5, z + 0.5)
                || !inUnion(x + 0.5, y + 1.5, z + 0.5)
                || !inUnion(x + 0.5, y - 0.5, z + 0.5)
                || !inUnion(x + 0.5, y + 0.5, z + 1.5)
                || !inUnion(x + 0.5, y + 0.5, z - 0.5);
    }

    /**
     * 造墙/补墙用的生物占位查询盒：合并中 = 并集包围盒 ✓，否则 = 自己的球盒 ✓。
     * （合并后外壳属于"并集"，占位也必须按并集查，否则同伴那半边的墙会砌进生物身上 ✗）
     */
    private AABB unionBox() {
        if (allyCenter == null) return sphereBox();
        return new AABB(
                Math.min(center.x - radius, allyCenter.x - allyRadius),
                Math.min(center.y - radius, allyCenter.y - allyRadius),
                Math.min(center.z - radius, allyCenter.z - allyRadius),
                Math.max(center.x + radius, allyCenter.x + allyRadius),
                Math.max(center.y + radius, allyCenter.y + allyRadius),
                Math.max(center.z + radius, allyCenter.z + allyRadius));
    }

    /**
     * 同心戒友好合并：<b>并集外壳</b>（两个球的并集的边界 = 一个不规则的大领域 ✓）。
     * <p>只在 {@link #isWallHost()} 时调用；合并区内部不产生任何墙 ✓。
     */
    public final void buildMergedBarrier(ServerLevel level) {
        barrierPositions.clear();
        barrierGaps.clear();
        if (allyCenter == null) {   // 不该发生（同伴球体已缓存）→ 退回单球
            buildBarrier(level);
            return;
        }
        // 并集包围盒内的生物占位（占位处不砌墙，免得把人闷在墙里 ✓）
        AABB box = unionBox();
        java.util.Set<net.minecraft.core.BlockPos> occupied = occupancyOf(
                level.getEntitiesOfClass(LivingEntity.class, box));
        int x0 = net.minecraft.util.Mth.floor(box.minX) - 1;
        int x1 = net.minecraft.util.Mth.floor(box.maxX) + 1;
        int y0 = net.minecraft.util.Mth.floor(box.minY) - 1;
        int y1 = net.minecraft.util.Mth.floor(box.maxY) + 1;
        int z0 = net.minecraft.util.Mth.floor(box.minZ) - 1;
        int z1 = net.minecraft.util.Mth.floor(box.maxZ) + 1;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (!isUnionShellBlock(x, y, z)) continue;
                    placeBarrier(level, new net.minecraft.core.BlockPos(x, y, z), occupied);
                }
            }
        }
        cachedEntitiesTime = Long.MIN_VALUE;   // 建墙期间生物可能被传送 → 作废本 tick 缓存
    }

    /**
     * 该点是否落在"合并后的共享空间"内（自己的球 ∪ 同伴的球）。
     * <p>困锁（{@link #clampEntities}）的"内/外"判定要用它：否则两个领域会各自把
     * 对方球内的实体往外推，共享空间实际只剩"自己的球减去同伴的球" ✗。
     */
    private boolean inMergedSpace(Vec3 pos) {
        if (pos.distanceToSqr(center) <= (double) radius * radius) return true;
        return allyCenter != null && pos.distanceToSqr(allyCenter) <= allyRadius * allyRadius;
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
        int result = CursePowerHelper.payCurseWithSoulFallback(player, cost);
        if (result >= 0) {
            // 首次进入灵魂兜底时提示一次（每个领域实例）
            if (result == 1 && !soulFallbackNotified) {
                soulFallbackNotified = true;
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.soul_fallback"), true);
            }
            return true;
        }
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.debug(
                "[TinkersNewlife] 咒力与灵魂能量均不足，领域关闭 (tick消耗={}, 当前咒力总量={})",
                cost, CursePowerHelper.getTotalCurse(player));
        return false;
    }

    /**
     * 双向封锁：不区分创造/玩家/主人，任何生物（含领域主人）一律
     * - 领域内生物试图离开 → 拉回球面内侧（出不去）
     * - 领域外生物试图进入 → 挡在球面外侧并反向推回（进不来）
     */
    protected final void clampEntities(Level level) {
        double r = radius;
        AABB box = sphereBox();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            seen.add(entity.getUUID());

            Vec3 delta = entity.position().subtract(center);
            double dist = delta.length();
            boolean inside = inMergedSpace(entity.position());   // ⭐ 合并后按"共享空间"判定（见 inMergedSpace）
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
