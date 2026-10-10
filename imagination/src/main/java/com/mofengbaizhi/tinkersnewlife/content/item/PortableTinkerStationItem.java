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
        // §1282 用户口径：开 GUI 要延后（管理员诊断：客户端还没收到方块就开界面 ⇒ JEI 崩／卡死）
        BlockPos base = sp.blockPosition();
        BlockPos target = null;
        BlockPos[] tries = { base, base.above(), base.below(), base.north(), base.south(), base.east(), base.west() };
        for (BlockPos p : tries) {
            if (level.getBlockState(p).isAir()) {
                target = p;
                break;
            }
        }
        if (target == null) {
            return InteractionResultHolder.fail(stack);
        }
        final BlockPos spot = target;
        net.minecraft.world.level.block.state.BlockState st = com.mofengbaizhi.tinkersnewlife.content.block
                .InvisibleStationRegistry.INVISIBLE_STATION.get().defaultBlockState();
        level.setBlockAndUpdate(spot, st);
        // ★ §1283 关键：借用真工匠砧自己的放置逻辑，让方块实体拿到与真砧**完全一样**的
        //   材质/贴图数据（屏幕布局与宽度就是按这些算的；空手 setBlock 会留下 AIR/UNKNOWN ⇒ 负宽度 ⇒ JEI 崩）
        try {
            com.mofengbaizhi.tinkersnewlife.content.block.InvisibleStationRegistry.INVISIBLE_STATION.get()
                    .setPlacedBy(level, spot, st, sp, new ItemStack(slimeknights.tconstruct.tables.TinkerTables
                            .tinkersAnvil.get().asItem()));
        } catch (Throwable ignored) {
        }
        if (level.getBlockEntity(spot) instanceof TinkerStationBlockEntity be) {
            loadInventory(stack, be);
            // ★ 延后 10 tick：等客户端把这块方块收下去之后再开界面 ✓
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
}
