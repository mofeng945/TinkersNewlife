package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.item.ExecutionSwordItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领域·伏诛赐死（新阴流之外的御厨子系领域——法庭处刑领域）
 * <ul>
 *   <li>开启：术式/领域展开键对视线目标使用（被告=视线目标），领域以目标为中心张开</li>
 *   <li>领域内除展开者外所有实体定身不能移动——<b>新阴流技巧无效</b>（弥虚葛笼/简易领域也无法抵御本领域定身）</li>
 *   <li>被告审判：亡灵 3s 后直接判有罪（攻击力归零 60s）；节肢动物 3s（白昼无罪/夜晚有罪）；玩家 6s 逐条向领域内所有玩家
 *       显示 title（玩家名 → 杀人数 → 判断 → 死刑/释放），击杀村民+动物 &gt;1000 判有罪</li>
 *   <li>有罪处刑：领域结束向展开者发放「处刑人之剑」（1 耐久/120s/唯一），命中被告直接处死、命中其它生物 200% 伤害</li>
 *   <li>玩家有罪：依序没收咒具 → 术式 → 咒力 60s（再次被审判惩罚递进），咒具=手持即回背包、术式=封印、咒力=恒 0</li>
 * </ul>
 */
public class ExecutionDomain extends BaseDomain {
    /** config: 领域 modifier path（系数键） */
    @Override
    protected String configScaleId() { return "fuzhu_cisi"; }

    /** 亡灵/节肢审判时长（tick）：3s */
    /** 非玩家被告的判罪时间（tick）：5 秒（原 3 秒） */
    private static final int JUDGE_MOB_TICKS = 100;

    /** 玩家定罪阈值：罪行分（击杀村民 + 击杀动物 + 击败玩家数）&gt; 此值即有罪 */
    public static final int PLAYER_GUILT_THRESHOLD = 100;
    /** 玩家审判时长（tick）：6s */
    private static final int JUDGE_PLAYER_TICKS = 120;
    /** 攻击力归零 / 没收时长（tick）：60s */
    public static final long PENALTY_TICKS = 60 * 20L;
    /** 处刑人之剑限时（tick）：120s */
    public static final long SWORD_LIFETIME_TICKS = 120 * 20L;

    /** 被告 UUID */
    private final UUID targetId;
    private final long startedAt;

    private ExecutionDomain(ServerPlayer owner, Vec3 center, int radius, LivingEntity target) {
        super(owner.getUUID(), center, radius, radius * 20.0);
        this.targetId = target.getUUID();
        this.startedAt = owner.serverLevel().getServer().getTickCount();
    }

