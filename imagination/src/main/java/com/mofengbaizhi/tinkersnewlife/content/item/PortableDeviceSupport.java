package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * §1323 便携设备的**共用底座**：幽灵实体／库存读写／玩家存档 只在这一处实现。
 *
 * <p>四个设备（工匠站／部件制作台／强化调配台／铸造箱／部件箱）全部调用它 ⇒ 不再各写一份。
 *
 * <p>两条关键事实（★都是反编译确认过的 ✓）：
 * <ul>
 *   <li>台子家族是 {@code Container} ✗ ⭐ 箱子家族**只有** {@code IItemHandler} ⇒ 统一入口
 *       {@link #handlerOf} 用 {@code Container} 优先、⭐ 兜底 capability ✓；</li>
 *   <li>箱子的槽位数由 NBT 的 {@code Size} 决定 ⇒ {@link #export} 一定写 Size ✓。</li>
 * </ul>
 */
public final class PortableDeviceSupport {

    /**
     * §1330 便携菜单 buf 的标记串（★只有我们自己的菜单会写它 ✓）。
     *
     * <p>⚠ 必须放在**这个普通类**里 ✗ ⭐ **不能放在混入类里** ✓ ——
     * Mixin 规范要求混入类只能有 private 字段 ✗ ⭐ 否则整个混入会被拒绝应用 ✓
     */
    public static final String MAGIC = "tnl:portable:v1";

    /** §1335 工具包里的六件设备（★类名 ✗ ⭐ 方块 id ✗ ⭐ 存档键 ✓） */
    public static final String[][] DEVICES = {
            {"slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity", "tconstruct:tinker_station", "tnl_portable_tinker_station_inv"},
            {"slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity", "tconstruct:tinkers_anvil", "tnl_portable_tinkers_anvil_inv"},
            {"slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity", "tconstruct:part_builder", "tnl_portable_part_builder_inv"},
            {"slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity", "tconstruct:modifier_worktable", "tnl_portable_modifier_worktable_inv"},
            {"slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity", "tconstruct:cast_chest", "tnl_portable_cast_chest_inv"},
            {"slimeknights.tconstruct.tables.block.entity.chest.PartChestBlockEntity", "tconstruct:part_chest", "tnl_portable_part_chest_inv"}
    };

    /** §1335 客户端暂存：混入解析出来的"侧栏幽灵"（★菜单客户端构造器要用 ✓） */
    public static final java.util.List<BlockEntity> CLIENT_SIDES = new java.util.ArrayList<>();

    /**
     * §1338 待加入的侧栏（★必须在 {@code detectStationParts} 里、玩家背包槽加入**之前**加 ✓）。
     *
     * <p>日志实证：晚于 super() 再加会被 Mantle 拒绝 ——
     * {@code IllegalStateException: BaseContainer: Player inventory has …} ✓
     */
    public static final java.util.List<BlockEntity> PENDING_SIDES = new java.util.ArrayList<>();

    /** §1338 侧栏用的玩家背包（★服务端在 openScreen 前塞入 ✓ ⭐客户端从自己身上取 ✓） */
    public static net.minecraft.world.entity.player.Inventory PENDING_INV = null;

    /** §1341 客户端标记：当前打开的是不是工具包（★是就自己造 6 个侧栏 ✓） */
    public static boolean CLIENT_TOOLKIT = false;

    /**
     * §1342 “贴在一起的幽灵”：坐标 → 实体。
     *
     * <p>★用法：把 6 个幽灵放在**相邻坐标** ✓ 再靠 `Level#getBlockEntity` 的插桩让匠魂自己扫到 ✓
     * ⇒ 侧栏由匠魂自己建 ✓ 且**两端自然一致** ✓（★用户的思路 ✓）
     */
    public static final java.util.Map<net.minecraft.core.BlockPos, BlockEntity> VIRTUAL =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 把幽灵注册到相邻坐标（★两端各自注册自己的份 ✓） */
    /** §1347 基准坐标（★玩家附近 ✓ ⭐区块已加载 ✓） */
    public static net.minecraft.core.BlockPos VIRTUAL_BASE = new net.minecraft.core.BlockPos(0, -1024, 0);   // §1347 默认口袋坐标（★避开前向引用 ✓）

    /** §1350 主实体也要注册 ✗ ⭐BFS 就从它开始（不注册 ⇒ 第一步就 continue ⇒ 扫到 0 个 ✗） */
    public static void registerVirtualMain(BlockEntity be) {
        try {
            VIRTUAL.put(VIRTUAL_BASE, be);
        } catch (Throwable ignored) {
        }
    }

    public static void registerVirtual(int index, BlockEntity be) {
        try {
            VIRTUAL.put(VIRTUAL_BASE.offset(index + 1, 0, 0), be);   // §1350 侧栏顺移一格（★基准点留给主实体 ✓）
        } catch (Throwable ignored) {
        }
    }

    /** 良性查找（★空表时几乎零开销 ✓） */
    /**
     * §1355 点顶栏标签 ⇒ 打开对应设备的界面（★服务端 ✓）。
     *
     * <p>index：0 = 主界面（工匠台），1..6 = DEVICES 的顺序。
     */
    public static void openToolkitDevice(net.minecraft.server.level.ServerPlayer sp, int index) {
        try {
            String beClass;
            String blockId;
            String key;
            if (index <= 0) {
                beClass = "slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity";
                blockId = "tconstruct:crafting_station";
                key = "tnl_portable_toolkit_inv";
            } else {
                String[] d = DEVICES[Math.min(index, DEVICES.length) - 1];
                beClass = d[0];
                blockId = d[1];
                key = d[2];
            }
            net.minecraft.core.BlockPos pos = VIRTUAL_BASE.offset(Math.max(index, 0), 0, 0);
            net.minecraft.world.level.block.entity.BlockEntity ghost = makeGhostAt(beClass, blockId, pos);
            if (ghost == null) {
                return;
            }
            try {
                ghost.setLevel(sp.level());
            } catch (Throwable ignored) {
            }
            loadFromPlayer(sp, ghost, key);
            net.minecraft.nbt.CompoundTag data = export(ghost);
            net.minecraft.world.SimpleMenuProvider provider = new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, pl) -> menuForIndex(index, id, inv, ghost),
                    net.minecraft.network.chat.Component.translatable("item.tinkersnewlife.portable_toolkit"));
            net.minecraftforge.network.NetworkHooks.openScreen(sp, provider, buf -> {
                buf.writeBlockPos(pos);
                buf.writeUtf(MAGIC);
                buf.writeUtf(beClass);
                buf.writeUtf(blockId);
                buf.writeVarInt(0);
            });
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 顶栏点击 ⇒ 打开 index={} {}", index, blockId);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 顶栏点击失败: {}", t.toString());
        }
    }

    /** ★按 index 找到对应的菜单类（★都是现成的 ✓） */
    private static net.minecraft.world.inventory.AbstractContainerMenu menuForIndex(int index, int id,
            net.minecraft.world.entity.player.Inventory inv,
            net.minecraft.world.level.block.entity.BlockEntity ghost) {
        try {
            if (index <= 0) {
                return new com.mofengbaizhi.tinkersnewlife.content.menu.PortableCraftingStationMenu(id, inv,
                        (slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity) ghost,
                        "tnl_portable_toolkit_inv");
            }
            String blockId = DEVICES[Math.min(index, DEVICES.length) - 1][1];
            switch (blockId) {
                case "tconstruct:tinker_station":
                    return new com.mofengbaizhi.tinkersnewlife.content.menu.PortableAnvilMenu(id, inv,
                            (slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity) ghost);
                case "tconstruct:tinkers_anvil":
                    return new com.mofengbaizhi.tinkersnewlife.content.menu.PortableAnvilMenu(id, inv,
                            (slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity) ghost);
                case "tconstruct:part_builder":
                    return new com.mofengbaizhi.tinkersnewlife.content.menu.PortablePartBuilderMenu(id, inv,
                            (slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity) ghost);
                case "tconstruct:modifier_worktable":
                    return new com.mofengbaizhi.tinkersnewlife.content.menu.PortableModifierWorktableMenu(id, inv,
                            (slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity) ghost);
                default:
                    return new com.mofengbaizhi.tinkersnewlife.content.menu.PortableChestMenu(id, inv,
                            (slimeknights.tconstruct.tables.block.entity.chest.AbstractChestBlockEntity) ghost,
                            blockId.contains("cast")
                                    ? com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.Kind.CAST_CHEST
                                    : com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.Kind.PART_CHEST);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** §1351 打一行总数（★在注册完之后调 ✓） */
    public static void dumpVirtual() {
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                "[便携设备] 虚拟表总数={} 基准={}", VIRTUAL.size(), VIRTUAL_BASE);
    }

    public static BlockEntity lookupVirtual(net.minecraft.core.BlockPos pos) {
        if (VIRTUAL.isEmpty() || pos == null) {
            return null;
        }
        BlockEntity hit = VIRTUAL.get(pos);
        if (hit != null) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] lookupVirtual 命中 {}", pos);
        }
        return hit;
    }

    /** §1341 用常量表造出六个幽灵侧栏（★不依赖服务端传数据 ✓） */
    public static java.util.List<BlockEntity> buildDeviceGhosts() {
        java.util.List<BlockEntity> out = new java.util.ArrayList<>();
        for (String[] d : DEVICES) {
            try {
                BlockEntity be = makeGhost(d[0], d[1]);
                if (be != null) {
                    be.setLevel(net.minecraft.client.Minecraft.getInstance().level);
                    out.add(be);
                }
            } catch (Throwable ignored) {
            }
        }
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 客户端自己造侧栏幽灵 {} 个", out.size());
        return out;
    }

    /** 幽灵实体的统一口袋坐标（★远离一切工作台 ⇒ 侧栏检测扫不到邻居 ✓） */
    public static final BlockPos POCKET_POS = new BlockPos(0, -1024, 0);

    private PortableDeviceSupport() {
    }

    /** 造游离实体（★公开构造器 (BlockPos, BlockState) ＋ 按注册表 id 取方块状态 ✓） */
    /**
     * 造游离实体（§1331 ★照匠魂自己的做法 ✓）。
     *
     * <p>⚠ 关键（★反编译 {@code TinkerStationBlock} 得到 ✓）：
     * <pre>
     * private final int slotCount;                                   // 槽位数存在【方块】里
     * public BlockEntity newBlockEntity(BlockPos, BlockState) {       // 由方块造实体 ⇒ 槽位数才对
     *     return new TinkerStationBlockEntity(pos, state, this.getSlotCount());
     * }
     * </pre>
     * 我原来直接 {@code new TinkerStationBlockEntity(pos, state)} ✗ ⭐ 用的是**默认槽位数** ⇒
     * 与真实方块差一格 ⇒ 客户端/服务端槽位数不一致 ⇒ {@code IndexOutOfBounds} ✓
     *
     * <p>所以这里**优先让方块造**（{@code Block#newBlockEntity} ✓）✗ 失败才回退反射 ✓
     */
    /** §1347 指定坐标的版本（★工具包要把幽灵放到**玩家附近已加载区块**✓） */
    public static BlockEntity makeGhostAt(String beClass, String blockId,
                                          net.minecraft.core.BlockPos pos) {
        try {
            net.minecraft.world.level.block.Block block =
                    ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
            net.minecraft.world.level.block.state.BlockState state = (block == null)
                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : block.defaultBlockState();
            if (block instanceof net.minecraft.world.level.block.EntityBlock eb) {
                net.minecraft.world.level.block.entity.BlockEntity be = eb.newBlockEntity(pos, state);
                if (be != null) {
                    return be;
                }
            }
            return (net.minecraft.world.level.block.entity.BlockEntity) Class.forName(beClass)
                    .getConstructor(net.minecraft.core.BlockPos.class,
                            net.minecraft.world.level.block.state.BlockState.class)
                    .newInstance(pos, state);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 造幽灵实体失败 {}: {}", beClass, t.toString());
            return null;
        }
    }

    public static BlockEntity makeGhost(String beClass, String blockId) {
        try {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
            BlockState state = (block == null)
                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : block.defaultBlockState();
            if (block != null) {
                try {
                    // ★newBlockEntity 在 EntityBlock 接口上 ✗ ⭐ 不是 Block ✓
                    BlockEntity be = (block instanceof net.minecraft.world.level.block.EntityBlock eb)
                            ? eb.newBlockEntity(POCKET_POS, state)
                            : null;
                    if (be != null) {
                        return be;
                    }
                } catch (Throwable ignored) {
                }
            }
            Class<?> c = Class.forName(beClass);
            return (BlockEntity) c.getConstructor(BlockPos.class, BlockState.class).newInstance(POCKET_POS, state);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 造幽灵实体失败 {}: {}", beClass, t.toString());
            return null;
        }
    }
    /** 统一槽位入口：Container 优先 ⇒ 否则走 Forge IItemHandler（★箱子只有 handler ✓） */
    public static IItemHandler handlerOf(BlockEntity be) {
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

    public static int slots(BlockEntity be) {
        IItemHandler h = handlerOf(be);
        return h == null ? 0 : h.getSlots();
    }

    /** 导出库存：{@code Size ＋ inv 列表}（★Size 必须写 ✗ ⭐ 箱子据此定槽位 ✓） */
    /**
     * 导出库存（§1324 按查到的源码口径重写）。
     *
     * <p>★关键：**不写 Size** ✗ —— 箱子 handler 天生 256 槽（{@code ScalingChestItemHandler()} ⇒ {@code super(256)}），
     * 写 Size 会被 {@code ItemStackHandler#deserializeNBT} 读进来 ⇒ 容量被砍 ✓
     * <p>★只导出到"最后一个非空格" ⇒ 存档小 ✓ 且导入时按升序 {@code setStackInSlot} ⇒
     * {@code visualSize} 才会被 {@code updateVisualSize} 正确算出来 ✓
     */
    public static CompoundTag export(BlockEntity be) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        int last = -1;
        try {
            IItemHandler h = handlerOf(be);
            if (h != null) {
                for (int i = 0; i < h.getSlots(); i++) {
                    if (!h.getStackInSlot(i).isEmpty()) {
                        last = i;
                    }
                }
                for (int i = 0; i <= last; i++) {
                    ItemStack s = h.getStackInSlot(i);
                    list.add(s.isEmpty() ? new CompoundTag() : s.save(new CompoundTag()));
                }
            }
        } catch (Throwable ignored) {
        }
        tag.put("inv", list);
        return tag;
    }
    /** 导入库存（★两种格式都认 ✓：我们的 inv 列表 ✗ 或箱子的 Size/Items ✓） */
    /**
     * 导入库存（§1324）。
     *
     * <p>★必须走 {@code setStackInSlot} **升序**逐个灌 ✗ ——
     * {@code ScalingChestItemHandler#setStackInSlot} 是**唯一**会调 {@code updateVisualSize} 的入口 ✓
     * （{@code deserializeNBT} 直接改内部数组 ⇒ 不更新 ⇒ 界面显示格数停在 1 ⇒ 布局错位 ✓）
     * ★绝不写 Size ✗ 保持 handler 原生槽位数 ✓
     */
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
            int n = Math.min(list.size(), h.getSlots());
            for (int i = 0; i < n; i++) {
                CompoundTag e = list.getCompound(i);
                ItemStack s = e.isEmpty() ? ItemStack.EMPTY : ItemStack.of(e);
                try {
                    h.insertItem(i, s, false);          // ★insertItem 也会更新 visualSize ✓
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }
    public static void loadFromPlayer(Player player, BlockEntity be, String key) {
        try {
            importInto(be, player.getPersistentData().getCompound(key));
        } catch (Throwable ignored) {
        }
    }

    public static void storeToPlayer(Player player, BlockEntity be, String key) {
        try {
            player.getPersistentData().put(key, export(be));
        } catch (Throwable ignored) {
        }
    }
}
