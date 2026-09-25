package com.mofengbaizhi.tinkersnewlife.content.portal;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>伟大白色空间</b>（§660）的维度常量与「开门」逻辑。
 *
 * <h2>这个维度是什么</h2>
 * 连接相隔若干光年之地的奇异维度空间；古老者在地球上至少修过一座通往它的门。
 * 它只有 <b>2 层</b>：{@code y=0} 基岩层，{@code y=1} 一整层<b>不可破坏的白色方块</b>；
 * 天空<b>不随昼夜更替变化</b>，恒定一片白（维度类型 {@code fixed_time=6000} + 客户端自定义
 * {@code DimensionSpecialEffects} 的白色雾色 / {@code SkyType.NONE}，见
 * {@code client/renderer/WhiteSpaceDimensionEffects}）。
 *
 * <h2>门（传送门）的模型</h2>
 * 通行证开出来的是<b>成对</b>的两扇门，互相指向对方 —— 这是「永久存在且可自由往返」的唯一做法：
 * <ul>
 *   <li><b>在非白色空间</b>（主世界等）对着某方块用通行证：门开在<b>该方块上方</b>，
 *       目的地 = 白色空间里<b>相同 xz</b>、{@code y = }{@link #GROUND_Y}（地面表层）的对应位置；
 *       同时在白色空间那个对应位置也开一扇门指回来。</li>
 *   <li><b>在白色空间里</b>用通行证：先弹 GUI 选维度 + 填 xyz，门开在<b>使用的方块上方</b>，
 *       目的地就是选的那个坐标；同时在该坐标处开一扇门指回来。</li>
 * </ul>
 * 门的位置与目的地都存在 {@link WhiteSpacePortalBlockEntity} 里 ⇒ 存档后依然有效 ✓ 永久 ✓。
 *
 * <h2>传送冷却</h2>
 * 玩家身上（Forge 的 {@code Entity#getPersistentData()}）记一个「下次可传送的 tick」，
 * 避免刚落地就被对面那扇门又送回去（两扇门互相指向对方 ⇒ 没有冷却会变成乒乓球）✓。
 */
public final class WhiteSpaceDimensions {

    private WhiteSpaceDimensions() {
    }

    /** 伟大白色空间的维度 key（数据文件 {@code data/tinkersnewlife/dimension/great_white_space.json} + 同名 dimension_type） */
    public static final ResourceKey<Level> WHITE_SPACE = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(TinkersNewlife.MOD_ID, "great_white_space"));

    /** 白色方块那一层（{@code dimension/great_white_space.json} 的 flat layers 第 2 层 ⇒ min_y + 1） */
    public static final int FLOOR_Y = 1;

    /** 地面表层：站上去脚底所在的 y（门就开在这一层） */
    public static final int GROUND_Y = 2;

    /** 同一根柱子上一连串门最多堆到这一层（映射点被占了就往上找空位） */
    private static final int MAX_LINK_Y = 14;

    /** 玩家使用通行证的最大「手长」（防作弊：C2S 包里的锚点必须离玩家这么近） */
    public static final double MAX_REACH_SQR = 64.0D;

    /** 跨维度传送后玩家的冷却（tick）——4 秒，够走出门口 */
    public static final int ARRIVE_COOLDOWN = 80;

    /** 刚开完门的玩家冷却（tick）——5 秒，免得开完门站在原地被自己的门吸走 */
    public static final int CREATE_COOLDOWN = 100;

    /** 玩家持久化数据里存「下次可传送 tick」的键 */
    private static final String COOLDOWN_KEY = "tinkersnewlife:white_space_portal_next";

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

    /**
     * 服务端当前<b>已加载</b>的全部维度 id（给 GUI 的下拉列表用）。
     * <p>含未加载维度是没意义的（{@code server.getLevel} 会返回 null）✓ 所以直接取 {@code levelKeys()}。
     */
    public static List<String> dimensionIds(@Nullable MinecraftServer server) {
        if (server == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (ResourceKey<Level> key : server.levelKeys()) {
            ids.add(key.location().toString());
        }
        Collections.sort(ids);
        return ids;
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
     * <b>在非白色空间</b>用通行证：门开在 {@code anchor} 上方，映射到白色空间相同 xz 的地面表层。
     *
     * @param level 玩家当前所在维度（必须不是白色空间）
     * @param anchor 右键点到的那个方块
     */
    public static PortalResult linkFromOutside(ServerLevel level, BlockPos anchor) {
        ServerLevel ws = getWhiteSpace(level.getServer());
        if (ws == null) return PortalResult.failure("message.tinkersnewlife.white_space.no_dimension");

        BlockPos localPos = anchor.above();
        BlockPos remotePos = findLinkSpot(ws, anchor.getX(), anchor.getZ(), level.dimension(), localPos);
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
     * <b>在白色空间里</b>用通行证：门开在 {@code anchor} 上方，目的地是 GUI 里选的那个坐标。
     *
     * @param ws     白色空间
     * @param anchor 右键点到的那个方块
     * @param destDim 目标维度
     * @param destPos 目标坐标
     */
    public static PortalResult linkFromWhiteSpace(ServerLevel ws, BlockPos anchor,
                                                 ResourceKey<Level> destDim, BlockPos destPos) {
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
