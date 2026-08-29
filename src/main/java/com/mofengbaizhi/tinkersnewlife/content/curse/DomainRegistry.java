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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

import java.util.Iterator;
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

        Iterator<Map.Entry<UUID, BaseDomain>> it = DOMAINS.entrySet().iterator();
        while (it.hasNext()) {
            BaseDomain domain = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(domain.getOwner());
            if (player == null || !player.isAlive()
                    || !player.level().hasChunkAt(BlockPos.containing(domain.getCenter()))) {
                it.remove();
                forceRemove(player, domain);
                continue;
            }

            // 领域被破坏（咒力核心被取下 / 特性丢失等）
            if (!domain.isValid(player)) {
                it.remove();
                forceRemove(player, domain);
                domain.onClose(player, "message.tinkersnewlife.domain.broken");
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.broken"), true);
                continue;
            }

            // 咒力消耗（耗尽自动关闭）
            if (!domain.spendCurse(player)) {
                it.remove();
                forceRemove(player, domain);
                domain.onClose(player, "message.tinkersnewlife.domain.exhausted");
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.exhausted"), true);
                continue;
            }

            // 通用外壳：困锁生物（每 5 tick）
            if (now % 5 == 0) {
                domain.clampEntities(player.level());
            }

            // 子类逻辑（抽奖等）
            domain.onTick(player, now);
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
