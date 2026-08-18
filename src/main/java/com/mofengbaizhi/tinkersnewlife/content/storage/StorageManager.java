package com.mofengbaizhi.tinkersnewlife.content.storage;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;  // ✅ 添加这行导入

public class StorageManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageManager.class);
    private static StorageManager INSTANCE;

    // ============================================================
    //  🔧 可调常量
    // ============================================================
    public static final int BASE_SLOTS = 27;
    public static final int SLOTS_PER_LEVEL = 27;
    public static final int MAX_LEVEL = 5;
    public static final int STACK_MULTIPLIER = 64;
    public static final int MAX_STACK_SIZE = 64 * STACK_MULTIPLIER; // 4096
    // ============================================================

    // ============================================================
    //  📦 自定义 ItemStackHandler，支持超大堆叠
    // ============================================================
    public static class BigStackHandler extends ItemStackHandler {
        private final int maxStackSize;

        public BigStackHandler(int slots) {
            this(slots, MAX_STACK_SIZE);
        }

        public BigStackHandler(int slots, int maxStackSize) {
            super(slots);
            this.maxStackSize = maxStackSize;
        }

        public int getMaxStackSize() {
            return maxStackSize;
        }

        @Override
        public int getSlotLimit(int slot) {
            return maxStackSize;
        }

        @Override
        public int getStackLimit(int slot, @Nonnull ItemStack stack) {
            return maxStackSize;
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            validateSlotIndex(slot);
            if (!stack.isEmpty() && stack.getCount() > maxStackSize) {
                stack = stack.copy();
                stack.setCount(maxStackSize);
            }
            this.stacks.set(slot, stack);
            onContentsChanged(slot);
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            int size = nbt.contains("Size", Tag.TAG_INT) ? nbt.getInt("Size") : getSlots();
            setSize(size);

            ListTag itemList = nbt.getList("Items", Tag.TAG_COMPOUND);

            for (int i = 0; i < itemList.size(); i++) {
                CompoundTag itemTag = itemList.getCompound(i);
                int slot = itemTag.getInt("Slot");

                if (slot >= 0 && slot < size) {
                    String id = itemTag.getString("id");
                    int count = itemTag.getInt("Count");
                    CompoundTag tag = itemTag.contains("tag") ? itemTag.getCompound("tag") : null;

                    ItemStack stack = ItemStack.EMPTY;
                    if (!id.isEmpty()) {
                        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                        if (item != null) {
                            stack = new ItemStack(item, Math.min(count, maxStackSize));
                            if (tag != null && !tag.isEmpty()) {
                                stack.setTag(tag);
                            }
                        }
                    }

                    if (!stack.isEmpty() && count > 64 && count <= maxStackSize) {
                        stack.setCount(count);
                    }

                    stacks.set(slot, stack);
                }
            }

            onLoad();
        }

        @Nonnull
        @Override
        public CompoundTag serializeNBT() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("Size", getSlots());

            ListTag itemList = new ListTag();
            for (int i = 0; i < getSlots(); i++) {
                ItemStack stack = getStackInSlot(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putInt("Slot", i);
                    itemTag.putString("id", ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
                    itemTag.putInt("Count", stack.getCount());
                    if (stack.getTag() != null) {
                        itemTag.put("tag", stack.getTag());
                    }
                    itemList.add(itemTag);
                }
            }
            nbt.put("Items", itemList);
            return nbt;
        }
    }

    // ============================================================
    //  单例管理
    // ============================================================

    private final ConcurrentHashMap<UUID, BigStackHandler> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> dirtyFlags = new ConcurrentHashMap<>();

    private Path storageDir;
    private boolean isServerSide = false;

    private StorageManager() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static StorageManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StorageManager();
        }
        return INSTANCE;
    }

    public void initServer(Path worldSaveDir) {
        if (EffectiveSide.get() != LogicalSide.SERVER) return;
        this.isServerSide = true;
        this.storageDir = worldSaveDir.resolve("data")
                .resolve(TinkersNewlife.MOD_ID)
                .resolve("backpacks");
        try {
            Files.createDirectories(storageDir);
            LOGGER.info("[TinkersNewlife] 量子背包存储目录: {}", storageDir.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[TinkersNewlife] 无法创建背包存储目录!", e);
        }
    }

    public static int getCapacityForLevel(int level) {
        if (level <= 0) return BASE_SLOTS;
        if (level > MAX_LEVEL) level = MAX_LEVEL;
        return level * SLOTS_PER_LEVEL;
    }

    private BigStackHandler adjustHandlerCapacity(BigStackHandler oldHandler, int newCapacity) {
        if (oldHandler.getSlots() == newCapacity) {
            return oldHandler;
        }

        LOGGER.info("[TinkersNewlife] 调整背包容量: {} -> {}", oldHandler.getSlots(), newCapacity);
        BigStackHandler newHandler = new BigStackHandler(newCapacity);

        int copySlots = Math.min(oldHandler.getSlots(), newCapacity);
        for (int i = 0; i < copySlots; i++) {
            ItemStack stack = oldHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                newHandler.setStackInSlot(i, stack.copy());
            }
        }

        return newHandler;
    }

    public BigStackHandler getOrCreate(UUID uuid, int level) {
        if (!isServerSide) {
            LOGGER.warn("[TinkersNewlife] 客户端不应直接调用 StorageManager.getOrCreate()");
            return new BigStackHandler(getCapacityForLevel(level));
        }

        int requiredCapacity = getCapacityForLevel(level);

        BigStackHandler cached = cache.get(uuid);
        if (cached != null) {
            if (cached.getSlots() != requiredCapacity) {
                BigStackHandler adjusted = adjustHandlerCapacity(cached, requiredCapacity);
                cache.put(uuid, adjusted);
                markDirty(uuid);
                return adjusted;
            }
            return cached;
        }

        BigStackHandler handler = loadFromFile(uuid, level);
        if (handler != null) {
            if (handler.getSlots() != requiredCapacity) {
                handler = adjustHandlerCapacity(handler, requiredCapacity);
            }
            cache.put(uuid, handler);
            return handler;
        }

        handler = new BigStackHandler(requiredCapacity);
        cache.put(uuid, handler);
        LOGGER.info("[TinkersNewlife] 创建新背包: {} ({} 格)", uuid, requiredCapacity);
        return handler;
    }

    public void markDirty(UUID uuid) {
        if (!isServerSide) return;
        dirtyFlags.put(uuid, true);
    }

    @Nullable
    private BigStackHandler loadFromFile(UUID uuid, int currentLevel) {
        if (storageDir == null) return null;
        Path file = storageDir.resolve(uuid.toString() + ".nbt");
        if (!Files.exists(file)) return null;

        int currentCapacity = getCapacityForLevel(currentLevel);

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file.toFile()))) {
            CompoundTag tag = NbtIo.read(dis);
            if (tag == null) return null;

            int savedSlots = tag.getInt("Size");
            BigStackHandler handler = new BigStackHandler(Math.max(savedSlots, currentCapacity));

            handler.deserializeNBT(tag);

            if (savedSlots > currentCapacity) {
                LOGGER.warn("[TinkersNewlife] 背包 {} 容量从 {} 缩减到 {}，将截断多余物品", uuid, savedSlots, currentCapacity);
                BigStackHandler truncated = new BigStackHandler(currentCapacity);
                for (int i = 0; i < Math.min(savedSlots, currentCapacity); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        truncated.setStackInSlot(i, stack.copy());
                    }
                }
                return truncated;
            }

            return handler;
        } catch (Exception e) {
            LOGGER.error("[TinkersNewlife] 读取背包文件失败: {}", uuid, e);
            return null;
        }
    }

    private void saveToFile(UUID uuid, BigStackHandler handler) {
        if (storageDir == null) return;
        Path file = storageDir.resolve(uuid.toString() + ".nbt");
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(file.toFile()))) {
            CompoundTag tag = handler.serializeNBT();
            NbtIo.write(tag, dos);
        } catch (Exception e) {
            LOGGER.error("[TinkersNewlife] 保存背包文件失败: {}", uuid, e);
        }
    }

    public void saveAllDirty() {
        if (!isServerSide) return;
        for (UUID uuid : dirtyFlags.keySet()) {
            if (Boolean.TRUE.equals(dirtyFlags.get(uuid))) {
                BigStackHandler handler = cache.get(uuid);
                if (handler != null) {
                    saveToFile(uuid, handler);
                }
                dirtyFlags.remove(uuid);
            }
        }
    }

    public void saveAll() {
        if (!isServerSide) return;
        LOGGER.info("[TinkersNewlife] 保存所有背包数据...");
        saveAllDirty();
        for (var entry : cache.entrySet()) {
            saveToFile(entry.getKey(), entry.getValue());
        }
        cache.clear();
        dirtyFlags.clear();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        saveAll();
        isServerSide = false;
    }

    public void autoSave() {
        saveAllDirty();
    }

    // ============================================================
    //  🧹 背包整理功能
    // ============================================================

    /**
     * 整理指定 UUID 的背包：合并同类物品，空出前面的格子
     */
    public void sortInventory(UUID uuid) {
        if (!isServerSide) return;

        BigStackHandler handler = cache.get(uuid);
        if (handler == null) return;

        int slotCount = handler.getSlots();

        // 1. 收集所有非空物品
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
        }

        // 2. 清空背包
        for (int i = 0; i < slotCount; i++) {
            handler.setStackInSlot(i, ItemStack.EMPTY);
        }

        // 3. 按物品分组（相同 ID + 相同 NBT）
        Map<String, List<ItemStack>> grouped = new LinkedHashMap<>();

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            String key = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString() + "_" +
                    (stack.getTag() != null ? stack.getTag().toString() : "");
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(stack);
        }

        // 4. 合并每个组的物品
        List<ItemStack> mergedItems = new ArrayList<>();
        for (List<ItemStack> group : grouped.values()) {
            int totalCount = 0;
            ItemStack sample = null;
            for (ItemStack stack : group) {
                if (sample == null) sample = stack;
                totalCount += stack.getCount();
            }

            while (totalCount > 0) {
                int toTake = Math.min(totalCount, MAX_STACK_SIZE);
                ItemStack copy = sample.copy();
                copy.setCount(toTake);
                mergedItems.add(copy);
                totalCount -= toTake;
            }
        }

        // 5. 放回背包
        int slotIndex = 0;
        for (ItemStack stack : mergedItems) {
            if (slotIndex >= slotCount) break;
            handler.setStackInSlot(slotIndex, stack);
            slotIndex++;
        }

        markDirty(uuid);
    }
}