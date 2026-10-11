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
        // §1314 用子类（★stillValid 恒真 ⇒ 不因"距离太远"被秒关 ✓）
        TinkerStationBlockEntity be = new com.mofengbaizhi.tinkersnewlife.content.block.GhostStationBlockEntity(POCKET_POS,
                TinkerTables.tinkerStation.get().defaultBlockState());
        be.setLevel(level);
        return be;
    }

    /** NBT 键：背包内容（★每个槽一个物品 compound ✗ 空槽给空 compound ✓） */
    public static final String KEY_INV = "PortableStationInv";

    /** §1311 把方块实体的库存导成 NBT（★不用 BlockEntity#saveWithoutId ✗ ⭐ 只存槽位 ✓） */
    public static CompoundTag exportInventory(TinkerStationBlockEntity be) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < be.getContainerSize(); i++) {
            ItemStack s = be.getItem(i);
            list.add(s.isEmpty() ? new CompoundTag() : s.save(new CompoundTag()));
        }
        tag.put(KEY_INV, list);
        return tag;
    }

    /** §1311 把 NBT 里的库存灌回方块实体（★客户端重建 ✓） */
    public static void importInventory(TinkerStationBlockEntity be, CompoundTag tag) {
        try {
            if (tag == null || !tag.contains(KEY_INV, 9)) {
                return;
            }
            ListTag list = tag.getList(KEY_INV, 10);
            for (int i = 0; i < be.getContainerSize() && i < list.size(); i++) {
                be.setItem(i, ItemStack.of(list.getCompound(i)));
            }
        } catch (Throwable ignored) {
        }
    }
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
        // §1310 幽灵方块（★不放任何真方块 ✓ 数据走 buf 送客户端 ✓）
        TinkerStationBlockEntity ghost = createDetached(level);
        loadInventory(stack, ghost);
        CompoundTag data = exportInventory(ghost);   // §1311 只传库存 ✓
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携站] use: 打开前 count={} nbt={}B",
                stack.getCount(), data.sizeInBytes());
        NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                (id, inv, p) -> new com.mofengbaizhi.tinkersnewlife.content.menu.PortableStationMenu(id, inv, ghost),
                Component.translatable("item.tinkersnewlife.portable_tinker_station")),
                buf -> {
                    buf.writeBlockPos(POCKET_POS);   // §1313 匠魂期望的坐标 ✓
                    buf.writeNbt(data);                // §1313 之后跟库存 ⇒ 混入据此重建 ✓
                });
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携站] use: 打开后 count={}（★服务端没动它 ✓）", stack.getCount());
        return InteractionResultHolder.success(stack);
    }

    /** 记录"这次借用"的位置与原方块（★供关闭／维护时还原 ✓） */
    public static final String KEY_POS = "tnl_station_pos";
    public static final String KEY_STATE = "tnl_station_state";

    /** 从玩家脚下往下找一块基岩（★不破坏建筑 ✗ ⭐ 也天然没有邻接容器 ✓） */
    private static BlockPos findBedrock(Level level, BlockPos from) {
        // §1290 用户口径：优先"基岩正上方那一格"（空气／石头都行，只要不是矿物）；
        //         没有基岩时，取从下往上第一块"非基岩且非矿物"的方块。
        int min = level.getMinBuildHeight();
        int maxY = Math.min(from.getY() + 1, level.getMaxBuildHeight() - 1);
        for (int y = min; y <= maxY; y++) {
            BlockPos p = new BlockPos(from.getX(), y, from.getZ());
            if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                BlockPos up = p.above();
                if (isTarget(level, up)) {
                    return up;
                }
                continue;
            }
            if (isTarget(level, p)) {
                return p;
            }
        }
        return null;
    }

    /** 还原上一次遗留的那一格（★进世界／⭐ 再次使用／⭐ 关闭时都会调 ✓） */
    public static void restoreStale(ServerPlayer sp, ItemStack stack) {
        try {
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(KEY_POS)) {
                return;
            }
            BlockPos old = BlockPos.of(tag.getLong(KEY_POS));
            if (!(sp.level().getBlockEntity(old) instanceof TinkerStationBlockEntity)) {
                tag.remove(KEY_POS);
                tag.remove(KEY_STATE);
                return;
            }
            net.minecraft.world.level.block.state.BlockState st = net.minecraft.world.level.block.Blocks.BEDROCK
                    .defaultBlockState();
            if (tag.contains(KEY_STATE)) {
                try {
                    st = net.minecraft.nbt.NbtUtils.readBlockState(sp.level().holderLookup(
                            net.minecraft.core.registries.Registries.BLOCK), tag.getCompound(KEY_STATE));
                } catch (Throwable ignored) {
                }
            }
            sp.level().setBlockAndUpdate(old, st);
            tag.remove(KEY_POS);
            tag.remove(KEY_STATE);
        } catch (Throwable ignored) {
        }
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

    /** 不是基岩、也不是矿物（★空气／石头都算 ✓） */
    private static boolean isTarget(Level level, BlockPos p) {
        try {
            net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
            if (s.is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                return false;
            }
            return !s.is(net.minecraftforge.common.Tags.Blocks.ORES);
        } catch (Throwable t) {
            return false;
        }
    }}
