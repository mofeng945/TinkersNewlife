package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.events.SoulBodyProjectileHandler.Flight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A short, tracked distance budget. Does NOT touch the projectile's original gravity or physics flags. */
@Mixin(Projectile.class)
public abstract class SoulBodyProjectileMixin implements Flight {
    @Unique private static final EntityDataAccessor<Float> TNL_SOUL_LEFT = SynchedEntityData.defineId(Projectile.class, EntityDataSerializers.FLOAT);
    @Unique private static final String TNL_SOUL_KEY = "tnlSoulFlightRemaining";
    @Unique private Vec3 tnl$previousPosition;
    @Unique private double tnl$stepLength;
    @Override public void tnl$traceLength(double length) { tnl$stepLength = length; }
    @Override public boolean tnl$phaseThroughThisStep() { return tnl$soulDistance() > tnl$stepLength; }
    @Unique private Projectile tnl$self() { return (Projectile)(Object)this; }
    @Inject(method="<init>", at=@At("RETURN"))
    private void tnl$defineBudget(CallbackInfo ci) { tnl$self().getEntityData().define(TNL_SOUL_LEFT, 0f); }
    @Override public float tnl$soulDistance() { return tnl$self().getEntityData().get(TNL_SOUL_LEFT); }
    @Override public void tnl$startSoulFlight(float distance) {
        // A tool that is both launcher and ammunition must not get twice the distance.
        tnl$self().getEntityData().set(TNL_SOUL_LEFT, Math.max(tnl$soulDistance(),distance));
        // Some launchers finalize their spawn offset after firing the hook. Start measuring on the first movement tick.
        tnl$previousPosition = null;
    }
    @Unique private float tnl$leftAtPosition() {
        float left = tnl$soulDistance();
        return tnl$previousPosition == null || left <= 0 ? left : (float)Math.max(0, left - tnl$previousPosition.distanceTo(tnl$self().position()));
    }
    @Inject(method="tick", at=@At("HEAD"))
    private void tnl$advance(CallbackInfo ci) {
        // Server sends the remaining distance through vanilla entity data, including for new trackers.
        if (tnl$self().level().isClientSide || tnl$soulDistance() <= 0) return;
        float left = tnl$leftAtPosition();
        tnl$self().getEntityData().set(TNL_SOUL_LEFT,left);
        tnl$previousPosition = left > 0 ? tnl$self().position() : null;
    }
    @Inject(method="addAdditionalSaveData", at=@At("RETURN"))
    private void tnl$save(CompoundTag tag, CallbackInfo ci) {
        float left = tnl$leftAtPosition();
        if (left > 0) tag.putFloat(TNL_SOUL_KEY, left);
    }
    @Inject(method="readAdditionalSaveData", at=@At("RETURN"))
    private void tnl$load(CompoundTag tag, CallbackInfo ci) {
        float left = tag.getFloat(TNL_SOUL_KEY);
        if (Float.isFinite(left) && left > 0) tnl$startSoulFlight(left);
    }
}
