package com.mofengbaizhi.tinkersnewlife.content.portal;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlockEntity;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <b>伟大白色空间</b>（§660／§661）的维度常量、「开门」逻辑、传送黑名单与维度解锁。
 *
 * <h2>这个维度是什么</h2>
 * 连接相隔若干光年之地的奇异维度空间；古老者在地球上至少修过一座通往它的门。
 * 它只有 <b>2 层</b>：{@code y=0} 基岩层，{@code y=1} 一整层<b>不可破坏的白色方块</b>；
 * 天空<b>不随昼夜更替变化</b>，恒定一片白（维度类型 {@code fixed_time=6000} ＋ 客户端自定义
 * {@code DimensionSpecialEffects} 的白色雾色／{@code SkyType.NONE}）。
 *
 * <h2>门（传送门）的模型：永远成对 ＋ 自动记录落点</h2>
 * 通行证开出来的永远是<b>成对</b>的两扇门，互相指向对方 —— 这是「永久存在且可自由往返」的唯一做法：
 * <ul>
 *   <li><b>在伟大白色空间之外的（非黑名单）维度</b>对着方块用通行证 ⇒ GUI 里维度<b>锁死</b>为
 *       伟大白色空间、<b>不能填 y</b>，只需（可选地）改 x／z；门开在<b>该方块上方</b>，
 *       目的地 ＝ 伟大白色空间里那个 xz、{@code y = }{@link #GROUND_Y} 的对应落点。
 *       x／z 的默认值就是<b>门自己的 xz</b> ⇒ 「相对落点」自动记录 ✓。</li>
 *   <li><b>在伟大白色空间里</b>用通行证 ⇒ GUI 里下拉选维度 ＋ 填 xyz；门开在<b>使用的方块上方</b>，
 *       目的地是选的那个坐标，同时在该坐标处也开一扇指回来的门 ✓。</li>
 * </ul>
 * 目的地与「对面落点」都存在 {@link WhiteSpacePortalBlockEntity} 里 ⇒ 存档后依然有效（永久 ✓）。
 * 走进门时会调 {@link #resolveLanding}：对面那扇门要是没了（被拆／被炸），只要落点还是空地就
 * <b>按记录自动补回来</b>并指回原地 ⇒ 「自动记录相对落点，以便往返」在门被拆掉之后依然成立 ✓。
 *
 * <h2>传送黑名单（用户口径）</h2>
 * <b>狱门疆维度</b>（{@code tinkersnewlife:gourd}）与<b>铁魔法口袋维度</b>（{@code irons_spellbooks:pocket_dimension}）
 * 双向禁止：既不能把门开到那里，也不能在里面用通行证开门。
 * 前者尤其重要 —— 否则被封印在狱门疆里的囚徒可以直接用通行证越狱 ✗（§659 的整套封印逻辑就废了）。
 *
 * <h2>其他维度要「去过一次」才解锁</h2>
 * 只有玩家<b>到过</b>的维度才会出现在 GUI 的下拉列表里（记录写在玩家自己的持久化数据里，
 * 登录时记当前维度、每次跨维度时记目标维度 ⇒ 见 {@code WhiteSpaceUnlockHandler}）。
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

    /** 同一根柱子上一连串门最多堆到这一层（映射点被占了就往上找空位） */
    private static final int MAX_LINK_Y = 14;

    /** {@link #resolveLanding} 自愈时最多往上找几格 */
    private static final int MAX_HEAL_UP = 8;

    /** 玩家使用通行证的最大「手长」（防作弊：C2S 包里的锚点必须离玩家这么近） */
    public static final double MAX_REACH_SQR = 64.0D;

    /** 跨维度传送后玩家的冷却（tick）——4 秒，够走出门口 */
    public static final int ARRIVE_COOLDOWN = 80;

    /** 刚开完门的玩家冷却（tick）——5 秒，免得开完门站在原地被自己的门吸走 */
    public static final int CREATE_COOLDOWN = 100;

    /** 玩家持久化数据里存「下次可传送 tick」的键 */
    private static final String COOLDOWN_KEY = "tinkersnewlife:white_space_portal_next";

    /** 玩家持久化数据里存「去过的维度」的键（一个 CompoundTag，维度 id → true） */
    private static final String VISITED_KEY = "tinkersnewlife:visited_dimensions";

    // ============================================================
    //  传送黑名单
    // ============================================================

    /** 狱门疆维度（{@code GourdJailHandler.GOURD_DIM} 的同一条 key ⇒ 不引那个类，免得跨包耦合） */
    private static final ResourceKey<Level> GOURD_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(TinkersNewlife.MOD_ID, "gourd"));

    /**
     * 铁魔法（Iron's Spellbooks）的口袋维度。
     * <p>id 是从它自己的 jar 里查实的：{@code data/irons_spellbooks/dimension/pocket_dimension.json}
     * ⇒ 维度 key 是 {@code pocket_dimension}（{@code pocket_dimension_type} 是它的<b>维度类型</b>，不是 key ✗）。
     * 没装铁魔法时这条 key 永远不会被匹配上 ✓ 无需按模组判断 ✓。
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
     * @param level 玩家当前所在维度（不能是黑名单维度）
     * @param anchor 右键点到的那个方块（门开在它上方）
     * @param destX 白色空间侧的落点 x（默认＝门自己的 x ⇒ 相对落点）
     * @param destZ 白色空间侧的落点 z
     */
    public static PortalResult linkFromOutside(ServerLevel level, BlockPos anchor, int destX, int destZ) {
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

        PortalResult result = link(level, localPos, ws, remotePos);
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
                                                 ResourceKey<Level> destDim, BlockPos destPos) {
        if (isBlacklisted(destDim)) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blacklist_target",
                    displayName(destDim));
        }
        ServerLevel target = ws.getServer() == null ? null : ws.getServer().getLevel(destDim);
        if (target == null) return PortalResult.failure("message.tinkersnewlife.white_space.no_dimension");
        // 目标 y 必须落在那个维度自己的建筑高度里，否则 setBlock 会抛异常 / 静默失败
        if (destPos.getY() < target.getMinBuildHeight() || destPos.getY() >= target.getMaxBuildHeight()) {
            return PortalResult.failure("message.tinkersnewlife.white_space.bad_coords");
        }
        if (Math.abs(destPos.getX()) > 30000000 || Math.abs(destPos.getZ()) > 30000000) {
            return PortalResult.failure("message.tinkersnewlife.white_space.bad_coords");
        }

        BlockPos localPos = anchor.above();
        PortalResult result = link(ws, localPos, target, destPos);
        if (result.ok()) {
            return PortalResult.success("message.tinkersnewlife.white_space.opened",
                    displayName(destDim), coords(destPos));
        }
        return result;
    }

    // ============================================================
    //  开门底层
    // ============================================================

    /**
     * 建<b>一对</b>互通的门：{@code (aLevel, aPos)} ↔ {@code (bLevel, bPos)}。
     * <p>先建 b 侧、再建 a 侧（a 侧是玩家眼前那扇）；a 侧失败就把刚建好的 b 侧撤掉，
     * 绝不留一扇只能去、回不来的单向门 ✓。
     */
    private static PortalResult link(ServerLevel aLevel, BlockPos aPos, ServerLevel bLevel, BlockPos bPos) {
        if (aLevel == bLevel && aPos.equals(bPos)) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blocked");
        }
        if (!canHost(aLevel, aPos) || !canHost(bLevel, bPos)) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blocked");
        }

        boolean bExisted = bLevel.getBlockState(bPos).is(ModBlocks.WHITE_SPACE_PORTAL.get());
        if (!placePortal(bLevel, bPos, aLevel.dimension(), aPos)) {
            return PortalResult.failure("message.tinkersnewlife.white_space.blocked");
        }
        if (!placePortal(aLevel, aPos, bLevel.dimension(), bPos)) {
            if (!bExisted) bLevel.removeBlock(bPos, false);
            return PortalResult.failure("message.tinkersnewlife.white_space.blocked");
        }
        return PortalResult.success("message.tinkersnewlife.white_space.opened");
    }

    /** 这个位置能不能承载一扇门（空气 / 可替换 / 已经是我们的门 ⇒ 只改目的地） */
    private static boolean canHost(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced()
                || state.is(ModBlocks.WHITE_SPACE_PORTAL.get());
    }

    /**
     * 在 {@code pos} 放一扇门 / 把已有的门重新指向目的地。
     *
     * @return 该位置最终确实是一扇带方块实体的门
     */
    private static boolean placePortal(ServerLevel level, BlockPos pos,
                                       ResourceKey<Level> destDim, BlockPos destPos) {
        BlockState portal = ModBlocks.WHITE_SPACE_PORTAL.get().defaultBlockState();
        if (!level.getBlockState(pos).is(portal.getBlock())) {
            // 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS：会把新方块与新方块实体一起发给客户端 ✓
            level.setBlock(pos, portal, 3);
        }
        if (level.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity be) {
            be.setDestination(destDim, destPos);
            // ⚠ 必须显式 sendBlockUpdated：方块实体的数据只有在
            //   ChunkHolder.broadcastBlockEntityIfNeeded 里才会随 getUpdatePacket() 发出去
            //   （就是告示牌那一套），否则客户端那扇门没有目的地 ⇒ §661 的指向提示是空的 ✗
            level.sendBlockUpdated(pos, portal, portal, 3);
            return true;
        }
        return false;
    }

    /**
     * 白色空间里「相同 xz、地面表层」那个位置：
     * <ol>
     *   <li>如果那根柱子上<b>已经有一扇正好指回同一个地方</b>的门 ⇒ 直接复用它（重复使用同一根柱子
     *       不会越堆越高）✓；</li>
     *   <li>否则顺着柱子往上找<b>第一个空格</b>（别人的门不抢 ✗ —— 抢了会把那扇门的回路改掉，
     *       表现为"从 A 进去、从 B 出来"的不对称）；</li>
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
                if (ws.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity be
                        && backDim.equals(be.getDestinationDimension())
                        && backPos.equals(be.getDestinationPos())) {
                    return pos;
                }
                continue;
            }
            if (free == null && (state.isAir() || state.canBeReplaced())) {
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
    //  落点自愈（「自动记录相对落点，以便往返」）
    // ============================================================

    /**
     * 走进一扇门时先把<b>对面落点</b>落实：
     * <ol>
     *   <li>记录的落点上还是我们的门 ⇒ 用它（顺手把它的目的地校正回我们这边，双向自愈 ✓）；</li>
     *   <li>记录的落点变成空地（对面那扇被拆了／被炸了）⇒ <b>按记录自动补一扇指回来的门</b> ✓
     *       —— 这就是"自动记录相对落点，以便往返"在门被破坏之后仍然成立的原因；</li>
     *   <li>往上最多 {@link #MAX_HEAL_UP} 格找空位（玩家在落点上盖了房子的话，别把人埋进墙里 ✓）；</li>
     *   <li>整段都被实体方块占住 ⇒ 返回 {@code null}，门拒绝传送并提示（宁可不传，也不把玩家塞进方块里 ✗）。</li>
     * </ol>
     *
     * @param target   目的地维度
     * @param recorded 记录下来的落点
     * @param backDim  我们这边（出发侧）的维度
     * @param backPos  我们这边（出发侧）那扇门的位置
     * @return 实际可以落脚的坐标；null ＝ 落点被堵死
     */
    @Nullable
    public static BlockPos resolveLanding(ServerLevel target, BlockPos recorded,
                                         ResourceKey<Level> backDim, BlockPos backPos) {
        for (int dy = 0; dy <= MAX_HEAL_UP; dy++) {
            BlockPos pos = recorded.offset(0, dy, 0);
            if (target.isOutsideBuildHeight(pos)) break;
            BlockState state = target.getBlockState(pos);

            if (state.is(ModBlocks.WHITE_SPACE_PORTAL.get())) {
                if (target.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity be
                        && (!backDim.equals(be.getDestinationDimension())
                        || !backPos.equals(be.getDestinationPos()))) {
                    be.setDestination(backDim, backPos);
                    target.sendBlockUpdated(pos, state, state, 3);
                }
                return pos;
            }

            if (state.isAir() || state.canBeReplaced()) {
                if (isWhiteSpace(target)) ensureFloor(target, pos);
                if (placePortal(target, pos, backDim, backPos)) return pos;
            }
        }
        return null;
    }

    // ============================================================
    //  传送冷却（存在实体自己的持久化数据里，跟着实体走、自动随实体释放）
    // ============================================================

    /** 让这个实体在接下来 {@code ticks} tick 内不被门传送 */
    public static void armCooldown(Entity entity, int ticks) {
        entity.getPersistentData().putLong(COOLDOWN_KEY, entity.level().getGameTime() + ticks);
    }

    public static boolean isOnCooldown(Entity entity) {
        return entity.level().getGameTime() < entity.getPersistentData().getLong(COOLDOWN_KEY);
    }
}
