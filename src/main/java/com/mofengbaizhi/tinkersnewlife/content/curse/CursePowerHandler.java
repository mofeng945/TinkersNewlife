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
import slimeknights.tconstruct.library.modifiers.ModifierId;

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

            // 咒力恢复：原 5 秒恢复量 (输出+亲和/10)×5 分散到每 tick（总量不变，回复更平滑）
            if (wearing) {
                int output = CursePowerHelper.getCurseOutputLevel(player);
                int affinity = CursePowerHelper.getCurseAffinity(player);
                double regenPerTick = (output + affinity / 10.0) * 5.0 / 100.0;
                // ⭐ 黑闪增幅状态内，咒力回复速度提升为 5 倍
                if (BlackFlashHandler.isBuffActive(player.getUUID())) {
                    regenPerTick *= 5.0;
                }
                CursePowerHelper.addCurse(player, regenPerTick);
            }

            // ⭐ 特等奖增益（33 秒）：HP 锁定在上限
            if (CursePowerHelper.isGrandActive(player)) {
                player.setHealth(player.getMaxHealth());
            }

            // 每秒同步一次 HUD 数据
            if (now % 20 == 0) {
                syncToClient(player);
            }
        }
    }

    /**
     * 向客户端同步咒力 HUD 数据（咒力/上限/领域状态/无限状态/当前选中的术式）。
     * 由每秒心跳与术式切换时调用。
     */
    public static void syncToClient(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        boolean wearing = !core.isEmpty();
        double curse = wearing ? CursePowerHelper.getCurse(player) : 0;
        double max = wearing ? CursePowerHelper.getMaxCurse(player) : 0;
        boolean domainActive = DomainRegistry.isActive(player.getUUID());
        boolean infinite = wearing && CursePowerHelper.isCurseInfinite(player);
        ModifierId technique = wearing ? TechniqueHandler.getSelectedTechniqueId(player) : null;
        int tamedMask = wearing ? com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiHandler.getTamedMask(player) : 0;
        int affinity = wearing ? CursePowerHelper.getCurseAffinity(player) : 0;
        int output = wearing ? CursePowerHelper.getCurseOutputLevel(player) : 1;
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncCurse(curse, max, domainActive, infinite,
                        technique == null ? "" : technique.toString(), tamedMask, affinity, output));
    }
}
