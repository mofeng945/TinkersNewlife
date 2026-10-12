package com.mofengbaizhi.tinkersnewlife.content.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity;
import slimeknights.tconstruct.tables.menu.CraftingStationContainerMenu;

/**
 * §1334 便携工匠台菜单（★工具包也用它 ✓）。
 *
 * <p>为什么工匠台就够用：匠魂的**工匠台**界面自带"工具切换"标签
 * （{@code TinkerStationButtonsWidget} / {@code TinkerTabsWidget} ✓）
 * ⇒ 在同一个界面里就能用**工匠站／工匠砧**的工具制作 ✓
 *
 * <p>存档键由构造时传入（★工匠台与工具包各存各的 ✓）。
 */
public class PortableCraftingStationMenu extends CraftingStationContainerMenu {

        private net.minecraft.world.entity.player.Inventory playerInv;
private final CraftingStationBlockEntity ghost;
    private final String saveKey;

        public PortableCraftingStationMenu(int id, Inventory inv, CraftingStationBlockEntity tile, String saveKey) {
        super(id, inv, tile);
        tnl$ensureGhostLevel();
        this.playerInv = inv;
        this.ghost = tile;
        this.saveKey = saveKey;
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 槽数（菜单）{} slots={}",
                this.getClass().getSimpleName(), this.slots.size());
    }


    public PortableCraftingStationMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, (CraftingStationBlockEntity) com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport
                .makeGhost("slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity",
                        "tconstruct:crafting_station"),
                "tnl_portable_crafting_station_inv");
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
            if (this.ghost != null) {
                if (!this.playerInv.player.level().isClientSide) {
                    this.ghost.calcResult(this.playerInv.player);
                    // TNL832 \u2605\u2605 \u670d\u52a1\u7aef\u628a\u7b97\u51fa\u7684\u914d\u65b9**\u81ea\u5df1\u53d1\u7ed9\u73a9\u5bb6** \u2713
                    //   \u2605\u4e0d\u8d70\u5320\u9b42\u90a3\u6761\uff08\u2605\u5b83\u6309\u4e16\u754c\u5750\u6807\u67e5\u65b9\u5757\u5b9e\u4f53 \u2717 \u53e3\u888b\u5750\u6807\u6c38\u8fdc\u67e5\u4e0d\u5230 \u2717\uff09
                    try {
                        Object recipe = tnl$readField(this.ghost, "lastRecipe");
                        if (recipe instanceof net.minecraft.world.item.crafting.Recipe<?> r
                                && this.playerInv.player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            String rid = r.getId().toString();
                            if (!rid.equals(this.tnl$sentRecipeId)) {
                                this.tnl$sentRecipeId = rid;
                                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                                        new com.mofengbaizhi.tinkersnewlife.network.tools.PacketPortableRecipe(
                                                this.ghost.getBlockPos(), r.getId()));
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
        if (ghost == null || player.level().isClientSide()) {
            return;
        }
        com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.storeToPlayer(player, ghost, saveKey);
        for (int i = 0; i < ghost.getContainerSize(); i++) {
            ghost.setItem(i, ItemStack.EMPTY);
        }
    }

    /** §821 ★客户端自己算配方（★完全不依赖混入/包/匠魂的同步 ✓） */
    private String tnl$lastRecipeId = null;



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
            net.minecraft.world.level.block.entity.BlockEntity be = this.ghost;
            if (be == null || be.getLevel() != null) {
                return;
            }
            be.setLevel(this.playerInv.player.level());
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

    /**
     * TNL834 \u2605\u2605 \u5b9e\u65f6\u4fdd\u5b58\uff08\u2605\u7528\u6237\u53e3\u5f84\uff1a\u201c\u5f00\u7740\u754c\u9762\u5d29\u4e86\u4f1a\u4e22\u7269\u54c1\u201d \u2717\uff09
     *
     * <p>\u539f\u6765\u53ea\u5728 {@code removed()} \u5173\u754c\u9762\u65f6\u5199\u56de \u2717 \u21d2 \u5d29\u6e83/\u5f3a\u9000\u5c31\u4e22 \u2717
     * \u2605\u73b0\u5728**\u6bcf\u6b21\u683c\u5b50\u53d8\u5316\u5c31\u5199** \u2713\uff08\u2605\u53ea\u670d\u52a1\u7aef \u2713 \u2605\u4e0d\u6e05\u7a7a\u5e7d\u7075 \u2713\uff09\u3002
     */
    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        tnl$storeNow();
    }

    /** TNL834 \u2605\u7acb\u5373\u5199\u56de\uff08\u2605\u4e0d\u52a8\u5e7d\u7075\u91cc\u7684\u4e1c\u897f \u2713\uff09 */
    private void tnl$storeNow() {
        try {
            if (this.ghost == null || this.playerInv == null || this.playerInv.player.level().isClientSide()) {
                return;
            }
            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.storeToPlayer(
                    this.playerInv.player, this.ghost, this.saveKey);
        } catch (Throwable ignored) {
        }
    }
}
