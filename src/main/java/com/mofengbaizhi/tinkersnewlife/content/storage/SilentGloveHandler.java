package com.mofengbaizhi.tinkersnewlife.content.storage;

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
    private static final Path STORAGE_DIR = Path.of("tinkersnewlife/glove_vaults");

    private static final ConcurrentHashMap<UUID, SilentGloveHandler> SERVER_CACHE = new ConcurrentHashMap<>();

    private final UUID uuid;
    private final boolean isClient; // true 表示客户端实例，不持久化
    private boolean dirty = false;

    // 服务端构造：使用缓存
    private SilentGloveHandler(UUID uuid) {
        super(SIZE);
        this.uuid = uuid;
        this.isClient = false;
        load();
    }

    // 客户端构造：从 NBT 加载数据，不持久化
    public SilentGloveHandler(UUID uuid, CompoundTag data) {
        super(SIZE);
        this.uuid = uuid;
        this.isClient = true;
        if (data != null) {
            this.deserializeNBT(data);
        }
    }

    // 服务端获取或创建
    public static SilentGloveHandler getOrCreate(UUID uuid) {
        return SERVER_CACHE.computeIfAbsent(uuid, SilentGloveHandler::new);
    }

    // 客户端获取（只读，不使用缓存）
    public static SilentGloveHandler createClient(UUID uuid, CompoundTag data) {
        return new SilentGloveHandler(uuid, data);
    }

    // ========== 过滤规则 ==========

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        // ❌ 禁止手套自身
        if (stack.getItem() instanceof SilentGloveItem) {
            return false;
        }
        // ✅ 只允许匠魂可改装物品
        return stack.is(TinkerTags.Items.MODIFIABLE);
    }

    // ========== 重写插入和提取 ==========

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

    // ========== 持久化（仅服务端） ==========

    private Path getFilePath() {
        return STORAGE_DIR.resolve(uuid.toString() + ".nbt");
    }

    private void load() {
        if (isClient) return;
        Path file = getFilePath();
        if (!Files.exists(file)) return;
        try (DataInputStream dis = new DataInputStream(new FileInputStream(file.toFile()))) {
            CompoundTag tag = NbtIo.read(dis);
            if (tag != null) {
                this.deserializeNBT(tag);
            }
        } catch (IOException e) {
            LOGGER.error("[TinkersNewlife] 读取手套存储失败: {}", uuid, e);
        }
    }

    public void save() {
        if (isClient) return;
        try {
            Files.createDirectories(getFilePath().getParent());
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(getFilePath().toFile()))) {
                NbtIo.write(this.serializeNBT(), dos);
            }
        } catch (IOException e) {
            LOGGER.error("[TinkersNewlife] 保存手套存储失败: {}", uuid, e);
        }
        dirty = false;
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        if (!isClient) {
            dirty = true;
            save();
        }
    }

    // ========== 辅助方法 ==========

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

    public boolean isDirty() {
        return dirty;
    }
}