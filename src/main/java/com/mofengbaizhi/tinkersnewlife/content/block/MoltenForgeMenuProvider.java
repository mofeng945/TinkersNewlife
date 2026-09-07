package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.menu.MoltenForgeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** 融锻炉菜单提供器 */
public class MoltenForgeMenuProvider implements MenuProvider {

    private final MoltenForgeBlockEntity te;

    public MoltenForgeMenuProvider(MoltenForgeBlockEntity te) {
        this.te = te;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.tinkersnewlife.melt_forge");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MoltenForgeMenu(id, inventory, te);
    }
}
