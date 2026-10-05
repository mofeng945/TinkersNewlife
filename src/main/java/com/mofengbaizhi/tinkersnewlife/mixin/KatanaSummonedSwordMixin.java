package com.mofengbaizhi.tinkersnewlife.mixin;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>§1022 照抄 TiCEX 的 {@code SummonedSwordMixin}</b> ✓
 * —— 让拔刀剑特殊技召唤出来的剑命中目标时，也走一遍**匠魂的投射物命中钩子** ✓
 * （{@code ModifierHooks.PROJECTILE_HIT} ✓，任一钩子返回 true 就取消本体那次命中 ✓）。
 *
 * <p>⚠ 拔刀剑不在场时本 mixin 静默不生效 ✓（该类只在拔刀剑在场时才会被 mixin 处理 ✓）。
 */
@Mixin(value = EntityAbstractSummonedSword.class, remap = false)
public abstract class KatanaSummonedSwordMixin extends Projectile {

    protected KatanaSummonedSwordMixin(EntityType<? extends Projectile> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(at = @At("HEAD"), method = "onHitEntity", cancellable = true, remap = false)
    protected void onHitEntity(EntityHitResult entityHitResult, CallbackInfo ci) {
        Entity shooter = this.getOwner();
        Entity target = entityHitResult.getEntity();
        if (shooter instanceof LivingEntity livingShooter) {
            ItemStack mainHandStack = livingShooter.getMainHandItem();
            if (mainHandStack.getItem() instanceof IModifiable) {
                ToolStack tool = ToolStack.from(mainHandStack);
                ModifierNBT modifiers = tool.getModifiers();
                ModDataNBT persistentData = tool.getPersistentData();

                for (ModifierEntry modifier : tool.getModifierList()) {
                    boolean cancelFlag = modifier.getHook(ModifierHooks.PROJECTILE_HIT).onProjectileHitEntity(
                            modifiers,
                            persistentData,
                            modifier,
                            (EntityAbstractSummonedSword) (Object) this,
                            entityHitResult,
                            livingShooter,
                            target instanceof LivingEntity livingTarget ? livingTarget : null
                    );
                    if (cancelFlag) {
                        ci.cancel();
                    }
                }
            }
        }
    }
}
