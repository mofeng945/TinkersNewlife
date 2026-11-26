package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.DimensionalHuntModifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
public abstract class DimensionalHuntArrowMixin {
    // Skip vanilla's Enderman early return after a successful hit. The usual arrow disposal,
    // piercing, impact sound and post-hit effects then run instead of repeatedly hitting the same target.
    @Redirect(method = "onHitEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getType()Lnet/minecraft/world/entity/EntityType;"))
    private EntityType<?> tnl$normalEndermanImpact(Entity entity) {
        EntityType<?> type = entity.getType();
        if (type == EntityType.ENDERMAN && DimensionalHuntModifier.hasHunt((AbstractArrow)(Object)this)) {
            return EntityType.PLAYER; // Consumed only by the local comparison against ENDERMAN.
        }
        return type;
    }
}
