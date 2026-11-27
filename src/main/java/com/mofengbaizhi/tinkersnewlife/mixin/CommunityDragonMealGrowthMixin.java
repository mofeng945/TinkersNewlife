package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.handler.DragonGrowthNest;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Ice and Fire Community Edition moved the dragon class to a different package. */
@Pseudo
@Mixin(targets = "com.iafenvoy.iceandfire.entity.EntityDragonBase")
abstract class CommunityDragonMealGrowthMixin {
    @ModifyArg(
            method = "mobInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lcom/iafenvoy/iceandfire/entity/EntityDragonBase;growDragon(I)V", remap = false),
            index = 0,
            require = 0
    )
    private int tnl$doubleRegisteredDragonMealGrowth(int days) {
        return DragonGrowthNest.regularMealGrowthDays((LivingEntity) (Object) this, days);
    }
}
