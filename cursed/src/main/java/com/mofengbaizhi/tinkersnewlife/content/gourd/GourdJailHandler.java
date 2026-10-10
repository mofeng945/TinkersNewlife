package com.mofengbaizhi.tinkersnewlife.content.gourd;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * 狱门疆系统核心：维度访问、封印坐标、基岩球笼、封印/释放、维度禁传送。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GourdJailHandler {

    /** 狱门疆维度 */
    public static final ResourceKey<net.minecraft.world.level.Level> GOURD_DIM =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(TinkersNewlife.MOD_ID, "gourd"));
    /** 基岩球笼半径（格） */
    public static final int CAGE_RADIUS = 20;

    /** Boss 封印=击败时压制的掉落标记：本次死亡不掉战利品/经验，解除封印后打死再正常掉落 */
    public static final String KEY_SUPPRESS_LOOT = "tinkersnewlife.gourd_suppress_loot";

    /** 获取狱门疆维度服务端实例（不存在则返回 null） */
    public static ServerLevel getGourdLevel(MinecraftServer server) {
        return server.getLevel(GOURD_DIM);
    }

    /** 实体是否位于狱门疆维度 */
    public static boolean isInGourdDimension(Entity entity) {
        return entity.level().dimension().equals(GOURD_DIM);
    }

    // ============================================================
    //  封印
    // ============================================================

    /**
     * 封印玩家：分配坐标 → 生成基岩球笼 → 传送玩家到球笼中心。
     * 普通生物不走维度（由狱门疆实体记录 NBT 后清除，释放时重新生成）。
     * 返回分配的球笼中心坐标；失败返回 null。
     */
    public static BlockPos sealPlayerToDimension(MinecraftServer server, ServerPlayer victim) {
        ServerLevel gourd = getGourdLevel(server);
        if (gourd == null) return null;
        GourdJailData data = GourdJailData.get(gourd);
        BlockPos center = data.assignCoordinate();
        buildCage(gourd, center);
        victim.teleportTo(gourd, center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                java.util.Set.of(), victim.getYRot(), victim.getXRot());
        victim.setNoGravity(true);
        // ⭐ 登记封印名单：登录/每 tick 兜底判定用（球笼已拆还留在维度里 → 必须送出维度）
        data.registerPrisoner(victim.getUUID(), center);
        return center;
    }

    /** 生成半径 20 格的空心基岩球体（牢笼），中心留空 */
    public static void buildCage(ServerLevel gourd, BlockPos center) {
        int r = CAGE_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d < r - 0.5 || d > r + 0.5) continue;
                    gourd.setBlock(center.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
    }

    /** 清除半径 20 格的基岩球笼（释放时） */
    public static void clearCage(ServerLevel gourd, BlockPos center) {
        int r = CAGE_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d < r - 0.5 || d > r + 0.5) continue;
                    BlockPos pos = center.offset(x, y, z);
                    if (gourd.getBlockState(pos).is(Blocks.BEDROCK)) {
                        gourd.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /**
     * 释放被封印的玩家：清除球笼 → 解放坐标 → 玩家传送回释放位置（狱门疆所在世界）。
     * <p>
     * ⭐ 修复：跨维度传送（{@code ServerPlayer#teleportTo(ServerLevel,...)}）在 Forge 里会先抛
     * {@code EntityTravelToDimensionEvent}，而本类自己的「狱门疆维度禁传送」拦截会把它取消 →
     * 旧代码只清掉了球笼却没能把囚徒送出来，囚徒留在空球笼位置<b>坠入虚空</b>。
     * 现在释放传送走 {@link #teleportOut}（带放行标记），并且囚徒不在线/传送失败时记入
     * 「待送回名单」，由每 tick 的兜底逻辑持续重试。
     */
    public static void releasePlayerFromDimension(MinecraftServer server, BlockPos cageCenter, UUID prisonerId,
                                                  ServerLevel returnLevel, Vec3 returnPos) {
        ServerLevel gourd = getGourdLevel(server);
        GourdJailData data = gourd == null ? null : GourdJailData.get(gourd);
        if (gourd != null && cageCenter != null) {
            clearCage(gourd, cageCenter);
            GourdJailData.get(gourd).releaseCoordinate(cageCenter);
        }
        if (prisonerId == null || returnLevel == null) return;
        if (data != null) data.unregisterPrisoner(prisonerId);
        ServerPlayer prisoner = server.getPlayerList().getPlayer(prisonerId);
        if (prisoner != null) {
            if (!teleportOut(prisoner, returnLevel, returnPos)) {
                // 传送被别的模组拦截等：记为待送回，tick 里持续重试（此时保持无重力，不下坠）
                if (data != null) data.queuePendingReturn(prisonerId, returnLevel.dimension(), returnPos);
            }
            prisoner.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.tinkersnewlife.gourd.unsealed"), true);
        } else if (data != null) {
            // ⭐ 囚徒不在线：球笼已经拆了，他上线时会被放进虚空 → 记下返回点，一上线立刻送出
            data.queuePendingReturn(prisonerId, returnLevel.dimension(), returnPos);
        }
    }

    /** 正在执行「释放出狱」传送的玩家：跨维度传送时放行（绕过狱门疆禁传送拦截） */
    private static final java.util.Set<UUID> RELEASING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static boolean isReleasing(UUID id) {
        return id != null && RELEASING.contains(id);
    }

    /**
     * 把玩家送出当前维度（释放出狱专用）：临时加入放行名单，
     * 避免被自己的 {@link #onTravelToDimension} 取消。
     * 返回是否真的换了维度；失败时恢复原重力状态（防在虚空里下坠）。
     */
    private static boolean teleportOut(ServerPlayer prisoner, ServerLevel target, Vec3 pos) {
        boolean wasNoGravity = prisoner.isNoGravity();
        RELEASING.add(prisoner.getUUID());
        try {
            prisoner.setNoGravity(false);
            prisoner.teleportTo(target, pos.x, pos.y, pos.z, java.util.Set.of(),
                    prisoner.getYRot(), prisoner.getXRot());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[狱门疆] 释放传送异常：{}", t.toString());
        } finally {
            RELEASING.remove(prisoner.getUUID());
        }
        if (prisoner.serverLevel() == target) return true;
        prisoner.setNoGravity(wasNoGravity);   // 没送出去：维持原状，等兜底重试
        return false;
    }

    // ============================================================
    //  维度禁传送：狱门疆维度内禁止任何主动传送
    // ============================================================

    /** 跨维度传送（末影珍珠/紫颂果/下界门等）——从狱门疆维度离开被禁止（释放出狱放行） */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (!isInGourdDimension(entity)) return;
        // ⭐ 释放出狱的传送必须放行，否则囚徒出不来（见 releasePlayerFromDimension）
        if (entity instanceof ServerPlayer sp && isReleasing(sp.getUUID())) return;
        event.setCanceled(true);
        if (entity instanceof ServerPlayer sp) {
            sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.tinkersnewlife.gourd.no_travel"), true);
        }
    }

    /** 食用末影珍珠/紫颂果等传送物品——狱门疆维度内右键即拦截 */
    @SubscribeEvent
    public static void onRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity().level().dimension().equals(GOURD_DIM)
                && (event.getItemStack().is(net.minecraft.world.item.Items.ENDER_PEARL)
                || event.getItemStack().is(net.minecraft.world.item.Items.CHORUS_FRUIT))) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.tinkersnewlife.gourd.no_travel"), true);
        }
    }

    /**
     * 狱门疆维度兜底（每 tick）：
     * <ol>
     *   <li><b>待送回名单</b>：囚徒不在线时被释放（或释放传送失败）→ 一上线就送回原处；</li>
     *   <li><b>非法滞留</b>：既不在封印名单、又不在任何已占用球笼附近 → 说明球笼已拆（记录丢失/
     *       旧存档/异常）却还留在维度里，直接送回主世界出生点，<b>绝不让他坠入虚空</b>；</li>
     *   <li><b>旧存档补登记</b>：站在已占用球笼中心的人，补进封印名单（升级前封印的囚徒不受影响）。</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        ServerLevel gourd = getGourdLevel(server);
        if (gourd == null) return;
        GourdJailData data = GourdJailData.get(gourd);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!isInGourdDimension(sp)) continue;
            // 1) 待送回
            GourdJailData.PendingReturn pending = data.pollPendingReturn(sp.getUUID());
            if (pending != null) {
                ServerLevel target = resolveLevel(server, pending.dimension());
                if (target == null) target = server.overworld();
                if (teleportOut(sp, target, new Vec3(pending.x(), pending.y(), pending.z()))) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.tinkersnewlife.gourd.unsealed"), true);
                } else {
                    data.queuePendingReturn(sp.getUUID(), target.dimension(),
                            new Vec3(pending.x(), pending.y(), pending.z()));
                }
                continue;
            }
            // 2) 封印名单内的正常囚徒：不动
            if (data.isSealed(sp.getUUID())) continue;
            // 3) 旧存档/记录丢失：站在已占用球笼中心 → 补登记，照旧关押
            BlockPos near = data.occupiedNear(sp.blockPosition(), 4.0);
            if (near != null) {
                data.registerPrisoner(sp.getUUID(), near);
                continue;
            }
            // 4) 球笼已拆却滞留维度 → 送出维度（防坠虚空）
            ServerLevel overworld = server.overworld();
            Vec3 spawn = Vec3.atBottomCenterOf(overworld.getSharedSpawnPos());
            if (teleportOut(sp, overworld, spawn)) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.gourd.void_escape"), true);
                TinkersNewlife.LOGGER.info("[狱门疆] {} 球笼已拆除却滞留狱门疆维度，已送回主世界",
                        sp.getName().getString());
            }
        }
    }

    /** 按维度 id 找服务端世界（找不到返回 null → 调用方回退主世界） */
    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        try {
            net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(dimensionId);
            return server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, rl));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 狱门疆坐标 NBT 键 */
    public static final String KEY_CAGE_POS = "tinkersnewlife.gourd_cage";
    public static final String KEY_PRISONER = "tinkersnewlife.gourd_prisoner";
    public static final String KEY_OWNER = "tinkersnewlife.gourd_owner";
    public static final String KEY_SEALED = "tinkersnewlife.gourd_sealed";
    public static final String KEY_MOB_NBT = "tinkersnewlife.gourd_mob_nbt";
    /** 已封印物品上保存的被封印玩家名字（生物名可从 MOB_NBT 推导，无需单独存） */
    public static final String KEY_PRISONER_NAME = "tinkersnewlife.gourd_prisoner_name";

    /** 辅助：坐标 <-> NBT */
    public static void writePos(CompoundTag tag, String key, BlockPos pos) {
        if (pos != null) tag.putLong(key, pos.asLong());
    }

    public static BlockPos readPos(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }

    // ============================================================
    //  交互：拾取（仅放置者）/ 天逆鉾释放
    // ============================================================

    @SubscribeEvent
    public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getTarget() instanceof GourdJailEntity jail)) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;
        // 天逆鉾右键已封印的狱门疆 → 破坏并释放囚犯
        ItemStack held = sp.getMainHandItem();
        boolean isTianNiHuo = held.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.TianNiHuoItem;
        if (isTianNiHuo && jail.isSealed()) {
            releaseByTool(sp, jail);
            event.setCanceled(true);
            return;
        }
        // 仅放置者拾取
        if (jail.getOwnerId() != null && jail.getOwnerId().equals(sp.getUUID())) {
            pickUp(sp, jail);
            event.setCanceled(true);
        }
    }

    /** 拾取狱门疆实体 → 手中物品（按形态），实体消失 */
    private static void pickUp(ServerPlayer player, GourdJailEntity jail) {
        BlockPos cage = jail.getCagePos();
        UUID prisoner = jail.getPrisoner();
        ItemStack stack = com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailItem
                .makeStack(jail.isSealed(), cage, prisoner, jail.getOwnerId(), jail.getPrisonerNbt(),
                        jail.getPrisonerName());
        if (!player.getInventory().add(stack)) {
            // 背包满：掉落
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
                    jail.level(), jail.getX(), jail.getY(), jail.getZ(), stack);
            jail.level().addFreshEntity(item);
        }
        jail.discard();
    }

    /** 天逆鉾破坏已封印狱门疆：释放囚犯，狱门疆消失 */
    private static void releaseByTool(ServerPlayer player, GourdJailEntity jail) {
        jail.releasePrisonerAndDestroy();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.tinkersnewlife.gourd.released"), true);
    }

    // ============================================================
    //  Boss 封印=击败：本次死亡不掉战利品/经验（解除封印后打死再正常掉落）
    // ============================================================

    @SubscribeEvent
    public static void onBossDrops(LivingDropsEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(KEY_SUPPRESS_LOOT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBossXp(LivingExperienceDropEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(KEY_SUPPRESS_LOOT)) {
            event.setCanceled(true);
        }
    }
}
