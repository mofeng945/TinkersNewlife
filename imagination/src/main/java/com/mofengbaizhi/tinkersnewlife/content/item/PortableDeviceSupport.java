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

    /** 幽灵实体的统一口袋坐标（★远离一切工作台 ⇒ 侧栏检测扫不到邻居 ✓） */
    public static final BlockPos POCKET_POS = new BlockPos(0, -1024, 0);

    private PortableDeviceSupport() {
    }

    /** 造游离实体（★公开构造器 (BlockPos, BlockState) ＋ 按注册表 id 取方块状态 ✓） */
    public static BlockEntity makeGhost(String beClass, String blockId) {
        try {
            Class<?> c = Class.forName(beClass);
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
            BlockState state = (block == null)
                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : block.defaultBlockState();
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
        // ★箱子要的原始格式（ItemStackHandler#deserializeNBT 认 Size/Items ✓）
        ListTag items = new ListTag();
        for (int i = 0; i < size; i++) {
            CompoundTag e = new CompoundTag();
            e.putInt("Slot", i);
            if (i < list.size() && !list.getCompound(i).isEmpty()) {
                e.merge(list.getCompound(i));
            }
            items.add(e);
        }
        tag.putInt("Size", size);
        tag.put("Items", items);
        return tag;
    }

    /** 导入库存（★两种格式都认 ✓：我们的 inv 列表 ✗ 或箱子的 Size/Items ✓） */
    public static void importInto(BlockEntity be, CompoundTag tag) {
        try {
            if (tag == null) {
                return;
            }
            IItemHandler h = handlerOf(be);
            if (h == null) {
                return;
            }
            if (tag.contains("Items", 9)) {   // ★箱子格式 ⇒ 直接交给 handler ✓
                if (h instanceof net.minecraftforge.items.ItemStackHandler ish) {
                    ish.deserializeNBT(tag);
                    return;
                }
            }
            if (!tag.contains("inv", 9)) {
                return;
            }
            ListTag list = tag.getList("inv", 10);
            for (int i = 0; i < h.getSlots() && i < list.size(); i++) {
                h.insertItem(i, ItemStack.of(list.getCompound(i)), false);
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

    public static void storeToPlayer(Player player, BlockEntity be, String key, int size) {
        try {
            player.getPersistentData().put(key, export(be, size));
        } catch (Throwable ignored) {
        }
    }
}