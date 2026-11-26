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
                    // TNL832 ★★ 服务端把算出的配方**自己发给玩家** ✓
                    //   ★不走匠魂那条（★它按世界坐标查方块实体 ✗ 口袋坐标永远查不到 ✗）
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
            net.minecraft.world.level.block.entity.BlockEntity be = this.ghost;
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
     * TNL834 ★★ 实时保存（★用户口径：“开着界面崩了会丢物品” ✗）
     *
     * <p>原来只在 {@code removed()} 关界面时写回 ✗ ⇒ 崩溃/强退就丢 ✗
     * ★现在**每次格子变化就写** ✓（★只服务端 ✓ ★不清空幽灵 ✓）。
     */
    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        tnl$storeNow();
    }

    /** TNL834 ★立即写回（★不动幽灵里的东西 ✓） */
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
