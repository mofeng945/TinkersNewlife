package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import com.mofengbaizhi.tinkersnewlife.util.GloveHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GloveWeaponStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(GloveWeaponStorage.class);
    private static final Random RANDOM = new Random();
    private static final ScheduledExecutorService RETURN_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SilentGloveReturn");
        t.setDaemon(true);
        return t;
    });

    public static final int HOLD_TICKS = 30;
    public static final long HOLD_MILLIS = HOLD_TICKS * 50L;
    public static final int SCAN_INTERVAL_TICKS = 40;
    private static int tickCounter = 0;

    private static final Map<UUID, PendingTool> PENDING_TOOLS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<PendingRecovery>> PENDING_RECOVERIES = new ConcurrentHashMap<>();

    private static class PendingTool {
        final int slot;
        final ItemStack original;
        final UUID playerId;
        PendingTool(int slot, ItemStack original, UUID playerId) {
            this.slot = slot;
            this.original = original;
            this.playerId = playerId;
        }
    }

    private static class PendingRecovery {
        final ItemStack stack;
        PendingRecovery(ItemStack stack) { this.stack = stack; }
    }

    // ===================== 公开方法 =====================

    public static ItemStack extractRandomTool(Player player) {
        UUID playerId = player.getUUID();
        if (DarkSilentManager.isActive(playerId)) return ItemStack.EMPTY;

        if (PENDING_TOOLS.containsKey(playerId)) return ItemStack.EMPTY;

        forceReturnPending(player);
        if (PENDING_TOOLS.containsKey(playerId)) return ItemStack.EMPTY;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && !(mainHand.getItem() instanceof SilentGloveItem)) {
            boolean stored = tryStoreInVault(player, mainHand);
            if (stored) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.tinkersnewlife.glove.full_hand")
                            .withStyle(ChatFormatting.RED), true
                    );
                }
                return ItemStack.EMPTY;
            }
        }

        var curios = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
        if (curios.isEmpty()) return ItemStack.EMPTY;

        // ⭐ 统一查找佩戴的手套（GloveHelper）
        ItemStack gloveStack = GloveHelper.findWornGlove(player);
        if (gloveStack.isEmpty()) return ItemStack.EMPTY;

        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return ItemStack.EMPTY;

        List<Integer> validSlots = new ArrayList<>();
        for (int i = 0; i < vault.getSlots(); i++) {
            if (!vault.getStackInSlot(i).isEmpty()) validSlots.add(i);
        }
        if (validSlots.isEmpty()) return ItemStack.EMPTY;

        int selectedIndex = RANDOM.nextInt(validSlots.size());
        int slot = validSlots.get(selectedIndex);
        ItemStack original = vault.getStackInSlot(slot).copy();
        ItemStack taken = vault.extractItem(slot, original.getCount(), false);
        if (taken.isEmpty()) return ItemStack.EMPTY;

        taken.getOrCreateTag().putBoolean("from_silent_glove", true);

        PENDING_TOOLS.put(playerId, new PendingTool(slot, original, playerId));
        return taken;
    }

    public static boolean returnTool(Player player, int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (DarkSilentManager.isActive(player.getUUID())) return false;

        if (stack.hasTag()) stack.getTag().remove("from_silent_glove");

        // ⭐ 统一查找佩戴的手套（GloveHelper）
        ItemStack gloveStack = GloveHelper.findWornGlove(player);
        if (gloveStack.isEmpty()) return false;

        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return false;

        ItemStack remaining = vault.insertItem(slot, stack, false);
        if (remaining.isEmpty()) {
            vault.save();
            DarkSilentManager.checkAndTriggerOnStored(player, stack);
            return true;
        }

        for (int i = 0; i < vault.getSlots(); i++) {
            if (i == slot) continue;
            if (vault.getStackInSlot(i).isEmpty()) {
                remaining = vault.insertItem(i, remaining, false);
                if (remaining.isEmpty()) {
                    vault.save();
                    DarkSilentManager.checkAndTriggerOnStored(player, stack);
                    return true;
                }
            }
        }

        if (!remaining.isEmpty()) {
            vault.save();
            ItemHandlerHelper.giveItemToPlayer(player, remaining);
            LOGGER.warn("手套空间奇点库已满，工具 {} 已返还到玩家背包",
                    remaining.getDisplayName().getString());
            return true;
        }
        return false;
    }

    public static boolean tryStoreInVault(Player player, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (DarkSilentManager.isActive(player.getUUID())) return false;

        // ⭐ 统一查找佩戴的手套（GloveHelper）
        ItemStack gloveStack = GloveHelper.findWornGlove(player);
        if (gloveStack.isEmpty()) return false;

        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return false;

        int totalSpace = 0;
        for (int i = 0; i < vault.getSlots(); i++) {
            ItemStack slotStack = vault.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                totalSpace += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(slotStack, stack)) {
                totalSpace += (stack.getMaxStackSize() - slotStack.getCount());
            }
        }
        if (totalSpace < stack.getCount()) return false;

        ItemStack remaining = stack.copy();
        for (int i = 0; i < vault.getSlots(); i++) {
            if (remaining.isEmpty()) break;
            remaining = vault.insertItem(i, remaining, false);
        }
        if (remaining.isEmpty()) {
            vault.save();
            DarkSilentManager.checkAndTriggerOnStored(player, stack);
            return true;
        }
        return false;
    }

    public static void forceReturnPending(Player player) {
        UUID playerId = player.getUUID();
        if (DarkSilentManager.isActive(playerId)) return;

        PendingTool pending = PENDING_TOOLS.remove(playerId);
        if (pending == null) return;

        if (player.hasEffect(ModEffects.DISARM.get())) {
            PENDING_TOOLS.put(playerId, pending);
            scheduleReturnDelayed(player, pending);
            return;
        }

        ItemStack found = findItemInInventory(player, pending.original);
        if (!found.isEmpty()) {
            boolean success = returnTool(player, pending.slot, found);
            if (!success) addPendingRecovery(player, found);
        } else {
            addPendingRecovery(player, pending.original);
        }
    }

    public static void scheduleReturn(Player player) {
        UUID playerId = player.getUUID();
        if (player.hasEffect(ModEffects.DISARM.get())) return;

        RETURN_SCHEDULER.schedule(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                Player targetPlayer = server.getPlayerList().getPlayer(playerId);
                if (targetPlayer == null) { PENDING_TOOLS.remove(playerId); return; }
                if (DarkSilentManager.isActive(playerId)) return;
                if (targetPlayer.hasEffect(ModEffects.DISARM.get())) {
                    scheduleReturn(targetPlayer);
                    return;
                }

                PendingTool pending = PENDING_TOOLS.remove(playerId);
                if (pending == null) return;

                ItemStack toReturn = findItemInInventory(targetPlayer, pending.original);
                if (!toReturn.isEmpty()) {
                    boolean success = returnTool(targetPlayer, pending.slot, toReturn);
                    if (!success) addPendingRecovery(targetPlayer, toReturn);
                } else {
                    addPendingRecovery(targetPlayer, pending.original);
                }
            });
        }, HOLD_MILLIS, TimeUnit.MILLISECONDS);
    }

    public static void addPendingRecovery(Player player, ItemStack stack) {
        UUID playerId = player.getUUID();
        List<PendingRecovery> list = PENDING_RECOVERIES.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (list) { list.add(new PendingRecovery(stack.copy())); }
    }

    public static void clearPendingRecoveries(Player player) {
        PENDING_RECOVERIES.remove(player.getUUID());
    }

    public static void tickScan(Player player) {
        if (player == null) return;
        UUID playerId = player.getUUID();
        List<PendingRecovery> recoveries = PENDING_RECOVERIES.get(playerId);
        if (recoveries == null || recoveries.isEmpty()) return;

        synchronized (recoveries) {
            for (int i = recoveries.size() - 1; i >= 0; i--) {
                PendingRecovery rec = recoveries.get(i);
                ItemStack target = rec.stack;
                ItemStack found = findItemInInventory(player, target);
                if (!found.isEmpty()) {
                    boolean success = tryStoreInVault(player, found);
                    if (success) {
                        recoveries.remove(i);
                        LOGGER.debug("定时扫描：玩家 {} 的待回收物品 {} 已存入空间奇点库",
                                playerId, target.getDisplayName().getString());
                    }
                }
            }
        }
    }

    public static void removePlayerData(UUID uuid) {
        PENDING_TOOLS.remove(uuid);
        PENDING_RECOVERIES.remove(uuid);
    }

    public static int getScanIntervalTicks() { return SCAN_INTERVAL_TICKS; }
    public static int getTickCounter() { return tickCounter; }
    public static void incrementTickCounter() { tickCounter++; }

    // ===================== 内部辅助 =====================

    private static void scheduleReturnDelayed(Player player, PendingTool pending) {
        RETURN_SCHEDULER.schedule(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                Player target = server.getPlayerList().getPlayer(pending.playerId);
                if (target == null) return;
                if (target.hasEffect(ModEffects.DISARM.get())) {
                    PENDING_TOOLS.put(target.getUUID(), pending);
                    scheduleReturnDelayed(target, pending);
                    return;
                }
                forceReturnPending(target);
            });
        }, 20, TimeUnit.SECONDS);
    }

    private static ItemStack findItemInInventory(Player player, ItemStack target) {
        ItemStack main = player.getMainHandItem();
        if (isMatchingItem(main, target) || ItemStack.isSameItem(main, target)) {
            ItemStack found = main.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return found;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (isMatchingItem(invStack, target) || ItemStack.isSameItem(invStack, target)) {
                ItemStack found = invStack.copy();
                player.getInventory().removeItem(i, invStack.getCount());
                return found;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isMatchingItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!ItemStack.isSameItem(a, b)) return false;

        CompoundTag tagA = a.getTag();
        CompoundTag tagB = b.getTag();
        if (tagA == null && tagB == null) return true;
        if (tagA == null || tagB == null) return false;

        if (tagA.contains("display", Tag.TAG_COMPOUND) || tagB.contains("display", Tag.TAG_COMPOUND)) {
            CompoundTag displayA = tagA.getCompound("display");
            CompoundTag displayB = tagB.getCompound("display");
            if (displayA.contains("Name", Tag.TAG_STRING) && displayB.contains("Name", Tag.TAG_STRING)) {
                if (!displayA.getString("Name").equals(displayB.getString("Name"))) return false;
            } else if (displayA.contains("Name", Tag.TAG_STRING) || displayB.contains("Name", Tag.TAG_STRING)) {
                return false;
            }
        }

        if (tagA.contains("Enchantments", Tag.TAG_LIST) || tagB.contains("Enchantments", Tag.TAG_LIST)) {
            ListTag enchA = tagA.getList("Enchantments", Tag.TAG_COMPOUND);
            ListTag enchB = tagB.getList("Enchantments", Tag.TAG_COMPOUND);
            if (!enchA.equals(enchB)) return false;
        }
        return true;
    }
}