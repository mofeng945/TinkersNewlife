package com.mofengbaizhi.tinkersnewlife.content.goety;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模块化魔杖 · 模式（铁魔法 ⇄ 巫法）与聚晶体系。
 * 模式标记存魔杖 ModDataNBT（与亲和同机制）；聚晶包内容存【玩家持久数据】（与咒灵记录同机制，
 * 被验证稳定不丢），按魔杖唯一 uid 区分多把魔杖。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModularStaffGoety {

    public static final int MODE_IRON = 0;
    public static final int MODE_GOETY = 1;
    public static final int POUCH_SLOTS = 6;

    private static final ResourceLocation KEY_MODE = key("staff_mode");
    private static final ResourceLocation KEY_IDX = key("staff_focus_idx");
    /** 聚晶包存玩家持久数据固定键（先排除"uid 键漂移"这类变量） */
    private static final String PLAYER_KEY = "tnl_staff_pouch";
    private static final String TAG_ITEMS = "items";

    private ModularStaffGoety() {}

    private static ResourceLocation key(String path) {
        return new ResourceLocation(TinkersNewlife.MOD_ID, path);
    }

    private static ToolStack tool(ItemStack stack) {
        return ToolHelper.getToolStack(stack);
    }

    // ================= 魔杖身份 / 模式 =================

    public static int getMode(ItemStack stack) {
        ToolStack t = tool(stack);
        return t == null ? MODE_IRON : t.getPersistentData().getInt(KEY_MODE);
    }

    private static void setMode(ItemStack stack, int mode) {
        ToolStack t = tool(stack);
        if (t == null) return;
        t.getPersistentData().putInt(KEY_MODE, mode);
        t.updateStack(stack);
    }

    private static int getFocusIndex(ItemStack stack) {
        ToolStack t = tool(stack);
        return t == null ? -1 : t.getPersistentData().getInt(KEY_IDX);
    }

    private static void setFocusIndex(ItemStack stack, int idx) {
        ToolStack t = tool(stack);
        if (t == null) return;
        t.getPersistentData().putInt(KEY_IDX, idx);
        t.updateStack(stack);
    }

    // ================= 聚晶包（玩家持久数据） =================

    private static List<ItemStack> getFoci(ServerPlayer player, ItemStack staff) {
        List<ItemStack> list = new ArrayList<>();
        CompoundTag root = player.getPersistentData();
        if (root.contains(PLAYER_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag box = root.getCompound(PLAYER_KEY);
            if (box.contains(TAG_ITEMS, Tag.TAG_LIST)) {
                ListTag tag = box.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
                for (int i = 0; i < tag.size() && i < POUCH_SLOTS; i++) {
                    list.add(ItemStack.of(tag.getCompound(i)));
                }
            }
        }
        while (list.size() < POUCH_SLOTS) {
            list.add(ItemStack.EMPTY);
        }
        return list;
    }

    private static void saveFoci(ServerPlayer player, ItemStack staff, List<ItemStack> foci) {
        CompoundTag root = player.getPersistentData();
        ListTag tag = new ListTag();
        for (ItemStack s : foci) {
            tag.add(s.save(new CompoundTag()));
        }
        CompoundTag box = new CompoundTag();
        box.put(TAG_ITEMS, tag);
        root.put(PLAYER_KEY, box);
    }

    private static ItemStack getEquippedFocus(ServerPlayer player, ItemStack staff) {
        List<ItemStack> foci = getFoci(player, staff);
        int idx = getFocusIndex(staff);
        return idx >= 0 && idx < foci.size() ? foci.get(idx) : ItemStack.EMPTY;
    }

    /** 公开读"装备中聚晶"（GoetyStaffItem 镜像写入用） */
    public static ItemStack equippedFocusOf(ServerPlayer player, ItemStack staff) {
        return getEquippedFocus(player, staff);
    }

    @Nullable
    private static ItemStack heldStaff(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ModularStaffItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof ModularStaffItem) return off;
        return null;
    }

    /** 诡厄巫法是否加载（运行时守卫：任何 GoetyStaffItem 的直接引用前必须先过此检查） */
    public static boolean isGoetyLoaded() {
        try {
            Class.forName("com.Polarice3.Goety.api.items.magic.IWand");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ================= 自动连招（开关式：客户端长按 5s 开启，再按右键解除） =================
    // 施法节奏由客户端"模拟按住右键"(KeyMapping.setDown) 驱动：瞬发走原版自动连点、
    // 咏唱/蓄力自然引导至满、超长持续段(≥30s)由客户端引导 3s 后主动释放；服务端只管施法结束自动切下一聚晶。

    /** 连招模式中的玩家 */
    private static final Set<UUID> AUTOCAST = ConcurrentHashMap.newKeySet();
    /** 施法结束待切换下一聚晶（等使用状态结束后的下一 tick 落地） */
    private static final Set<UUID> PENDING_ADVANCE = ConcurrentHashMap.newKeySet();

    /** 客户端开启/解除连招；开启时先请求切到下一个聚晶（5s 长按期间的手动首发已放完） */
    public static void setAutoCast(ServerPlayer player, boolean on) {
        UUID id = player.getUUID();
        if (on) {
            AUTOCAST.add(id);
            PENDING_ADVANCE.add(id);
        } else {
            AUTOCAST.remove(id);
            PENDING_ADVANCE.remove(id);
        }
    }

    public static boolean isAutoCasting(ServerPlayer player) {
        return AUTOCAST.contains(player.getUUID());
    }

    /** 施法结束（消耗动作且未进入引导/引导结束/满蓄释放）请求切换下一个聚晶 */
    public static void requestAdvance(ServerPlayer player) {
        if (AUTOCAST.contains(player.getUUID())) {
            PENDING_ADVANCE.add(player.getUUID());
        }
    }

    /** 切换到下一个非空聚晶（静默，不弹提示） */
    private static void advanceFocusSilent(ServerPlayer player) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        List<ItemStack> foci = getFoci(player, staff);
        int idx = getFocusIndex(staff);
        int next = idx;
        for (int step = 1; step <= POUCH_SLOTS; step++) {
            int cand = (idx + step) % POUCH_SLOTS;
            if (!foci.get(cand).isEmpty()) {
                next = cand;
                break;
            }
        }
        if (next == idx || foci.get(next).isEmpty()) return;
        setFocusIndex(staff, next);
        mirrorFocus(player);
        syncTo(player, staff, false);
    }

    /** 聚晶包内交换两格（a≠b；装备位跟着聚晶物品走） */
    public static void swapFocus(ServerPlayer player, int a, int b) {
        if (a == b) return;
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        List<ItemStack> foci = getFoci(player, staff);
        if (a < 0 || a >= foci.size() || b < 0 || b >= foci.size()) return;
        ItemStack tmp = foci.get(a);
        foci.set(a, foci.get(b));
        foci.set(b, tmp);
        int idx = getFocusIndex(staff);
        if (idx == a) {
            setFocusIndex(staff, b);
        } else if (idx == b) {
            setFocusIndex(staff, a);
        }
        saveFoci(player, staff, foci);
        mirrorFocus(player);
        syncTo(player, staff, false);
    }

    /**
     * 聚晶状态变化/周期校正后调用：把"装备中聚晶"镜像写入魔杖本体（真法杖自带槽）。
     * 诡厄未加载时直接跳过（此时魔杖为普通形态，无自带槽概念）。
     */
    public static void mirrorFocus(ServerPlayer player) {
        if (!isGoetyLoaded()) return;
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        com.mofengbaizhi.tinkersnewlife.content.item.GoetyStaffItem.mirrorEquippedFocus(player, staff);
    }

    // ================= 动作 =================

    public static void toggleMode(ServerPlayer player) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        int next = getMode(staff) == MODE_IRON ? MODE_GOETY : MODE_IRON;
        if (next == MODE_GOETY && !isGoetyLoaded()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.goety_missing"), true);
            return;
        }
        setMode(staff, next);
        player.displayClientMessage(Component.translatable(next == MODE_GOETY
                ? "message.tinkersnewlife.staff.mode_goety"
                : "message.tinkersnewlife.staff.mode_iron"), true);
        mirrorFocus(player); // 真法杖：进巫法模式写聚晶 / 回铁魔法模式清空（HUD 不误显示）
        syncTo(player, staff, false);
    }

    public static void openPouch(ServerPlayer player) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        syncTo(player, staff, true);
    }

    public static void cycleFocus(ServerPlayer player) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        List<ItemStack> foci = getFoci(player, staff);
        int count = 0;
        for (ItemStack s : foci) if (!s.isEmpty()) count++;
        if (count == 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.no_focus"), true);
            return;
        }
        int idx = getFocusIndex(staff);
        int next = idx;
        for (int step = 1; step <= POUCH_SLOTS; step++) {
            int cand = (idx + step) % POUCH_SLOTS;
            if (!foci.get(cand).isEmpty()) {
                next = cand;
                break;
            }
        }
        setFocusIndex(staff, next);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.focus_equipped",
                foci.get(next).getHoverName()), true);
        mirrorFocus(player); // 真法杖：把新装备的聚晶写入魔杖本体槽
        syncTo(player, staff, false);
    }

    public static void putFocus(ServerPlayer player, int invIndex) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        List<ItemStack> invFoci = inventoryFoci(player);
        if (invIndex < 0 || invIndex >= invFoci.size()) return;
        ItemStack focus = invFoci.get(invIndex);
        List<ItemStack> foci = getFoci(player, staff);
        int slot = -1;
        for (int i = 0; i < foci.size(); i++) {
            if (foci.get(i).isEmpty()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.pouch_full"), true);
            return;
        }
        if (!consumeFromInventory(player, focus)) return;
        foci.set(slot, focus.copy());
        saveFoci(player, staff, foci);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.focus_stored",
                focus.getHoverName()), true);
        mirrorFocus(player); // 幂等：装备位未变时为 no-op
        syncTo(player, staff, false);
    }

    public static void takeFocus(ServerPlayer player, int slot) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        List<ItemStack> foci = getFoci(player, staff);
        if (slot < 0 || slot >= foci.size() || foci.get(slot).isEmpty()) return;
        ItemStack focus = foci.get(slot);
        if (!player.getInventory().add(focus)) {
            player.spawnAtLocation(focus, 0.5f);
        }
        foci.set(slot, ItemStack.EMPTY);
        if (getFocusIndex(staff) == slot) setFocusIndex(staff, -1);
        saveFoci(player, staff, foci);
        mirrorFocus(player); // 若取出的是装备位 → 魔杖本体槽同步清空
        syncTo(player, staff, false);
    }

    private static boolean consumeFromInventory(Player player, ItemStack target) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(s, target) && !s.isEmpty()) {
                s.shrink(1);
                if (s.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    public static List<ItemStack> inventoryFoci(Player player) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && isFocusItem(s)) {
                list.add(s.copy()); // ⭐ 副本：后续 consume 会把原栈清空，必须先用拷贝
            }
        }
        return list;
    }

    private static boolean isFocusItem(ItemStack s) {
        try {
            Class<?> iface = Class.forName("com.Polarice3.Goety.api.items.magic.IFocus");
            Class<?> c = s.getItem().getClass();
            while (c != null && c != Object.class) {
                for (Class<?> i : c.getInterfaces()) {
                    if (iface.isAssignableFrom(i)) return true;
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static void syncTo(ServerPlayer player, ItemStack staff, boolean openScreen) {
        int mode = getMode(staff);
        int idx = getFocusIndex(staff);
        List<ItemStack> foci = getFoci(player, staff);
        List<ItemStack> invFoci = inventoryFoci(player);
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketStaffGoetySync(openScreen, mode, idx, foci, invFoci));
    }

    // ================= 事件 =================

    /** 蓄力引导期间逐 tick 检测：结束即换回原魔杖（仅旧版换手桥接路径使用，真法杖不走这里） */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;
        tickCasting(sp);
        // Spell 属性增益：手持真法杖(巫法模式) → 按法杖强度刷新；否则清残留（值未变时内部跳过）
        refreshSpellAttrsTick(sp);
        // 连招切换落地：施法结束的下一 tick（使用状态已结束）切到下一个聚晶
        if (PENDING_ADVANCE.remove(sp.getUUID())) {
            if (AUTOCAST.contains(sp.getUUID()) && !sp.isUsingItem()) {
                advanceFocusSilent(sp);
            }
        }
        // 周期校正：真法杖本体槽与聚晶包装备位保持一致（覆盖升级前旧栈/箱中取出/数据异常等场景）
        if ((sp.tickCount & 9) == 0) {
            mirrorFocus(sp);
        }
    }

    /** 手持真法杖（巫法模式）→ 按法杖强度给持有者上诡厄 Spell 属性；否则清空残留 */
    private static void refreshSpellAttrsTick(ServerPlayer sp) {
        if (!isGoetyLoaded()) return;
        ItemStack staff = heldStaff(sp);
        if (staff != null && !staff.isEmpty()
                && staff.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.GoetyStaffItem) {
            com.mofengbaizhi.tinkersnewlife.content.item.GoetyStaffItem.refreshSpellAttrs(sp, staff);
        } else {
            com.mofengbaizhi.tinkersnewlife.content.item.GoetyStaffItem.clearSpellAttrs(sp);
        }
    }

    /** 登出：换回原魔杖，清理引导/连招状态 */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            restoreHand(sp);
            CASTING_ORIGINAL.remove(sp.getUUID());
            setAutoCast(sp, false);
        }
    }

    /** 死亡重生：瞬态 Spell 属性随旧实体销毁，清缓存让新实体按需重挂；连招一并清 */
    @SubscribeEvent
    public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (isGoetyLoaded()) {
                com.mofengbaizhi.tinkersnewlife.content.item.GoetyStaffItem.clearSpellAttrs(sp);
                setAutoCast(sp, false);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        if (player.isShiftKeyDown()) {
            event.setCanceled(true);
            toggleMode(player);
            return;
        }
        if (getMode(staff) == MODE_GOETY) {
            // 真法杖形态（GoetyStaffItem implements IWand）：不取消，交给原版 use() →
            // GoetyStaffItem.use → 诡厄原生施法（长吟唱蓄力/冷却/灵魂全走原生管线，双端一致）
            if (isGoetyLoaded()
                    && staff.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.GoetyStaffItem) {
                return;
            }
            // 旧版/异常环境兜底：换手拿真法杖施法（仅普通形态魔杖且处于巫法模式时可能走到）
            ItemStack focus = getEquippedFocus(player, staff);
            if (!focus.isEmpty()) {
                event.setCanceled(true);
                tryCast(player, staff, focus);
            }
        }
    }

    private static final java.util.Map<java.util.UUID, ItemStack> CASTING_ORIGINAL =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static void tryCast(ServerPlayer player, ItemStack staff, ItemStack focus) {
        try {
            Class<?> iwand = Class.forName("com.Polarice3.Goety.api.items.magic.IWand");
            Item wandItem = null;
            for (Item it : BuiltInRegistries.ITEM) {
                // 跳过魔杖自己（真法杖形态也是 IWand，但走 tryCast 说明它不可用/非真法杖）
                if (it == staff.getItem()) continue;
                if (implementsInterface(it.getClass(), iwand)) {
                    wandItem = it;
                    break;
                }
            }
            if (wandItem == null) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.no_goety_wand"), true);
                return;
            }
            ItemStack wandStack = new ItemStack(wandItem);
            // 注入聚晶到真魔杖内嵌槽
            try {
                Object handler = iwand.getMethod("getItemHandler", ItemStack.class).invoke(null, wandStack);
                if (handler instanceof net.minecraftforge.items.IItemHandler itemHandler) {
                    itemHandler.insertItem(0, focus.copy(), false);
                }
            } catch (Throwable ignored) {
            }
            // 诡厄施法读取"手持物品"且需要原版"正在使用"状态才能蓄力/引导：
            // 把真魔杖换入主手，走 ServerPlayerGameMode.useItem 完整右键管线（会 startUsingItem，
            // 长吟唱可持续引导），松手/结束后由 tick 恢复原魔杖。
            ItemStack original = player.getMainHandItem();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wandStack);
            CASTING_ORIGINAL.put(player.getUUID(), original);
            try {
                net.minecraft.world.InteractionResult r =
                        player.gameMode.useItem(player, player.level(), wandStack,
                                net.minecraft.world.InteractionHand.MAIN_HAND);
                if (!r.consumesAction() && !r.shouldSwing()) {
                    // 未开始施法（冷却/灵魂不足等）：立即换回
                    restoreHand(player);
                }
            } catch (Throwable t2) {
                // 个别版本 gameMode.useItem 签名差异兜底：直接调 item.use
                restoreHand(player);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wandStack);
                CASTING_ORIGINAL.put(player.getUUID(), original);
                var result = wandItem.use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
                if (!result.getResult().consumesAction() && !result.getResult().shouldSwing()) {
                    restoreHand(player);
                }
            }
        } catch (Throwable t) {
            restoreHand(player);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.cast_failed"), true);
            TinkersNewlife.LOGGER.warn("[魔杖·巫法] 施法桥接失败：", t);
        }
    }

    /** 蓄力结束/玩家不再使用物品时，把原魔杖换回主手 */
    public static void tickCasting(ServerPlayer player) {
        if (!CASTING_ORIGINAL.containsKey(player.getUUID())) return;
        if (player.isUsingItem()) return; // 仍在蓄力/引导
        restoreHand(player);
    }

    private static void restoreHand(ServerPlayer player) {
        ItemStack original = CASTING_ORIGINAL.remove(player.getUUID());
        if (original != null && !original.isEmpty()) {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, original);
        }
    }

    private static boolean implementsInterface(Class<?> clazz, Class<?> target) {
        while (clazz != null && clazz != Object.class) {
            for (Class<?> i : clazz.getInterfaces()) {
                if (i == target || target.isAssignableFrom(i)) return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    public static boolean isStaffGoetyMode(Player player) {
        ItemStack staff = heldStaff(player);
        return staff != null && getMode(staff) == MODE_GOETY;
    }
}
