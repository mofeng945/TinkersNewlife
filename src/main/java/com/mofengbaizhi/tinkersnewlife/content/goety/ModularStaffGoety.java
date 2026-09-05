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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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

/**
 * 模块化魔杖 · 模式（铁魔法 ⇄ 巫法）与聚晶体系。
 * <p>
 * 常态为「铁魔法模式」；Shift+右键 切到「巫法模式」（诡厄巫法存在时生效）：
 * <li>聚晶包：魔杖内建（6 格，存于工具持久数据），J 键打开（放入/取出/查看）；</li>
 * <li>R 键循环装备包内聚晶（同诡厄魔杖手感）；</li>
 * <li>巫法模式下右键 = 释放当前装备聚晶法术（走诡厄灵魂体系，反射桥接其 IWand 施法）。</li>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModularStaffGoety {

    public static final int MODE_IRON = 0;      // 铁魔法（常态）
    public static final int MODE_GOETY = 1;     // 巫法

    public static final int POUCH_SLOTS = 6;

    private static final net.minecraft.resources.ResourceLocation KEY_MODE =
            new net.minecraft.resources.ResourceLocation(TinkersNewlife.MOD_ID, "staff_mode");
    private static final net.minecraft.resources.ResourceLocation KEY_FOCI =
            new net.minecraft.resources.ResourceLocation(TinkersNewlife.MOD_ID, "staff_foci");
    private static final net.minecraft.resources.ResourceLocation KEY_IDX =
            new net.minecraft.resources.ResourceLocation(TinkersNewlife.MOD_ID, "staff_focus_idx");

    private ModularStaffGoety() {}

    // ================= 读写（ToolStack 持久数据） =================

    private static ToolStack tool(ItemStack stack) {
        return ToolHelper.getToolStack(stack);
    }

    public static int getMode(ItemStack stack) {
        ToolStack t = tool(stack);
        return t == null ? MODE_IRON : t.getPersistentData().getInt(KEY_MODE);
    }

    public static void setMode(ItemStack stack, int mode) {
        ToolStack t = tool(stack);
        if (t == null) return;
        t.getPersistentData().putInt(KEY_MODE, mode);
        t.updateStack(stack);
    }

    /** 读取聚晶包（固定 POUCH_SLOTS 长度，空位为 EMPTY） */
    public static List<ItemStack> getFoci(ItemStack stack) {
        List<ItemStack> list = new ArrayList<>();
        ToolStack t = tool(stack);
        if (t != null && t.getPersistentData().contains(KEY_FOCI)) {
            CompoundTag box = t.getPersistentData().getCompound(KEY_FOCI);
            if (box.contains("items", Tag.TAG_LIST)) {
                ListTag tag = box.getList("items", Tag.TAG_COMPOUND);
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

    private static void saveFoci(ItemStack stack, List<ItemStack> foci) {
        ToolStack t = tool(stack);
        if (t == null) return;
        ListTag tag = new ListTag();
        for (ItemStack s : foci) {
            tag.add(s.save(new CompoundTag()));
        }
        CompoundTag box = new CompoundTag();
        box.put("items", tag);
        t.getPersistentData().put(KEY_FOCI, box);
        t.updateStack(stack);
    }

    public static int getFocusIndex(ItemStack stack) {
        ToolStack t = tool(stack);
        return t == null ? -1 : t.getPersistentData().getInt(KEY_IDX);
    }

    private static void setFocusIndex(ItemStack stack, int idx) {
        ToolStack t = tool(stack);
        if (t == null) return;
        t.getPersistentData().putInt(KEY_IDX, idx);
        t.updateStack(stack);
    }

    /** 打开聚晶包（服务端构建并推送界面数据） */
    public static void openPouch(ServerPlayer player) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        syncTo(player, staff, true);
    }

    /** 当前装备的聚晶（可能为空） */
    public static ItemStack getEquippedFocus(ItemStack stack) {
        List<ItemStack> foci = getFoci(stack);
        int idx = getFocusIndex(stack);
        if (idx < 0 || idx >= foci.size()) return ItemStack.EMPTY;
        return foci.get(idx);
    }

    /** 手持魔杖（主手/副手） */
    @Nullable
    private static ItemStack heldStaff(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ModularStaffItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof ModularStaffItem) return off;
        return null;
    }

    private static boolean isGoetyLoaded() {
        try {
            Class.forName("com.Polarice3.Goety.api.items.magic.IWand");
            return true;
        } catch (Throwable t) {
            return false;
        }
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
        syncTo(player, staff, false);
    }

    public static void cycleFocus(ServerPlayer player) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        List<ItemStack> foci = getFoci(staff);
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
        ItemStack focus = foci.get(next);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.focus_equipped",
                focus.getHoverName()), true);
        syncTo(player, staff, false);
    }

    /** 把玩家背包内第 invIndex 个聚晶放入魔杖聚晶包第一个空位 */
    public static void putFocus(ServerPlayer player, int invIndex) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        List<ItemStack> invFoci = inventoryFoci(player);
        if (invIndex < 0 || invIndex >= invFoci.size()) return;
        ItemStack focus = invFoci.get(invIndex);
        List<ItemStack> foci = getFoci(staff);
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
        // 从背包扣除该物品（同 stack 计数）
        if (!consumeFromInventory(player, focus)) return;
        foci.set(slot, focus.copy());
        saveFoci(staff, foci);
        syncTo(player, staff, false);
    }

    /** 取出魔杖聚晶包第 slot 个聚晶回背包 */
    public static void takeFocus(ServerPlayer player, int slot) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        List<ItemStack> foci = getFoci(staff);
        if (slot < 0 || slot >= foci.size() || foci.get(slot).isEmpty()) return;
        ItemStack focus = foci.get(slot);
        if (!player.getInventory().add(focus)) {
            player.spawnAtLocation(focus, 0.5f);
        }
        foci.set(slot, ItemStack.EMPTY);
        int idx = getFocusIndex(staff);
        if (idx == slot) setFocusIndex(staff, -1);
        saveFoci(staff, foci);
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

    /** 背包中所有聚晶（实现 Goety IFocus 的物品，反射判定） */
    public static List<ItemStack> inventoryFoci(Player player) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && isFocusItem(s)) {
                list.add(s);
            }
        }
        return list;
    }

    private static boolean isFocusItem(ItemStack s) {
        try {
            Class<?> iface = Class.forName("com.Polarice3.Goety.api.items.magic.IFocus");
            if (iface.isAssignableFrom(s.getItem().getClass())) return true;
            // 部分实现藏在父类，逐级查
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

    // ================= 同步 =================

    public static void syncTo(ServerPlayer player, ItemStack staff, boolean openScreen) {
        int mode = getMode(staff);
        int idx = getFocusIndex(staff);
        List<ItemStack> foci = getFoci(staff);
        List<ItemStack> invFoci = inventoryFoci(player);
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketStaffGoetySync(openScreen, mode, idx, foci, invFoci));
    }

    // ================= 事件 =================

    /** Shift+右键 = 切换铁魔法/巫法；巫法模式下普通右键 = 释放装备聚晶 */
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
            ItemStack focus = getEquippedFocus(staff);
            if (!focus.isEmpty()) {
                event.setCanceled(true);
                tryCast(player, staff, focus);
            }
        }
    }

    /** 反射桥接：借 Goety IWand 物品执行施法（灵魂/冷却由 Goety 本体处理） */
    private static void tryCast(ServerPlayer player, ItemStack staff, ItemStack focus) {
        try {
            Class<?> iwand = Class.forName("com.Polarice3.Goety.api.items.magic.IWand");
            // 1) 找到任一实现 IWand 的诡厄魔杖物品
            Item wandItem = null;
            for (Item it : BuiltInRegistries.ITEM) {
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
            // 2) 把装备聚晶塞入其内嵌槽（IWand.getItemHandler）
            try {
                Object handler = iwand.getMethod("getItemHandler", ItemStack.class).invoke(null, wandStack);
                if (handler instanceof net.minecraftforge.items.IItemHandler itemHandler) {
                    itemHandler.insertItem(0, focus.copy(), false);
                }
            } catch (Throwable ignored) {
            }
            // 3) 调用魔杖 use 施法
            wandItem.use(player.level(), player, InteractionHand.MAIN_HAND);
        } catch (Throwable t) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.cast_failed"), true);
            TinkersNewlife.LOGGER.warn("[魔杖·巫法] 施法桥接失败：", t);
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
