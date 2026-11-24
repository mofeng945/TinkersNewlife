package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.helper.ModifierLootingHandler;

/** Replace the primary weapon hit, never synthesize a second attack or write into an unrelated projectile. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class SoulStrikeModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("soul_strike"));
    public static final ResourceKey<DamageType> DREAD = ResourceKey.create(Registries.DAMAGE_TYPE, TinkersNewlife.prefix("dread"));

    private static final class DreadHit extends DamageSource {
        private final int rank;
        DreadHit(Holder<DamageType> type, DamageSource original, int rank) {
            super(type, original.getDirectEntity(), original.getEntity(), original.sourcePositionRaw());
            this.rank = rank;
        }
        @Override public boolean is(TagKey<DamageType> tag) {
            return tag.equals(DamageTypeTags.BYPASSES_ARMOR) || tag.equals(DamageTypeTags.BYPASSES_SHIELD)
                    || tag.equals(DamageTypeTags.BYPASSES_COOLDOWN) || tag.equals(DamageTypeTags.BYPASSES_RESISTANCE)
                    || tag.equals(DamageTypeTags.BYPASSES_ENCHANTMENTS) || tag.equals(DamageTypeTags.BYPASSES_EFFECTS)
                    || (tag.equals(DamageTypeTags.IS_PROJECTILE) && getDirectEntity() instanceof Projectile) || super.is(tag);
        }
    }

    public static DamageSource convert(LivingEntity target, DamageSource original) {
        if (!(target.level() instanceof ServerLevel level) || !(original.getEntity() instanceof LivingEntity attacker)
                || target == attacker || original.is(DREAD) || !IntegrationLoader.isIceAndFire()) return original;
        int rank;
        if (original.getDirectEntity() instanceof Projectile projectile && !original.is(DamageTypeTags.IS_EXPLOSION)) {
            rank = EntityModifierCapability.getOrEmpty(projectile).getLevel(ID);
        } else if (original.getDirectEntity() == attacker && (original.is(DamageTypes.PLAYER_ATTACK)
                || original.is(DamageTypes.MOB_ATTACK) || original.is(DamageTypes.MOB_ATTACK_NO_AGGRO))) {
            var weapon = attacker.getItemBySlot(ModifierLootingHandler.getLootingSlot(attacker));
            rank = ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(weapon), ID);
        } else return original;
        if (rank <= 0) return original;
        return new DreadHit(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DREAD), original, rank);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void settle(LivingHurtEvent event) {
        if (!(event.getSource() instanceof DreadHit hit)) return;
        var target = event.getEntity();
        float amount = event.getAmount();
        // Cancel only the *inner damage calculation*. The outer hurt call still handles player attribution,
        // incoming-attack hooks, animation, totems, die(source), and projectile success exactly once.
        event.setCanceled(true);
        if (!(amount > 0) || !Float.isFinite(amount) || !target.isAlive()) return;
        if (!target.getRootVehicle().onGround() && !target.isInFluidType()) {
            amount = (float)Math.min(Float.MAX_VALUE, amount * (1.0 + 0.5 * hit.rank));
        }
        if (SoulWardModifier.tryBlockDirect(target, hit, amount)) return;
        float before = target.getHealth();
        target.getCombatTracker().recordDamage(hit, amount);
        target.setHealth(Math.max(0f, before - amount));
        if (target.getHealth() < before) {
            SoulWardModifier.recordHit(target);
            target.gameEvent(GameEvent.ENTITY_DAMAGE);
        }
    }
}
