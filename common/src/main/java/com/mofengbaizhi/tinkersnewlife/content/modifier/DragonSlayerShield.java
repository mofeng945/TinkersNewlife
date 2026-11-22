package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.logic.InteractionHandler;

import java.util.Set;

/** The shield contributes stats and opens the shield-block pipeline for directional breath. No second damage reduction here. */
public final class DragonSlayerShield {
    private DragonSlayerShield() {}

    /** Apply once when TCon rebuilds stats. InteractionHandler.onBlock reads BLOCK_AMOUNT straight from getStats(). */
    public static final ToolStatsModifierHook STATS = (tool, entry, builder) -> {
        if (tool.hasTag(TinkerTags.Items.SHIELDS) && IntegrationLoader.isIceAndFire()) {
            int level = entry.getLevel();
            ToolStats.BLOCK_AMOUNT.multiply(builder, 1.0 + 0.25 * level);
            ToolStats.BLOCK_ANGLE.multiply(builder, 1.0 + 0.10 * level);
        }
    };

    private static final Set<ResourceLocation> BREATH_TYPES = Set.of(iaf("dragon_fire"), iaf("dragon_ice"), iaf("dragon_lightning"));
    private static final Set<ResourceLocation> CHARGES = Set.of(iaf("fire_dragon_charge"), iaf("ice_dragon_charge"), iaf("lightning_dragon_charge"));
    private static final Set<ResourceLocation> DRAGONS = Set.of(iaf("fire_dragon"), iaf("ice_dragon"), iaf("lightning_dragon"));
    private static final TagKey<DamageType> EXTRA_BREATH = tag("tinkersnewlife:dragon_breath");
    private static final TagKey<DamageType> EXTRA_VOID = tag("tinkersnewlife:is_void");
    private static final TagKey<DamageType> FORGE_VOID = tag("forge:is_void");
    private static final TagKey<DamageType> FORGE_MAGIC = tag("forge:is_magic");
    private static final TagKey<DamageType> NEO_MAGIC = tag("neoforge:is_magic"); // Iron's Spells uses this tag even in the shipped Forge jar.
    private static final TagKey<DamageType> MC_MAGIC = tag("minecraft:is_magic");
    private static final TagKey<DamageType> C_MAGIC = tag("c:is_magic");

    private static ResourceLocation iaf(String path) { return new ResourceLocation("iceandfire", path); }
    private static TagKey<DamageType> tag(String id) { return TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(id)); }
    private static ResourceLocation type(DamageSource source) { return source.typeHolder().unwrapKey().map(ResourceKey::location).orElse(null); }
    private static ResourceLocation type(Entity entity) { return ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()); }

    public static boolean isDragonBreath(DamageSource source) {
        if (source == null) return false;
        if (source.is(DamageTypes.DRAGON_BREATH) || source.is(EXTRA_BREATH)) return true;
        ResourceLocation key = type(source);
        if (key != null && BREATH_TYPES.contains(key)) return true;
        Entity direct = source.getDirectEntity();
        if (direct == null) return false;
        if (direct instanceof DragonFireball) return true;
        ResourceLocation projectile = type(direct);
        if (projectile != null && CHARGES.contains(projectile)) return true;
        if (direct instanceof AreaEffectCloud cloud) {
            // Vanilla breath clouds apply instantaneous HARM as indirect_magic, not dragon_breath.
            if (cloud.getParticle().getType() == ParticleTypes.DRAGON_BREATH) return true;
            Entity owner = cloud.getOwner();
            if (owner == null) owner = source.getEntity();
            if (owner instanceof EnderDragon) return true;
            ResourceLocation ownerType = owner == null ? null : type(owner);
            return ownerType != null && DRAGONS.contains(ownerType);
        }
        return false; // A bite or tail swipe is not breath merely because its attacker is a dragon.
    }

    static boolean isVoid(DamageSource source) {
        if (source == null) return false;
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(EXTRA_VOID) || source.is(FORGE_VOID)) return true;
        ResourceLocation key = type(source);
        if (key == null) return false;
        // Match damage-type tokens, never the attacker's name or arbitrary "end" substrings.
        String path = key.getPath();
        return path.equals("void") || path.startsWith("void_") || path.endsWith("_void") || path.contains("_void_")
                || path.equals("voided") || (key.getNamespace().equals("goety") && path.equals("doom"));
    }

    /** Armor passive excludes void unconditionally. Breath clouds are the explicit exception to magic exclusion. */
    public static boolean isExcluded(DamageSource source) {
        if (source == null || isVoid(source)) return true;
        return !isDragonBreath(source) && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(FORGE_MAGIC) || source.is(NEO_MAGIC)
                || source.is(MC_MAGIC) || source.is(C_MAGIC));
    }

    public static boolean canBlockBypassedBreath(LivingEntity defender, DamageSource source) {
        if (!source.is(DamageTypeTags.BYPASSES_SHIELD) || !defender.isBlocking()) return false;
        ItemStack held = defender.getUseItem();
        if (!held.is(TinkerTags.Items.SHIELDS) || !IntegrationLoader.isIceAndFire()) return false;
        var tool = ToolHelper.getToolStack(held);
        return ToolHelper.getActiveModifierLevel(tool, DragonSlayerModifier.ID) > 0 && !isVoid(source) && isDragonBreath(source)
                && InteractionHandler.canBlock(defender, source.getSourcePosition(), tool);
    }
}
