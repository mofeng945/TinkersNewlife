package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.unlock.FlyingSwordResultSlot;
import com.mofengbaizhi.tinkersnewlife.content.unlock.FlyingSwordUnlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.layout.StationSlotLayout;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;
import slimeknights.tconstruct.tables.client.inventory.TinkerStationScreen;

@Mixin(value = TinkerStationScreen.class, remap = false)
public abstract class FlyingSwordStationScreenMixin {
    // The screen builds a fresh button list on init; leave the shared layout registry alone.
    @Inject(method = "lambda$init$0", at = @At("HEAD"), cancellable = true, require = 1)
    private void tinkersnewlife$hideLockedSword(StationSlotLayout layout, CallbackInfoReturnable<Boolean> cir) {
        if (layout.getName().getNamespace().equals("tinkersnewlife")
            && layout.getName().getPath().equals("flying_sword")) {
            Player player = Minecraft.getInstance().player;
            if (player == null || !FlyingSwordUnlock.isUnlocked(player)) {
                cir.setReturnValue(false);
            }
        }
    }

    // Beyond the output item, the screen separately uses the raw result for its tool stats panel.
    @Redirect(method = "updateDisplay", at = @At(value = "INVOKE", target = "Lslimeknights/tconstruct/tables/block/entity/table/TinkerStationBlockEntity;getResult()Lslimeknights/tconstruct/library/tools/nbt/LazyToolStack;"), require = 1)
    private LazyToolStack tinkersnewlife$hideLockedPreview(TinkerStationBlockEntity station) {
        LazyToolStack result = station.getResult();
        return FlyingSwordResultSlot.isLockedAssembly(station, Minecraft.getInstance().player) ? null : result;
    }
}
