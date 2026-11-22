package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import java.util.UUID;

@Mod.EventBusSubscriber(modid="tinkersnewlife")
public final class DarkDenizenModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(new ResourceLocation("tinkersnewlife", "dark_denizen"));
    private static final UUID KNOCKBACK = UUID.fromString("4c61dade-5c21-4543-a8ec-6f4fd39b7aae");
    private static final ToolStatsModifierHook SHIELD = (tool, entry, stats) -> {
        if (!IntegrationLoader.isIceAndFire() || !tool.hasTag(TinkerTags.Items.SHIELDS)) return;
        ToolStats.BLOCK_AMOUNT.multiply(stats, 1 + .08 * entry.getLevel());
        ToolStats.BLOCK_ANGLE.multiply(stats, 1 + .04 * entry.getLevel());
    };

    @Override protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(SHIELD, ModifierHooks.TOOL_STATS);
    }

    private static int rank(LivingEntity wearer, EquipmentSlot slot) {
        var stack = wearer.getItemBySlot(slot);
        boolean eligible = slot.getType() == EquipmentSlot.Type.HAND ? stack.is(TinkerTags.Items.SHIELDS)
                : stack.is(TinkerTags.Items.WORN_ARMOR) && LivingEntity.getEquipmentSlotForItem(stack) == slot;
        return eligible ? ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(stack), ID) : 0;
    }

    private static long setRank(LivingEntity wearer) {
        long levels = 0;
        for (var slot : EquipmentSlot.values()) levels += rank(wearer, slot);
        return levels;
    }

    @SubscribeEvent public static void resist(LivingDamageEvent event) {
        var wearer = event.getEntity();
        if (wearer.level().isClientSide || !IntegrationLoader.isIceAndFire() || !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return;
        double reduction = .05 * rank(wearer, EquipmentSlot.HEAD) + .15 * rank(wearer, EquipmentSlot.CHEST)
                + .10 * rank(wearer, EquipmentSlot.LEGS) + .05 * rank(wearer, EquipmentSlot.FEET);
        if (reduction > 0) event.setAmount(event.getAmount() * (float)(1 - Math.min(.70, reduction)));
    }

    @SubscribeEvent public static void damage(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || !IntegrationLoader.isIceAndFire()
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == event.getEntity()) return;
        long rank = setRank(attacker);
        if (rank >= 4) event.setAmount(event.getAmount() * (1 + .015f * rank));
    }

    @SubscribeEvent public static void sustain(LivingEvent.LivingTickEvent event) {
        var wearer = event.getEntity();
        if (wearer.level().isClientSide || !IntegrationLoader.isIceAndFire()) return;
        boolean active = wearer.isAlive() && !wearer.isSpectator() && setRank(wearer) >= 4;
        var attr = wearer.getAttribute(Attributes.ATTACK_KNOCKBACK);
        if (attr != null) {
            if (active && attr.getModifier(KNOCKBACK) == null) {
                attr.addTransientModifier(new AttributeModifier(KNOCKBACK, "Dark Denizen", 1, AttributeModifier.Operation.ADDITION));
            } else if (!active && attr.getModifier(KNOCKBACK) != null) attr.removeModifier(KNOCKBACK);
        }
        // Separate effect identity: unequipping the set never clears potion Regeneration.
        var regeneration = ModEffects.DARK_DENIZEN_REGENERATION.get();
        if (active && !wearer.hasEffect(regeneration)) {
            wearer.addEffect(new MobEffectInstance(regeneration, -1, 1, true, false, true));
        } else if (!active && wearer.hasEffect(regeneration)) wearer.removeEffect(regeneration);
    }
}
