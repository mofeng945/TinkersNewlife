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