package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 坐杀搏徒 —— 领域展开处理（服务端）
 * <p>
 * - 按键展开：半径 = 咒力输出等级 × 5，领域以展开瞬间玩家位置为球心固定
 * - 困锁：领域内生物（除主人与创造/旁观玩家）无法离开球体范围
 * - 消耗：每秒 半径×20 咒力；咒力耗尽、咒力核心被取下或玩家死亡时领域关闭
 * - 抽奖：展开期间每 3 秒摇一次（70% 小奖 / 29% 大奖 / 1% 特等奖）
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DomainHandler {

    private static final ModifierId ZUOSHA_BOTU_ID = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "zuosha_botu"));

    /** 抽奖间隔：3 秒 */
    private static final int GAMBLE_INTERVAL_TICKS = 60;
    private static final double SMALL_CHANCE = 0.70;
    private static final double BIG_CHANCE = 0.99; // 70%~99% 为大奖，剩余 1% 为特等奖
    /** 特等奖：咒力无限持续 60 秒 */
    private static final int GRAND_INFINITE_TICKS = 60 * 20;
    /** 保底：连续 10 次未中特等奖后，下一次摇奖必出特等奖 */
    private static final int GRAND_PITY_STREAK = 10;
    /** 小奖恢复咒力 */
    private static final double SMALL_PRIZE_CURSE = 200;
    /** 大奖恢复咒力 */
    private static final double BIG_PRIZE_CURSE = 400;

    private static final Map<UUID, Domain> DOMAINS = new ConcurrentHashMap<>();

    /** 展开中的领域状态 */
    private static class Domain {
        final UUID owner;
        final Vec3 center;
        final int radius;
        long nextGambleTick;
        int noGrandStreak; // 连续未中特等奖次数（保底计数）

        Domain(UUID owner, Vec3 center, int radius, long nextGambleTick) {
            this.owner = owner;
            this.center = center;
            this.radius = radius;
            this.nextGambleTick = nextGambleTick;
            this.noGrandStreak = 0;
        }
    }

    public static boolean isActive(UUID playerId) {
        return DOMAINS.containsKey(playerId);
    }

    // ============================================================
    //  展开 / 关闭
    // ============================================================

    /** 按键切换：展开或关闭领域 */
    public static void toggleDomain(ServerPlayer player) {
        UUID id = player.getUUID();
        if (DOMAINS.containsKey(id)) {
            closeDomain(player, "message.tinkersnewlife.domain.closed");
            return;
        }

        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            sendMessage(player, "message.tinkersnewlife.domain.no_core");
            return;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null || tool.getModifierLevel(ZUOSHA_BOTU_ID) <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_trait");
            return;
        }

        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_trait");
            return;
        }
        // 只要咒力 > 0 即可展开，消耗与自动关闭由每 tick 逻辑处理（咒力耗尽领域自动关闭）
        if (!CursePowerHelper.isCurseInfinite(player) && CursePowerHelper.getCurse(player) <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_curse");
            return;
        }

        DOMAINS.put(id, new Domain(id, player.position(), radius,
                player.level().getGameTime() + GAMBLE_INTERVAL_TICKS));
        sendMessage(player, Component.translatable("message.tinkersnewlife.domain.open", radius));
    }

    private static void closeDomain(Player player, String messageKey) {
        DOMAINS.remove(player.getUUID());
        if (player != null && player.isAlive()) {
            sendMessage(player, messageKey);
        }
    }

    private static void sendMessage(Player player, String key) {
        sendMessage(player, Component.translatable(key));
    }

    private static void sendMessage(Player player, Component component) {
        player.displayClientMessage(component, true);
    }

    // ============================================================
    //  每 tick 处理：消耗 / 困锁 / 抽奖 / 粒子
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long now = server.getTickCount();

        Iterator<Map.Entry<UUID, Domain>> it = DOMAINS.entrySet().iterator();
        while (it.hasNext()) {
            Domain domain = it.next().getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(domain.owner);
            if (player == null || !player.isAlive() || !player.level().hasChunkAt(BlockPos.containing(domain.center))) {
                it.remove();
                continue;
            }

            // 咒力核心被取下 / 特性丢失 → 领域被破坏
            ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
            ToolStack tool = ToolHelper.getToolStack(core);
            if (tool == null || tool.getModifierLevel(ZUOSHA_BOTU_ID) <= 0) {
                it.remove();
                sendMessage(player, "message.tinkersnewlife.domain.broken");
                continue;
            }

            // 咒力消耗：每秒 半径×20（=每 tick 半径×20/20）
            if (!CursePowerHelper.isCurseInfinite(player)) {
                CursePowerHelper.spendCurse(player, domain.radius * 20.0 / 20.0);
                if (CursePowerHelper.getCurse(player) <= 0) {
                    it.remove();
                    sendMessage(player, "message.tinkersnewlife.domain.exhausted");
                    continue;
                }
            }

            // 困锁生物（每 5 tick 一次，避免每 tick 全量扫描）
            if (now % 5 == 0) {
                clampEntities(player.level(), domain);
            }

            // 每 3 秒摇奖
            if (now >= domain.nextGambleTick) {
                gamble(player, domain);
                domain.nextGambleTick = now + GAMBLE_INTERVAL_TICKS;
            }

            // 领域边界粒子（每 10 tick）
            if (now % 10 == 0 && player.level() instanceof ServerLevel serverLevel) {
                spawnDomainParticles(serverLevel, domain);
            }
        }
    }

    // ============================================================
    //  困锁：生物无法离开领域范围
    // ============================================================

    private static void clampEntities(Level level, Domain domain) {
        double r = domain.radius;
        AABB box = new AABB(
                domain.center.x - r - 1.5, domain.center.y - r - 1.5, domain.center.z - r - 1.5,
                domain.center.x + r + 1.5, domain.center.y + r + 1.5, domain.center.z + r + 1.5);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (entity.getUUID().equals(domain.owner)) continue;
            if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) continue;

            Vec3 delta = entity.position().subtract(domain.center);
            double dist = delta.length();
            if (dist <= r) continue;

            Vec3 dir = dist < 1e-4 ? new Vec3(0, 0, 0) : delta.normalize();
            double edge = r - 0.35;
            Vec3 target = findSafeSpot(level, domain, dir, edge, dist);

            // 竖直偏移后重新水平缩放，让落点始终在球面边界上
            double dy = target.y - domain.center.y;
            double hRemain = Math.sqrt(Math.max(0, r * r - dy * dy));
            double h = Math.hypot(dir.x, dir.z);
            if (h > 1e-4 && hRemain < h) {
                double scale = hRemain / h;
                target = new Vec3(domain.center.x + dir.x * scale, target.y, domain.center.z + dir.z * scale);
            }

            entity.teleportTo(target.x, target.y, target.z);

            // 消除朝外速度，防止立刻被推/冲出
            Vec3 motion = entity.getDeltaMovement();
            double outward = motion.dot(dir);
            if (outward > 0) {
                entity.setDeltaMovement(motion.subtract(dir.scale(outward)));
            }
            entity.fallDistance = 0;
        }
    }

    /** 在球面候选点附近寻找不卡进方块的安全落点（上下最多偏移 6 格） */
    private static Vec3 findSafeSpot(Level level, Domain domain, Vec3 dir, double edge, double dist) {
        double tx = domain.center.x + dir.x * edge;
        double ty = domain.center.y + dir.y * edge;
        double tz = domain.center.z + dir.z * edge;
        BlockPos pos = BlockPos.containing(tx, ty, tz);
        if (isSafe(level, pos)) return new Vec3(tx, ty, tz);
        for (int dy = 1; dy <= 6; dy++) {
            if (isSafe(level, pos.above(dy))) return new Vec3(tx, ty + dy, tz);
        }
        for (int dy = 1; dy <= 6; dy++) {
            if (isSafe(level, pos.below(dy))) return new Vec3(tx, ty - dy, tz);
        }
        return new Vec3(tx, ty, tz);
    }

    private static boolean isSafe(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    // ============================================================
    //  抽奖：每 3 秒一次
    // ============================================================

    private static void gamble(Player player, Domain domain) {
        // 保底计数：连续未中特等奖
        domain.noGrandStreak++;
        if (domain.noGrandStreak >= GRAND_PITY_STREAK) {
            // 连续 10 次未中特等奖，本次必出特等奖
            domain.noGrandStreak = 0;
            grandPrize(player);
            return;
        }

        double roll = player.getRandom().nextDouble();
        if (roll < SMALL_CHANCE) {
            // 小奖：恢复 200 咒力
            CursePowerHelper.addCurse(player, SMALL_PRIZE_CURSE);
            sendMessage(player, "message.tinkersnewlife.gamble.small");
        } else if (roll < BIG_CHANCE) {
            // 大奖：恢复 400 咒力 + 10 秒伤害吸收 IV
            CursePowerHelper.addCurse(player, BIG_PRIZE_CURSE);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 10 * 20, 3));
            sendMessage(player, "message.tinkersnewlife.gamble.big");
        } else {
            domain.noGrandStreak = 0;
            grandPrize(player);
        }
    }

    /** 特等奖：60 秒咒力无限（重复触发刷新到上限）+ 获得与咒力输出等级相同的生命恢复 */
    private static void grandPrize(Player player) {
        CursePowerHelper.setInfiniteUntil(player, player.level().getGameTime() + GRAND_INFINITE_TICKS);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, GRAND_INFINITE_TICKS, Math.max(0, output - 1)));
        sendMessage(player, "message.tinkersnewlife.gamble.grand");
    }

    // ============================================================
    //  领域边界粒子
    // ============================================================

    private static void spawnDomainParticles(ServerLevel level, Domain domain) {
        double r = domain.radius;
        int ringCount = Math.max(16, Math.min(48, (int) (r * 5)));
        spawnRing(level, domain, r, ringCount, domain.center.y + 1.0);
        spawnRing(level, domain, r, ringCount / 2, domain.center.y - 0.5);
    }

    private static void spawnRing(ServerLevel level, Domain domain, double r, int count, double y) {
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = domain.center.x + Math.cos(angle) * r;
            double z = domain.center.z + Math.sin(angle) * r;
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    // ============================================================
    //  关闭条件：死亡 / 登出 / 脱下咒力核心
    // ============================================================

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            DOMAINS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DOMAINS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCurioUnequip(CurioUnequipEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            DOMAINS.remove(player.getUUID());
        }
    }
}
