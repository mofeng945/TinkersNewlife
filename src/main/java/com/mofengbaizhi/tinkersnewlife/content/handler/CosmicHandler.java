package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
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

    // ==================== 近战 ====================
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;
        if (tool.getStats().getContainedStats().isEmpty()) return;

        int level = tool.getModifierLevel(COSMIC_ORDER_VOICE);
        if (level > 0) applyEffect(target, level);
    }

    // ==================== 弹射物 ====================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getProjectile().getOwner() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;
        if (tool.getStats().getContainedStats().isEmpty()) return;

        int level = tool.getModifierLevel(COSMIC_ORDER_VOICE);
        if (level > 0) applyEffect(target, level);
    }

    private static void applyEffect(LivingEntity target, int level) {
        int duration = DURATION_BASE + (level - 1) * DURATION_PER_LEVEL;
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 1));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 10));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 1));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
    }
}