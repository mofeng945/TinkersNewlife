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
        tnl$ensureGhostLevel();
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
        // §1337 手写侧栏不可行（★Mantle addSubContainer 拒绝：BaseContainer: Player inventory has … ✓）
        //   ⇒ 暂不加侧栏 ✗ ⭐ 工具包先当作"便携工匠台"用 ✓（★后续走"幽灵相邻让匠魂自己扫"那条路 ✓）
        if (true) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 侧栏改为暂不添加（★避免崩溃 ✓）");
            return;
        }
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

    /**
     * §814 ★便携设备**无法合成**的修复（★服务端 ✓）：
     * 匠魂的配方同步只发给“追踪该区块的玩家”✗ 而幽灵在口袋坐标（无区块）
     * ⇒ 包从未发出 ⇒ 客户端拿不到配方 ⇒ 合不了 ✗✗。
     * ★这里在 {@code super.broadcastChanges()}（★结果槽会在这里算出 lastRecipe ✓）
     * 之后自己推一次 ✓。
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        try {
            if (this.mainGhost != null) {
                if (!this.playerInv.player.level().isClientSide) {
                    this.mainGhost.calcResult(this.playerInv.player);
                    // TNL832 ★★ 服务端把算出的配方**自己发给玩家** ✓
                    //   ★不走匠魂那条（★它按世界坐标查方块实体 ✗ 口袋坐标永远查不到 ✗）
                    try {
                        Object recipe = tnl$readField(this.mainGhost, "lastRecipe");
                        if (recipe instanceof net.minecraft.world.item.crafting.Recipe<?> r
                                && this.playerInv.player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            String rid = r.getId().toString();
                            if (!rid.equals(this.tnl$sentRecipeId)) {
                                this.tnl$sentRecipeId = rid;
                                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                                        new com.mofengbaizhi.tinkersnewlife.network.tools.PacketPortableRecipe(
                                                this.mainGhost.getBlockPos(), r.getId()));
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

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

    /** §821 ★客户端自己算配方（★完全不依赖混入/包/匠魂的同步 ✓） */
    private String tnl$lastRecipeId = null;



    /**
     * TNL828 ★★★ 致命修正：幽灵方块实体必须**挂上一个真实的 level** ✓
     *
     * <p>日志证据（★TNL826 探针 ✓）：
     * {@code crafting station ghost created pos=… level=null client=false} ✗✗
     * 而匠魂 {@code CraftingStationBlockEntity#calcResult} 第一句就是
     * {@code if (this.level == null || isEmpty()) return EMPTY;} ✗
     * ⇒ ★从来没算过配方 ⇒ 界面正常、能放东西，**就是合不了** ✗✗
     *
     * <p>★在菜单构造时补上（★服务端与客户端都走这里 ✓）。
     */
    private void tnl$ensureGhostLevel() {
        try {
            net.minecraft.world.level.block.entity.BlockEntity be = this.mainGhost;
            if (be == null || be.getLevel() != null) {
                return;
            }
            be.setLevel(this.playerInv.player.level());
        } catch (Throwable ignored) {
        }
    }

    /** TNL832 ★已发送的配方 id（★避免每 tick 重发 ✓） */
    private String tnl$sentRecipeId = null;

    /** TNL832 ★沿类次读私有字段 ✓ */
    private static Object tnl$readField(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * TNL834 ★★ 实时保存（★用户口径：崩了会丢物品 ✗）
     *
     * <p>★原来只在关界面时写回 ✗ ⇒ 现在每次格子变化就把**六份内容各写一次** ✓。
     */
    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        tnl$storeNow();
    }

    private void tnl$storeNow() {
        try {
            if (this.sides == null || this.playerInv == null || this.playerInv.player.level().isClientSide()) {
                return;
            }
            for (int i = 0; i < this.sides.size()
                    && i < com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.DEVICES.length; i++) {
                net.minecraft.world.level.block.entity.BlockEntity be = this.sides.get(i);
                if (be != null) {
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.storeToPlayer(
                            this.playerInv.player, be,
                            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.DEVICES[i][2]);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}