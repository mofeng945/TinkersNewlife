package com.mofengbaizhi.tinkersnewlife.content.storage;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slimeknights.tconstruct.common.TinkerTags;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SilentGloveHandler extends ItemStackHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SilentGloveHandler.class);
    private static final int SIZE = 12;
    // 存储目录由 initServer() 设为世界存档下的 data/tinkersnewlife/glove_vaults，
    // 避免使用相对路径导致跨世界/多人串档，且随世界备份。
    private static Path STORAGE_DIR = null;

    private static final ConcurrentHashMap<UUID, SilentGloveHandler> SERVER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> DIRTY_FLAGS = new ConcurrentHashMap<>();

    /**
     * 服务端启动时初始化存储目录（必须传入世界存档根路径）。
     */
    public static void initServer(Path worldSaveDir) {
        STORAGE_DIR = worldSaveDir.resolve("data")
                .resolve(TinkersNewlife.MOD_ID)
                .resolve("glove_vaults");
        try {
            Files.createDirectories(STORAGE_DIR);
            LOGGER.info("[TinkersNewlife] 空间奇点库存储目录: {}", STORAGE_DIR.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[TinkersNewlife] 无法创建空间奇点库存储目录!", e);
        }
    }

    private final UUID uuid;
    private final boolean isClient;

    private SilentGloveHandler(UUID uuid) {
        super(SIZE);
        this.uuid = uuid;
        this.isClient = false;
        load();
    }

    public SilentGloveHandler(UUID uuid, CompoundTag data) {
        super(SIZE);
        this.uuid = uuid;
        this.isClient = true;
        if (data != null) {
            this.deserializeNBT(data);
        }
    }

    public static SilentGloveHandler getOrCreate(UUID uuid) {
        return SERVER_CACHE.computeIfAbsent(uuid, SilentGloveHandler::new);
    }

    public static SilentGloveHandler createClient(UUID uuid, CompoundTag data) {
        return new SilentGloveHandler(uuid, data);
    }

    // ⭐ 保存所有脏数据
    public static void saveAllDirty() {
        for (UUID uuid : DIRTY_FLAGS.keySet()) {
            if (Boolean.TRUE.equals(DIRTY_FLAGS.get(uuid))) {
                SilentGloveHandler handler = SERVER_CACHE.get(uuid);
                if (handler != null) {
                    handler.save();
                }
                DIRTY_FLAGS.remove(uuid);
            }
        }
    }

    // ⭐ 在服务器停止时调用，确保所有保存
    public static void saveAll() {
        saveAllDirty();
        for (var entry : SERVER_CACHE.entrySet()) {
            entry.getValue().save();
        }
        SERVER_CACHE.clear();
        DIRTY_FLAGS.clear();
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (stack.getItem() instanceof SilentGloveItem) {
            return false;
        }
        return stack.is(TinkerTags.Items.MODIFIABLE);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) {
            return stack;
        }
        ItemStack result = super.insertItem(slot, stack, simulate);
        if (!simulate && !result.equals(stack) && !isClient) {
            onContentsChanged(slot);
        }
        return result;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack result = super.extractItem(slot, amount, simulate);
        if (!simulate && !result.isEmpty() && !isClient) {
            onContentsChanged(slot);
        }
        return result;
    }

    private Path getFilePath() {
        if (STORAGE_DIR == null) return null;
        return STORAGE_DIR.resolve(uuid.toString() + ".nbt");
    }

    private void load() {
        if (isClient) return;
        Path file = getFilePath();
        if (file == null || !Files.exists(file)) return;
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file.toFile()))) {
            // ⭐ 压缩读取
            CompoundTag tag = NbtIo.readCompressed(dis);
            if (tag != null) {
                this.deserializeNBT(tag);
            }
        } catch (IOException e) {
            LOGGER.error("[TinkersNewlife] 读取手套存储失败: {}", uuid, e);
        }
    }

    public void save() {
        if (isClient) return;
        Path file = getFilePath();
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file.toFile()))) {
                // ⭐ 压缩写入
                NbtIo.writeCompressed(this.serializeNBT(), dos);
            }
        } catch (IOException e) {
            LOGGER.error("[TinkersNewlife] 保存手套存储失败: {}", uuid, e);
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        if (!isClient) {
            // ⭐ 只标记脏，不立即保存
            DIRTY_FLAGS.put(uuid, true);
        }
    }

    public int getUsedSlots() {
        int count = 0;
        for (int i = 0; i < getSlots(); i++) {
            if (!getStackInSlot(i).isEmpty()) count++;
        }
        return count;
    }

    public UUID getUUID() {
        return uuid;
    }
}