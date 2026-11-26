package com.mofengbaizhi.tinkersnewlife.content.portal;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>「去过的维度才解锁」的记录员</b>（§661）。
 *
 * <p>用户口径：「其他维度需要玩家至少去过一次才能解锁」⇒ 维度通行证 GUI 的下拉列表里，
 * 只列出玩家<b>到过</b>的那些维度（黑名单维度永远不列）。
 *
 * <p>记录写在<b>玩家自己的持久化数据</b>里（{@code WhiteSpaceDimensions.markVisited}）：
 * 键 {@code tinkersnewlife:visited_dimensions} 是一个 {@code 维度id → true} 的小表。
 * 选它而不是另开一份存档数据（{@code SavedData}）的原因：这是<b>纯玩家个人</b>的进度，
 * 跟着玩家走最自然 —— 死亡重生时 Forge 会连持久化数据一起拷贝（{@code PlayerEvent.Clone}），
 * 单机换存档也不会串味 ✓。
 *
 * <p>三个入口都记：<b>登录</b>（人在哪个维度就算到过）、<b>跨维度</b>（目标维度）、
 * <b>重生</b>（重生可能换维度，而 {@code PlayerChangedDimensionEvent} 不保证每次都发 ⇒ 补一遍）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class WhiteSpaceUnlockHandler {

    private WhiteSpaceUnlockHandler() {
    }

    /** 登录：把「人现在站的维度」记成去过 —— 新装的存档里至少主世界是解锁的 ✓ */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WhiteSpaceDimensions.markVisited(player, player.level().dimension());
        }
    }

    /** 跨维度：把目标维度记成去过 */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WhiteSpaceDimensions.markVisited(player, event.getTo());
        }
    }

    /** 重生：补一遍（重生会换维度，但跨维度事件不一定发） */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WhiteSpaceDimensions.markVisited(player, player.level().dimension());
        }
    }
}
