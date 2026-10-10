package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

/**
 * §1278 Portable Tinker Station (placeholder art).
 *
 * <p>No mixin: we never use Tinker's own MenuType. Its client constructor resolves the
 * block entity from the world by position, which a portable item cannot provide. Instead
 * we register OUR MenuType whose factory builds a DETACHED TinkerStationBlockEntity
 * (public constructor) and the screen is TiC's own TinkerStationScreen (public constructor).
 *
 * <p>Detached position note: TableBlockEntity#setItem broadcasts InventorySlotSyncPacket
 * around worldPosition, so we park the fake entity far away where nothing else lives.
 */
public class PortableTinkerStationItem extends Item {

    /** 远离一切工作台的"口袋坐标"（★只为让侧栏检测找不到邻居 ✓） */
    public static final BlockPos POCKET_POS = new BlockPos(0, -1024, 0);

    public PortableTinkerStationItem(Properties properties) {
        super(properties);
    }

    /** 造一个游离的工匠站方块实体（★不入世界 ✓ ⭐ 但给它 level 与坐标 ✗ 屏幕要用 ✓） */
    public static TinkerStationBlockEntity createDetached(Level level) {
        TinkerStationBlockEntity be = new TinkerStationBlockEntity(POCKET_POS,
                TinkerTables.tinkerStation.get().defaultBlockState());
        be.setLevel(level);
        return be;
    }

    /** NBT 键：背包内容（★每个槽一个物品 compound ✗ 空槽给空 compound ✓） */
    public static final String KEY_INV = "PortableStationInv";

    /** 从物品 NBT 把内容装进游离方块实体（★打开时 ✓） */
    public static void loadInventory(ItemStack stack, TinkerStationBlockEntity be) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY_INV)) {
            return;
        }
        ListTag list = tag.getList(KEY_INV, 10);
        for (int i = 0; i < be.getContainerSize() && i < list.size(); i++) {
            be.setItem(i, ItemStack.of(list.getCompound(i)));
        }
    }

    /** 把游离方块实体的内容写回物品 NBT（★关闭时 ✓） */
    public static void saveInventory(ItemStack stack, TinkerStationBlockEntity be) {
        ListTag list = new ListTag();
        for (int i = 0; i < be.getContainerSize(); i++) {
            ItemStack s = be.getItem(i);
            list.add(s.isEmpty() ? new CompoundTag() : s.save(new CompoundTag()));
        }
        stack.getOrCreateTag().put(KEY_INV, list);
    }

    /** 在玩家背包里找到"那一把"便携站（★关界面时写回用 ✓ ✗ 找不到返回空 ✓） */
    public static ItemStack findIn(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof PortableTinkerStationItem) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        TinkerStationBlockEntity be = createDetached(level);
        loadInventory(stack, be);   // ★ 打开时从物品 NBT 还原 ✓
        NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                (id, inv, p) -> new com.mofengbaizhi.tinkersnewlife.content.menu.PortableStationMenu(id, inv, be),
                Component.translatable("item.tinkersnewlife.portable_tinker_station")), POCKET_POS);
        return InteractionResultHolder.success(stack);
    }
}