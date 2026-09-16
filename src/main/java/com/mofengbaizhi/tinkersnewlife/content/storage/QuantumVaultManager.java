package com.mofengbaizhi.tinkersnewlife.content.storage;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 量子背包·6 级「按数量存放」容器的管理器：每个背包（UUID）一个 {@link QuantumVault} ✓。
 *
 * <p>持久化方式与 1~5 级的 {@link StorageManager} 保持一致（**按 UUID 存一个 nbt 文件到世界目录** ✓），
 * 只是换了子目录 {@code data/tinkersnewlife_quantum_vault} ✓；脏数据每 100 tick 落盘一次、
 * 服务器关闭时兜底存一次 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuantumVaultManager {

    private QuantumVaultManager() {
    }

    private static final QuantumVaultManager INSTANCE = new QuantumVaultManager();

    public static QuantumVaultManager getInstance() {
        return INSTANCE;
    }

    private final Map<UUID, QuantumVault> vaults = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> missing = ConcurrentHashMap.newKeySet();   // 世界里没有文件的（新建过）→ 也要存 ✓

    private Path dir;

    // ============================================================
    //  取用 / 存档
    // ============================================================

    public QuantumVault getOrCreate(UUID uuid) {
        return vaults.computeIfAbsent(uuid, this::load);
    }

    private QuantumVault load(UUID uuid) {
        QuantumVault vault = new QuantumVault();
        Path file = fileOf(uuid);
        if (file != null && Files.isRegularFile(file)) {
            try {
                CompoundTag tag = NbtIo.readCompressed(file.toFile());
                vault.load(tag);
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[量子背包] 读取 6 级存储失败（{}）: {}", uuid, t.toString());
            }
        }
        return vault;
    }

    public void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    /** 新建过（哪怕没内容）也标记，保证文件会生成 ✓ */
    public void markCreated(UUID uuid) {
        missing.add(uuid);
        dirty.add(uuid);
    }

    private Path fileOf(UUID uuid) {
        Path base = baseDir();
        return base == null ? null : base.resolve(uuid + ".nbt");
    }

    private Path baseDir() {
        if (dir != null) return dir;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        dir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("tinkersnewlife_quantum_vault");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            TinkersNewlife.LOGGER.warn("[量子背包] 无法创建 6 级存储目录: {}", e.toString());
            dir = null;
        }
        return dir;
    }

    private void save(UUID uuid) {
        Path file = fileOf(uuid);
        if (file == null) return;
        try {
            QuantumVault vault = vaults.get(uuid);
            if (vault == null) return;
            NbtIo.writeCompressed(vault.save(), file.toFile());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[量子背包] 保存 6 级存储失败（{}）: {}", uuid, t.toString());
        }
    }

    private void flush(boolean all) {
        if (all) {
            for (UUID uuid : vaults.keySet()) save(uuid);
            dirty.clear();
            missing.clear();
            return;
        }
        if (dirty.isEmpty()) return;
        for (UUID uuid : dirty.toArray(new UUID[0])) {
            save(uuid);
            dirty.remove(uuid);
        }
        missing.clear();
    }

    // ============================================================
    //  从 1~5 级的格子存储迁移过来（升级到 6 级时调用 ✓）
    // ============================================================

    /**
     * 把旧的格子内容整体并入 6 级存储 ✓（容量只增不减，所以理论上不会溢出 ✓；
     * 万一溢出就返回剩下的物品，由调用方决定怎么办 ✓）。
     */
    public java.util.List<ItemStack> migrateFrom(UUID uuid, Iterable<ItemStack> oldContents) {
        QuantumVault vault = getOrCreate(uuid);
        java.util.List<ItemStack> leftovers = new java.util.ArrayList<>();
        for (ItemStack stack : oldContents) {
            if (stack == null || stack.isEmpty()) continue;
            int inserted = vault.insert(stack);
            if (inserted < stack.getCount()) {
                ItemStack rest = stack.copy();
                rest.setCount(stack.getCount() - inserted);
                leftovers.add(rest);
            }
        }
        markDirty(uuid);
        return leftovers;
    }

    // ============================================================
    //  落盘时机
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 100 != 0) return;
        INSTANCE.flush(false);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        INSTANCE.flush(true);
        INSTANCE.vaults.clear();
        INSTANCE.dir = null;
    }
}
