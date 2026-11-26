package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmithingMenu.class)
public abstract class DragonManualSmithingMixin {
    @Shadow(remap = false, aliases = "f_40242_") private SmithingRecipe selectedRecipe;

    @org.spongepowered.asm.mixin.Unique private boolean tnl$keepCatalysts;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void tnl$captureRecipe(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack output, CallbackInfo ci) {
        tnl$keepCatalysts = selectedRecipe != null && TinkersNewlife.prefix("smithing/ancient_dragon_taming_manual").equals(selectedRecipe.getId());
    }

    @Inject(method = "onTake", at = @At("RETURN"))
    private void tnl$clearRecipe(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack output, CallbackInfo ci) {
        tnl$keepCatalysts = false;
    }

    @Inject(method = "shrinkStackInSlot", at = @At("HEAD"), cancellable = true)
    private void tnl$keepDragonTools(int slot, CallbackInfo ci) {
        if (tnl$keepCatalysts && (slot == 0 || slot == 2)) {
            ci.cancel();
        }
    }
}
