package com.mofengbaizhi.tinkersnewlife.content.portal;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <b>伟大白色空间</b>（§660／§661／§663）的维度常量、「开门」逻辑、传送黑名单与维度解锁。
 *
 * <h2>这个维度是什么</h2>
 * 连接相隔若干光年之地的奇异维度空间；古老者在地球上至少修过一座通往它的门。
 * 它只有 <b>2 层实体方块</b>：{@code y=0} 基岩层，{@code y=1} 一整层<b>不可破坏的白色方块</b>；
 * 天空<b>不随昼夜更替变化</b>，恒定一片白。
 * <p>⚠ <b>§691 用户口径</b>：建筑高度从 {@code 0..15} <b>拓到 {@code 0..399}</b>（
 * {@code dimension_type/great_white_space.json} 的 {@code height}／{@code logical_height} = 400 ✓）。
 * 动机是<b>保险</b>：客户端换维度时新建的 {@code LocalPlayer} 若停在"高度之外"的 Y，
 * 原版 {@code resetPos()} 的循环跑不动 ⇒ 会一直往虚空里掉 ✗；
 * 抬到 400 之后，来自 ATM 采矿维度 253 层这类情况都<b>落在世界内</b> ⇒ 最坏也只是掉到白地板上 ✓
 * （⇒ 地板以上全是空气，外观完全不变 ✓）。
 *
 * <h2>门：<b>2 格高</b> ＋ 永远成对 ＋ 自动记录落点（§663／§661）</h2>
 * <ul>
 *   <li><b>2 格高</b>：一扇门＝<b>上下两格</b>同款方块（{@code HALF=lower/upper}），
 *       两格各存一份相同的目的地 ⇒ 从哪一格走进去都能传送 ✓。
 *       整扇门<b>同生共死</b>：{@link #removePortal} 拆一对；
 *       玩家挖掉任意一格时由 {@code WhiteSpacePortalBlock#onRemove} 清掉另一格 ✓。</li>
 *   <li><b>朝向</b>：开门时取玩家的水平朝向写进 {@code FACING} ⇒ 那片平面横在玩家面前 ✓。
 *       自愈补门（{@link #resolveLanding}）时取"正在走进去的玩家"的朝向 ✓。</li>
 *   <li><b>成对</b>：通行证开出来的永远是两扇互相指向对方的门 —— 这是「永久存在且可自由往返」的前提 ✓。
 *       先建对面、失败回滚，绝不留单向门 ✓。</li>
 *   <li><b>自动记录相对落点</b>：在伟大白色空间之外的维度用通行证时，GUI 里 x／z 的默认值
 *       就是<b>门自己的 xz</b>（玩家不改就正对着这一列）✓。</li>
 *   <li><b>自愈</b>：走进门时调 {@link #resolveLanding} —— 对面那扇门要是没了（被拆／被炸），
 *       只要落点还是空地就<b>按记录自动补回来</b>并指回原地 ✓；整段被实体方块堵死则拒绝传送并提示 ✓。</li>
 *   <li><b>§663 新口径：脚下没有落点就铺平台</b> —— {@link #ensurePlatform} 在传送前检查落点下方，
 *       没有支撑就用一块 <b>3×3 黑曜石平台</b>接住玩家（只替换空气／可替换方块，绝不挖玩家盖的东西 ✓）。</li>
 * </ul>
 *
 * <h2>传送黑名单（§661 用户口径）</h2>
 * <b>狱门疆维度</b>（{@code tinkersnewlife:gourd}）与<b>铁魔法口袋维度</b>
 * （{@code irons_spellbooks:pocket_dimension}）双向禁止：既不能把门开到那里，也不能在里面用通行证开门。
 * 前者尤其重要 —— 否则被封印在狱门疆里的囚徒可以直接用通行证越狱 ✗。
 *
 * <h2>其他维度要「去过一次」才解锁（§661）</h2>
 * 只有玩家<b>到过</b>的维度才会出现在 GUI 的下拉列表里（记录写在玩家自己的持久化数据里）。
 * ⚠ 这是<b>服务端</b>过滤 ✓ 客户端改包也没用 ✓ 而且 C2S 那边还会再验一遍黑名单 ✓。
 */
public final class WhiteSpaceDimensions {

    private WhiteSpaceDimensions() {
    }

    /** 伟大白色空间的维度 key（数据文件 {@code data/tinkersnewlife/dimension/great_white_space.json} + 同名 dimension_type） */
    public static final ResourceKey<Level> WHITE_SPACE = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(TinkersNewlife.MOD_ID, "great_white_space"));

    /** 白色方块那一层（{@code dimension/great_white_space.json} 的 flat layers 第 2 层 ⇒ min_y + 1） */
    public static final int FLOOR_Y = 1;

    /** 地面表层：站上去脚底所在的 y（门就开在这一层，也是「不能填 y」的那个固定值） */
    public static final int GROUND_Y = 2;

    /**
     * 白色空间里同一根柱子上最多把门堆到这一层。
     * <p>⚠ 门是 2 格高的 ⇒ 下半在 {@code y}、上半在 {@code y+1}。
     * <p>§691 起本维度的高度已从 {@code 0..15} 拓到 <b>{@code 0..399}</b>（用户口径"保险"）✓，
     * 所以这里 <b>不再是</b>"世界高度逼出来的上限" ✗ ——
     * 它现在只是"同一根柱子最多堆 13 扇门"的一个<b>人为约定</b> ✓：
     * 正常使用（每根柱子一对门）永远只会用到 {@code GROUND_Y = 2} 那一格 ✓，
     * 只有同一 xz 反复开很多门时才会往上摞 ✓。
     */
    private static final int MAX_LINK_Y = 14;

    /** {@link #resolveLanding} 自愈时最多往上找几格 */
    private static final int MAX_HEAL_UP = 8;

    /** 玩家使用通行证的最大「手长」（防作弊：C2S 包里的锚点必须离玩家这么近） */
    public static final double MAX_REACH_SQR = 64.0D;

    /**
     * 跨维度传送后的兜底冷却（tick）——1 秒。
     * <p>⚠ §678 起<b>不再靠它防乒乓</b>：落点就是对面那扇门的下半格 ⇒ 人一落地就站在门里，
     * 用计时器挡的结果是"4 秒后必定被弹回去"（用户实测的乒乓就是这个）。
     * 真正的防线是 {@link #wasInsidePortalLastTick}（只在"从门外走进来"的那一刻传送），
     * 这里只留 1 秒防止同一 tick 内的重复触发 ✓。
     */
    public static final int ARRIVE_COOLDOWN = 20;

    /** 刚开完门的玩家冷却（tick）——5 秒，免得开完门站在原地被自己的门吸走 */
    public static final int CREATE_COOLDOWN = 100;

    /** 玩家持久化数据里存「下次可传送 tick」的键 */
    private static final String COOLDOWN_KEY = "tinkersnewlife:white_space_portal_next";

    /** 玩家持久化数据里存「去过的维度」的键（一个 CompoundTag，维度 id → true） */
    private static final String VISITED_KEY = "tinkersnewlife:visited_dimensions";

    /**
     * 实体持久化数据里存「最后一次还在门里的游戏刻」的键（§678 防乒乓的家底）。
     * <p>⚠ 必须存在实体自己的 {@code ForgeData} 里：① 玩家跨维度传送是同一个对象 ⇒ 天然带过去 ✓；
     * ② 生物的跨维度传送是"新建实体 + 拷 NBT"（{@code Entity#restoreFrom} ⇒ {@code saveWithoutId}/{@code load}），
     * ForgeData 同样会被继承 ✓ ⇒ 落地就在对面门里也不会被立刻弹回来 ✓。
     */
    private static final String INSIDE_KEY = "tinkersnewlife:white_space_portal_inside";

    // ============================================================
    //  传送黑名单
    // ============================================================

    /** 狱门疆维度（与 {@code GourdJailHandler.GOURD_DIM} 是同一条 key；不直接引那个类，免得跨包耦合） */
    private static final ResourceKey<Level> GOURD_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(TinkersNewlife.MOD_ID, "gourd"));

    /**
     * 铁魔法（Iron's Spellbooks）的口袋维度。
     * <p>id 从它自己的 jar 里查实：{@code data/irons_spellbooks/dimension/pocket_dimension.json}
     * ⇒ 维度 key 是 {@code pocket_dimension}（{@code pocket_dimension_type} 是它的<b>维度类型</b>，不是 key ✗）。
     * 没装铁魔法时这条 key 永远匹配不上 ✓ 无需按模组判断 ✓。
     */
    private static final ResourceKey<Level> IRONS_POCKET_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation("irons_spellbooks", "pocket_dimension"));

    private static final Set<ResourceKey<Level>> BLACKLIST = Set.of(GOURD_DIMENSION, IRONS_POCKET_DIMENSION);

    /** 这个维度是不是传送黑名单（不能作为目的地，也不能在里面用通行证） */
    public static boolean isBlacklisted(@Nullable ResourceKey<Level> key) {
        return key != null && BLACKLIST.contains(key);
    }

    public static boolean isBlacklisted(@Nullable Level level) {
        return level != null && isBlacklisted(level.dimension());
    }

    // ============================================================
    //  查询
    // ============================================================

    public static boolean isWhiteSpace(@Nullable Level level) {
        return level != null && level.dimension().equals(WHITE_SPACE);
    }

    public static boolean isWhiteSpace(@Nullable ResourceKey<Level> key) {
        return WHITE_SPACE.equals(key);
    }

    @Nullable
    public static ServerLevel getWhiteSpace(@Nullable MinecraftServer server) {
        return server == null ? null : server.getLevel(WHITE_SPACE);
    }

    /** 维度的展示名：优先 {@code dimension.<命名空间>.<路径>} 翻译键，缺了就直接显示 id */
    public static Component displayName(ResourceKey<Level> key) {
        ResourceLocation id = key.location();
        return Component.translatableWithFallback(
                "dimension." + id.getNamespace() + "." + id.getPath(), id.toString());
    }

    /** 坐标的展示串，纯粹给提示语用 */
    public static String coords(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    // ============================================================
    //  「去过的维度才解锁」
    // ============================================================

    /** 记下"这个玩家到过这个维度"（幂等；已经记过就不写存档 ✓） */
    public static void markVisited(@Nullable ServerPlayer player, @Nullable ResourceKey<Level> key) {
        if (player == null || key == null) return;
        CompoundTag data = player.getPersistentData();
        CompoundTag visited = data.getCompound(VISITED_KEY);
        String id = key.location().toString();
        if (visited.getBoolean(id)) return;
        visited.putBoolean(id, true);
        data.put(VISITED_KEY, visited);
    }

    /** 这个玩家到过这个维度吗（人就站在这个维度里时当然算到过 ✓） */
    public static boolean hasVisited(@Nullable ServerPlayer player, @Nullable ResourceKey<Level> key) {
        if (player == null || key == null) return false;
        if (player.level().dimension().equals(key)) return true;
        return player.getPersistentData().getCompound(VISITED_KEY).getBoolean(key.location().toString());
    }

    /**
     * GUI 下拉列表用的「<b>已解锁</b>」维度：排除传送黑名单 ＋ 排除没去过的。
     * <p>⚠ 这是<b>服务端</b>算的名单 ⇒ 客户端就算改包把别的维度塞进来，C2S 那边还会再验一遍 ✓。
     */
    public static List<String> unlockedDimensionIds(@Nullable ServerPlayer player) {
        if (player == null) return List.of();
        MinecraftServer server = player.getServer();
        if (server == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (ResourceKey<Level> key : server.levelKeys()) {
            if (isBlacklisted(key)) continue;
            if (!hasVisited(player, key)) continue;
            ids.add(key.location().toString());
        }
        Collections.sort(ids);
        return ids;
    }

    /** 开门结果：成功与否 + 要给玩家看的一句话（已本地化） */
    public record PortalResult(boolean ok, Component message) {

        static PortalResult success(String key, Object... args) {
            return new PortalResult(true, Component.translatable(key, args));
        }

        static PortalResult failure(String key, Object... args) {
            return new PortalResult(false, Component.translatable(key, args));
        }
    }

    // ============================================================
    //  两条入口
    // ============================================================

    /**
     * <b>在伟大白色空间之外的维度</b>用通行证（GUI 锁定模式：维度不可选、不能填 y）。
     *
     * @param level  玩家当前所在维度（不能是黑名单维度）
     * @param anchor 右键点到的那个方块（门开在它上方，占它上方两格）
     * @param destX  白色空间侧的落点 x（默认＝门自己的 x ⇒ 相对落点）
     * @param destZ  白色空间侧的落点 z
     * @param facing 门的朝向（取玩家的水平朝向）
     */
    public static PortalResult linkFromOutside(ServerLevel level, BlockPos anchor, int destX, int destZ,
                                              Direction facing) {
        if (isBlacklisted(level.dimension())) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blacklisted",
                    displayName(level.dimension()));
        }
        ServerLevel ws = getWhiteSpace(level.getServer());
        if (ws == null) return PortalResult.failure("message.tinkersnewlife.white_space.no_dimension");
        if (Math.abs(destX) > 30000000 || Math.abs(destZ) > 30000000) {
            return PortalResult.failure("message.tinkersnewlife.white_space.bad_coords");
        }

        BlockPos localPos = anchor.above();
        BlockPos remotePos = findLinkSpot(ws, destX, destZ, level.dimension(), localPos);
        if (remotePos == null) return PortalResult.failure("message.tinkersnewlife.white_space.blocked");

        // 白色空间那边先把地面补上（正常情况下 flat 生成器已经铺好了，这里只是兜底）
        ensureFloor(ws, remotePos);

        PortalResult result = link(level, localPos, ws, remotePos, facing);
        if (result.ok()) {
            return PortalResult.success("message.tinkersnewlife.white_space.opened",
                    displayName(WHITE_SPACE), coords(remotePos));
        }
        return result;
    }

    /**
     * <b>在伟大白色空间里</b>用通行证（GUI 自由模式：下拉选维度 ＋ 填 xyz）。
     */
    public static PortalResult linkFromWhiteSpace(ServerLevel ws, BlockPos anchor,
                                                 ResourceKey<Level> destDim, BlockPos destPos,
                                                 Direction facing) {
        if (isBlacklisted(destDim)) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blacklist_target",
                    displayName(destDim));
        }
        ServerLevel target = ws.getServer() == null ? null : ws.getServer().getLevel(destDim);
        if (target == null) return PortalResult.failure("message.tinkersnewlife.white_space.no_dimension");
        // 目标是 2 格高的门 ⇒ 上下两格都得落在那条维度自己的建筑高度里
        if (destPos.getY() < target.getMinBuildHeight() || destPos.getY() + 1 >= target.getMaxBuildHeight()) {
            return PortalResult.failure("message.tinkersnewlife.white_space.bad_coords");
        }
        if (Math.abs(destPos.getX()) > 30000000 || Math.abs(destPos.getZ()) > 30000000) {
            return PortalResult.failure("message.tinkersnewlife.white_space.bad_coords");
        }

        BlockPos localPos = anchor.above();
        PortalResult result = link(ws, localPos, target, destPos, facing);
        if (result.ok()) {
            return PortalResult.success("message.tinkersnewlife.white_space.opened",
                    displayName(destDim), coords(destPos));
        }
        return result;
    }

    // ============================================================
    //  开门底层（一扇门 ＝ 上下两格）
    // ============================================================

    /**
     * 建<b>一对</b>互通的门：{@code (aLevel, aPos)} ↔ {@code (bLevel, bPos)}，两边同朝 {@code facing}。
     * <p>先建 b 侧、再建 a 侧（a 侧是玩家眼前那扇）；a 侧失败就把刚建好的 b 侧撤掉，
     * 绝不留一扇只能去、回不来的单向门 ✓。
     */
    private static PortalResult link(ServerLevel aLevel, BlockPos aPos, ServerLevel bLevel, BlockPos bPos,
                                     Direction facing) {
        if (aLevel == bLevel && aPos.equals(bPos)) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blocked");
        }
        if (!canOccupyPair(aLevel, aPos) || !canOccupyPair(bLevel, bPos)) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blocked");
        }

        boolean bExisted = isOurLower(bLevel, bPos);
        if (!placePortal(bLevel, bPos, aLevel.dimension(), aPos, facing)) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blocked");
        }
        if (!placePortal(aLevel, aPos, bLevel.dimension(), bPos, facing)) {
            if (!bExisted) removePortal(bLevel, bPos);
            return PortalResult.failure("message.tinkersnewlife.white_space.blocked");
        }
        return PortalResult.success("message.tinkersnewlife.white_space.opened");
    }

    /** 这一格是不是"下半"那格的门 */
    private static boolean isOurLower(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.WHITE_SPACE_PORTAL.get())
                && state.getValue(WhiteSpacePortalBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** 单格能不能被门占用：空气／可替换／已经是我们的门（后者表示"只改目的地重新接线"） */
    private static boolean canOccupy(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced()
                || state.is(ModBlocks.WHITE_SPACE_PORTAL.get());
    }

    /** 上下两格都能被门占用吗 */
    private static boolean canOccupyPair(ServerLevel level, BlockPos lower) {
        return canOccupy(level, lower) && canOccupy(level, lower.above());
    }

    /** 严格版：这一格是空地吗 */
    private static boolean isFree(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    /** 严格版：上下两格都是空地（新开一扇门时用，不去抢别人的门） */
    private static boolean canPlacePair(ServerLevel level, BlockPos lower) {
        return isFree(level, lower) && isFree(level, lower.above());
    }

    /**
     * 在 {@code lower} 放一对 2 格高的门（下半 ＋ 上半），两格写入<b>同一份</b>目的地。
     *
     * @return 上下两格最终都是带目的地的一扇门
     */
    private static boolean placePortal(ServerLevel level, BlockPos lower,
                                       ResourceKey<Level> destDim, BlockPos destPos, Direction facing) {
        BlockPos upper = lower.above();
        if (level.isOutsideBuildHeight(upper)) return false;
        if (!canOccupyPair(level, lower)) return false;

        BlockState lowerState = ModBlocks.WHITE_SPACE_PORTAL.get().defaultBlockState()
                .setValue(WhiteSpacePortalBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(WhiteSpacePortalBlock.FACING, facing)
                // §666 用户口径：白色空间里的门用**黑色**贴图，别处用**白色**贴图
                // （纯白背景上白门几乎看不见）—— 完全由"门放在哪个维度"决定 ✓
                .setValue(WhiteSpacePortalBlock.DARK, isWhiteSpace(level));
        BlockState upperState = lowerState.setValue(WhiteSpacePortalBlock.HALF, DoubleBlockHalf.UPPER);

        // 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS：会把新方块与新方块实体一起发给客户端 ✓
        level.setBlock(lower, lowerState, 3);
        level.setBlock(upper, upperState, 3);

        boolean ok = writeDestination(level, lower, lowerState, destDim, destPos);
        ok &= writeDestination(level, upper, upperState, destDim, destPos);
        return ok;
    }

    /**
     * 把目的地写进这一格的方块实体并发给客户端，<b>顺手把黑白变体校正成"这个维度该有的样子"</b>。
     * <p>⚠ 必须显式 {@code sendBlockUpdated}：方块实体数据只有在
     * {@code ChunkHolder#broadcastBlockEntityIfNeeded} 里才会随 {@code getUpdatePacket()} 发出去
     * （告示牌那一套），否则客户端那扇门没目的地 ⇒ §661 的指向提示是空的 ✗。
     * <p>§666：{@code dark} 只由维度决定 ⇒ 旧存档里颜色不对的门、或被 {@code /setblock} 放错维度的门，
     * 下次被写一次目的地就会自己变对 ✓（同方块换状态不会重建方块实体 ✓）。
     */
    private static boolean writeDestination(ServerLevel level, BlockPos pos, BlockState state,
                                           ResourceKey<Level> destDim, BlockPos destPos) {
        BlockState want = state.setValue(WhiteSpacePortalBlock.DARK, isWhiteSpace(level));
        if (!want.equals(state)) {
            level.setBlock(pos, want, 3);
            state = want;
        }
        if (level.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity be) {
            be.setDestination(destDim, destPos);
            level.sendBlockUpdated(pos, state, state, 3);
            return true;
        }
        return false;
    }

    /**
     * 把一整扇门（上下两格）拆掉 —— 不留半扇。
     * <p>§664 起由 {@code WhiteSpacePortalBlock#use}（天逆鉾右键）调用，所以是 public。
     */
    public static void removePortal(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos lower = pos;
        if (state.is(ModBlocks.WHITE_SPACE_PORTAL.get())
                && state.getValue(WhiteSpacePortalBlock.HALF) == DoubleBlockHalf.UPPER) {
            lower = pos.below();
        }
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos p = lower.offset(0, dy, 0);
            if (level.getBlockState(p).is(ModBlocks.WHITE_SPACE_PORTAL.get())) {
                level.removeBlock(p, false);
            }
        }
    }

    /**
     * 白色空间里「相同 xz、地面表层」那个位置：
     * <ol>
     *   <li>那根柱子上<b>已经有一扇正好指回同一个地方</b>的门（认"下半"那格）⇒ 直接复用它，
     *       重复使用同一根柱子不会越堆越高 ✓；</li>
     *   <li>否则顺着柱子往上找<b>第一个能放下 2 格门的空位</b>（别人的门不抢 ✗ ——
     *       抢了会把那扇门的回路改掉，表现为"从 A 进去、从 B 出来"的不对称）；</li>
     *   <li>到 {@link #MAX_LINK_Y} 还找不到 ⇒ null。</li>
     * </ol>
     */
    @Nullable
    private static BlockPos findLinkSpot(ServerLevel ws, int x, int z,
                                        ResourceKey<Level> backDim, BlockPos backPos) {
        BlockPos free = null;
        for (int y = GROUND_Y; y <= MAX_LINK_Y; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = ws.getBlockState(pos);
            if (state.is(ModBlocks.WHITE_SPACE_PORTAL.get())) {
                if (state.getValue(WhiteSpacePortalBlock.HALF) == DoubleBlockHalf.LOWER
                        && ws.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity be
                        && backDim.equals(be.getDestinationDimension())
                        && backPos.equals(be.getDestinationPos())) {
                    return pos;
                }
                continue;
            }
            if (free == null && canPlacePair(ws, pos)) {
                free = pos;
            }
        }
        return free;
    }

    /** 白色空间地面兜底：门位正好在地面表层时，确保脚底下那一格是白色方块 */
    private static void ensureFloor(ServerLevel ws, BlockPos portalPos) {
        if (portalPos.getY() != GROUND_Y) return;
        BlockPos floor = portalPos.below();
        if (floor.getY() != FLOOR_Y) return;
        if (ws.getBlockState(floor).isAir()) {
            ws.setBlock(floor, ModBlocks.WHITE_SPACE_BLOCK.get().defaultBlockState(), 3);
        }
    }

    // ============================================================
    //  落点自愈 ＋ 落脚平台
    // ============================================================

    /**
     * 走进一扇门时先把<b>对面落点</b>落实：
     * <ol>
     *   <li>记录的落点上还是我们的门 ⇒ 用它，顺手把<b>上下两格</b>的目的地都校正回我们这边
     *       （双向自愈 ✓）；</li>
     *   <li>记录的落点变成空地（对面那扇被拆了／被炸了）⇒ <b>按记录自动补一扇指回来的门</b> ✓
     *       —— 这就是"自动记录相对落点，以便往返"在门被破坏之后仍然成立的原因；</li>
     *   <li>往上最多 {@link #MAX_HEAL_UP} 格找空位（玩家在落点上盖了房子的话，别把人埋进墙里 ✓）；</li>
     *   <li>整段都被实体方块占住 ⇒ 返回 {@code null}，门拒绝传送并提示
     *       （宁可不传，也不把玩家塞进方块里 ✗）。</li>
     * </ol>
     *
     * @param target   目的地维度
     * @param recorded 记录下来的落点
     * @param backDim  我们这边（出发侧）的维度
     * @param backPos  我们这边（出发侧）那扇门的位置
     * @param facing   补门时用的朝向（取正在走进来的玩家的水平朝向）
     * @return 实际可以落脚的坐标（门的下半那格）；null ＝ 落点被堵死
     */
    @Nullable
    public static BlockPos resolveLanding(ServerLevel target, BlockPos recorded,
                                         ResourceKey<Level> backDim, BlockPos backPos, Direction facing) {
        for (int dy = 0; dy <= MAX_HEAL_UP; dy++) {
            BlockPos pos = recorded.offset(0, dy, 0);
            if (target.isOutsideBuildHeight(pos) || target.isOutsideBuildHeight(pos.above())) break;
            BlockState state = target.getBlockState(pos);

            if (state.is(ModBlocks.WHITE_SPACE_PORTAL.get())) {
                // 只认"下半"那格；记录点若落到上半，就往下一格找它的下半
                BlockPos lower = state.getValue(WhiteSpacePortalBlock.HALF) == DoubleBlockHalf.LOWER
                        ? pos : pos.below();
                BlockState lowerState = target.getBlockState(lower);
                if (!lowerState.is(ModBlocks.WHITE_SPACE_PORTAL.get())
                        || lowerState.getValue(WhiteSpacePortalBlock.HALF) != DoubleBlockHalf.LOWER) {
                    continue;
                }
                writeDestination(target, lower, lowerState, backDim, backPos);
                BlockState upperState = target.getBlockState(lower.above());
                if (upperState.is(ModBlocks.WHITE_SPACE_PORTAL.get())) {
                    writeDestination(target, lower.above(), upperState, backDim, backPos);
                }
                return lower;
            }

            if (canPlacePair(target, pos)) {
                if (isWhiteSpace(target)) ensureFloor(target, pos);
                if (placePortal(target, pos, backDim, backPos, facing)) return pos;
            }
        }
        return null;
    }

    /**
     * <b>§663 用户口径</b>：「如果传送出来脚底下没有落点，自动生成一块 3×3 的黑曜石平台提供落脚」。
     *
     * <p>检查落点正下方那一格：
     * <ul>
     *   <li>已经有实体方块（不是空气、也不可替换）⇒ 有地方站，<b>什么都不做</b> ✓；</li>
     *   <li>没有 ⇒ 以落点正下方为中心铺 <b>3×3 黑曜石</b>，让玩家一落地就有地方站 ✓。</li>
     * </ul>
     * ⚠ <b>只替换空气／可替换方块</b>（水、草、雪这类）⇒ <b>绝不会把玩家盖的建筑挖掉</b> ✓；
     * 越界的高度直接跳过 ✓。
     */
    public static void ensurePlatform(ServerLevel level, BlockPos landing) {
        BlockPos support = landing.below();
        if (level.isOutsideBuildHeight(support)) return;
        BlockState below = level.getBlockState(support);
        if (!below.isAir() && !below.canBeReplaced()) return;   // 已经有落脚点 ⇒ 不动

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = support.offset(dx, 0, dz);
                if (level.isOutsideBuildHeight(p)) continue;
                BlockState current = level.getBlockState(p);
                if (current.isAir() || current.canBeReplaced()) {
                    level.setBlock(p, Blocks.OBSIDIAN.defaultBlockState(), 3);
                }
            }
        }
    }

    // ============================================================
    //  传送执行 ＋ 冷却
    // ============================================================

    /**
     * 把一个实体（玩家或生物）跨维度送到目标坐标；返回是否成功。
     *
     * <p>这里对**任何**实体都用 Forge 的 {@code Entity#teleportTo(ServerLevel, ...)}：
     * 目标维度不同时它会 {@code unRide()}、用 {@code getType().create(target)} 造一个新实体、
     * {@code restoreFrom} 拷 NBT、再把旧的标记成 {@code CHANGED_DIMENSION}、最后
     * {@code addDuringTeleport} ✓ —— 这正是 §666"生物也要能传送"要的东西 ✓。
     *
     * <p>⚠ <b>玩家侧与生物侧的返回值可靠性不一样</b>：
     * {@code ServerPlayer#teleportTo} <b>恒返回 true</b>（即便 Forge 的
     * {@code EntityTravelToDimensionEvent} 被别的模组取消，它也只是什么都不做）
     * ⇒ 调用方对玩家还要复核 {@code serverLevel()}；生物侧 {@code Entity#teleportTo} 的返回值可靠 ✓。
     *
     * <p>顺带一条（§666 依赖它）：{@code restoreFrom} 走的是 {@code saveWithoutId} / {@code load}，
     * 而 Forge 的持久化数据就存在 {@code ForgeData} 键里 ⇒ <b>传送前装好的冷却会被新实继承</b> ✓
     * ⇒ 落地就在对面那扇门里也不会被立刻弹回来 ✓。
     */
    public static boolean teleportEntity(Entity entity, ServerLevel target, BlockPos landing) {
        try {
            return entity.teleportTo(target,
                    landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                    Set.of(), entity.getYRot(), entity.getXRot());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[伟大白色空间] 跨维度传送异常：{}", t.toString());
            return false;
        }
    }

    /** 让这个实体在接下来 {@code ticks} tick 内不被门传送 */
    public static void armCooldown(Entity entity, int ticks) {
        entity.getPersistentData().putLong(COOLDOWN_KEY, entity.level().getGameTime() + ticks);
    }

    public static boolean isOnCooldown(Entity entity) {
        return entity.level().getGameTime() < entity.getPersistentData().getLong(COOLDOWN_KEY);
    }

    // ============================================================
    //  §678「只在走进门的那一下传送」——防两个维度之间乒乓
    // ============================================================

    /**
     * 记下"这一 tick 这个实体还在门方块里"（每次 {@code entityInside} 都要调，且要在任何 return 之前）。
     * <p>⚠ 用"最后一次在门里的游戏刻"来判断，而不是布尔标记：实体走出去以后不再收到
     * {@code entityInside} ⇒ 这个值就停在那一刻 ⇒ 隔几 tick 再走进来会被判定为"新的一次进入" ✓。
     */
    public static void markInsidePortal(Entity entity) {
        entity.getPersistentData().putLong(INSIDE_KEY, entity.level().getGameTime());
    }

    /**
     * 上一个 tick 这个实体也在门里吗（§678 用户实测口径：<b>走进去才传送</b>）。
     *
     * <h2>为什么要这条</h2>
     * 落点就是目标维度那扇门<b>下半格</b>的坐标 ⇒ <b>人一落地就站在门方块里</b> ✓
     * （§661 用户口径「自动记录相对落点」的必然结果）。
     * 旧实现只用一个落地冷却计时器挡着，冷却一到（80 tick ＝ 4 秒）人还在门里 ⇒
     * <b>又被原路弹回出发维度那扇门口</b> ✗ —— 用户实测的"落点变成原本在对应维度的位置"
     * 就是这个乒乓（日志里每 4~6 秒一个来回，间隔正好≈冷却）。
     *
     * <h2>⚠ 调用顺序（§680 踩过的坑，必须照这个来）</h2>
     * <b>先读、再 {@link #markInsidePortal} 打卡</b>：
     * <pre>
     * boolean wasInside = wasInsidePortalLastTick(entity);   // ① 先读旧值
     * markInsidePortal(entity);                              // ② 再打卡
     * if (wasInside) return;                                 // ③ 用①判断
     * </pre>
     * 反过来的话，打卡会把值写成"现在"⇒ `now - last == 0` 恒成立 ⇒ <b>永远判定为"刚进来过"，
     * 一次都传送不了</b> ✗（用户实测「站门里传送不了」就是这个 ✗）。
     *
     * <h2>判定</h2>
     * <ul>
     *   <li>上一 tick 也在门里（差值 ≤ 1）⇒ <b>这次不传送</b>（站着不动／刚落地待在门里都不会被弹走 ✓）；</li>
     *   <li>上一 tick 不在（走出去过）⇒ 正常传送（回程、反复进出都照常 ✓）；</li>
     *   <li>从没在门里过（{@code 0} ＝ 从未记过）⇒ 正常传送 ✓。
     *       ⚠ 游戏刻为 0 的新存档理论上会误判一次，但那种世界连门都还没有，
     *       且冷却兜底仍在 ⇒ 不处理 ✓。</li>
     * </ul>
     */
    public static boolean wasInsidePortalLastTick(Entity entity) {
        long now = entity.level().getGameTime();
        long last = entity.getPersistentData().getLong(INSIDE_KEY);
        return last != 0L && now - last <= 1L;
    }
}