    /** 工厂：视线目标作为被告（无目标则提示），领域以目标位置为中心 */
    public static ExecutionDomain tryCreate(ServerPlayer player) {
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.technique.no_target"), true);
            return null;
        }
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 10;
        return new ExecutionDomain(player, target.position(), radius, target);
    }

    /** 视线索敌（24 格，排除自身/幽灵） */
    private static LivingEntity findLookTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        double reach = 24.0;
        Vec3 end = eye.add(look.scale(reach));
        var box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity le && le.isAlive(),
                reach * reach);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    // ==================== 生命周期 ====================

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.fuzhu_cisi";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        return player.isAlive();
    }

    @Override
    public void onOpen(ServerPlayer player) {
        Entity t = player.serverLevel().getEntity(targetId);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.fuzhu_cisi.open",
                t != null ? t.getDisplayName() : Component.literal("?")), true);
        TinkersNewlife.LOGGER.info("[伏诛赐死] 开庭 owner={} 被告={}({}) 半径={}",
                player.getName().getString(),
                t != null ? t.getType().getDescriptionId() : "?",
                t != null ? t.getUUID() : targetId, radius);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        long elapsed = now - startedAt;
        ServerLevel level = player.serverLevel();

        // 诊断（每 20 tick）：确认 onTick 在跑、计时在走、目标是否仍在
        if (now % 20 == 0) {
            Entity diag = level.getEntity(targetId);
            TinkersNewlife.LOGGER.info("[伏诛赐死] tick elapsed={}t 目标存活={} 墙格数={}",
                    elapsed, diag instanceof LivingEntity le && le.isAlive(),
                    getBarrierPositions().size());
        }

        // 1) 领域内除展开者外所有实体定身（技巧无效：不查 SkillHandler、不给通用抵抗）
        if (now % 5 == 0) {
            double r = radius;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                            center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5))) {
                if (e.getUUID().equals(owner)) continue;
                if (e.position().distanceToSqr(center) > r * r) continue;
                e.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 60, 0, false, false));
                if (e instanceof Mob mob) {
                    net.minecraft.world.entity.ai.navigation.PathNavigation nav = mob.getNavigation();
                    if (nav != null) nav.stop();
                    // ⭐ 登记 noAi（否则生物只是"被停了导航"，AI 照常攻击/移动，静止对生物形同虚设）
                    com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler.onStunApplied(mob);
                }
            }
        }

        // 2) 玩家被告：审判期间逐条向领域内所有玩家显示 title（每 1.5s 一条）
        Entity t = level.getEntity(targetId);
        if (t instanceof ServerPlayer targetPlayer && targetPlayer.isAlive()) {
            int total = playerGuiltScore(targetPlayer);
            boolean guilty = total > PLAYER_GUILT_THRESHOLD;
            if (elapsed >= 0 && elapsed < 5) {
                broadcastTitle(level, targetPlayer.getDisplayName());
            } else if (elapsed >= 30 && elapsed < 35) {
                broadcastTitle(level, Component.translatable(
                        "message.tinkersnewlife.fuzhu_cisi.kills", total));
            } else if (elapsed >= 60 && elapsed < 65) {
                broadcastTitle(level, Component.translatable(guilty
                        ? "message.tinkersnewlife.fuzhu_cisi.guilty"
                        : "message.tinkersnewlife.fuzhu_cisi.innocent"));
            } else if (elapsed >= 90 && elapsed < 95) {
                broadcastTitle(level, Component.translatable(guilty
                        ? "message.tinkersnewlife.fuzhu_cisi.death"
                        : "message.tinkersnewlife.fuzhu_cisi.acquitted"));
            }
        }

        // 3) 裁决：到时自动结束领域（结算在 close 前由 doVerdict 完成）
        long judgeTicks = (t instanceof Player) ? JUDGE_PLAYER_TICKS : JUDGE_MOB_TICKS;
        if (elapsed >= judgeTicks) {
            doVerdict(player);
            DomainRegistry.close(player, "message.tinkersnewlife.fuzhu_cisi.end");
        }
    }

    // ==================== 裁决 ====================

    /** 裁决并按类别结算（在领域关闭前调用一次） */
    private void doVerdict(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        Entity target = level.getEntity(targetId);
        TinkersNewlife.LOGGER.info("[伏诛赐死] 裁决 tick={} 被告实体={}",
                level.getServer().getTickCount(), target == null ? "null(已消失)" : target.getType().getDescriptionId());
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return;

        boolean guilty = true;
        if (living instanceof ServerPlayer p) {
            // 玩家：罪行分 = 击杀村民 + 击杀动物 + 击败玩家数，> 100 有罪
            guilty = playerGuiltScore(p) > PLAYER_GUILT_THRESHOLD;
            if (guilty) {
                applyPlayerPenalty(owner, p);
            }
            return; // 玩家不用处刑剑
        }
        // ⭐ 非玩家实体：<b>不论种类，指向即被告、5 秒后一律有罪</b>
        //   （原逻辑是"亡灵有罪 / 节肢按天色 / 其它无罪"，现在统一判有罪）
        guilty = true;

        if (!guilty) {
            owner.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.fuzhu_cisi.innocent_end",
                    living.getDisplayName()), true);
            return;
        }
        // 有罪（亡灵/节肢夜晚）：攻击力归零 60s + 发处刑人之剑
        ATK_ZERO_UNTIL.put(living.getUUID(), level.getServer().getTickCount() + PENALTY_TICKS);
        owner.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.fuzhu_cisi.guilty_end",
                living.getDisplayName()), true);
        giveExecutionSword(owner, living);
    }

    // ==================== 玩家惩罚（没收三级递进） ====================

    private static final String KEY_NEXT_TIER = "tnl_exec_next_tier";
    private static final String KEY_ACTIVE_TIER = "tnl_exec_active_tier";
    private static final String KEY_UNTIL = "tnl_exec_until";
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> CURSED_TOOLS =
            net.minecraft.tags.ItemTags.create(
                    new net.minecraft.resources.ResourceLocation(TinkersNewlife.MOD_ID, "cursed_tools"));

    /** 依序没收：咒具(0) → 术式(1) → 咒力(2)；下次审判递进 */
    private static void applyPlayerPenalty(ServerPlayer owner, ServerPlayer defendant) {
        var data = defendant.getPersistentData();
        int tier = data.getInt(KEY_NEXT_TIER);
        long until = defendant.serverLevel().getServer().getTickCount() + PENALTY_TICKS;
        data.putInt(KEY_ACTIVE_TIER, tier);
        data.putLong(KEY_UNTIL, until);
        data.putInt(KEY_NEXT_TIER, (tier + 1) % 3);
        String key = switch (tier) {
            case 0 -> "message.tinkersnewlife.fuzhu_cisi.penalty_tool";
            case 1 -> {
                CursePowerHelper.applySeal(defendant, (int) (PENALTY_TICKS / 20));
                yield "message.tinkersnewlife.fuzhu_cisi.penalty_technique";
            }
            default -> "message.tinkersnewlife.fuzhu_cisi.penalty_curse";
        };
        owner.displayClientMessage(Component.translatable(key, defendant.getDisplayName()), true);
        defendant.displayClientMessage(Component.translatable(key, defendant.getDisplayName()), true);
    }

    /** 该玩家当前是否处于"咒具没收"中（主手/副手咒具 → 每 tick 强制回背包） */
    private static boolean toolPenaltyActive(ServerPlayer p) {
        var data = p.getPersistentData();
        return data.getInt(KEY_ACTIVE_TIER) == 0 && data.getLong(KEY_UNTIL) > p.serverLevel().getServer().getTickCount();
    }

    // ==================== 处刑人之剑 ====================

    /** 发放处刑人之剑（背包已有则不再发） */
    private static void giveExecutionSword(ServerPlayer owner, LivingEntity target) {
        var inv = owner.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof ExecutionSwordItem) {
                owner.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.fuzhu_cisi.sword_exists"), true);
                return;
            }
        }
        ItemStack sword = new ItemStack(ModItems.EXECUTION_SWORD.get());
        ExecutionSwordItem.setTarget(sword, target.getUUID());
        sword.getOrCreateTag().putLong("execution_spawn", owner.serverLevel().getServer().getTickCount());
        if (owner.getMainHandItem().isEmpty()) {
            owner.getInventory().setItem(owner.getInventory().selected, sword);
        } else if (!owner.getInventory().add(sword)) {
            owner.drop(sword, false);
        }
        owner.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.fuzhu_cisi.sword_got"), true);
    }

    // ==================== 击杀统计 ====================

    private static final String KEY_KILL_VILLAGER = "tnl_exec_kill_villager";
    private static final String KEY_KILL_ANIMAL = "tnl_exec_kill_animal";

    public static void recordKill(ServerPlayer killer, LivingEntity victim) {
        var data = killer.getPersistentData();
        if (victim instanceof Villager) {
            data.putInt(KEY_KILL_VILLAGER, data.getInt(KEY_KILL_VILLAGER) + 1);
        } else if (victim instanceof Animal) {
            data.putInt(KEY_KILL_ANIMAL, data.getInt(KEY_KILL_ANIMAL) + 1);
        }
    }

    /** 杀人数 = 击杀村民 + 击杀动物 */
    public static long killScore(ServerPlayer p) {
        var data = p.getPersistentData();
        return data.getInt(KEY_KILL_VILLAGER) + (long) data.getInt(KEY_KILL_ANIMAL);
    }

    /**
     * 玩家罪行分 = 击杀村民/动物数 + <b>击败玩家数</b>（原版统计 {@code minecraft:player_kills}）。
     * 判罪阈值见 {@link #PLAYER_GUILT_THRESHOLD}。
     */
    public static int playerGuiltScore(ServerPlayer p) {
        int playerKills = 0;
        try {
            playerKills = p.getStats().getValue(
                    net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAYER_KILLS));
        } catch (Throwable ignored) {
            // 统计读不到就不计
        }
        return (int) (killScore(p) + playerKills);
    }

    // ==================== 类别判定 ====================

    // （类别判定已移除：非玩家实体现在一律有罪）

    /** 向领域内所有玩家（含展开者）广播大标题 */
    private void broadcastTitle(ServerLevel level, Component text) {
        net.minecraft.server.MinecraftServer server = level.getServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != level.dimension()) continue;
            if (p.position().distanceToSqr(center) > radius * radius) continue;
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(text));
        }
    }

    // ==================== 跨领域状态 ====================

    /** 攻击力归零截止：UUID → 服务器 tick（亡灵有罪 60s） */
    public static final Map<UUID, Long> ATK_ZERO_UNTIL = new ConcurrentHashMap<>();

    // ==================== 事件（统计 / 剑 / 攻击力0 / 没收执行 / 剑过期） ====================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ExecutionEvents {

        /** 统计击杀村民/动物 */
        @SubscribeEvent
        public static void onKill(LivingDeathEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
            recordKill(killer, event.getEntity());
        }

        /** 处刑人之剑命中 / 攻击力归零拦截 */
        @SubscribeEvent
        public static void onDamage(LivingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            // 攻击力归零（亡灵有罪 60s）：该生物造成伤害 → 无效
            if (event.getSource().getEntity() instanceof LivingEntity attacker
                    && ATK_ZERO_UNTIL.containsKey(attacker.getUUID())) {
                Long until = ATK_ZERO_UNTIL.get(attacker.getUUID());
                if (until > ((net.minecraft.server.level.ServerLevel) attacker.level()).getServer().getTickCount()) {
                    event.setAmount(0.0F);
                    return;
                }
                ATK_ZERO_UNTIL.remove(attacker.getUUID());
            }
            // 处刑人之剑
            if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
            LivingEntity victim = event.getEntity();
            ItemStack held = p.getMainHandItem();
            boolean main = held.getItem() instanceof ExecutionSwordItem;
            if (!main && p.getOffhandItem().getItem() instanceof ExecutionSwordItem) {
                held = p.getOffhandItem();
                main = true;
            }
            if (!main) return;
            UUID swordTarget = ExecutionSwordItem.getTarget(held);
            boolean isJudged = swordTarget != null && swordTarget.equals(victim.getUUID());
            // ⭐ 修复：复仇处决只对<b>非玩家</b>生效。
            //   原来 `killedMeBefore(p, victim)` 只看"该实体类型是否杀过我"，
            //   于是只要曾被任何玩家杀过一次，处刑人之剑就对<b>任何</b>玩家必杀（判罪目标是谁都无所谓）。
            //   玩家必须正好是本次判决的被告（swordTarget）才处死。
            boolean revenge = !(victim instanceof net.minecraft.world.entity.player.Player)
                    && killedMeBefore(p, victim);
            if (isJudged || revenge) {
                // 命中被告（或被杀过的非玩家生物：亚波伦这类不可杀 Boss 的复仇处决）→ 直接处死
                execute(p, victim, event);
            } else {
                // 命中其它生物 → 200% 伤害
                event.setAmount(event.getAmount() * 2.0F);
            }
            // 一击即毁（1 耐久）
            held.shrink(1);
            p.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND);
        }

        /** 原版统计中该玩家是否曾被该类型杀死（击杀记录 Killed By） */
        private static boolean killedMeBefore(ServerPlayer p, LivingEntity victim) {
            return p.getStats().getValue(net.minecraft.stats.Stats.ENTITY_KILLED_BY.get(victim.getType())) > 0;
        }

        /** 处决：无视无敌帧直接击杀（Boss 也能秒） */
        private static void execute(ServerPlayer p, LivingEntity victim,
                                    LivingDamageEvent event) {
            victim.invulnerableTime = 0;
            // 先尝试直接击杀（绕过大部分 Boss 的伤害上限/免疫）
            if (victim.isAlive() && !victim.isRemoved()) {
                victim.kill();
            }
            // 兜底：仍存活（自定义死亡逻辑/复活）→ 巨额咒术伤害
            if (victim.isAlive()) {
                event.setAmount(1.0E7F);
            } else {
                event.setCanceled(true);
            }
        }

        /** 每 tick：没收执行（咒具回包 / 咒力归零） */
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            net.minecraft.server.MinecraftServer server = event.getServer();
            if (server == null) return;
            long now = server.getTickCount();
            // 攻击力归零过期清理
            ATK_ZERO_UNTIL.entrySet().removeIf(e -> e.getValue() <= now);
            // 过期处刑剑清理 + 没收执行（每 4 tick）
            if (now % 4 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                // 过期处刑剑（120s）移除
                var inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (s.getItem() instanceof ExecutionSwordItem
                            && s.getTag() != null && s.getTag().contains("execution_spawn")
                            && now - s.getTag().getLong("execution_spawn") > SWORD_LIFETIME_TICKS) {
                        s.shrink(s.getCount());
                    }
                }
                // 咒具没收：主/副手持咒具 → 强制放回背包
                if (toolPenaltyActive(p)) {
                    forceToolsToInventory(p);
                }
                // 咒力没收：恒为 0
                var data = p.getPersistentData();
                if (data.getInt(KEY_ACTIVE_TIER) == 2
                        && data.getLong(KEY_UNTIL) > now) {
                    CursePowerHelper.setCurse(p, 0.0);
                }
                // 惩罚到期清理
                if (data.getLong(KEY_UNTIL) <= now && data.contains(KEY_ACTIVE_TIER)) {
                    data.remove(KEY_ACTIVE_TIER);
                    data.remove(KEY_UNTIL);
                }
            }
        }

        /** 主手/副手咒具 → 移入背包（背包满则掉落） */
        private static void forceToolsToInventory(ServerPlayer p) {
            for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
                ItemStack held = p.getItemInHand(hand);
                if (held.isEmpty()) continue;
                if (!held.is(CURSED_TOOLS)) continue;
                if (p.getInventory().add(held)) {
                    p.setItemInHand(hand, ItemStack.EMPTY);
                }
            }
        }
    }
}
