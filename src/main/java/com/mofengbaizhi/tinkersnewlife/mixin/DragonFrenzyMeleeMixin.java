package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.handler.DragonFrenzy;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Pseudo
@Mixin(targets = "com.github.alexthe666.iceandfire.entity.ai.DragonAIAttackMelee", remap = false)
abstract class DragonFrenzyMeleeMixin {
    @Unique private LivingEntity tnl$frenzyDragon;
    @Unique private boolean tnl$frenzyResolved;

    // The sole constant 20 in tick is written to attackTick immediately before the melee attempt.
    @ModifyConstant(method = "m_8037_", constant = @Constant(intValue = 20), require = 1, remap = false)
    private int tnl$frenzyInterval(int baseTicks) {
        if (!tnl$frenzyResolved) {
            tnl$frenzyResolved = true;
            for (Class<?> type = getClass(); type != null; type = type.getSuperclass()) {
                try {
                    var field = type.getDeclaredField("dragon");
                    if (field.trySetAccessible() && field.get(this) instanceof LivingEntity dragon) tnl$frenzyDragon = dragon;
                    break;
                } catch (NoSuchFieldException ignored) {
                    // A goal subclass can inherit the field.
                } catch (IllegalAccessException ignored) {
                    break;
                }
            }
        }
        return tnl$frenzyDragon == null ? baseTicks : DragonFrenzy.meleeInterval(tnl$frenzyDragon, baseTicks);
    }
}
