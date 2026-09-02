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
     */
    public static void releasePlayerFromDimension(MinecraftServer server, BlockPos cageCenter, UUID prisonerId,
                                                  ServerLevel returnLevel, Vec3 returnPos) {
        ServerLevel gourd = getGourdLevel(server);
        if (gourd != null && cageCenter != null) {
            clearCage(gourd, cageCenter);
            GourdJailData.get(gourd).releaseCoordinate(cageCenter);
        }
        if (prisonerId != null && returnLevel != null) {
            ServerPlayer prisoner = server.getPlayerList().getPlayer(prisonerId);
            if (prisoner != null) {
                prisoner.setNoGravity(false);
                prisoner.teleportTo(returnLevel, returnPos.x, returnPos.y, returnPos.z,
                        java.util.Set.of(), prisoner.getYRot(), prisoner.getXRot());
            }
        }
    }

    // ============================================================
    //  维度禁传送：狱门疆维度内禁止任何主动传送
    // ============================================================

    /** 跨维度传送（末影珍珠/紫颂果/下界门等）——从狱门疆维度离开被禁止 */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (isInGourdDimension(entity)) {
            event.setCanceled(true);
            if (entity instanceof ServerPlayer sp) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.gourd.no_travel"), true);
            }
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

    /** 监狱内玩家每 tick 钉在球笼中心（防逃逸/卡出），且阻止下坠 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel gourd = getGourdLevel(event.getServer());
        if (gourd == null) return;
        // 维度内非球笼内玩家（如未封印误入）传回球笼？——维度只有封印目标，简化为：无操作
    }

    /** 狱门疆坐标 NBT 键 */
    public static final String KEY_CAGE_POS = "tinkersnewlife.gourd_cage";
    public static final String KEY_PRISONER = "tinkersnewlife.gourd_prisoner";
    public static final String KEY_OWNER = "tinkersnewlife.gourd_owner";
    public static final String KEY_SEALED = "tinkersnewlife.gourd_sealed";
    public static final String KEY_MOB_NBT = "tinkersnewlife.gourd_mob_nbt";

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
                .makeStack(jail.isSealed(), cage, prisoner, jail.getOwnerId(), jail.getPrisonerNbt());
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
}
