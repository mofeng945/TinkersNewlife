package com.mofengbaizhi.tinkersnewlife.content.handler;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.FumoMoDoll;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>墨默好感度 30 自动赠送 fufu</b>（§889 用户口径 ✓）：
 * 每 20 tick 检查一次佩戴/持有的好感度（{@code MomoFavor.get} ✓ 范围 −50~+50 ✓），
 * **首次达到 30** 就送一只玩偶 ✓ ＋ 一条 actionbar 提示 ✓；
 * 用玩家持久数据里的"已领"标记防重复 ✓（`tn_fumo_gift` ✓ 掉线/重登不会重复领 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FumoMoGiftHandler {

    /** 首次赠送的好感度门槛（用户口径：30 ✓） */
    public static final int THRESHOLD = 30;
    /** 玩家持久数据里的"已领"标记 ✓ 防重复 ✓ */
    private static final String KEY_GIVEN = "tn_fumo_gift";

    private FumoMoGiftHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;                 // 每秒查一次 ✓ 够用 ✓
        try {
            if (player.getPersistentData().getBoolean(KEY_GIVEN)) return;
            if (MomoFavor.get(player) < THRESHOLD) return;
            player.getPersistentData().putBoolean(KEY_GIVEN, true);
            ItemStack doll = new ItemStack(FumoMoDoll.FUMO_MO_ITEM.get());
            if (!player.getInventory().add(doll)) {
                player.drop(doll, false);                        // 背包满了就掉在脚下 ✓ 不吞 ✓
            }
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.fumo_gift"), false);
        } catch (Throwable ignored) {
        }
    }
}
