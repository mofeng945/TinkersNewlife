package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.BlackFlashHandler;
import com.mofengbaizhi.tinkersnewlife.network.PacketSyncCurse;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 咒力恢复与数值同步（服务端）
 * <p>
 * - 佩戴咒力核心时，每 5 秒恢复 (咒力输出等级 + 咒力亲和/10) × 5 点咒力
 * - 每秒向客户端同步一次咒力数值/上限/领域状态/无限状态，供 HUD 显示
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CursePowerHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long now = server.getTickCount();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
            boolean wearing = !core.isEmpty();

            // 每 5 秒恢复一次（仅佩戴咒力核心时）
            if (wearing && now % 100 == 0) {
                int output = CursePowerHelper.getCurseOutputLevel(player);
                int affinity = CursePowerHelper.getCurseAffinity(player);
                double regen = (output + affinity / 10.0) * 5.0;
                // ⭐ 黑闪增幅状态内，咒力回复速度提升为 5 倍
                if (BlackFlashHandler.isBuffActive(player.getUUID())) {
                    regen *= 5.0;
                }
                CursePowerHelper.addCurse(player, regen);
            }

            // 每秒同步一次 HUD 数据
            if (now % 20 == 0) {
                double curse = wearing ? CursePowerHelper.getCurse(player) : 0;
                double max = wearing ? CursePowerHelper.getMaxCurse(player) : 0;
                boolean domainActive = DomainHandler.isActive(player.getUUID());
                boolean infinite = wearing && CursePowerHelper.isCurseInfinite(player);
                TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncCurse(curse, max, domainActive, infinite));
            }
        }
    }
}
