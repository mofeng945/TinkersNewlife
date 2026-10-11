package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * §1322 便携台子（★部件制作台 / 强化调配台 ✗ ⭐ 一个类管两种 ✓）。
 *
 * <p>与工匠站同构（★幽灵实体 ＋ ⭐ 内容存玩家存档 ＋ ⭐ buf 带库存 ✓）✗
 * ⭐ 但**独立实现** ✗ ⭐ 不共用工匠站那份代码 ⇒ 互不影响 ✓（用户口径 ✓）。
 */
public class PortableTableItem extends Item {

    public enum Kind {
        PART_BUILDER("tconstruct:part_builder",
                "slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity"),
        MODIFIER_WORKTABLE("tconstruct:modifier_worktable",
                "slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity");

        public final String blockId;
        public final String beClass;

        Kind(String blockId, String beClass) {
            this.blockId = blockId;
            this.beClass = beClass;
        }
    }

    private final Kind kind;

    public PortableTableItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static String saveKey(Kind kind) {
        return "tnl_portable_" + kind.name().toLowerCase() + "_inv";
    }

    public static String nameKey(Kind kind) {
        return "item.tinkersnewlife.portable_" + kind.name().toLowerCase();
    }

    /** 造游离实体（★公开构造器 (BlockPos, BlockState) ✓） */
    public static BlockEntity makeGhost(Kind kind) {
        try {
            Class<?> c = Class.forName(kind.beClass);
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(kind.blockId));
            BlockState state = block == null
                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : block.defaultBlockState();
            return (BlockEntity) c.getConstructor(BlockPos.class, BlockState.class)
                    .newInstance(new BlockPos(0, -1024, 0), state);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携台] 造实体失败: {}", t.toString());
            return null;
        }
    }

    /** 统一槽位入口：Container 优先（★台子是 Container ✓）✗ ⭐ 兜底 IItemHandler ✓ */
    private static IItemHandler handlerOf(BlockEntity be) {
        try {
            if (be instanceof net.minecraft.world.Container c) {
                return new InvWrapper(c);
            }
        } catch (Throwable ignored) {
        }
        try {
            return be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static CompoundTag export(BlockEntity be, int size) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        try {
            IItemHandler h = handlerOf(be);
            for (int i = 0; i < size; i++) {
                ItemStack s = (h == null || i >= h.getSlots()) ? ItemStack.EMPTY : h.getStackInSlot(i);
                list.add(s.isEmpty() ? new CompoundTag() : s.save(new CompoundTag()));
            }
        } catch (Throwable ignored) {
        }
        tag.putInt("size", size);
        tag.put("inv", list);
        return tag;
    }

    public static void importInto(BlockEntity be, CompoundTag tag) {
        try {
            if (tag == null || !tag.contains("inv", 9)) {
                return;
            }
            IItemHandler h = handlerOf(be);
            if (h == null) {
                return;
            }
            ListTag list = tag.getList("inv", 10);
            for (int i = 0; i < h.getSlots() && i < list.size(); i++) {
                h.insertItem(i, ItemStack.of(list.getCompound(i)), false);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void loadFromPlayer(Player player, BlockEntity be, Kind kind) {
        try {
            importInto(be, player.getPersistentData().getCompound(saveKey(kind)));
        } catch (Throwable ignored) {
        }
    }

    public static void storeToPlayer(Player player, BlockEntity be, Kind kind) {
        try {
            player.getPersistentData().put(saveKey(kind), export(be, 6));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        BlockEntity ghost = makeGhost(kind);
        if (ghost == null) {
            return InteractionResultHolder.fail(stack);
        }
        loadFromPlayer(sp, ghost, kind);
        CompoundTag data = export(ghost, 6);
        IItemHandler h = handlerOf(ghost);
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携台] 打开 {} slots={}",
                kind.name(), h == null ? -1 : h.getSlots());
        NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                (id, inv, p) -> menuFor(kind, id, inv, ghost),
                net.minecraft.network.chat.Component.translatable(nameKey(kind))),
                buf -> {
                    buf.writeBlockPos(new BlockPos(0, -1024, 0));
                    buf.writeNbt(data);
                });
        return InteractionResultHolder.success(stack);
    }

    private static net.minecraft.world.inventory.AbstractContainerMenu menuFor(Kind kind, int id,
            net.minecraft.world.entity.player.Inventory inv, BlockEntity ghost) {
        if (kind == Kind.PART_BUILDER) {
            return new com.mofengbaizhi.tinkersnewlife.content.menu.PortablePartBuilderMenu(id, inv,
                    (slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity) ghost);
        }
        return new com.mofengbaizhi.tinkersnewlife.content.menu.PortableModifierWorktableMenu(id, inv,
                (slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity) ghost);
    }
}