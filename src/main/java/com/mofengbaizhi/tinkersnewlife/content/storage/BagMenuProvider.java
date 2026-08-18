package com.mofengbaizhi.tinkersnewlife.content.storage;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;
import java.util.UUID;

public class BagMenuProvider implements MenuProvider {

    private final UUID uuid;
    private final int level;
    private final Component displayName;

    public BagMenuProvider(UUID uuid, int level) {
        this.uuid = uuid;
        this.level = level;
        // ✅ 使用翻译键，等级作为参数插入
        this.displayName = Component.translatable("container.tinkersnewlife.bag", level);
    }

    @Override
    public Component getDisplayName() {
        return displayName;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BagContainer(containerId, playerInventory, uuid, level);
    }
}