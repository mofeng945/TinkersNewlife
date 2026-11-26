package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.unlock.FlyingSwordUnlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

/** Award the manual only during unpacking a real structure chest, never from arbitrary loot-table rolls. */
@Mixin(RandomizableContainerBlockEntity.class)
public abstract class AncientManualChestMixin {
    private static final Set<ResourceLocation> tnl$bastionTables = Set.of(new ResourceLocation("chests/bastion_bridge"),
            new ResourceLocation("chests/bastion_hoglin_stable"), new ResourceLocation("chests/bastion_other"),
            new ResourceLocation("chests/bastion_treasure"));
    private static final ResourceLocation tnl$endCityTable = new ResourceLocation("chests/end_city_treasure");

    @Redirect(method = "unpackLootTable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;fill(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/storage/loot/LootParams;J)V"), require = 1)
    private void tnl$fillAndGrantPage(LootTable table, Container container, LootParams params, long seed) {
        table.fill(container, params, seed);
        if (!(container instanceof ChestBlockEntity chest) || params.getLevel().getBlockEntity(chest.getBlockPos()) != chest
                || !(params.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)) return;
        ResourceLocation lootId = table.getLootTableId();
        if (lootId == null) return;
        var structures = params.getLevel().structureManager();
        FlyingSwordUnlock.Manual manual;
        if (tnl$bastionTables.contains(lootId) && structures.getStructureWithPieceAt(chest.getBlockPos(), BuiltinStructures.BASTION_REMNANT).isValid()) {
            manual = FlyingSwordUnlock.Manual.FLYING_SWORD;
        } else if (tnl$endCityTable.equals(lootId) && structures.getStructureWithPieceAt(chest.getBlockPos(), BuiltinStructures.END_CITY).isValid()) {
            manual = FlyingSwordUnlock.Manual.TANG_HENG_DAO;
        } else return;
        if (!FlyingSwordUnlock.claimChestPage(player, manual)) return;
        ItemStack page = new ItemStack(manual == FlyingSwordUnlock.Manual.FLYING_SWORD
                ? ModItems.ANCIENT_CULTIVATION_PAGE.get() : ModItems.ANCIENT_WEAPON_MANUAL.get());
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).isEmpty()) {
                chest.setItem(i, page);
                return;
            }
        }
        // Modded loot may occupy every slot. Preserve the chest loot and still deliver the guaranteed page.
        player.addItem(page);
        if (!page.isEmpty()) player.drop(page, false);
    }
}
