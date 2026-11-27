package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.handler.DragonGrowthNest;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Optional Ice and Fire hook; leaves natural aging and growDragon's own checks untouched. */
@Pseudo
@Mixin(targets = "com.github.alexthe666.iceandfire.entity.EntityDragonBase")
abstract class DragonMealGrowthMixin {
    @ModifyArg(
            method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lcom/github/alexthe666/iceandfire/entity/EntityDragonBase;growDragon(I)V", remap = false),
            index = 0,
            require = 0
    )
    private int tnl$doubleRegisteredDragonMealGrowth(int days) {
        return DragonGrowthNest.regularMealGrowthDays((LivingEntity) (Object) this, days);
    }
}
