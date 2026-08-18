package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.item.DurandalSwordItem;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class SilentGloveInteractionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SilentGloveInteractionHandler.class);
    private static final Random RANDOM = new Random();

    private static final int HOLD_TICKS = 30;
    private static final long HOLD_MILLIS = HOLD_TICKS * 50L;
    private static final int SCAN_INTERVAL_TICKS = 40;
    private static int tickCounter = 0;

    private static final Map<UUID, PendingTool> PENDING_TOOLS = new ConcurrentHashMap<>();
    private static final Map<UUID, List<PendingRecovery>> PENDING_RECOVERIES = new ConcurrentHashMap<>();

    private static final Set<UUID> DARK_SILENT_ACTIVE = ConcurrentHashMap.newKeySet();

    // ============================================================
    //  检查是否装备了噤默手套
    // ============================================================

    private static boolean isWearingGlove(Player player) {
        var curios = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
        if (curios.isEmpty()) return false;

        var gloveHandler = curios.get().getStacksHandler("hands").orElse(null);
        if (gloveHandler == null) return false;

        for (int i = 0; i < gloveHandler.getSlots(); i++) {
            ItemStack stack = gloveHandler.getStacks().getStackInSlot(i);
            if (stack.getItem() instanceof SilentGloveItem) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    //  强制归还旧武器（大招期间禁用）
    // ============================================================

    private static void forceReturnPending(Player player) {
        UUID playerId = player.getUUID();
        if (DARK_SILENT_ACTIVE.contains(playerId)) return;

        PendingTool pending = PENDING_TOOLS.remove(playerId);
        if (pending == null) return;

        ItemStack found = ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        if (isMatchingItem(main, pending.original)) {
            found = main.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        } else {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack invStack = player.getInventory().getItem(i);
                if (isMatchingItem(invStack, pending.original)) {
                    found = invStack.copy();
                    player.getInventory().removeItem(i, invStack.getCount());
                    break;
                }
            }
        }

        if (!found.isEmpty()) {
            boolean success = returnTool(player, pending.slot, found);
            if (!success) {
                addPendingRecovery(player, found);
            }
        } else {
            addPendingRecovery(player, pending.original);
        }
    }

    // ============================================================
    //  取出随机武器（大招期间返回空，并标记来源）
    // ============================================================

    private static ItemStack extractRandomTool(Player player) {
        UUID playerId = player.getUUID();
        if (DARK_SILENT_ACTIVE.contains(playerId)) return ItemStack.EMPTY;

        forceReturnPending(player);

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && !(mainHand.getItem() instanceof SilentGloveItem)) {
            boolean stored = tryStoreInVault(player, mainHand);
            if (stored) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                ItemHandlerHelper.giveItemToPlayer(player, mainHand);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }

        var curios = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
        if (curios.isEmpty()) return ItemStack.EMPTY;

        var gloveHandler = curios.get().getStacksHandler("hands").orElse(null);
        if (gloveHandler == null) return ItemStack.EMPTY;

        ItemStack gloveStack = ItemStack.EMPTY;
        for (int i = 0; i < gloveHandler.getSlots(); i++) {
            ItemStack stack = gloveHandler.getStacks().getStackInSlot(i);
            if (stack.getItem() instanceof SilentGloveItem) {
                gloveStack = stack;
                break;
            }
        }
        if (gloveStack.isEmpty()) return ItemStack.EMPTY;

        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return ItemStack.EMPTY;

        List<Integer> validSlots = new ArrayList<>();
        for (int i = 0; i < vault.getSlots(); i++) {
            if (!vault.getStackInSlot(i).isEmpty()) {
                validSlots.add(i);
            }
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

    // ============================================================
    //  存回武器（大招期间返回false，并在存回后检查杜兰达尔计数）
    // ============================================================

    private static boolean returnTool(Player player, int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return false;

        if (stack.hasTag()) {
            stack.getTag().remove("from_silent_glove");
        }

        var curios = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
        if (curios.isEmpty()) return false;

        var gloveHandler = curios.get().getStacksHandler("hands").orElse(null);
        if (gloveHandler == null) return false;

        ItemStack gloveStack = ItemStack.EMPTY;
        for (int i = 0; i < gloveHandler.getSlots(); i++) {
            ItemStack gs = gloveHandler.getStacks().getStackInSlot(i);
            if (gs.getItem() instanceof SilentGloveItem) {
                gloveStack = gs;
                break;
            }
        }
        if (gloveStack.isEmpty()) return false;

        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return false;

        ItemStack remaining = vault.insertItem(slot, stack, false);
        if (remaining.isEmpty()) {
            vault.save();
            tryTriggerDarkSilentOnStored(player, stack);
            return true;
        }

        for (int i = 0; i < vault.getSlots(); i++) {
            if (i == slot) continue;
            if (vault.getStackInSlot(i).isEmpty()) {
                remaining = vault.insertItem(i, remaining, false);
                if (remaining.isEmpty()) {
                    vault.save();
                    tryTriggerDarkSilentOnStored(player, stack);
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

    // ============================================================
    //  尝试存入手套库（大招期间返回false，并在存回后检查杜兰达尔计数）
    // ============================================================

    private static boolean tryStoreInVault(Player player, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return false;

        var curios = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
        if (curios.isEmpty()) return false;

        var gloveHandler = curios.get().getStacksHandler("hands").orElse(null);
        if (gloveHandler == null) return false;

        ItemStack gloveStack = ItemStack.EMPTY;
        for (int i = 0; i < gloveHandler.getSlots(); i++) {
            ItemStack gs = gloveHandler.getStacks().getStackInSlot(i);
            if (gs.getItem() instanceof SilentGloveItem) {
                gloveStack = gs;
                break;
            }
        }
        if (gloveStack.isEmpty()) return false;

        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return false;

        for (int i = 0; i < vault.getSlots(); i++) {
            if (vault.getStackInSlot(i).isEmpty()) {
                ItemStack remaining = vault.insertItem(i, stack, false);
                if (remaining.isEmpty()) {
                    vault.save();
                    tryTriggerDarkSilentOnStored(player, stack);
                    return true;
                }
            }
        }
        return false;
    }

    // ============================================================
    //  检查存回的杜兰达尔是否触发大招（需同时满足：计数≥9 + 装备手套）
    // ============================================================

    private static void tryTriggerDarkSilentOnStored(Player player, ItemStack stack) {
        if (player.level().isClientSide) return;
        if (!(stack.getItem() instanceof DurandalSwordItem)) return;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return;

        int count = DurandalSwordItem.getHitCount(stack);
        if (count >= 9 && isWearingGlove(player)) {
            DurandalSwordItem.setHitCount(stack, 0);
            triggerDarkSilent(player);
        }
    }

    // ============================================================
    //  定时归还（大招期间不执行）
    // ============================================================

    private static void scheduleReturn(Player player) {
        UUID playerId = player.getUUID();
        if (player.hasEffect(ModEffects.DISARM.get())) {
            PENDING_TOOLS.remove(playerId);
            return;
        }

        new Thread(() -> {
            try {
                Thread.sleep(HOLD_MILLIS);
            } catch (InterruptedException ignored) {}
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                Player targetPlayer = server.getPlayerList().getPlayer(playerId);
                if (targetPlayer == null) {
                    PENDING_TOOLS.remove(playerId);
                    return;
                }
                if (DARK_SILENT_ACTIVE.contains(playerId)) return;
                if (targetPlayer.hasEffect(ModEffects.DISARM.get())) {
                    PENDING_TOOLS.remove(playerId);
                    return;
                }

                PendingTool pending = PENDING_TOOLS.remove(playerId);
                if (pending == null) return;

                ItemStack toReturn = ItemStack.EMPTY;
                ItemStack currentMain = targetPlayer.getMainHandItem();
                if (isMatchingItem(currentMain, pending.original)) {
                    toReturn = currentMain;
                    targetPlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                } else {
                    for (int i = 0; i < targetPlayer.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = targetPlayer.getInventory().getItem(i);
                        if (isMatchingItem(invStack, pending.original)) {
                            toReturn = invStack;
                            targetPlayer.getInventory().removeItem(i, invStack.getCount());
                            break;
                        }
                    }
                }

                if (!toReturn.isEmpty()) {
                    boolean success = returnTool(targetPlayer, pending.slot, toReturn);
                    if (!success) {
                        addPendingRecovery(targetPlayer, toReturn);
                        LOGGER.debug("玩家 {} 的工具 {} 存回失败，加入待回收列表",
                                playerId, toReturn.getDisplayName().getString());
                    }
                } else {
                    addPendingRecovery(targetPlayer, pending.original);
                    LOGGER.debug("玩家 {} 的工具 {} 不在背包，加入待回收列表",
                            playerId, pending.original.getDisplayName().getString());
                }
            });
        }).start();
    }

    private static void addPendingRecovery(Player player, ItemStack stack) {
        UUID playerId = player.getUUID();
        PENDING_RECOVERIES.computeIfAbsent(playerId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new PendingRecovery(stack.copy()));
    }

    public static void clearPendingRecoveries(Player player) {
        PENDING_RECOVERIES.remove(player.getUUID());
    }

    // ============================================================
    //  定时扫描
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        tickCounter++;
        if (tickCounter % SCAN_INTERVAL_TICKS != 0) return;

        for (UUID playerId : PENDING_RECOVERIES.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                scanPlayerInventory(player);
            }
        }
    }

    private static void scanPlayerInventory(ServerPlayer player) {
        UUID playerId = player.getUUID();
        List<PendingRecovery> recoveries = PENDING_RECOVERIES.get(playerId);
        if (recoveries == null || recoveries.isEmpty()) return;

        synchronized (recoveries) {
            for (int i = recoveries.size() - 1; i >= 0; i--) {
                PendingRecovery rec = recoveries.get(i);
                ItemStack target = rec.stack;
                int foundSlot = -1;
                ItemStack foundStack = ItemStack.EMPTY;

                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack invStack = player.getInventory().getItem(slot);
                    if (isMatchingItem(target, invStack)) {
                        foundSlot = slot;
                        foundStack = invStack;
                        break;
                    }
                }

                if (foundSlot != -1) {
                    ItemStack toStore = foundStack.copy();
                    player.getInventory().removeItem(foundSlot, toStore.getCount());
                    boolean success = tryStoreInVault(player, toStore);
                    if (success) {
                        recoveries.remove(i);
                        LOGGER.debug("定时扫描：玩家 {} 的待回收物品 {} 已存入空间奇点库",
                                playerId, target.getDisplayName().getString());
                    } else {
                        player.getInventory().setItem(foundSlot, toStore);
                        LOGGER.debug("定时扫描：玩家 {} 的待回收物品 {} 无法存入空间奇点库，保留在背包",
                                playerId, target.getDisplayName().getString());
                    }
                }
            }
        }
    }

    private static boolean isMatchingItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (!ItemStack.isSameItem(a, b)) return false;

        CompoundTag tagA = a.getTag();
        CompoundTag tagB = b.getTag();

        if (tagA != null && tagA.contains("display", Tag.TAG_COMPOUND)) {
            if (tagB == null) return false;
            CompoundTag displayA = tagA.getCompound("display");
            CompoundTag displayB = tagB.getCompound("display");
            if (displayA.contains("Name", Tag.TAG_STRING)) {
                if (!displayB.contains("Name", Tag.TAG_STRING)) return false;
                if (!displayA.getString("Name").equals(displayB.getString("Name"))) return false;
            }
        }

        if (tagA != null && tagA.contains("Enchantments", Tag.TAG_LIST)) {
            if (tagB == null) return false;
            ListTag enchA = tagA.getList("Enchantments", Tag.TAG_COMPOUND);
            ListTag enchB = tagB.getList("Enchantments", Tag.TAG_COMPOUND);
            if (!enchA.equals(enchB)) return false;
        } else if (tagB != null && tagB.contains("Enchantments", Tag.TAG_LIST)) {
            return false;
        }

        return true;
    }

    private static boolean equipRandomTool(Player player) {
        if (player.level().isClientSide) return false;
        if (!player.getMainHandItem().isEmpty()) return false;

        ItemStack tool = extractRandomTool(player);
        if (tool.isEmpty()) return false;

        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        scheduleReturn(player);
        return true;
    }

    private static void resetAttackCooldown(Player player) {
        player.resetAttackStrengthTicker();
    }

    // ============================================================
    //  杜兰达尔计数（攻击时累加，上限9，不触发大招）
    // ============================================================

    private static void checkAndTriggerDarkSilent(Player player, ItemStack usedWeapon) {
        if (player.level().isClientSide) return;
        if (!(usedWeapon.getItem() instanceof DurandalSwordItem)) return;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return;

        int count = DurandalSwordItem.getHitCount(usedWeapon);
        if (count >= 9) return;

        count++;
        DurandalSwordItem.setHitCount(usedWeapon, count);
    }

    // ============================================================
    //  大招执行（含 Action Bar 提示，使用翻译键）
    // ============================================================

    private static void triggerDarkSilent(Player player) {
        if (player.level().isClientSide) return;
        UUID uuid = player.getUUID();

        if (DARK_SILENT_ACTIVE.contains(uuid)) {
            LOGGER.warn("[漆黑噤默] 玩家 {} 已有大招正在执行，跳过", player.getName().getString());
            return;
        }

        // ===== 处理主手物品 =====
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && !(mainHand.getItem() instanceof SilentGloveItem)) {
            boolean stored = tryStoreInVault(player, mainHand);
            if (stored) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                ItemStack remaining = ItemHandlerHelper.insertItemStacked(
                        new InvWrapper(player.getInventory()),
                        mainHand,
                        false
                );
                if (remaining.isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                } else {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.connection.send(
                            new ClientboundSetActionBarTextPacket(
                                Component.translatable("message.tinkersnewlife.dark_silent.no_space")
                                    .withStyle(ChatFormatting.RED)
                            )
                        );
                    }
                    LOGGER.warn("[漆黑噤默] 玩家 {} 背包已满，大招取消", player.getName().getString());
                    return;
                }
            }
        }

        DARK_SILENT_ACTIVE.add(uuid);

        List<ItemStack> weapons = getAllWeaponsFromGlove(player);
        if (weapons.isEmpty()) {
            LOGGER.debug("[漆黑噤默] 玩家 {} 手套中没有武器，静默取消", player.getName().getString());
            DARK_SILENT_ACTIVE.remove(uuid);
            return;
        }

        // 成功触发：显示红色乱码 + furioso（翻译键）
        if (player instanceof ServerPlayer serverPlayer) {
            Component actionBar = Component.literal("#")
                    .withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.RED)
                    .append(Component.translatable("action.tinkersnewlife.dark_silent.furioso")
                            .withStyle(ChatFormatting.RED))
                    .append(Component.literal("*")
                            .withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.RED));
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
        }

        LOGGER.info("[漆黑噤默] 玩家 {} 开始执行，共 {} 把武器", player.getName().getString(), weapons.size());

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        final int totalAttacks = weapons.size();
        final int[] attackIndex = {0};

        executor.scheduleAtFixedRate(() -> {
            ServerLifecycleHooks.getCurrentServer().execute(() -> {
                if (player.isRemoved() || !player.isAlive()) {
                    executor.shutdown();
                    DARK_SILENT_ACTIVE.remove(uuid);
                    return;
                }

                if (attackIndex[0] >= totalAttacks) {
                    executor.shutdown();
                    DARK_SILENT_ACTIVE.remove(uuid);
                    LOGGER.info("[漆黑噤默] 玩家 {} 大招完成", player.getName().getString());
                    return;
                }

                ItemStack weapon = weapons.get(attackIndex[0]);
                if (weapon.isEmpty()) {
                    attackIndex[0]++;
                    return;
                }

                LivingEntity target = findTarget(player);
                if (target == null) {
                    LOGGER.debug("[漆黑噤默] 玩家 {} 未找到目标，跳过本次攻击", player.getName().getString());
                    attackIndex[0]++;
                    return;
                }

                Vec3 behind = calculateBehindPosition(target, player);
                if (behind != null) {
                    player.teleportTo(behind.x, behind.y, behind.z);
                }

                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
                player.attack(target);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                attackIndex[0]++;
            });
        }, 0, 500, TimeUnit.MILLISECONDS);

        executor.schedule(() -> {
            if (!executor.isShutdown()) {
                executor.shutdown();
                DARK_SILENT_ACTIVE.remove(uuid);
                LOGGER.warn("[漆黑噤默] 玩家 {} 大招超时强制结束", player.getName().getString());
            }
        }, totalAttacks * 500L + 2000, TimeUnit.MILLISECONDS);
    }

    private static List<ItemStack> getAllWeaponsFromGlove(Player player) {
        List<ItemStack> weapons = new ArrayList<>();
        var curios = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
        if (curios.isEmpty()) return weapons;

        var gloveHandler = curios.get().getStacksHandler("hands").orElse(null);
        if (gloveHandler == null) return weapons;

        ItemStack gloveStack = ItemStack.EMPTY;
        for (int i = 0; i < gloveHandler.getSlots(); i++) {
            ItemStack stack = gloveHandler.getStacks().getStackInSlot(i);
            if (stack.getItem() instanceof SilentGloveItem) {
                gloveStack = stack;
                break;
            }
        }
        if (gloveStack.isEmpty()) return weapons;

        SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
        if (vault == null) return weapons;

        for (int i = 0; i < vault.getSlots(); i++) {
            ItemStack stack = vault.getStackInSlot(i);
            if (!stack.isEmpty()) {
                weapons.add(stack.copy());
            }
        }
        return weapons;
    }

    private static LivingEntity findTarget(Player player) {
        Level level = player.level();
        AABB aabb = player.getBoundingBox().inflate(16.0);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e != player && e.isAlive() && !e.isAlliedTo(player) && !e.isInvulnerable());
        if (entities.isEmpty()) return null;
        entities.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        return entities.get(0);
    }

    private static Vec3 calculateBehindPosition(LivingEntity target, Player player) {
        Vec3 lookVec = target.getLookAngle().normalize();
        Vec3 behind = target.position().subtract(lookVec.scale(1.5));
        behind = new Vec3(behind.x, Math.max(target.getY(), behind.y), behind.z);
        return behind;
    }

    // ============================================================
    //  事件监听
    // ============================================================

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return;

        ItemStack mainHand = player.getMainHandItem();

        if (mainHand.isEmpty()) {
            if (equipRandomTool(player)) {
                event.setCanceled(true);
                resetAttackCooldown(player);
                player.attack(event.getTarget());
                player.swing(InteractionHand.MAIN_HAND);

                ItemStack currentMain = player.getMainHandItem();
                if (!currentMain.isEmpty()) {
                    checkAndTriggerDarkSilent(player, currentMain);
                }
            } else {
                event.setCanceled(true);
            }
            return;
        }

        if (mainHand.getItem() instanceof DurandalSwordItem) {
            checkAndTriggerDarkSilent(player, mainHand);
        }
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return;
        if (!player.getMainHandItem().isEmpty()) return;

        equipRandomTool(player);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return;
        if (!player.getOffhandItem().isEmpty()) return;
        if (!player.getMainHandItem().isEmpty()) return;

        ItemStack tool = extractRandomTool(player);
        if (tool.isEmpty()) return;

        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        event.setCanceled(true);

        Level level = player.level();
        InteractionHand hand = event.getHand();
        InteractionResultHolder<ItemStack> result = tool.use(level, player, hand);
        if (result.getResult().consumesAction()) {
            player.setItemInHand(hand, result.getObject());
        }

        scheduleReturn(player);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return;
        if (!player.getOffhandItem().isEmpty()) return;
        if (!player.getMainHandItem().isEmpty()) return;

        ItemStack tool = extractRandomTool(player);
        if (tool.isEmpty()) return;

        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        event.setCanceled(true);

        Level level = player.level();
        InteractionHand hand = event.getHand();
        InteractionResultHolder<ItemStack> result = tool.use(level, player, hand);
        if (result.getResult().consumesAction()) {
            player.setItemInHand(hand, result.getObject());
        }

        scheduleReturn(player);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (DARK_SILENT_ACTIVE.contains(player.getUUID())) return;
        if (!player.getMainHandItem().isEmpty()) return;

        ItemStack tool = extractRandomTool(player);
        if (tool.isEmpty()) return;

        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        event.setCanceled(true);

        player.interactOn(event.getTarget(), event.getHand());
        scheduleReturn(player);
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack picked = event.getItem().getItem().copy();
        if (picked.isEmpty()) return;

        UUID playerId = player.getUUID();
        List<PendingRecovery> recoveries = PENDING_RECOVERIES.get(playerId);
        if (recoveries == null || recoveries.isEmpty()) return;

        synchronized (recoveries) {
            for (int i = recoveries.size() - 1; i >= 0; i--) {
                PendingRecovery rec = recoveries.get(i);
                if (isMatchingItem(rec.stack, picked)) {
                    if (tryStoreInVault(player, picked)) {
                        event.setCanceled(true);
                        ItemStack remaining = event.getItem().getItem();
                        remaining.shrink(picked.getCount());
                        if (remaining.isEmpty()) {
                            event.getItem().discard();
                        } else {
                            event.getItem().setItem(remaining);
                        }
                        recoveries.remove(i);
                        return;
                    } else {
                        recoveries.remove(i);
                        return;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUUID();

        DARK_SILENT_ACTIVE.remove(uuid);

        forceReturnPending(player);

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && !(mainHand.getItem() instanceof SilentGloveItem)) {
            boolean stored = tryStoreInVault(player, mainHand);
            if (stored) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                ItemHandlerHelper.giveItemToPlayer(player, mainHand);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }

        PENDING_RECOVERIES.remove(uuid);
    }

    // ============================================================
    //  内部类
    // ============================================================

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
        PendingRecovery(ItemStack stack) {
            this.stack = stack;
        }
    }
}