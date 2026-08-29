package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 坐杀搏徒领域
 * <p>
 * - 半径 = 咒力输出等级 × 5
 * - 每秒消耗 半径×20 咒力，耗尽自动关闭
 * - 展开期间每 3 秒摇奖：70% 小奖（+200 咒力）/ 29% 大奖（+400 咒力 + 10s 伤害吸收IV）
 *   / 1% 特等奖（60s 咒力无限 + 生命恢复）；连续 10 次未中特等奖则下次必出特等奖
 */
public class ZuoShaBoTuDomain extends BaseDomain {

    private static final ModifierId ZUOSHA_BOTU_ID = new ModifierId(
            new net.minecraft.resources.ResourceLocation(com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, "zuosha_botu"));

    /** 抽奖间隔：3 秒 */
    private static final int GAMBLE_INTERVAL_TICKS = 60;
    private static final double SMALL_CHANCE = 0.70;
    private static final double BIG_CHANCE = 0.99;
    /** 特等奖：咒力无限持续 60 秒 */
    private static final int GRAND_INFINITE_TICKS = 60 * 20;
    /** 保底：连续 10 次未中特等奖后，下一次摇奖必出特等奖 */
    private static final int GRAND_PITY_STREAK = 10;
    /** 小奖 / 大奖恢复咒力 */
    private static final double SMALL_PRIZE_CURSE = 200;
    private static final double BIG_PRIZE_CURSE = 400;

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
        domain.nextGambleTick = player.level().getGameTime() + GAMBLE_INTERVAL_TICKS;
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
            // 小奖：恢复 200 咒力
            CursePowerHelper.addCurse(player, SMALL_PRIZE_CURSE);
            sendMessage(player, "message.tinkersnewlife.gamble.small");
        } else if (roll < BIG_CHANCE) {
            // 大奖：恢复 400 咒力 + 10 秒伤害吸收 IV
            CursePowerHelper.addCurse(player, BIG_PRIZE_CURSE);
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 10 * 20, 3));
            sendMessage(player, "message.tinkersnewlife.gamble.big");
        } else {
            noGrandStreak = 0;
            grandPrize(player);
        }
    }

    /** 特等奖：60 秒咒力无限（重复触发刷新到上限）+ 获得与咒力输出等级相同的生命恢复 */
    private void grandPrize(ServerPlayer player) {
        CursePowerHelper.setInfiniteUntil(player, player.level().getGameTime() + GRAND_INFINITE_TICKS);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, GRAND_INFINITE_TICKS, Math.max(0, output - 1)));
        sendMessage(player, "message.tinkersnewlife.gamble.grand");
    }

    private static void sendMessage(ServerPlayer player, String key) {
        sendMessage(player, Component.translatable(key));
    }

    private static void sendMessage(ServerPlayer player, Component component) {
        player.displayClientMessage(component, true);
    }
}
