package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.DomainVisualEntity;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 领域注册表（通用管理）
 * <p>
 * 管理所有玩家展开中的领域：切换开关、每 tick 驱动（消耗/困锁/子类逻辑/视觉）、
 * 关闭清理（按键关闭、咒力耗尽、领域被破坏、死亡/登出/脱下核心）。
 * 具体领域的创建通过工厂函数传入（工厂内校验条件并提示，条件不满足返回 null）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DomainRegistry {

    private static final Map<UUID, BaseDomain> DOMAINS = new ConcurrentHashMap<>();
    /** 主人 → 视觉实体 ID（便于关闭时移除） */
    private static final Map<UUID, Integer> VISUAL_ENTITY_IDS = new ConcurrentHashMap<>();
    /** 领域特性注册表：领域修饰符 → 领域工厂（咒力核心只能装一个领域槽，按修饰符匹配展开） */
    private static final Map<ModifierId, Function<ServerPlayer, BaseDomain>> DOMAIN_FACTORIES = new ConcurrentHashMap<>();

    private DomainRegistry() {}

    /** 注册领域特性：修饰符 ID → 领域工厂（后续新增领域在此登记） */
    public static void registerDomain(ModifierId modifierId, Function<ServerPlayer, BaseDomain> factory) {
        DOMAIN_FACTORIES.put(modifierId, factory);
    }

    public static boolean isActive(UUID playerId) {
        return DOMAINS.containsKey(playerId);
    }

    /** 玩家当前展开的领域（无则 null） */
    public static BaseDomain get(UUID playerId) {
        return DOMAINS.get(playerId);
    }

    // ============================================================
    //  通用领域展开键
    // ============================================================

    /**
     * 通用领域展开/关闭：已展开则关闭；
     * 未展开则扫描佩戴咒力核心上的领域特性，展开对应的领域（咒力核心仅 1 个领域槽）。
     */
    public static void toggleDomain(ServerPlayer player) {
        UUID id = player.getUUID();
        if (DOMAINS.containsKey(id)) {
            close(player, "message.tinkersnewlife.domain.closed");
            // 生存模式：手动关闭领域 → 术式熔断
            applyBurnoutIfSurvival(player);
            return;
        }

        // 术式熔断期间无法再次展开领域（创造模式豁免，便于测试）
        if (!player.isCreative() && CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }

        // 扫描佩戴咒力核心上的领域特性
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_core"), true);
            return;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return;
        for (ModifierEntry entry : tool.getModifierList()) {
            Function<ServerPlayer, BaseDomain> factory = DOMAIN_FACTORIES.get(entry.getId());
            if (factory == null) continue;
            BaseDomain domain = factory.apply(player);
            if (domain == null) return; // 工厂内已提示条件不满足（如咒力不足）
            DOMAINS.put(id, domain);
            domain.onOpen(player);
            spawnVisual(player.serverLevel(), domain);
            domain.buildBarrier(player.serverLevel());
            // 展开时：给领域内所有玩家显示领域名大标题
            broadcastDomainTitle(player.serverLevel(), domain);
            return;
        }
        // 核心上没有已注册的领域特性
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_trait"), true);
    }

    /** 关闭领域（按键/咒力耗尽/领域被破坏等）：移除视觉与阻挡墙 */
    public static void close(ServerPlayer player, String messageKey) {
        BaseDomain domain = DOMAINS.remove(player.getUUID());
        if (domain == null) return;
        ServerLevel level = player.serverLevel();
        removeVisual(level, player.getUUID());
        domain.removeBarrier(level);
        domain.onClose(player, messageKey);
        if (player.isAlive()) {
            player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    /** 无条件移除领域（死亡/登出/脱下核心/服务器停止）：不发送消息 */
    private static void forceRemove(ServerPlayer player, BaseDomain domain) {
        ServerLevel level = player != null ? player.serverLevel() : null;
        removeVisual(level, domain.getOwner());
        domain.removeBarrier(level);
        // ⭐ 必须调用 onClose：无量空处等需要为被定身实体设置延续时长，
        // 否则保持 10 万 tick 的静止效果 → 永久定身
        domain.onClose(player, null);
    }

    /**
     * 术式熔断（生存模式专属）：手动关闭领域或领域被破坏后进入熔断状态，
     * 期间无法再次展开领域。创造/旁观模式豁免（便于测试），咒力耗尽不算熔断。
     */
    private static void applyBurnoutIfSurvival(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return;
        CursePowerHelper.applyBurnout(player);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.entered",
                CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
    }

    // ============================================================
    //  每 tick 驱动
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long now = server.getTickCount();

        // 快照迭代：对抗检测/结算需要同时看到双方领域
        java.util.List<BaseDomain> snapshot = new java.util.ArrayList<>(DOMAINS.values());
        for (BaseDomain domain : snapshot) {
            ServerPlayer player = server.getPlayerList().getPlayer(domain.getOwner());
            if (player == null || !player.isAlive()
                    || !player.level().hasChunkAt(BlockPos.containing(domain.getCenter()))) {
                DOMAINS.remove(domain.getOwner());
                forceRemove(player, domain);
                continue;
            }

            // 领域被破坏（咒力核心被取下 / 特性丢失等）→ 生存模式术式熔断
            if (!domain.isValid(player)) {
                DOMAINS.remove(domain.getOwner());
                forceRemove(player, domain);
                domain.onClose(player, "message.tinkersnewlife.domain.broken");
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.broken"), true);
                applyBurnoutIfSurvival(player);
                continue;
            }

            // 领域对抗：先结算/检测，再消耗
            boolean clashing = domain.isClashing();
            if (clashing) {
                // 对手领域已关闭（败者）→ 本领域胜出：恢复效果、重建球壳、拉入败者
                BaseDomain opponent = DOMAINS.get(domain.getClashOpponent());
                if (opponent == null) {
                    endClashVictory(server, player, domain);
                    clashing = false;
                }
            } else {
                // 未在对抗：检测与其它领域球体是否相交
                tryStartClash(server, player, domain);
                clashing = domain.isClashing();
            }

            // 咒力消耗（耗尽自动关闭；对抗期间消耗已按倍率放大）
            if (!domain.spendCurse(player)) {
                DOMAINS.remove(domain.getOwner());
                forceRemove(player, domain);
                domain.onClose(player, "message.tinkersnewlife.domain.exhausted");
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.exhausted"), true);
                continue;
            }

            // 对抗中：领域效果与困锁暂时失效（空间已合并，双方效果停摆）
            if (clashing) continue;

            // 通用外壳：困锁生物（每 5 tick）
            if (now % 5 == 0) {
                domain.clampEntities(player.level());
            }

            // 子类逻辑（抽奖等）
            domain.onTick(player, now);
        }
    }

    // ============================================================
    //  领域对抗：球体相交 → 空间合并，效果停摆，消耗加剧；一方耗尽则败者被拉入胜者领域
    // ============================================================

    /**
     * 检测本领域是否与其它活跃领域球体相交；相交则双方进入对抗：
     * - 移除双方重合部分的阻挡墙（打通空间）
     * - 双方领域效果暂时失效（onTick 暂停）
     * - 双方消耗变为 (1+(对方输出等级+对方亲和/10)/100) × 原消耗
     */
    private static void tryStartClash(MinecraftServer server, ServerPlayer player, BaseDomain domain) {
        for (BaseDomain other : DOMAINS.values()) {
            if (other == domain) continue;
            if (other.isClashing()) continue; // 对方已在对抗（一领域对一对手）
            ServerPlayer otherPlayer = server.getPlayerList().getPlayer(other.getOwner());
            if (otherPlayer == null || !otherPlayer.isAlive()) continue;

            Vec3 delta = domain.getCenter().subtract(other.getCenter());
            double rSum = domain.getRadius() + other.getRadius();
            if (delta.lengthSqr() >= rSum * rSum) continue; // 不相交

            // 消耗倍率：本领域用"对方"的输出/亲和；对方用"本领域"的
            double otherStats = CursePowerHelper.getCurseOutputLevel(otherPlayer)
                    + CursePowerHelper.getCurseAffinity(otherPlayer) / 10.0;
            double thisStats = CursePowerHelper.getCurseOutputLevel(player)
                    + CursePowerHelper.getCurseAffinity(player) / 10.0;
            domain.setClash(other.getOwner(), 1.0 + otherStats / 100.0);
            other.setClash(domain.getOwner(), 1.0 + thisStats / 100.0);

            ServerLevel level = player.serverLevel();
            // 打通重合部分：移除双方阻挡墙的重叠块
            domain.removeBarrierOverlap(level, other.getCenter(), other.getRadius());
            other.removeBarrierOverlap(level, domain.getCenter(), domain.getRadius());

            // 双方领域效果暂停
            domain.onClashStart(player, other);
            other.onClashStart(otherPlayer, domain);

            // 客户端：隐藏双方黑色球壳的重合部分边缘
            syncClashVisual(level, domain, other);
            syncClashVisual(level, other, domain);

            // 提示双方
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.start"), true);
            otherPlayer.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.start"), true);
            return;
        }
    }

    /** 对抗结束（本领域胜出）：恢复领域效果、重建完整球壳、把败者强行拉入本领域 */
    private static void endClashVictory(MinecraftServer server, ServerPlayer winner, BaseDomain domain) {
        UUID loserId = domain.getClashOpponent();
        ServerPlayer loser = server.getPlayerList().getPlayer(loserId);
        domain.clearClash();

        // 重建完整球壳（补回对抗期间移除的重合部分）
        domain.buildBarrier(winner.serverLevel());
        // 领域效果恢复
        domain.onClashEnd(winner, null);
        // 客户端：恢复完整黑色球壳
        clearClashVisual(winner.serverLevel(), domain);

        // 败者被强行拉入胜者领域（仅同维度且在线存活时）
        if (loser != null && loser.isAlive()
                && loser.level().dimension() == winner.level().dimension()) {
            Vec3 target = domain.getClashPullTarget(winner.serverLevel());
            loser.teleportTo(target.x, target.y, target.z);
            loser.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.lose"), true);
        }
        winner.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.win"), true);
    }

    /** 客户端视觉：让视觉实体隐藏落入对方球体内的黑色边缘部分 */
    private static void syncClashVisual(ServerLevel level, BaseDomain domain, BaseDomain opponent) {
        Integer entityId = VISUAL_ENTITY_IDS.get(domain.getOwner());
        if (entityId != null && level.getEntity(entityId) instanceof DomainVisualEntity visual) {
            visual.setClashRegion(opponent.getCenter(), opponent.getRadius());
        }
    }

    /** 客户端视觉：恢复完整黑色球壳 */
    private static void clearClashVisual(ServerLevel level, BaseDomain domain) {
        Integer entityId = VISUAL_ENTITY_IDS.get(domain.getOwner());
        if (entityId != null && level.getEntity(entityId) instanceof DomainVisualEntity visual) {
            visual.clearClashRegion();
        }
    }

    // ============================================================
    //  视觉：纯黑色空心圆球（线框）
    // ============================================================

    private static void spawnVisual(ServerLevel level, BaseDomain domain) {
        if (level == null) return;
        DomainVisualEntity visual = new DomainVisualEntity(level,
                domain.getCenter().x, domain.getCenter().y, domain.getCenter().z,
                domain.getRadius(), domain.getOwner());
        level.addFreshEntity(visual);
        VISUAL_ENTITY_IDS.put(domain.getOwner(), visual.getId());
    }

    private static void removeVisual(ServerLevel level, UUID ownerId) {
        Integer entityId = VISUAL_ENTITY_IDS.remove(ownerId);
        if (entityId == null) return;
        if (level == null) return;
        if (level.getEntity(entityId) instanceof DomainVisualEntity visual) {
            visual.discard();
        }
    }

    /** 展开时：给领域范围内所有玩家显示领域名大标题 */
    private static void broadcastDomainTitle(ServerLevel level, BaseDomain domain) {
        double r = domain.getRadius();
        net.minecraft.network.chat.Component title = net.minecraft.network.chat.Component.translatable(domain.getDomainNameKey());
        net.minecraft.network.chat.Component subtitle = net.minecraft.network.chat.Component.translatable("title.tinkersnewlife.domain.open");
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(domain.getCenter().x - r, domain.getCenter().y - r, domain.getCenter().z - r,
                        domain.getCenter().x + r, domain.getCenter().y + r, domain.getCenter().z + r))) {
            if (p.position().distanceToSqr(domain.getCenter()) > r * r) continue;
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    // ============================================================
    //  关闭条件：死亡 / 登出 / 脱下咒力核心
    // ============================================================

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            BaseDomain domain = DOMAINS.remove(player.getUUID());
            if (domain != null) {
                forceRemove((ServerPlayer) player, domain);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        BaseDomain domain = DOMAINS.remove(player.getUUID());
        if (domain != null) {
            forceRemove(player instanceof ServerPlayer sp ? sp : null, domain);
        }
    }

    @SubscribeEvent
    public static void onCurioUnequip(CurioUnequipEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            BaseDomain domain = DOMAINS.remove(player.getUUID());
            if (domain != null) {
                forceRemove((ServerPlayer) player, domain);
            }
        }
    }

    /** 服务器停止：清理所有领域阻挡墙（防止写入存档残留隐形方块） */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        for (BaseDomain domain : DOMAINS.values()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(domain.getOwner());
            forceRemove(player, domain);
        }
        DOMAINS.clear();
        VISUAL_ENTITY_IDS.clear();
    }
}
