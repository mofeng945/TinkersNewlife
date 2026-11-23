package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.build.CraftCountModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.item.ModifiableArrowItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.entity.ModifiableArrow;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class LifeCurseModifier extends NoLevelsModifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("life_curse"));

    @Override protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook((ToolStatsModifierHook) (tool, modifier, stats) -> {
            if (tool.getItem() instanceof ModifiableArrowItem && IntegrationLoader.isIceAndFire()) {
                ToolStats.PROJECTILE_DAMAGE.add(stats, 2);
            }
        }, ModifierHooks.TOOL_STATS);
        builder.addHook((CraftCountModifierHook) (tool, modifier, count) ->
                tool.getItem() instanceof ModifiableArrowItem && IntegrationLoader.isIceAndFire() ? count * 2 : count,
                ModifierHooks.CRAFT_COUNT);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        var victim = event.getEntity();
        var source = event.getSource();
        if (victim.level().isClientSide || !IntegrationLoader.isIceAndFire()
                || !(event.getAmount() > 0) || !Float.isFinite(event.getAmount())
                || !(source.getDirectEntity() instanceof Projectile projectile)
                || !(source.getEntity() instanceof LivingEntity shooter) || shooter == victim) return;
        boolean hasTrait = EntityModifierCapability.getOrEmpty(projectile).getLevel(ID) > 0;
        if (!hasTrait && projectile instanceof ModifiableArrow arrow && !arrow.getDisplayTool().isEmpty()) {
            hasTrait = ToolStack.from(arrow.getDisplayTool()).getModifierLevel(ID) > 0;
        }
        if (!hasTrait) return;
        // Forge posts LivingDamage after mitigation and absorption, immediately before reducing health.
        float healthLost = Math.min(victim.getHealth(), event.getAmount());
        if (healthLost <= 0) return;
        victim.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0), shooter);
        if (shooter.isAlive()) shooter.heal(healthLost);
    }
}
