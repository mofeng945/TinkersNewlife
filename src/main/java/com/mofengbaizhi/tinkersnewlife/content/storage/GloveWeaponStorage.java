package com.mofengbaizhi.tinkersnewlife.content.storage;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import com.mofengbaizhi.tinkersnewlife.util.GloveHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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

        taken.getOrCreateTag().putBoolean(TAG_FROM_GLOVE, true);

        PENDING_TOOLS.put(playerId, new PendingTool(slot, original, playerId));
        return taken;
    }

    /**
     * 掏出武器时打的临时标记：<b>这是"该物品正被手套借出"的唯一可靠凭据</b>。
     * <p>
     * ⭐ 不能只靠"NBT 全等"找回借出的武器：工具体系会在使用中改写 NBT——
     * 除耐久（{@code Damage}）外，匠魂还有 {@code tic_volatile_data} / {@code tic_persistent_data}
     * （特性计数、法术状态等），本模组不少特性也在上面记数。只要玩家挥了一下，
     * 全等匹配就失效 → 找不到武器 → 永远回不了库（表现：武器留在手上 / 最后被塞进背包）。
     */
    public static final String TAG_FROM_GLOVE = "from_silent_glove";

    /** 该物品是否带着"手套借出"标记 */
    public static boolean isDrawnFromGlove(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(TAG_FROM_GLOVE);
    }

    public static boolean returnTool(Player player, int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (DarkSilentManager.isActive(player.getUUID())) return false;

        if (stack.hasTag()) stack.getTag().remove(TAG_FROM_GLOVE);

        // ⭐ 统一查找佩戴的手套（GloveHelper）
        ItemStack gloveStack = GloveHelper.findWornGlove(player);
        if (gloveStack.isEmpty()) return false;

        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return false;

        // 1) 先回原槽位（该槽位此时通常已空）
        ItemStack remaining = stack;
        if (slot >= 0 && slot < vault.getSlots()) {
            remaining = vault.insertItem(slot, stack, false);
        }
        // ⭐ 2) 其余槽位**逐个尝试**：insertItem 会自己叠放并把放不下的余量返回。
        //    旧实现只在"空槽"里找 → 库位被占但仍有可叠放槽位时，武器会被判成"库已满"丢进背包
        //    （报告现象："放在第一格的物品不会被正常回收"）。
        for (int i = 0; i < vault.getSlots() && !remaining.isEmpty(); i++) {
            if (i == slot) continue;
            remaining = vault.insertItem(i, remaining, false);
        }

        if (remaining.isEmpty()) {
            vault.save();
            DarkSilentManager.checkAndTriggerOnStored(player, stack);
            return true;
        }

        vault.save();
        ItemHandlerHelper.giveItemToPlayer(player, remaining);
        LOGGER.warn("手套空间奇点库已满，工具 {} 已返还到玩家背包",
                remaining.getDisplayName().getString());
        return true;
    }

    /**
     * 检查捡起的物品是否可回收进手套：与手套库中任意物品匹配（固定物品，
     * 物品相同 + NBT 全等，仅忽略耐久）。
     * <p>
     * 库里已有的物品（无论手动放入还是自动回收进入）捡回时都能匹配；
     * 库中不存在的物品不会被自动回收。打开手套增删物品即更改可回收集合。
     */
    public static boolean isRecyclable(Player player, ItemStack stack) {
        ItemStack gloveStack = GloveHelper.findWornGlove(player);
        if (gloveStack.isEmpty()) return false;
        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return false;
        for (int i = 0; i < vault.getSlots(); i++) {
            if (isSameFixedItem(vault.getStackInSlot(i), stack)) {
                return true;
            }
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
        // ⭐ 移除掏出时的临时标记，保证库中物品 NBT 干净（不干扰后续匹配）
        if (remaining.hasTag()) remaining.getTag().remove(TAG_FROM_GLOVE);
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

    /**
     * 移除与指定物品匹配的待回收记录（拾取直接回收后调用，避免残留空扫）。
     */
    public static void removePendingRecovery(Player player, ItemStack stack) {
        UUID playerId = player.getUUID();
        List<PendingRecovery> list = PENDING_RECOVERIES.get(playerId);
        if (list == null || list.isEmpty()) return;
        synchronized (list) {
            list.removeIf(rec -> isSameFixedItem(rec.stack, stack));
        }
    }

    public static void tickScan(Player player) {
        if (player == null) return;
        UUID playerId = player.getUUID();
        // ⭐ 顺便找回"借出记录已丢"的孤儿武器（重登/重启/匹配失败留下的）
        recoverOrphanedTools(player);
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

    /**
     * 从背包里找出"借出的那把武器"并取走。
     * <p>
     * ⭐ 两级匹配：**先认"手套借出"标记**（唯一可靠），再退回"NBT 全等"（忽略耐久）。
     * 只用后者时，玩家一旦挥过刀（耐久/匠魂 volatile、persistent 数据都会变）就再也找不回来。
     */
    private static ItemStack findItemInInventory(Player player, ItemStack target) {
        ItemStack main = player.getMainHandItem();
        if (isDrawnFromGlove(main)) {
            ItemStack found = main.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return found;
        }
        if (isSameFixedItem(main, target)) {
            ItemStack found = main.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return found;
        }
        int taggedIndex = -1;
        ItemStack tagged = ItemStack.EMPTY;
        int matchedIndex = -1;
        ItemStack matched = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (invStack.isEmpty()) continue;
            if (taggedIndex < 0 && isDrawnFromGlove(invStack)) {
                tagged = invStack.copy();
                taggedIndex = i;
            } else if (matchedIndex < 0 && isSameFixedItem(invStack, target)) {
                matched = invStack.copy();
                matchedIndex = i;
            }
            if (taggedIndex >= 0 && matchedIndex >= 0) break;
        }
        if (taggedIndex >= 0) {
            player.getInventory().removeItem(taggedIndex, tagged.getCount());
            return tagged;
        }
        if (matchedIndex >= 0) {
            player.getInventory().removeItem(matchedIndex, matched.getCount());
            return matched;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 找回"借出后没能归还"的武器（孤儿回收）。
     * <p>
     * 触发场景：登出/服务器重启把 {@code PENDING_TOOLS} 清空了（内存态），
     * 而武器还带在玩家身上；或归还时匹配失败被当成"待回收"。这些物品身上仍带
     * {@link #TAG_FROM_GLOVE} 标记，直接据此收进库里，避免"武器一直留在手上/背包里"。
     * <p>
     * 注意：正在借出中的那把（{@code PENDING_TOOLS} 有记录）不动——那是玩家正在用的。
     */
    public static void recoverOrphanedTools(Player player) {
        if (player == null || player.level().isClientSide) return;
        UUID playerId = player.getUUID();
        if (DarkSilentManager.isActive(playerId)) return;
        if (PENDING_TOOLS.containsKey(playerId)) return;   // 正在用，别抢

        ItemStack gloveStack = GloveHelper.findWornGlove(player);
        if (gloveStack.isEmpty()) return;
        if (SilentGloveItem.getHandler(gloveStack) == null) return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (!isDrawnFromGlove(st)) continue;
            ItemStack found = st.copy();
            player.getInventory().removeItem(i, found.getCount());
            boolean ok = returnTool(player, -1, found);
            LOGGER.info("[噤默手套] 孤儿回收：{} 已{}", found.getDisplayName().getString(),
                    ok ? "归还空间奇点库" : "归还失败，已留在背包");
        }
    }

    /**
     * 固定物品精确匹配：物品相同且 NBT 完全一致，忽略耐久（Damage）与
     * 掏出时的临时标记（from_silent_glove）。
     * ⭐ 防止待回收补偿逻辑在背包里误收"同类但不同配置"的其他物品，
     * 也确保丢到地上的掏出物品捡起后能与库中原物匹配。
     */
    private static boolean isSameFixedItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!ItemStack.isSameItem(a, b)) return false;
        ItemStack ca = a.copy();
        ItemStack cb = b.copy();
        if (ca.getTag() != null) {
            ca.getTag().remove("Damage");
            ca.getTag().remove(TAG_FROM_GLOVE);
        }
        if (cb.getTag() != null) {
            cb.getTag().remove("Damage");
            cb.getTag().remove(TAG_FROM_GLOVE);
        }
        return ItemStack.isSameItemSameTags(ca, cb);
    }
}