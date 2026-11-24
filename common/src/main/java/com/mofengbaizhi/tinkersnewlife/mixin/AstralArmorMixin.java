package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.astral.AstralProjection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skip armor and toughness for the projected player only; don't rewrite the source or deal damage twice. */
@Mixin(LivingEntity.class)
public abstract class AstralArmorMixin {
    @Inject(method="getDamageAfterArmorAbsorb",at=@At("HEAD"),cancellable=true)
    private void tnl$unarmoredSpirit(DamageSource source,float amount,CallbackInfoReturnable<Float> ci){
        if ((Object)this instanceof Player p && AstralProjection.active(p)) ci.setReturnValue(amount);
    }

    // The armor method has already applied wear; resistance, enchantments and absorption still follow.
    @Inject(method="getDamageAfterArmorAbsorb",at=@At("RETURN"),cancellable=true)
    private void tnl$rapierPierce(DamageSource source,float amount,CallbackInfoReturnable<Float> ci) {
        LivingEntity target = (LivingEntity)(Object)this;
        if (!target.level().isClientSide && source.getEntity() instanceof Player attacker
                && attacker != target && source.getDirectEntity() == attacker
                && source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
            var slot = slimeknights.tconstruct.library.tools.helper.ModifierLootingHandler.getLootingSlot(attacker);
            var stack = attacker.getItemBySlot(slot);
            if (stack.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.RapierItem
                    && !slimeknights.tconstruct.library.tools.nbt.ToolStack.from(stack).isBroken()) {
                ci.setReturnValue(amount);
            }
        }
    }
}
