package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.events.SoulBodyProjectileHandler;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

@Mixin(AbstractArrow.class)
public abstract class SoulBodyArrowMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/projectile/AbstractArrow;isNoGravity()Z"))
    private boolean tnl$arrowGravity(AbstractArrow p) {
        return p.isNoGravity() || SoulBodyProjectileHandler.suppressGravity(p);
    }
    // Even before the ray cast, vanilla marks an arrow inGround if its start point is embedded in a solid.
    @Redirect(method="tick", at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/state/BlockState;isAir()Z",ordinal=0))
    private boolean tnl$skipEmbed(BlockState state) {
        return state.isAir() || ((SoulBodyProjectileHandler.Flight)(Object)this).tnl$soulDistance() > 0;
    }
    @ModifyArg(index=0, method="tick", at=@At(value="INVOKE",target="Lnet/minecraft/world/level/ClipContext;<init>(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)V"))
    private Vec3 tnl$clipSuffix(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) {
        return SoulBodyProjectileHandler.blockTraceStart(from, to, entity);
    }
}
