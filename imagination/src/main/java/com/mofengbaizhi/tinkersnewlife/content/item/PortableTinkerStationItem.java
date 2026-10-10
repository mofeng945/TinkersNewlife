package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleMenuProvider;
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
        // §1288 用户/群友的洞察：真砧在"空旷的另一个维度"里就不崩 ⇒ 本质是"周围什么都没有"，
        //   匠魂的 detectStationParts 找不到邻块 ⇒ 不会挂侧栏模块 ⇒ 屏幕布局一出生就是最终态，
        //   那个矩形宽度就不会在 26 与 -2 之间抖 ⇒ JEI 不会撞上负数。
        //   ⚠ 但不能跨维度：客户端只能在自己所在的世界里按坐标找到方块实体。
        //   ⇒ 所以我们留在同一维度，把透明砧放到"绝对空旷"的位置。
        BlockPos base = sp.blockPosition();
        BlockPos target = null;
        for (int dy = 2; dy <= 16 && target == null; dy++) {
            BlockPos p = base.above(dy);
            if (level.isOutsideBuildHeight(p)) {
                break;
            }
            if (isClearAround(level, p, 2)) {
                target = p;
            }
        }
        if (target == null) {
            for (int dy : new int[] { 64, 96, 128 }) {
                BlockPos p = base.above(dy);
                if (level.isOutsideBuildHeight(p)) {
                    continue;
                }
                if (isClearAround(level, p, 1)) {
                    target = p;
                    break;
                }
            }
        }
        if (target == null) {
            for (BlockPos p : new BlockPos[] { base, base.below(), base.north(), base.south(), base.east(), base.west() }) {
                if (level.getBlockState(p).isAir()) {
                    target = p;
                    break;
                }
            }
        }
        if (target == null) {
            return InteractionResultHolder.fail(stack);
        }
        final BlockPos spot = target;
        net.minecraft.world.level.block.state.BlockState st = com.mofengbaizhi.tinkersnewlife.content.block
                .InvisibleStationRegistry.INVISIBLE_STATION.get().defaultBlockState();
        level.setBlockAndUpdate(spot, st);
        try {
            com.mofengbaizhi.tinkersnewlife.content.block.InvisibleStationRegistry.INVISIBLE_STATION.get()
                    .setPlacedBy(level, spot, st, sp, new ItemStack(slimeknights.tconstruct.tables.TinkerTables
                            .tinkersAnvil.get().asItem()));
        } catch (Throwable ignored) {
        }
        if (level.getBlockEntity(spot) instanceof TinkerStationBlockEntity be) {
            loadInventory(stack, be);
            final net.minecraft.server.MinecraftServer server = sp.getServer();
            if (server != null) {
                server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 10, () -> {
                    if (sp.isRemoved()) {
                        return;
                    }
                    if (sp.level().getBlockEntity(spot) instanceof TinkerStationBlockEntity ready) {
                        NetworkHooks.openScreen(sp, ready, spot);
                    }
                }));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    /** 以 pos 为中心、半径 r 的立方体是否全是空气（★要比匠魂自己的邻块扫描更宽 ✓） */
    private static boolean isClearAround(Level level, BlockPos pos, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!level.getBlockState(pos.offset(dx, dy, dz)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
