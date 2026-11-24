package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;

/** Personal post-hit window, independent of vanilla's invulnerableTime and attack cooldown. */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class SoulWardModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("soul_ward"));
    private static final String UNTIL = "tnlSoulWardUntil";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static int rank(LivingEntity entity) {
        int total = 0;
        for (var slot : EquipmentSlot.values()) {
            var stack = entity.getItemBySlot(slot);
            boolean equipped = slot.getType() == EquipmentSlot.Type.HAND ? stack.is(TinkerTags.Items.SHIELDS)
                    : stack.is(TinkerTags.Items.WORN_ARMOR) && LivingEntity.getEquipmentSlotForItem(stack) == slot;
            if (equipped) total += ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(stack), ID);
        }
        return total;
    }
    private static boolean window(LivingEntity entity) {
        return !entity.level().isClientSide && entity.isAlive() && !entity.isSpectator()
                && entity.getPersistentData().getLong(UNTIL) > entity.level().getGameTime() && IntegrationLoader.isIceAndFire();
    }
    public static boolean isProtected(LivingEntity entity) { return window(entity) && rank(entity) > 0; }

    public static void recordHit(LivingEntity entity) {
        if (entity.level().isClientSide || !entity.isAlive() || entity.isSpectator() || !IntegrationLoader.isIceAndFire()) return;
        int total = rank(entity);
        if (total > 0 && !window(entity)) entity.getPersistentData().putLong(UNTIL, entity.level().getGameTime() + Math.min(30, 10 + 4L * total));
    }

    public static boolean tryBlockDirect(LivingEntity entity, DamageSource source, float amount) {
        if (!(amount > 0) || !Float.isFinite(amount) || !window(entity)) return false;
        int total = rank(entity);
        if (total <= 0) return false;
        // Same per-piece cost as vanilla Inventory.hurtArmor, multiplied AFTER its minimum and rounding.
        int cost = (int)Math.min(Integer.MAX_VALUE, (long)Math.max(1, (int)(amount / 4f)) * total);
        for (var slot : ARMOR) {
            var stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem)
                    || source.is(DamageTypeTags.IS_FIRE) && stack.getItem().isFireResistant()) continue;
            stack.hurtAndBreak(cost, entity, e -> e.broadcastBreakEvent(slot));
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void protect(LivingAttackEvent event) {
        if (tryBlockDirect(event.getEntity(), event.getSource(), event.getAmount())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void hurt(LivingDamageEvent event) {
        if (event.getAmount() > 0 && !com.mofengbaizhi.tinkersnewlife.util.TruePierce.applyingSlicesTo(event.getEntity())) {
            recordHit(event.getEntity());
        }
    }
}
