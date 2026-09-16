package com.mofengbaizhi.tinkersnewlife.network;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.storage.QuantumVault;
import com.mofengbaizhi.tinkersnewlife.content.storage.QuantumVaultManager;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 6 级量子背包的同步助手：把服务端的按数量存储快照发给正在看界面的玩家 ✓。 */
public final class VaultNetwork {

    private VaultNetwork() {
    }

    /** 待同步（每 tick 合并成一份 ✓）：UUID → 窗口 id */
    private static final java.util.Map<UUID, Integer> PENDING = new java.util.concurrent.ConcurrentHashMap<>();

    /** 登记一次"待同步"，本 tick 末统一发 ✓（同一背包 tick 内多次操作只发一份 ✓） */
    public static void scheduleSync(ServerPlayer player, UUID uuid, int windowId) {
        PENDING.put(uuid, windowId);
        PENDING_PLAYERS.put(uuid, player);
    }

    private static final java.util.Map<UUID, ServerPlayer> PENDING_PLAYERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 服务端每 tick 末冲刷（由 {@code QuantumVaultManager} 的 tick 钩子调用 ✓） */
    public static void flushPending() {
        if (PENDING.isEmpty()) return;
        for (UUID uuid : PENDING.keySet().toArray(new UUID[0])) {
            Integer window = PENDING.remove(uuid);
            ServerPlayer player = PENDING_PLAYERS.remove(uuid);
            if (window == null || player == null) continue;
            sync(player, uuid, window);
        }
    }

    public static void sync(ServerPlayer player, UUID uuid, int windowId) {
        QuantumVault vault = QuantumVaultManager.getInstance().getOrCreate(uuid);
        List<PacketVaultSync.Entry> entries = new ArrayList<>();
        for (Map.Entry<QuantumVault.Key, Long> e : vault.entries()) {
            ItemStack template = vault.stackOf(e.getKey(), 1);
            if (template.isEmpty()) continue;
            entries.add(new PacketVaultSync.Entry(template, e.getValue()));
        }
        TinkersNewlife.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new PacketVaultSync(windowId, vault.total(), entries));
    }
}
