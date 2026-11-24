package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.events.CrystalCatalystCombat;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.build.ModifierTraitHook;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.Set;

public final class CrystalCatalystModifier extends Modifier implements ToolStatsModifierHook, ModifierTraitHook {
    private static ModifierId id(String name) { return new ModifierId(TinkersNewlife.prefix(name)); }
    // The complete trait families from the source materials' definitions, including the shared Dovahkiin trait.
    private static final Set<ModifierId> BOOSTED = Set.of(id("dragonsteel_fire"), id("dragonsteel_ice"),
            id("dragonsteel_lightning"), id("soul_body"), id("dreadsteel"), id("dovahkiin"),
            id("dragonsteel_fire_armor"), id("dragonsteel_ice_armor"), id("dragonsteel_lightning_armor"), id("dreadsteel_armor"));

    @Override protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(this, ModifierHooks.TOOL_STATS, ModifierHooks.MODIFIER_TRAITS);
        builder.addHook(CrystalCatalystCombat.MELEE, ModifierHooks.MELEE_HIT);
    }

    @Override public void addToolStats(IToolContext tool, ModifierEntry entry, ModifierStatsBuilder stats) {
        if (!IntegrationLoader.isIceAndFire()) return;
        if (ToolStats.DURABILITY.supports(tool.getItem())) ToolStats.DURABILITY.multiply(stats, 1 + .20 * entry.getLevel());
        if (ToolStats.MINING_SPEED.supports(tool.getItem())) ToolStats.MINING_SPEED.multiply(stats, 1 + .05 * entry.getLevel());
    }

    @Override public void addTraits(IToolContext tool, ModifierEntry entry, TraitBuilder builder, boolean firstEncounter) {
        if (!firstEncounter || !IntegrationLoader.isIceAndFire()) return;
        // TCon supplies the pre-expansion snapshot here: rebuilding never compounds our own bonus.
        for (var id : BOOSTED) if (tool.getModifierLevel(id) > 0) builder.add(id, entry.getLevel());
    }
}
