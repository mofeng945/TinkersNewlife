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
                list.add(s);
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
            ItemStack focus = getEquippedFocus(player, staff);
            if (!focus.isEmpty()) {
                event.setCanceled(true);
                tryCast(player, staff, focus);
            }
        }
    }

    private static void tryCast(ServerPlayer player, ItemStack staff, ItemStack focus) {
        try {
            Class<?> iwand = Class.forName("com.Polarice3.Goety.api.items.magic.IWand");
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
            try {
                Object handler = iwand.getMethod("getItemHandler", ItemStack.class).invoke(null, wandStack);
                if (handler instanceof net.minecraftforge.items.IItemHandler itemHandler) {
                    itemHandler.insertItem(0, focus.copy(), false);
                }
            } catch (Throwable ignored) {
            }
            wandItem.use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
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
