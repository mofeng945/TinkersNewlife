package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ProjectileWeaponHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChildOfTheStarsHandler {

    private static final ModifierId CHILD_OF_THE_STARS = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "child_of_the_stars")
    );

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        ItemStack weapon = ItemStack.EMPTY;

        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            weapon = ProjectileWeaponHelper.getProjectileWeapon(projectile, player);
            if (weapon.isEmpty()) {
                weapon = player.getMainHandItem();
            }
        } else {
            weapon = player.getMainHandItem();
        }

        if (weapon.isEmpty()) return;
        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(weapon);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHILD_OF_THE_STARS);
        if (level <= 0) return;

        float originalDamage = event.getAmount();
        float multipliedDamage = (float) (originalDamage * Math.pow(2, level));
        event.setAmount(multipliedDamage);
    }
}