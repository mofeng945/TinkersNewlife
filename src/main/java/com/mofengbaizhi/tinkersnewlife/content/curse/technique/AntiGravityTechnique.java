package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「反重力机构」（独立术式，与无下限系列无关）。
 * <p>
 * 影响范围（半径，格）= 4 + 咒力输出×0.6 + 咒力亲和×0.03，随两者增强。
 * <p>
 * 顺转（释放键）：以自身为球心，对范围内除施术者（及其召唤物）外的<b>所有生物</b>
 * 施加漂浮效果；每有一个目标消耗一份咒力（单位 = (1-亲和/100)×(6+输出×2)，至少 1），
 * 目标越多消耗越多；咒力不足则整发不发。
 * <p>
 * 反转（反转键 F，开关式）：展开压力场（持续每秒扣费，咒力耗尽自动关闭）：
 * <ul>
 *   <li>压力 p = 5 + 输出×1.8 + 亲和×0.05</li>
 *   <li>目标压力阈值 thr = 6 + (体宽×体高)×8 + 血量上限×0.12（体型越大、血越厚越抗压）</li>
 *   <li>p &lt; thr：仅受迟缓，离阈值越近迟缓等级越高（I~V）</li>
 *   <li>p ≥ thr：脚下非基岩方块被压碎（每 4 tick 一次，无掉落），目标被定身，
 *       并在压力场内持续受到咒术伤害（每 12 tick 一次，单次 = 1.5 + 超出量×0.35）</li>
 * </ul>
 * 压力场对施术者本人及自身召唤物无效。
 */
public final class AntiGravityTechnique extends BaseTechnique {

    public static final AntiGravityTechnique INSTANCE = new AntiGravityTechnique();

    /** 反转压力场开启中的玩家 */
    private static final Set<UUID> FIELD = ConcurrentHashMap.newKeySet();

    private AntiGravityTechnique() {
        super(Modifiers.ANTI_GRAVITY.getId());
    }

    public static boolean isFieldActive(ServerPlayer player) {
        return FIELD.contains(player.getUUID());
    }

    /** 登出/死亡清理 */
    public static void cleanup(ServerPlayer player) {
        FIELD.remove(player.getUUID());
    }

    // ================= 顺转：范围漂浮 =================

    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        double r = fieldRadius(player);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(r),
                e -> e != player && e.isAlive() && !e.isSpectator() && !PuppetUtil.isAllyOf(e, player));
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        // 每目标一份咒力
        long unit = unitCost(player);
        long total = unit * targets.size();
        if (!CursePowerHelper.isCurseInfinite(player)) {
            if (CursePowerHelper.getCurse(player) < total) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
                return;
            }
            CursePowerHelper.spendCurse(player, total);
        }
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int dur = 80 + output * 16;                       // 漂浮时长（tick）：4~8 秒随输出
        int amp = affinity >= 60 ? 2 : affinity >= 20 ? 1 : 0; // 漂浮等级随亲和
        for (LivingEntity t : targets) {
            t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, dur, amp, false, false));
            level.sendParticles(ParticleTypes.END_ROD,
                    t.getX(), t.getY() + t.getBbHeight(), t.getZ(), 12, 0.4, 0.2, 0.4, 0.03);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.7F);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.anti_gravity.float", targets.size(), (int) total), true);
    }

    // ================= 反转：压力场（开关） =================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (FIELD.contains(player.getUUID())) {
            FIELD.remove(player.getUUID());
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.anti_gravity.off"), true);
            return;
        }
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        FIELD.add(player.getUUID());
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.anti_gravity.on",
                String.format("%.1f", fieldRadius(player))), true);
    }

    // ================= 数值 =================

    /** 影响范围半径（格）：输出与亲和共同放大 */
    public static double fieldRadius(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return 4.0 + output * 0.6 + affinity * 0.03;
    }

    /** 每目标咒力单位 = (1 - 亲和/100) × (6 + 输出×2)，至少 1 */
    public static long unitCost(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double cost = (1.0 - affinity / 100.0) * (6.0 + output * 2.0);
        return Math.max(1, (long) Math.ceil(cost));
    }

    /** 反转压力场每秒（每 tick）扣费 */
    public static double tickCost(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return Math.max(1.0, (1.0 - affinity / 100.0) * (2.0 + output * 1.2));
    }

    /** 当前压力 */
    public static double pressure(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return 5.0 + output * 1.8 + affinity * 0.05;
    }

    /** 目标压力阈值：体型越大、血量上限越高越抗压 */
    public static double thresholdFor(LivingEntity target) {
        return 6.0 + target.getBbWidth() * target.getBbHeight() * 8.0 + target.getMaxHealth() * 0.12;
    }

    // ================= 事件：压力场每 tick 驱动 =================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class AntiGravityEvents {

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (FIELD.isEmpty()) return;
            MinecraftServer server = event.getServer();
            if (server == null) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!FIELD.contains(p.getUUID())) continue;
                if (p.isDeadOrDying() || !p.isAlive() || p.isRemoved()) {
                    FIELD.remove(p.getUUID());
                    continue;
                }
                ServerLevel level = p.serverLevel();
                // 持续扣费（无限模式免费）；咒力不足自动关闭
                double cost = tickCost(p);
                if (!CursePowerHelper.isCurseInfinite(p)) {
                    if (CursePowerHelper.getCurse(p) < cost) {
                        FIELD.remove(p.getUUID());
                        p.displayClientMessage(Component.translatable(
                                "message.tinkersnewlife.anti_gravity.drained"), true);
                        continue;
                    }
                    CursePowerHelper.spendCurse(p, cost);
                }
                double r = fieldRadius(p);
                double pr = pressure(p);
                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                        p.getBoundingBox().inflate(r),
                        e -> e != p && e.isAlive() && !e.isSpectator() && !PuppetUtil.isAllyOf(e, p));
                for (LivingEntity t : targets) {
                    double thr = thresholdFor(t);
                    if (pr < thr) {
                        // 阈值内：迟缓，离阈值越近等级越高（I~V）
                        double ratio = pr / thr;
                        int amp = Math.max(0, Math.min(4, (int) Math.ceil(ratio * 5.0) - 1));
                        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, amp, false, false));
                    } else {
                        // 超出阈值：定身 + 压碎 + 持续伤害
                        t.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 25, 0, false, false));
                        if (t instanceof Mob mob) {
                            StunHandler.onStunApplied(mob);
                        }
                        if (p.tickCount % 4 == 0) {
                            crushBelow(level, t);
                        }
                        if (p.tickCount % 12 == 0) {
                            double over = pr - thr;
                            float dmg = (float) (1.5 + over * 0.35);
                            t.invulnerableTime = 0;
                            t.hurt(level.damageSources().magic(), dmg);
                        }
                    }
                }
                // 场边缘粒子（每 6 tick 一圈，向下的重压尘埃）
                if (p.tickCount % 6 == 0) {
                    level.sendParticles(ParticleTypes.ASH,
                            p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(),
                            6, r * 0.8, r * 0.5, r * 0.8, 0.0);
                }
            }
        }

        /** 压碎目标脚下非基岩方块（无掉落） */
        private static void crushBelow(ServerLevel level, LivingEntity t) {
            var pos = t.blockPosition().below();
            var state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() == Blocks.BEDROCK || state.getBlock() instanceof LiquidBlock) {
                return;
            }
            level.destroyBlock(pos, false);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.02);
        }
    }
}
