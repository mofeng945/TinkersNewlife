package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.DreadsteelSlashEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DreadsteelHandler {

    private static final ModifierId DREADSTEEL = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "dreadsteel"));

    private static final int PIERCING_BASE = 3;
    private static final int WEAKNESS_DURATION = 5;
    private static final int BLINDNESS_DURATION = 4;
    private static final int WITHER_DURATION = 3;

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

        int level = tool.getModifierLevel(DREADSTEEL);
        if (level > 0) applyDreadsteelEffect(tool, player, target, level);
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

        int level = tool.getModifierLevel(DREADSTEEL);
        if (level > 0) applyDreadsteelEffect(tool, player, target, level);
    }

    private static void applyDreadsteelEffect(ToolStack tool, Player player, LivingEntity target, int level) {
        float weaponDamage = tool.getStats().get(ToolStats.ATTACK_DAMAGE);
        float damage = weaponDamage * (1 + level * 0.1f);
        int piercing = PIERCING_BASE + level;
        float width = 1.2f + level * 0.3f;
        float speed = 1.2f;

        DreadsteelSlashEntity slash = new DreadsteelSlashEntity(
                player.level(), player, damage, piercing, width, speed,
                WEAKNESS_DURATION * 20, BLINDNESS_DURATION * 20, WITHER_DURATION * 20);
        slash.setOwner(player);
        player.level().addFreshEntity(slash);

        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_DURATION * 20, 1));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_DURATION * 20, 0));
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION * 20, 1));
    }
}