package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import slimeknights.tconstruct.tables.TinkerTables;
import slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;
import slimeknights.tconstruct.tables.menu.module.SideInventoryContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * §1335 便携工匠工具包菜单 —— 把**六个幽灵方块**作为**六个侧栏**并排摆开。
 *
 * <p>机制（★反编译 TiC 得到 ✓）：
 * <pre>
 * // TabbedContainerMenu
 * this.detectStationParts(tile.getLevel(), tile.getBlockPos());        // 工匠台自动扫周围库存
 * this.addSubContainer(new SideInventoryContainer&lt;BlockEntity&gt;(…, be, x, y, …));   // 每个库存 ⇒ 一个侧栏
 * // BaseTabbedScreen（客户端）
 * getSubContainer(SideInventoryContainer.class)                        // ★通用 ⇒ 有几个画几个 ✓
 * </pre>
 * 所以只要**重写 detectStationParts** ✗ ⭐ 手动把我们那 6 个幽灵 `addSubContainer` 进去 ✓
 * ⇒ 客户端不用改一行 ⇒ 6 个侧栏自动并排出现 ✓✓
 */
public class PortableToolkitMenu extends CraftingStationContainerMenu {

    private final CraftingStationBlockEntity mainGhost;
    private final Inventory playerInv;
    private final List<BlockEntity> sides;
    private boolean sidesAdded = false;

    public PortableToolkitMenu(int id, Inventory inv, CraftingStationBlockEntity main,
                               List<BlockEntity> sides) {
        super(id, inv, main);
        this.mainGhost = main;
        this.playerInv = inv;
        this.sides = (sides == null) ? new ArrayList<>() : sides;
        addSides();   // §1336 ★super() 里会调 detectStationParts（那时 sides 还是 null ✗）⇒ 在这里真正加 ✓
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 槽数（菜单）{} slots={} sides={}",
                this.getClass().getSimpleName(), this.slots.size(), this.sides.size());
    }

    /** 服务端用 */
    public static PortableToolkitMenu server(int id, Inventory inv, CraftingStationBlockEntity main,
                                             List<BlockEntity> sides) {
        return new PortableToolkitMenu(id, inv, main, sides);
    }

    /** 客户端：侧栏幽灵由混入解析后暂存在 PortableDeviceSupport ✓ */
    public PortableToolkitMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (CraftingStationBlockEntity) com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport
                .makeGhost("slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity",
                        "tconstruct:crafting_station"),
                new ArrayList<>(com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_SIDES));
    }

    @Override
    public void detectStationParts(Level world, BlockPos start) {
        super.detectStationParts(world, start);   // ★保留原版行为 ✓
        // §1336 ★父类构造器里就会调到这里 ✗ ⭐ 那时 sides 还没赋值 ⇒ 必须判空 ✓
        if (this.sides == null || this.sidesAdded) {
            return;
        }
        addSides();
    }

    /** §1336 真正把 6 个幽灵加为侧栏（★只加一次 ✓） */
    private void addSides() {
        if (this.sidesAdded) {
            return;
        }
        this.sidesAdded = true;
        int columns = 9;
        int i = 0;
        for (BlockEntity be : this.sides) {
            if (be == null) {
                continue;
            }
            try {
                SideInventoryContainer<BlockEntity> side = new SideInventoryContainer<>(
                        TinkerTables.craftingStationContainer.get(), this.containerId, this.playerInv, be,
                        -40 - i * 100, 18, columns);
                this.addSubContainer(side, true);
                i++;
            } catch (Throwable t) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备] 侧栏添加失败: {}", t.toString());
            }
        }
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 侧栏已加 {} 个", i);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        try {
            super.removed(player);
        } catch (Throwable ignored) {
        }
        if (player.level().isClientSide()) {
            return;
        }
        // ★把 6 份内容各写回玩家存档（★与物品无关 ⇒ 无刷物品风险 ✓）
        for (int i = 0; i < sides.size() && i < com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.DEVICES.length; i++) {
            BlockEntity be = sides.get(i);
            if (be != null) {
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.storeToPlayer(player, be,
                        com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.DEVICES[i][2]);
                clear(be);
            }
        }
    }

    private static void clear(BlockEntity be) {
        try {
            var h = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.handlerOf(be);
            if (h != null) {
                for (int i = 0; i < h.getSlots(); i++) {
                    h.extractItem(i, Integer.MAX_VALUE, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}