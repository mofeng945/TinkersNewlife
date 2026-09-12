package com.mofengbaizhi.tinkersnewlife.content.curse.domain;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.BaseDomain;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;

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

    /** 全部已注册领域修饰符 id（供剥离/槽位配方等遍历；新增领域自动包含） */
    public static java.util.Set<ModifierId> getAllDomainIds() {
        return DOMAIN_FACTORIES.keySet();
    }

    /** 该修饰符是否为本模组已注册的领域 */
    public static boolean isDomain(ModifierId id) {
        return DOMAIN_FACTORIES.containsKey(id);
    }

    public static boolean isActive(UUID playerId) {
        return DOMAINS.containsKey(playerId);
    }

    /** 玩家当前展开的领域（无则 null） */
    public static BaseDomain get(UUID playerId) {
        return DOMAINS.get(playerId);
    }

    /**
     * 该玩家当前是否被"他人领域"包裹（处于非本人领域的领域球内，须同维度）；
     * 有则返回该领域，无则 null。新阴流三技巧（弥虚葛笼/落花之情/简易领域）
     * 以此作为被动开启的触发条件。
     */
    public static BaseDomain findEnemyDomain(net.minecraft.server.level.ServerPlayer player) {
        UUID pid = player.getUUID();
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = player.level().dimension();
        for (BaseDomain d : DOMAINS.values()) {
            if (d.getOwner().equals(pid)) continue;
            if (d.getDimension() == null || !d.getDimension().equals(dim)) continue;
            double r = d.getRadius();
            if (player.position().distanceToSqr(d.getCenter()) <= r * r) return d;
        }
        return null;
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

        // ⭐ 对抗失败锁定期：不能再展开领域（要等所有对抗结束）
        if (isClashLocked(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.clash.locked_domain"), true);
            return;
        }

        // 封印期间无法展开领域（雅各布天梯）
        if (CursePowerHelper.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.sealed.active",
                    CursePowerHelper.getSealedRemainingSeconds(player)), true);
            return;
        }

        // 扫描佩戴咒力核心上的领域特性
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            // ⭐ 未佩戴咒力核心时按键静默（不再弹提示）
            return;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return;
        for (ModifierEntry entry : tool.getModifierList()) {
            Function<ServerPlayer, BaseDomain> factory = DOMAIN_FACTORIES.get(entry.getId());
            if (factory == null) continue;
            BaseDomain domain = factory.apply(player);
            if (domain == null) return; // 工厂内已提示条件不满足（如咒力不足）
            domain.markCreated(player.serverLevel().getGameTime());
            domain.setDimension(player.serverLevel().dimension());
            DOMAINS.put(id, domain);
            domain.onOpen(player);
            spawnVisual(player.serverLevel(), domain);
            domain.buildBarrier(player.serverLevel());
            // 展开时：给领域内所有玩家显示领域名大标题
            broadcastDomainTitle(player.serverLevel(), domain);
            // ⭐ 说明：领域展开<b>不受</b>新阴流技巧限制（弥虚葛笼/简易领域只禁"术式"）。
            //    技巧若正处于激活态（被他人领域包裹），这里额外提示一句，避免玩家以为按键没生效。
            if (com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler.blocksTechnique(player)) {
                player.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.domain.opened_skill_active"), true);
            }
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

    /** 封印瞬间终止领域（雅各布天梯命中）：关闭且不触发熔断 */
    public static void closeBySeal(ServerPlayer player) {
        if (DOMAINS.containsKey(player.getUUID())) {
            close(player, "message.tinkersnewlife.domain.sealed_closed");
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

        // ⭐ 对抗全部结束 → 解除"对抗失败"锁
        tickClashLoserLock(server);

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

            // 领域主人被封印（雅各布天梯）→ 领域瞬间终止
            if (CursePowerHelper.isSealed(player)) {
                DOMAINS.remove(domain.getOwner());
                forceRemove(player, domain);
                domain.onClose(player, "message.tinkersnewlife.domain.sealed_closed");
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.sealed_closed"), true);
                continue;
            }

            // 领域对抗：先结算/检测，再消耗
            boolean clashing = domain.isClashing();
            if (clashing) {
                // ⭐ 多方混战：逐个清掉"已关闭"的对手（各算一次败者拉入）；全部清空才算胜出
                for (UUID oppId : new java.util.ArrayList<>(domain.getClashOpponents())) {
                    if (DOMAINS.get(oppId) != null) continue;
                    domain.removeClash(oppId);
                    pullLoserInto(server, domain, oppId);
                }
                if (!domain.isClashing()) {
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

            // 通用外壳：困锁生物（每 5 tick）+ 补上展开瞬间被生物占位跳过的墙块
            if (now % 5 == 0) {
                domain.clampEntities(player.level());
                domain.refillBarrierGaps(player.serverLevel());
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
        // ⭐ 多方混战：只要球体相交就互为对手（不再限制"一领域对一对手"）；
        //    并且把对方已有的对手也连通进来（A↔B、B↔C ⇒ A、B、C 同一场混战）。
        for (BaseDomain other : new java.util.ArrayList<>(DOMAINS.values())) {
            if (other == domain) continue;
            if (domain.isClashingWith(other.getOwner())) continue;
            ServerPlayer otherPlayer = server.getPlayerList().getPlayer(other.getOwner());
            if (otherPlayer == null || !otherPlayer.isAlive()) continue;

            Vec3 delta = domain.getCenter().subtract(other.getCenter());
            double rSum = domain.getRadius() + other.getRadius();
            if (delta.lengthSqr() >= rSum * rSum) continue; // 不相交

            joinClash(server, player, domain, otherPlayer, other);

            // 连通合并：把对方原来的对手也拉进本领域（反向同样登记）
            for (UUID thirdId : new java.util.ArrayList<>(other.getClashOpponents())) {
                if (thirdId.equals(domain.getOwner())) continue;
                BaseDomain third = DOMAINS.get(thirdId);
                ServerPlayer thirdPlayer = server.getPlayerList().getPlayer(thirdId);
                if (third == null || thirdPlayer == null || !thirdPlayer.isAlive()) continue;
                if (domain.isClashingWith(thirdId)) continue;
                domain.addClash(thirdId, 1.0 + stats(thirdPlayer) / 100.0);
                third.addClash(domain.getOwner(), 1.0 + stats(player) / 100.0);
                domain.onClashStart(player, third);
                third.onClashStart(thirdPlayer, domain);
                domain.removeBarrierOverlap(player.serverLevel(), third.getCenter(), third.getRadius());
                third.removeBarrierOverlap(player.serverLevel(), domain.getCenter(), domain.getRadius());
                thirdPlayer.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.start"), true);
            }
        }
    }

    /** 领域输出/亲和综合值（对抗消耗倍率用） */
    private static double stats(ServerPlayer player) {
        return CursePowerHelper.getCurseOutputLevel(player)
                + CursePowerHelper.getCurseAffinity(player) / 10.0;
    }

    /** 让 domain 与 other 互相成为对手：打通墙、暂停效果、提示双方 */
    private static void joinClash(MinecraftServer server, ServerPlayer player, BaseDomain domain,
                                  ServerPlayer otherPlayer, BaseDomain other) {
        domain.addClash(other.getOwner(), 1.0 + stats(otherPlayer) / 100.0);
        other.addClash(domain.getOwner(), 1.0 + stats(player) / 100.0);

        ServerLevel level = player.serverLevel();
        domain.removeBarrierOverlap(level, other.getCenter(), other.getRadius());
        other.removeBarrierOverlap(level, domain.getCenter(), domain.getRadius());
        domain.onClashStart(player, other);
        other.onClashStart(otherPlayer, domain);

        player.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.start"), true);
        otherPlayer.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.start"), true);
    }

    /** 对抗结束（本领域胜出）：恢复领域效果、重建完整球壳、把败者强行拉入本领域 */
    private static void endClashVictory(MinecraftServer server, ServerPlayer winner, BaseDomain domain) {
        domain.clearClash();
        // 重建完整球壳（补回对抗期间移除的重合部分）
        domain.buildBarrier(winner.serverLevel());
        // 领域效果恢复
        domain.onClashEnd(winner, null);
        // 客户端：恢复完整黑色球壳
        clearClashVisual(winner.serverLevel(), domain);
        winner.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.win"), true);
    }

    /** 某个对手领域已关闭（败者）→ 把他拉进胜者领域 */
    private static void pullLoserInto(MinecraftServer server, BaseDomain winnerDomain, UUID loserId) {
        ServerPlayer loser = server.getPlayerList().getPlayer(loserId);
        if (loser == null || !loser.isAlive()) return;
        ServerPlayer winner = server.getPlayerList().getPlayer(winnerDomain.getOwner());
        if (winner == null) return;
        if (loser.level().dimension() != winner.level().dimension()) return;
        Vec3 target = winnerDomain.getClashPullTarget(winner.serverLevel());
        loser.teleportTo(target.x, target.y, target.z);
        loser.displayClientMessage(Component.translatable("message.tinkersnewlife.clash.lose"), true);
    }

    /** 客户端视觉：让视觉实体隐藏落入对方球体内的黑色边缘部分 */
    private static void syncClashVisual(ServerLevel level, BaseDomain domain, BaseDomain opponent) {
        // 客户端改为"按所有相交的领域视觉实体自己挖洞"（支持多方混战），这里只做兜底标记
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
    //  咒具破坏领域（天逆鉾等）
    // ============================================================

    // ============================================================
    //  ⭐ 对抗失败锁（天逆鉾右键领域展开者 → 视为对抗失败）
    // ============================================================

    /** 被判"对抗失败"的玩家：在所有领域对抗结束前不能再展开领域、也不能使用术式 */
    private static final java.util.Set<UUID> CLASH_LOSERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 场上是否还有任何领域正在对抗 */
    public static boolean hasAnyClash() {
        for (BaseDomain d : DOMAINS.values()) {
            if (d.isClashing()) return true;
        }
        return false;
    }

    /**
     * 标记"对抗失败"（天逆鉾右键领域展开者时调用）。
     * <p>
     * 语义：领域被强制关闭 → 视为对抗失败；只要场上还有对抗，他就被锁住，
     * 直到<b>所有</b>对抗结束（见 {@link #onServerTick} 的解锁检查）。
     */
    public static void markClashLoser(ServerPlayer player, boolean wasClashing) {
        if (player == null) return;
        if (!wasClashing && !hasAnyClash()) return;   // 没对抗可失败 → 不锁
        if (CLASH_LOSERS.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.clash.loser_lock"), true);
        }
    }

    /** 该玩家是否处于"对抗失败"锁定期 */
    public static boolean isClashLocked(ServerPlayer player) {
        return player != null && CLASH_LOSERS.contains(player.getUUID());
    }

    /** 所有对抗结束 → 解锁（每 tick 检查）*/
    private static void tickClashLoserLock(MinecraftServer server) {
        if (CLASH_LOSERS.isEmpty()) return;
        if (hasAnyClash()) return;
        for (UUID id : CLASH_LOSERS) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                p.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.clash.loser_unlock"), true);
            }
        }
        CLASH_LOSERS.clear();
    }

    /** 该屏障方块位置所属的领域（无则 null） */
    public static BaseDomain findDomainByBarrier(ServerLevel level, BlockPos barrierPos) {
        for (BaseDomain domain : DOMAINS.values()) {
            if (domain.containsBarrier(barrierPos)) return domain;
        }
        return null;
    }

    /**
     * 咒具右键领域结界方块：破坏该领域；若该领域正处于领域对抗中，则双方领域同时崩坏。
     * 领域崩坏掉落结界碎片（1/100，散落为掉落物，谁都能捡）；
     * 仅有的防刷限制：领域展开不足 2 秒被破坏 → 不掉碎片（防"展开瞬间"低成本刷取）。
     * 领域崩坏仍会使主人进入术式熔断（创造模式豁免）。
     */
    public static void breakDomainByBarrier(ServerPlayer breaker, ServerLevel level, BlockPos barrierPos) {
        BaseDomain domain = findDomainByBarrier(level, barrierPos);
        if (domain == null) return;
        if (domain.isClashing()) {
            // ⭐ 对抗中（含多方混战）：终止整场对抗，并把这一场里<b>所有</b>领域一起崩坏
            java.util.List<BaseDomain> group = clashGroup(domain);
            for (BaseDomain member : group) {
                breakDomain(breaker, level, member);
            }
            return;
        }
        breakDomain(breaker, level, domain);
    }

    /** 取得与该领域连通的整场对抗（多方混战：递归收集所有对手） */
    private static java.util.List<BaseDomain> clashGroup(BaseDomain start) {
        java.util.LinkedHashSet<BaseDomain> out = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<BaseDomain> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        out.add(start);
        while (!queue.isEmpty()) {
            BaseDomain cur = queue.poll();
            for (UUID oppId : cur.getClashOpponents()) {
                BaseDomain opp = DOMAINS.get(oppId);
                if (opp == null || !out.add(opp)) continue;
                queue.add(opp);
            }
        }
        return new java.util.ArrayList<>(out);
    }

    /** 破坏单个领域：掉落碎片（防刷限制）→ 移除视觉/墙 → 关闭 → 熔断 */
    private static void breakDomain(ServerPlayer breaker, ServerLevel level, BaseDomain domain) {
        UUID ownerId = domain.getOwner();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        DOMAINS.remove(ownerId);
        // 1) 掉落结界碎片（须在 removeBarrier 之前）
        dropBoundaryFragments(level, domain);
        // 2) 移除视觉与阻挡墙
        removeVisual(level, ownerId);
        domain.removeBarrier(level);
        // 3) 领域效果收尾
        domain.onClose(owner, "message.tinkersnewlife.domain.broken");
        if (owner != null && owner.isAlive()) {
            owner.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.broken"), true);
        }
        if (breaker != null && breaker.isAlive() && breaker != owner) {
            breaker.displayClientMessage(Component.translatable("message.tinkersnewlife.cursed_tool.domain_destroyed"), true);
        }
        // 4) 熔断（生存模式；创造/旁观豁免）
        applyBurnoutIfSurvival(owner);
    }

    /** 领域崩坏时：每个结界方块 1/100 概率掉落一个结界碎片（散落为掉落物）；展开不足 2 秒不掉 */
    private static void dropBoundaryFragments(ServerLevel level, BaseDomain domain) {
        if (domain.getAgeTicks(level.getGameTime()) < 40) return; // 展开不足 2 秒
        var fragment = com.mofengbaizhi.tinkersnewlife.content.ModItems.BOUNDARY_FRAGMENT.get();
        for (BlockPos pos : domain.getBarrierPositions()) {
            if (level.random.nextInt(100) != 0) continue;
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new net.minecraft.world.item.ItemStack(fragment));
            item.setDeltaMovement(0, 0.1, 0);
            level.addFreshEntity(item);
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
