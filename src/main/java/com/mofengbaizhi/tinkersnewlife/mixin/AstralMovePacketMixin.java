package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.astral.AstralMovement;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class AstralMovePacketMixin {
    // Vanilla checks a DIFFERENT condition after move(): reject newly intersecting blocks, then absMoveTo
    // the client's requested coordinates. Don't simply return false: a thin wall could be crossed in one packet.
    @Inject(method="isPlayerCollidingWithAnythingNew",at=@At("HEAD"),cancellable=true,require=1)
    private void tnl$astralPacket(LevelReader world,AABB previous,double x,double y,double z,CallbackInfoReturnable<Boolean> cir) {
        var p=((ServerGamePacketListenerImpl)(Object)this).player;
        if(!AstralMovement.active(p)) return;
        Vec3 wanted=new Vec3(x-(previous.minX+previous.maxX)*.5,y-previous.minY,z-(previous.minZ+previous.maxZ)*.5);
        Vec3 allowed=AstralMovement.collide(p,previous,wanted);
        cir.setReturnValue(allowed.distanceToSqr(wanted)>1e-16);
    }
}
