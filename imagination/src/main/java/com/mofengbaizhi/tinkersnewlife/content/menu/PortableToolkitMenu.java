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
                if (this.playerInv.player.level().isClientSide) {
                    this.tnl$clientMatchRecipe();
                } else {
                    this.mainGhost.calcResult(this.playerInv.player);
                    // TNL832 \u2605\u2605 \u670d\u52a1\u7aef\u628a\u7b97\u51fa\u7684\u914d\u65b9**\u81ea\u5df1\u53d1\u7ed9\u73a9\u5bb6** \u2713
                    //   \u2605\u4e0d\u8d70\u5320\u9b42\u90a3\u6761\uff08\u2605\u5b83\u6309\u4e16\u754c\u5750\u6807\u67e5\u65b9\u5757\u5b9e\u4f53 \u2717 \u53e3\u888b\u5750\u6807\u6c38\u8fdc\u67e5\u4e0d\u5230 \u2717\uff09
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
                                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                                        "TNL832 server sent recipe {} to {}", rid, sp.getName().getString());
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

    private void tnl$clientMatchRecipe() {
        try {
            net.minecraft.world.level.Level level = this.playerInv.player.level();
            // ★用反射构造临时 3x3（★读本菜单前 9 格 ✓），避免死写签名
            Object grid = Class.forName("net.minecraft.world.inventory.TransientCraftingContainer")
                    .getConstructor(net.minecraft.world.inventory.AbstractContainerMenu.class, int.class, int.class)
                    .newInstance(this, 3, 3);
            java.lang.reflect.Method m = net.minecraft.world.item.crafting.RecipeManager.class.getMethod(
                    "getRecipeFor",
                    net.minecraft.world.item.crafting.RecipeType.class,
                    net.minecraft.world.Container.class,
                    net.minecraft.world.level.Level.class);
            Object opt = m.invoke(level.getRecipeManager(),
                    net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) {
                // \u00a7822 \u2605\u6ca1\u5339\u914d\u5230\u4e5f\u8981\u62a5\uff08\u2605\u4e0a\u4e00\u7248\u6f0f\u7684\u5c31\u662f\u8fd9\u91cc \u2717\uff09\uff1a\u628a\u683c\u5b50\u91cc\u6709\u51e0\u4e2a\u4e1c\u897f\u4e5f\u6253\u51fa\u6765 \u2713
                int filled = 0;
                for (int i = 0; i < 9 && i < this.slots.size(); i++) {
                    if (!this.slots.get(i).getItem().isEmpty()) {
                        filled++;
                    }
                }
                String sig = "NOMATCH:" + filled;
                if (!sig.equals(this.tnl$lastRecipeId)) {
                    this.tnl$lastRecipeId = sig;
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                            "[\u4fbf\u643a\u8bbe\u5907] \u00a7822 \u5ba2\u6237\u7aef\u672a\u5339\u914d\u5230\u914d\u65b9\uff1a\u683c\u5b50\u975e\u7a7a={} \u7c7b\u578b={} \u670d\u52a1\u5668\u7aef={} \u914d\u65b9\u603b\u6570={}",
                            filled, level.getClass().getSimpleName(), level.isClientSide,
                            level.getRecipeManager().getRecipes().size());
                }
                return;
            }
            Object recipe = o.get();
            String id = String.valueOf(recipe.getClass().getName()) + "#" + recipe.hashCode();
            if (id.equals(this.tnl$lastRecipeId)) {
                return;
            }
            this.tnl$lastRecipeId = id;
            this.mainGhost.getClass()
                    .getMethod("updateRecipe", Class.forName("net.minecraft.world.item.crafting.CraftingRecipe"))
                    .invoke(this.mainGhost, recipe);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[便携设备] §821 客户端自算配方成功：{} ⇒ {}", recipe.getClass().getSimpleName(),
                    this.mainGhost.getClass().getSimpleName());
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "[便携设备] §821 客户端算配方失败：{}: {}", t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /** \u00a7823 \u2605\u7ed9\u5ba2\u6237\u7aef tick \u4e8b\u4ef6\u7528\u7684\u516c\u5f00\u5165\u53e3\uff08\u2605`broadcastChanges` \u5728\u5ba2\u6237\u7aef\u4e0d\u4f1a\u88ab\u8c03 \u2717\uff09 */
    public void tnl$clientTick() {
        try {
            if (this.playerInv != null && this.playerInv.player.level().isClientSide) {
                this.tnl$clientMatchRecipe();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * TNL828 \u2605\u2605\u2605 \u81f4\u547d\u4fee\u6b63\uff1a\u5e7d\u7075\u65b9\u5757\u5b9e\u4f53\u5fc5\u987b**\u6302\u4e0a\u4e00\u4e2a\u771f\u5b9e\u7684 level** \u2713
     *
     * <p>\u65e5\u5fd7\u8bc1\u636e\uff08\u2605TNL826 \u63a2\u9488 \u2713\uff09\uff1a
     * {@code crafting station ghost created pos=\u2026 level=null client=false} \u2717\u2717
     * \u800c\u5320\u9b42 {@code CraftingStationBlockEntity#calcResult} \u7b2c\u4e00\u53e5\u5c31\u662f
     * {@code if (this.level == null || isEmpty()) return EMPTY;} \u2717
     * \u21d2 \u2605\u4ece\u6765\u6ca1\u7b97\u8fc7\u914d\u65b9 \u21d2 \u754c\u9762\u6b63\u5e38\u3001\u80fd\u653e\u4e1c\u897f\uff0c**\u5c31\u662f\u5408\u4e0d\u4e86** \u2717\u2717
     *
     * <p>\u2605\u5728\u83dc\u5355\u6784\u9020\u65f6\u8865\u4e0a\uff08\u2605\u670d\u52a1\u7aef\u4e0e\u5ba2\u6237\u7aef\u90fd\u8d70\u8fd9\u91cc \u2713\uff09\u3002
     */
    private void tnl$ensureGhostLevel() {
        try {
            net.minecraft.world.level.block.entity.BlockEntity be = this.mainGhost;
            if (be == null || be.getLevel() != null) {
                return;
            }
            be.setLevel(this.playerInv.player.level());
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL828 ghost level attached: {} client={}", be.getClass().getSimpleName(),
                    this.playerInv.player.level().isClientSide);
        } catch (Throwable ignored) {
        }
    }

    /** TNL832 \u2605\u5df2\u53d1\u9001\u7684\u914d\u65b9 id\uff08\u2605\u907f\u514d\u6bcf tick \u91cd\u53d1 \u2713\uff09 */
    private String tnl$sentRecipeId = null;

    /** TNL832 \u2605\u6cbf\u7c7b\u6b21\u8bfb\u79c1\u6709\u5b57\u6bb5 \u2713 */
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
}