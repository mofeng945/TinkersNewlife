package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ProjectileWeaponHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CosmicHandler {

    private static final ModifierId COSMIC_ORDER_VOICE = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "cosmic_order_voice"));

    private static final int DURATION_BASE = 100;
    private static final int DURATION_PER_LEVEL = 50;

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
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
        if (tool.getStats().getContainedStats().isEmpty()) return;

        int level = tool.getModifierLevel(COSMIC_ORDER_VOICE);
        if (level > 0) {
            applyEffect(target, level);
        }
    }

    private static void applyEffect(LivingEntity target, int level) {
        int duration = DURATION_BASE + (level - 1) * DURATION_PER_LEVEL;
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 1));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 10));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 1));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
    }
}