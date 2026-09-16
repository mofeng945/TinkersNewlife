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
