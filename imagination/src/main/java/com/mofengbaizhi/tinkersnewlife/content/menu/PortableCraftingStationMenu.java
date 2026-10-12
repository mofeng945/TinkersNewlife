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
                if (this.playerInv.player.level().isClientSide) {
                    this.tnl$clientMatchRecipe();
                } else {
                    this.ghost.calcResult(this.playerInv.player);
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
                return;
            }
            Object recipe = o.get();
            String id = String.valueOf(recipe.getClass().getName()) + "#" + recipe.hashCode();
            if (id.equals(this.tnl$lastRecipeId)) {
                return;
            }
            this.tnl$lastRecipeId = id;
            this.ghost.getClass()
                    .getMethod("updateRecipe", Class.forName("net.minecraft.world.item.crafting.CraftingRecipe"))
                    .invoke(this.ghost, recipe);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[便携设备] §821 客户端自算配方成功：{} ⇒ {}", recipe.getClass().getSimpleName(),
                    this.ghost.getClass().getSimpleName());
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "[便携设备] §821 客户端算配方失败：{}: {}", t.getClass().getSimpleName(), t.getMessage());
        }
    }
}
