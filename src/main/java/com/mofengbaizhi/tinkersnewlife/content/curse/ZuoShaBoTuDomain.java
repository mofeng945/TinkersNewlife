package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 坐杀搏徒领域
 * <p>
 * - 半径 = 咒力输出等级 × 5
 * - 每秒消耗 半径×20 咒力，耗尽自动关闭
 * - 展开期间每 3 秒摇奖（概率 70% 小奖 / 29% 大奖 / 1% 特等奖，连续 10 次未中特等奖保底必出）：
 *   摇到奖品时向领域范围内所有玩家播放标题文字；奖品效果作用于开启者：
 *   - 小奖：+250 咒力 + 10 秒伤害吸收 IV
 *   - 大奖：+900 咒力 + 30 秒与咒力输出等级相同的生命恢复
 *   - 特等奖：33 秒内咒力无限、HP 锁定上限、咒力亲和 +100
 */
public class ZuoShaBoTuDomain extends BaseDomain {

    private static final ModifierId ZUOSHA_BOTU_ID = new ModifierId(
            new net.minecraft.resources.ResourceLocation(com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, "zuosha_botu"));

    /** 抽奖间隔：3 秒 */
    private static final int GAMBLE_INTERVAL_TICKS = 60;
    private static final double SMALL_CHANCE = 0.70;
    private static final double BIG_CHANCE = 0.99;
    /** 保底：连续 10 次未中特等奖后，第 11 次摇奖必出特等奖 */
    private static final int GRAND_PITY_STREAK = 11;
    /** 小奖 / 大奖恢复咒力 */
    private static final double SMALL_PRIZE_CURSE = 250;
    private static final double BIG_PRIZE_CURSE = 900;
    /** 特等奖增益时长：33 秒 */
    private static final int GRAND_BUFF_TICKS = 33 * 20;
    /** 特等奖临时咒力亲和加成 */
    private static final int GRAND_AFFINITY_BUFF = 100;

    private long nextGambleTick;
    private int noGrandStreak;

    private ZuoShaBoTuDomain(UUID owner, net.minecraft.world.phys.Vec3 center, int radius) {
        super(owner, center, radius, radius * 20.0);
        this.noGrandStreak = 0;
    }

    /**
     * 尝试创建坐杀搏徒领域（工厂）：校验佩戴咒力核心、拥有坐杀搏徒特性、咒力 > 0。
     * 条件不满足时发送提示并返回 null。
     */
    public static ZuoShaBoTuDomain tryCreate(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            sendMessage(player, "message.tinkersnewlife.domain.no_core");
            return null;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null || tool.getModifierLevel(ZUOSHA_BOTU_ID) <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_trait");
            return null;
        }
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_trait");
            return null;
        }
        if (!CursePowerHelper.isCurseInfinite(player) && CursePowerHelper.getCurse(player) <= 0) {
            sendMessage(player, "message.tinkersnewlife.domain.no_curse");
            return null;
        }
        ZuoShaBoTuDomain domain = new ZuoShaBoTuDomain(player.getUUID(), player.position(), radius);
        // ⭐ 必须与 DomainRegistry 每 tick 传入的 server.getTickCount() 同源，
        // 不能用 level.getGameTime()（老存档里该值远大于 tickCount，会导致摇奖永不触发）
        domain.nextGambleTick = player.serverLevel().getServer().getTickCount() + GAMBLE_INTERVAL_TICKS;
        return domain;
    }

    // ============================================================
    //  生命周期
    // ============================================================

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(ZUOSHA_BOTU_ID) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        sendMessage(player, Component.translatable("message.tinkersnewlife.domain.open", radius));
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        // 每 3 秒摇奖
        if (now >= nextGambleTick) {
            gamble(player);
            nextGambleTick = now + GAMBLE_INTERVAL_TICKS;
        }
    }

    // ============================================================
    //  抽奖
    // ============================================================

    private void gamble(ServerPlayer player) {
        // 保底计数：连续未中特等奖
        noGrandStreak++;
        if (noGrandStreak >= GRAND_PITY_STREAK) {
            noGrandStreak = 0;
            grandPrize(player);
            return;
        }

        double roll = player.getRandom().nextDouble();
        if (roll < SMALL_CHANCE) {
            smallPrize(player);
        } else if (roll < BIG_CHANCE) {
            bigPrize(player);
        } else {
            noGrandStreak = 0;
            grandPrize(player);
        }
    }

    /** 小奖：+250 咒力 + 10 秒伤害吸收 IV */
    private void smallPrize(ServerPlayer player) {
        CursePowerHelper.addCurse(player, SMALL_PRIZE_CURSE);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 10 * 20, 3));
        broadcastTitle(player, "title.tinkersnewlife.gamble.small");
        sendMessage(player, "message.tinkersnewlife.gamble.small");
    }

    /** 大奖：+900 咒力 + 30 秒与咒力输出等级相同的生命恢复 */
    private void bigPrize(ServerPlayer player) {
        CursePowerHelper.addCurse(player, BIG_PRIZE_CURSE);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30 * 20, Math.max(0, output - 1)));
        broadcastTitle(player, "title.tinkersnewlife.gamble.big");
        sendMessage(player, "message.tinkersnewlife.gamble.big");
    }

    /** 特等奖：33 秒内咒力无限、HP 锁定上限、咒力亲和 +100 */
    private void grandPrize(ServerPlayer player) {
        long until = player.level().getGameTime() + GRAND_BUFF_TICKS;
        CursePowerHelper.setInfiniteUntil(player, until);
        CursePowerHelper.setGrandUntil(player, until);
        CursePowerHelper.setCurseAffinityBuff(player, GRAND_AFFINITY_BUFF, until);
        broadcastTitle(player, "title.tinkersnewlife.gamble.grand");
        sendMessage(player, "message.tinkersnewlife.gamble.grand");
    }

    // ============================================================
    //  标题广播：领域范围内所有玩家屏幕播放奖品标题
    // ============================================================

    private void broadcastTitle(ServerPlayer owner, String titleKey) {
        ServerLevel level = owner.serverLevel();
        Component title = Component.translatable(titleKey);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(center.x - radius, center.y - radius, center.z - radius,
                        center.x + radius, center.y + radius, center.z + radius))) {
            if (p.position().distanceToSqr(center) > radius * radius) continue;
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void sendMessage(ServerPlayer player, String key) {
        sendMessage(player, Component.translatable(key));
    }

    private static void sendMessage(ServerPlayer player, Component component) {
        player.displayClientMessage(component, true);
    }
}
