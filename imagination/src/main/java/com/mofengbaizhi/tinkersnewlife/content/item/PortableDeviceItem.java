package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
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
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * §1318 便携设备（★一个类管四种 ✗ ⭐ 用 Kind 区分 ✓）：部件制作台 / 强化调配台 / 铸造箱 / 部件箱。
 *
 * <p>与便携工匠站同构（★幽灵实体 ＋ ⭐ 玩家存档 ＋ ⭐ 混入接管客户端查找 ✓）：
 * <ul>
 *   <li>不放任何真方块；</li>
 *   <li>内容存**玩家持久数据**（★不存物品 NBT ⇒ 无刷物品风险 ✓）；</li>
 *   <li>打开时把库存写进 buf ⇒ 客户端重建数据完整的实体 ⇒ 永不 null ⇒ JEI 不崩。</li>
 * </ul>
 */
public class PortableDeviceItem extends Item {

    /** 设备种类（★方块 id 一律走注册表 ✗ ⭐ 不写字段名 ✓） */
    public enum Kind {
        PART_BUILDER("tconstruct:part_builder",
                "slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity"),
        MODIFIER_WORKTABLE("tconstruct:modifier_worktable",
                "slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity"),
        CAST_CHEST("tconstruct:cast_chest",
                "slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity"),
        PART_CHEST("tconstruct:part_chest",
                "slimeknights.tconstruct.tables.block.entity.chest.PartChestBlockEntity");

        public final String blockId;
        public final String beClass;

        Kind(String blockId, String beClass) {
            this.blockId = blockId;
            this.beClass = beClass;
        }
    }

    /** 每个种类一份存档键（★下次设备各自独立 ✓ 又不碰物品 NBT ✓） */
    public static String key(Kind kind) {
        return "tnl_portable_" + kind.name().toLowerCase() + "_inv";
    }

    private final Kind kind;

    public PortableDeviceItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** 按"种类"造幽灵实体（★服务端／客户端都用 ✓） */
    public static BlockEntity makeGhost(Kind kind, Level level) {
        try {
            return buildGhost(Class.forName(kind.beClass), null, kind.blockId);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String className(Kind kind) {
        return kind.beClass;
    }

    /** 造游离实体：反射 new (BlockPos, BlockState) ＋ 设 level（★方块状态按注册表 id 取 ✓） */
    public static BlockEntity buildGhost(Class<?> clazz, FriendlyByteBuf buf, String blockId) {
        try {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
            BlockState state = (block == null)
                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : block.defaultBlockState();
            BlockPos pos = new BlockPos(0, -1024, 0);
            if (buf != null) {
                // ★buf 里：坐标（匠魂要读的 ✓）＋ 库存 NBT（我们的 ✓）
                try {
                    pos = buf.readBlockPos();
                } catch (Throwable ignored) {
                }
            }
            BlockEntity be = (BlockEntity) clazz.getConstructor(BlockPos.class, BlockState.class).newInstance(pos, state);
            Level level = null;
            try {
                level = (net.minecraft.client.Minecraft.getInstance().level != null)
                        ? net.minecraft.client.Minecraft.getInstance().level
                        : null;
            } catch (Throwable ignored) {
            }
            be.setLevel(level);
            if (buf != null && buf.readableBytes() > 0) {
                CompoundTag nbt = buf.readNbt();
                importInventory(be, nbt);
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 幽灵实体 {}", clazz.getSimpleName());
            return be;
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 造幽灵实体失败 {}: {}", clazz.getName(), t.toString());
            return null;
        }
    }

    /* ---------------------------------------------------------------- 库存 NBT */

    /** 取"槽位数量"与"读写"的统一入口（★桌子是 Container ✗ ⭐ 箱子只有 IItemHandler ✓） */
    private static net.minecraftforge.items.IItemHandler handlerOf(BlockEntity be) {
        try {
            if (be instanceof net.minecraft.world.Container c) {
                return new net.minecraftforge.items.wrapper.InvWrapper(c);
            }
        } catch (Throwable ignored) {
        }
        try {
            return be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                    .resolve().orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static CompoundTag exportInventory(BlockEntity be) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        try {
            var h = handlerOf(be);
            int size = (h == null) ? 0 : h.getSlots();
            for (int i = 0; i < size; i++) {
                ItemStack s = h.getStackInSlot(i);
                list.add(s.isEmpty() ? new CompoundTag() : s.save(new CompoundTag()));
            }
            tag.putInt("size", size);
        } catch (Throwable ignored) {
        }
        tag.put("inv", list);
        return tag;
    }

    public static void importInventory(BlockEntity be, CompoundTag tag) {
        try {
            if (tag == null || !tag.contains("inv", 9)) {
                return;
            }
            var h = handlerOf(be);
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
    /** 打开前：从**玩家存档**装内容 */
    public static void loadFromPlayer(Player player, BlockEntity be, Kind kind) {
        try {
            importInventory(be, player.getPersistentData().getCompound(key(kind)));
        } catch (Throwable ignored) {
        }
    }

    /** 关闭时：写回**玩家存档**（★与物品无关 ✓） */
    public static void storeToPlayer(Player player, BlockEntity be) {
        try {
            for (Kind k : Kind.values()) {
                if (be.getClass().getName().equals(k.beClass)) {
                    player.getPersistentData().put(key(k), exportInventory(be));
                    return;
                }
            }
            player.getPersistentData().put("tnl_portable_misc_inv", exportInventory(be));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        BlockEntity ghost = makeGhost(kind, level);
        if (ghost == null) {
            return InteractionResultHolder.fail(stack);
        }
        loadFromPlayer(sp, ghost, kind);
        CompoundTag data = exportInventory(ghost);
        NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                (id, inv, p) -> menuFor(kind, id, inv, ghost),
                net.minecraft.network.chat.Component.translatable(nameKey(kind))),
                buf -> {
                    buf.writeBlockPos(new BlockPos(0, -1024, 0));
                    buf.writeUtf(kind.beClass);   // §1319 客户端据此反射造实体（★抽象类也能对上 ✓）
                    buf.writeNbt(data);
                });
        return InteractionResultHolder.success(stack);
    }

    public static String nameKey(Kind kind) {
        return "item.tinkersnewlife.portable_" + kind.name().toLowerCase();
    }

    private static net.minecraft.world.inventory.AbstractContainerMenu menuFor(Kind kind, int id,
            net.minecraft.world.entity.player.Inventory inv, BlockEntity ghost) {
        return switch (kind) {
            case PART_BUILDER -> new com.mofengbaizhi.tinkersnewlife.content.menu.PortablePartBuilderMenu(id, inv,
                    (slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity) ghost);
            case MODIFIER_WORKTABLE -> new com.mofengbaizhi.tinkersnewlife.content.menu.PortableModifierWorktableMenu(id, inv,
                    (slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity) ghost);
            case CAST_CHEST -> new com.mofengbaizhi.tinkersnewlife.content.menu.PortableCastChestMenu(id, inv,
                    (slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity) ghost);
            case PART_CHEST -> new com.mofengbaizhi.tinkersnewlife.content.menu.PortablePartChestMenu(id, inv,
                    (slimeknights.tconstruct.tables.block.entity.chest.PartChestBlockEntity) ghost);
        };
    }
}