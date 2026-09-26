package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 领域·荡蕴平线（陀艮式水域领域）
 * <ul>
 *   <li>展开瞬间把领域内部（半径 r-1 球体）所有空气灌成水并记录；
 *       每 2 秒补注一次（水被破坏/蒸发则补回）。</li>
 *   <li>施术者不会窒息（持续水呼吸）；施术者的式神/咒灵/守护等同队目标同样获得水呼吸；
 *       新阴流三技巧可抵挡本领域溺水环境（带技巧且咒力足够的玩家同样获得水呼吸，
 *       与胎藏遍野一致——仅伏诛赐死不可挡；溺尸攻击仍照常，技巧不挡召唤物）。</li>
 *   <li>领域内持续维持一批<b>以施术者为主人的溺尸</b>（数量 = 6 + 半径/2，上限 24）：
 *       溺尸会<b>主动索敌并攻击领域内所有非施术者阵营目标</b>（游过去近战），
 *       死亡后自动补充；约 1/5 概率手持三叉戟。</li>
 *   <li>领域结束（手动/咒力耗尽/被破坏/死亡登出）一切回归原样：<b>本领域注的水</b>消失、溺尸全部消散。
 *       ⚠ <b>§690 用户口径</b>：「清除水逻辑<b>不要清除原本已有的水</b>」——
 *       首 tick 注水前会把「球体 ＋ 8 格外扩」内**原本就存在**的水/含水方块记下来
 *       （{@link #preExistingWater}），关闭清理时一律跳过 ✓
 *       ⇒ 领域旁边的池塘／玩家自己放的水／海面都<b>不会</b>被误清 ✗。</li>
 * </ul>
 */
public class DangYunPingXianDomain extends BaseDomain {
    /** config: 领域 modifier path（系数键） */
    @Override
    protected String configScaleId() { return "dang_yun_ping_xian"; }

    /** 溺尸主人标记（本领域溺尸的 PersistentData） */
    private static final String KEY_DROWNED_OWNER = "tnl_dangyun_owner";

    /**
     * 该实体是不是本领域召唤的溺尸。
     * <p>召唤时会给它打上 {@link #KEY_DROWNED_OWNER} 持久标记；掉落/经验抑制
     * （{@code SummonDropSuppressor}）用它区分"领域召的"与"野生的"溺尸。
     */
    public static boolean isSummonedDrowned(net.minecraft.world.entity.Entity entity) {
        return entity instanceof Drowned && entity.getPersistentData().contains(KEY_DROWNED_OWNER);
    }

    /** 领域内部注水半径（留出球壳内缘） */
    private final double fillRadius;
    /** 被本领域灌成水的位置（结束时复原为空气） */
    private final Set<BlockPos> waterBlocks = new HashSet<>();

    /**
     * <b>§690 用户口径</b>：「荡蕴平线的清除水逻辑<b>不要清除原本已有的水</b>」。
     *
     * <p>首 tick 注水<b>之前</b>，先把「领域球体 ＋ 8 格外扩」范围内**原本就存在**的
     * 水方块／气泡柱／含水方块（{@code WATERLOGGED=true}）记下来 ✓
     * ⇒ 关闭时的 {@link #clearWater}／{@link #dryWaterlogged} 遇到它们<b>一律跳过</b> ✗。
     *
     * <p>⚠ 为什么要这么记：注水只往<b>空气</b>里灌 ✓（见 {@link #fillWater}），
     * 但关闭清理是从记录点做 <b>BFS</b> 把"连通的水"一并删掉 ✗
     * —— 领域旁边要是有个池塘、玩家自己放的水、或者一片海，就会被连着清空 ✗。
     * 记下原有水体后，BFS 到它们边上就停 ✓（范围与清理 BFS 上限完全一致 ⇒ 不漏 ✓）。
     */
    private final Set<BlockPos> preExistingWater = new HashSet<>();
    /** 领域溺尸实体 id */
    private final List<Integer> drownedIds = new ArrayList<>();
    /** 服务端世界引用（关闭时可能拿不到 player） */
    private ServerLevel levelRef;
    /** 是否已完成首 tick 注水/召溺尸（须等阻挡墙建好，见 onOpen javadoc） */
    private boolean initialized = false;

    private DangYunPingXianDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 45.0);
        this.fillRadius = Math.max(1, radius - 1);
    }

    /** 工厂：以施术者为中心展开（通用领域展开键） */
    public static DangYunPingXianDomain tryCreate(ServerPlayer player) {
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 8;
        return new DangYunPingXianDomain(player.getUUID(), player.position(), radius);
    }

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.dang_yun_ping_xian";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return ToolHelper.getActiveModifierLevel(tool, Modifiers.DANG_YUN_PING_XIAN.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        this.levelRef = player.serverLevel();
        // ⭐ 注水/召溺尸不能在这里做：DomainRegistry 在 onOpen 之后才 buildBarrier，
        // 此时墙未建，水会流到领域外一大片且不被记录 → 关闭清不干净。
        // 延迟到首个 onTick（那时阻挡墙已建好，水被墙封在领域内）。
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.dang_yun_ping_xian.open", radius), true);
        TinkersNewlife.LOGGER.info("[荡蕴平线] {} 展开：半径 {}（注水将在首 tick 完成）",
                player.getName().getString(), radius);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        if (levelRef == null) levelRef = player.serverLevel();
        ServerLevel level = levelRef;
        // ⭐ 首 tick：阻挡墙已建好，此时注水 + 召首批溺尸
        if (!initialized) {
            initialized = true;
            // §690：必须先记「原本就有的水」，再注水 —— 顺序反了就记不到原貌了 ✗
            recordPreExistingWater(level);
            fillWater(level, true);
            spawnDrownedBatch(level, player, targetCount());
            TinkersNewlife.LOGGER.info("[荡蕴平线] 首 tick 注水 {} 块 溺尸 {} 只",
                    waterBlocks.size(), drownedIds.size());
        }
        // 每 2 秒补注水
        if (now % 40 == 0) {
            fillWater(level, false);
        }
        // 施术者：持续水呼吸（不会窒息）
        player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, false, false));
        // 每 20 tick：友方/技巧玩家水呼吸 + 溺尸补充
        if (now % 20 == 0) {
            // ⭐ 增益用 allyTargetsInSphere（= 球内实体 + 同心戒同伴 ✓）：
            //    entitiesInSphere 为"互相免疫负面效果"把同伴剔除了 ✗，增益必须另外照顾到他 ✓
            for (LivingEntity e : allyTargetsInSphere(level)) {
                if (e.position().distanceToSqr(center) > radius * radius) continue;
                // 施术者阵营 + 新阴流技巧保护者（抵御溺水环境，技巧可挡本领域窒息）
                if (isFriendlyTo(player, e)
                        || (e instanceof ServerPlayer sp
                        && com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler.isProtected(sp, this))) {
                    e.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, false, false));
                }
            }
            // 溺尸补充：清死亡 → 补到目标数量
            drownedIds.removeIf(id -> !(level.getEntity(id) instanceof Drowned d) || !d.isAlive());
            int missing = targetCount() - drownedIds.size();
            if (missing > 0) {
                spawnDrownedBatch(level, player, missing);
            }
        }
        // 每 10 tick：溺尸索敌领域内目标并攻击
        if (now % 10 == 0) {
            driveDrowned(level, player);
        }
    }

    @Override
    public void onClose(ServerPlayer player, String messageKey) {
        // 溺尸全部消散
        if (levelRef != null) {
            for (int id : drownedIds) {
                Entity e = levelRef.getEntity(id);
                if (e != null) e.discard();
            }
            drownedIds.clear();
            // ⭐ 注的水复原为空气：从每个记录的水源出发，把与之连通的水全部删掉
            //（含扩散出的流动水），避免"关领域后残留水塘"。
            java.util.Set<BlockPos> cleared = clearWater(levelRef);
            // ⭐ 含水方块（waterlogged）也要一起清：注水后原版流体会把台阶/楼梯/栅栏/珊瑚
            // 这类"可含水"方块灌成含水状态，只删水方块的话它们会永远含着水。
            dryWaterlogged(levelRef, cleared);
            waterBlocks.clear();
            preExistingWater.clear();   // §690：本次领域用完了就丢掉，不留给下一次
        }
        // 施术者水呼吸随效果自然过期即可
        clearResist();
    }

    /**
     * 从记录水源出发 BFS，清除所有连通的水方块（仅限领域附近的连通水域），返回被清掉的位置。
     * <p>⚠ §690：**原本就存在的水一律跳过** ✗ —— 既不删、也不穿过它继续扩散
     * （{@link #preExistingWater}，首 tick 注水前记好 ✓）。
     */
    private java.util.Set<BlockPos> clearWater(ServerLevel level) {
        java.util.ArrayDeque<net.minecraft.core.BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<net.minecraft.core.BlockPos> visited = new java.util.HashSet<>();
        for (BlockPos pos : waterBlocks) {
            if (preExistingWater.contains(pos)) continue;      // §690：原本就是水 ⇒ 不动
            if (isWaterBody(level.getBlockState(pos))) {
                queue.add(pos);
                visited.add(pos);
            }
        }
        double limitSq = (radius + 8.0) * (radius + 8.0);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            // 六向扩散：连通的水（含流动水/低处积水）一并清掉
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (visited.contains(next)) continue;
                if (preExistingWater.contains(next)) continue;  // §690：原本就是水 ⇒ 不穿越、不清 ✗
                if (next.distSqr(BlockPos.containing(center)) > limitSq) continue;
                if (isWaterBody(level.getBlockState(next))) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        // ⭐ 复核：气泡柱依赖「下方灵魂沙/岩浆块 + 上方有水」，邻居更新或流体回填可能
        //    把刚清掉的位置重新变回水/气泡柱 → 再扫一遍，保证注的水一滴不留。
        int leftover = 0;
        for (BlockPos pos : waterBlocks) {          // 记录水位（含被破坏后又被灌成气泡柱的）
            if (preExistingWater.contains(pos)) continue;      // §690
            if (isWaterBody(level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                leftover++;
            }
        }
        for (BlockPos pos : visited) {              // visited 里本来就没有受保护的格子 ✓
            if (isWaterBody(level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                leftover++;
            }
        }
        if (leftover > 0) {
            TinkersNewlife.LOGGER.info("[荡蕴平线] 复核清除残留水体（含气泡柱）{} 块", leftover);
        }
        return visited;
    }

    /**
     * 该方块是否属于「本次注入的水体」：真水方块 + <b>气泡柱</b>。
     * <p>
     * ⭐ 关键修复：灵魂沙/岩浆块上方的水会被原版自动转成 {@code minecraft:bubble_column}，
     * 它<b>不是</b> {@code Blocks.WATER}——只按水方块判定时，气泡柱既不会被 BFS 遍历到、
     * 也不会被删除，于是关领域后原地留下一整根气泡柱水源（看起来就是"水没清干净"）。
     */
    private static boolean isWaterBody(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN);
    }

    /** 该方块是不是「含水的方块」（台阶／楼梯／栅栏／珊瑚这类 {@code WATERLOGGED=true}） */
    private static boolean isWaterlogged(net.minecraft.world.level.block.state.BlockState state) {
        return state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
    }

    /**
     * <b>§690 用户口径</b>：「荡蕴平线的清除水逻辑<b>不要清除原本已有的水</b>」。
     *
     * <p>在注水<b>之前</b>扫一遍「领域球体 ＋ 8 格外扩」范围（与 {@link #clearWater} 里 BFS 的
     * {@code radius + 8} 上限<b>完全一致</b> ✓），把**原本就存在**的
     * 水方块／气泡柱／含水方块记进 {@link #preExistingWater} ✓。
     *
     * <p>为什么这样刚好够：
     * <ul>
     *   <li>注水只灌<b>空气</b> ✓ ⇒ 这些原有水体本来就不会被改写 ✓；</li>
     *   <li>关闭清理的 BFS 视野被 {@code radius + 8} 卡住 ✓ ⇒ 只要"这一圈里的原有水"被标记，
     *       BFS 走到它们边上就停 ✓ —— <b>圈外</b>（例如整片海、整条河）的水根本进不了视野 ✓，
     *       不必也不该把整片水域都记下来 ✗；</li>
     *   <li>⇒ 效果：**领域自己灌的水照旧清干净 ✓，原本就有的水一滴不动 ✓**。</li>
     * </ul>
     */
    private void recordPreExistingWater(ServerLevel level) {
        preExistingWater.clear();
        double limitSq = (radius + 8.0) * (radius + 8.0);
        BlockPos centerPos = BlockPos.containing(center);
        int r = (int) Math.ceil(radius + 8.0);
        int cx = centerPos.getX(), cy = centerPos.getY(), cz = centerPos.getZ();
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                for (int z = cz - r; z <= cz + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.distSqr(centerPos) > limitSq) continue;
                    if (!level.isLoaded(pos)) continue;      // 未加载区块：读不到状态，跳过 ✓
                    var state = level.getBlockState(pos);
                    if (isWaterBody(state) || isWaterlogged(state)) {
                        preExistingWater.add(pos.immutable());
                    }
                }
            }
        }
        if (!preExistingWater.isEmpty()) {
            TinkersNewlife.LOGGER.info("[荡蕴平线] 记录到既有水体 {} 块 ⇒ 关闭时不会清除它们",
                    preExistingWater.size());
        }
    }

    /**
     * 把「因本次注水而含水的方块」（{@code WATERLOGGED=true}）烘干。
     * <p>
     * 注水后原版流体会顺着扩散把台阶/楼梯/栅栏/珊瑚这类「可含水」方块灌成含水状态，
     * 而 {@link #clearWater} 只处理真正的 {@code minecraft:water} 方块，含水方块会残留。
     * 这里从「刚被清掉的水 + 当初记录的水位」出发，沿含水方块做 BFS（含水方块彼此也连通），
     * 逐个把 {@code WATERLOGGED} 置回 false —— 只影响与领域水域连通的那些，
     * 不会误伤领域外/原本就含水的方块。
     * <p>⚠ §690：**原本就含水的方块**（{@link #preExistingWater}）一律跳过 ✗
     * —— 否则领域旁边本来就泡在水里的台阶/珊瑚会被一起烘干 ✗。
     */
    private void dryWaterlogged(ServerLevel level, java.util.Set<BlockPos> cleared) {
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>(cleared);
        queue.addAll(cleared);
        for (BlockPos pos : waterBlocks) {          // 水位可能已被玩家破坏 → 一并作为种子
            if (preExistingWater.contains(pos)) continue;   // §690：原本就在水里的方块不动 ✗
            if (visited.add(pos)) queue.add(pos);
        }
        double limitSq = (radius + 8.0) * (radius + 8.0);
        BlockPos centerPos = BlockPos.containing(center);
        int dried = 0;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (preExistingWater.contains(next)) continue;  // §690：原本就含水的方块不烘干 ✗
                if (!visited.add(next)) continue;
                if (next.distSqr(centerPos) > limitSq) continue;
                var state = level.getBlockState(next);
                if (!state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                        || !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                    continue;
                }
                level.setBlock(next, state.setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false), 2);
                dried++;
                queue.add(next);   // 含水方块之间也连通：继续往深处找
            }
        }
        if (dried > 0) {
            TinkersNewlife.LOGGER.info("[荡蕴平线] 已烘干 {} 个含水方块（waterlogged）", dried);
        }
    }

    // ============================================================
    //  注水 / 复原
    // ============================================================

    /** 把领域内部球体所有空气灌成水（记录位置）；full=false 只补注已记录但不再是水的位置 */
    private void fillWater(ServerLevel level, boolean full) {
        int r = (int) Math.ceil(fillRadius);
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);
        if (full) {
            waterBlocks.clear();
            for (int y = cy - r; y <= cy + r; y++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    for (int z = cz - r; z <= cz + r; z++) {
                        double dx = x + 0.5 - center.x;
                        double dy = y + 0.5 - center.y;
                        double dz = z + 0.5 - center.z;
                        if (dx * dx + dy * dy + dz * dz > fillRadius * fillRadius) continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).isAir()) continue;
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                        waterBlocks.add(pos);
                    }
                }
            }
        } else {
            // 补注：只处理已记录但不再是水的位置（玩家破坏/下界蒸发等）
            Iterator<BlockPos> it = waterBlocks.iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next();
                if (level.getBlockState(pos).is(Blocks.WATER)) continue;
                if (pos.distSqr(BlockPos.containing(center)) > fillRadius * fillRadius) {
                    it.remove(); // 已不在注水半径内（防御性清理）
                    continue;
                }
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
    }

    // ============================================================
    //  溺尸军团
    // ============================================================

    /** 目标溺尸数量 = 6 + 半径/2，上限 24 */
    private int targetCount() {
        return Math.min(24, 6 + radius / 2);
    }

    /** 三叉戟概率：20% */
    private static final double TRIDENT_CHANCE = 0.2;

    /** 在领域内注水中随机位置生成 n 只溺尸（认施术者为主人），并加入管理列表 */
    private void spawnDrownedBatch(ServerLevel level, ServerPlayer owner, int n) {
        List<BlockPos> waterPos = new ArrayList<>(waterBlocks);
        for (int i = 0; i < n; i++) {
            Drowned drowned = EntityType.DROWNED.create(level);
            if (drowned == null) return;
            // ⭐ 清空原生 AI（原版溺尸会主动攻击玩家/村民，含施术者本人）：
            // 索敌/攻击完全由本领域每 10 tick 驱动
            drowned.goalSelector.removeAllGoals(g -> true);
            drowned.targetSelector.removeAllGoals(g -> true);
            Vec3 spawn = pickSpawn(waterPos);
            drowned.moveTo(spawn.x, spawn.y, spawn.z,
                    owner.getRandom().nextFloat() * 360.0F, 0);
            drowned.setPersistenceRequired();
            drowned.getPersistentData().putUUID(KEY_DROWNED_OWNER, owner.getUUID());
            // 概率手持三叉戟
            if (owner.getRandom().nextFloat() < TRIDENT_CHANCE) {
                drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                drowned.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
            level.addFreshEntity(drowned);
            drownedIds.add(drowned.getId());
        }
    }

    /** 选一个落水点：优先注水方块，其次领域中心附近随机可站位置 */
    private Vec3 pickSpawn(List<BlockPos> waterPos) {
        if (!waterPos.isEmpty()) {
            BlockPos p = waterPos.get((int) (Math.random() * waterPos.size()));
            return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        }
        double a = Math.random() * Math.PI * 2;
        double rr = Math.random() * fillRadius * 0.6;
        return new Vec3(center.x + Math.cos(a) * rr,
                center.y + Math.random() * 2 - 1,
                center.z + Math.sin(a) * rr);
    }

    /** 每 10 tick：每只溺尸索敌领域内最近的非施术者阵营目标，游近并近战攻击 */
    private void driveDrowned(ServerLevel level, ServerPlayer owner) {
        for (int id : new ArrayList<>(drownedIds)) {
            Entity e = level.getEntity(id);
            if (!(e instanceof Drowned drowned) || !drowned.isAlive()) {
                drownedIds.remove((Integer) id);
                continue;
            }
            LivingEntity target = nearestEnemy(level, owner, drowned);
            if (target == null) {
                drowned.setTarget(null);
                drowned.getNavigation().stop();
                continue;
            }
            drowned.setTarget(target);
            // ⭐ 贴身近战：判定距离压到 1.6 格（原 3.2 格看起来像"隔空攻击"），
            //    并且必须真的走到身边才出手；够不着就继续游过去（导航失败时用移动控制兜底）。
            double reach = 1.6;
            double distSq = drowned.distanceToSqr(target);
            if (distSq > reach * reach) {
                drowned.getNavigation().moveTo(target, 1.25);
                if (drowned.getNavigation().isDone()) {
                    drowned.getMoveControl().setWantedPosition(
                            target.getX(), target.getY(), target.getZ(), 1.25);
                }
                continue;
            }
            drowned.getNavigation().stop();
            drowned.lookAt(target, 30.0F, 30.0F);
            drowned.getLookControl().setLookAt(target, 30.0F, 30.0F);
            // 出手节奏对齐原版近战（每 20 tick 一次，扫描本身 10 tick 一次）
            if (level.getGameTime() % 20 != 0) continue;
            if (drowned.attackAnim > 0.0F) continue;
            drowned.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            drowned.doHurtTarget(target);
        }
    }

    /** 领域内最近的非施术者阵营活体目标（含玩家/敌对/中立；不含施术者及其同队） */
    private LivingEntity nearestEnemy(ServerLevel level, ServerPlayer owner, Mob self) {
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        double r = radius;
        for (LivingEntity e : entitiesInSphere(level)) {
            if (e == self || !e.isAlive()) continue;
            if (e.getUUID().equals(owner)) continue;
            if (e.position().distanceToSqr(center) > r * r) continue;
            if (isFriendlyTo(owner, e)) continue;
            double d = self.distanceToSqr(e);
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    /** 是否属于施术者阵营（本人/驯养宠物/已调伏式神/咒灵/守护/本领域溺尸） */
    private static boolean isFriendlyTo(ServerPlayer owner, LivingEntity e) {
        if (e == owner) return true;
        // ⭐ 同心戒同伴：合并领域里也算"己方" ✓ ——
        //    必须放在下面 `e instanceof Player → false` 之前，否则同伴（也是玩家）会被挡掉 ✗
        if (com.mofengbaizhi.tinkersnewlife.content.curse.TwinRingLink.arePaired(owner, e)) return true;
        if (e instanceof Player) return false;
        // ⭐ getUUID 在 key 缺失时抛 NPE——必须先 contains 再取值
        var tag = e.getPersistentData();
        if (tag.contains(KEY_DROWNED_OWNER)
                && owner.getUUID().equals(tag.getUUID(KEY_DROWNED_OWNER))) {
            return true;
        }
        if (e instanceof TamableAnimal tame && owner.getUUID().equals(tame.getOwnerUUID())) return true;
        // ⭐ 只有"已调伏"的式神才算己方：未调伏式神是敌人（调伏战中主人必须能打死它）
        if (e instanceof com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob sm
                && sm.isTamed() && owner.getUUID().equals(sm.getOwnerId())) return true;
        if (tag.contains(com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.KEY_GUARD_OWNER)
                && owner.getUUID().equals(tag.getUUID(
                com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.KEY_GUARD_OWNER))) {
            return true;
        }
        return CursedSpiritTechnique.isSpiritTeam(e, owner);
    }
}
