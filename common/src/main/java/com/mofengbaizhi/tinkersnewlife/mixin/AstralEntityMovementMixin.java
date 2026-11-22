package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.astral.AstralMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class AstralEntityMovementMixin {
    @Inject(method="collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",at=@At("HEAD"),cancellable=true,require=1)
    private void tnl$astralMove(Vec3 wanted, CallbackInfoReturnable<Vec3> cir) {
        if((Object)this instanceof Player p && AstralMovement.active(p))
            cir.setReturnValue(AstralMovement.collide(p,p.getBoundingBox(),wanted));
    }
    private static boolean tnl$spirit(Entity entity) {
        return entity instanceof Player player && AstralMovement.active(player);
    }

    // Forge clears cached fluid heights before this scan. Keep them empty and do not apply currents.
    @Inject(method="updateFluidHeightAndDoFluidPushing(Ljava/util/function/Predicate;)V", at=@At("HEAD"), cancellable=true, remap=false)
    private void tnl$skipFluidScan(java.util.function.Predicate<net.minecraft.world.level.material.FluidState> filter,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if(tnl$spirit((Entity)(Object)this)) ci.cancel();
    }

    // Inject AFTER clearing the previous eye-fluid tag/type, BEFORE sampling the new eye position.
    @Inject(method="updateFluidOnEyes", at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getEyeY()D"), cancellable=true)
    private void tnl$dryEyes(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if(tnl$spirit((Entity)(Object)this)) ci.cancel();
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method="checkInsideBlocks", at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;entityInside(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V"))
    private void tnl$fluidContact(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, Entity entity) {
        if(tnl$spirit(entity) && !state.getFluidState().isEmpty() && (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock || state.is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN))) return;
        state.entityInside(level,pos,entity);
    }

    @Inject(method="isInBubbleColumn", at=@At("HEAD"), cancellable=true)
    private void tnl$ignoreBubbleColumn(CallbackInfoReturnable<Boolean> ci) {
        if(tnl$spirit((Entity)(Object)this)) ci.setReturnValue(false);
    }

    @Inject(method="isInWall",at=@At("HEAD"),cancellable=true,require=1)
    private void tnl$astralBreathing(CallbackInfoReturnable<Boolean> cir) {
        if((Object)this instanceof Player p && AstralMovement.active(p)) cir.setReturnValue(false);
    }
}
