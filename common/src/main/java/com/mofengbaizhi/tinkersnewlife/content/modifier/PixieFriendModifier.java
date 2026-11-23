package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class PixieFriendModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("friend_of_pixies"));
    private static final MobEffect[] EFFECTS = {MobEffects.DAMAGE_BOOST, MobEffects.JUMP, MobEffects.MOVEMENT_SPEED, MobEffects.LUCK, MobEffects.DIG_SPEED};
    private static final int[] CAPS = {8, 5, 6, 3, 5};

    public static int rank(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator() || !IntegrationLoader.isIceAndFire()) return 0;
        int result = 0;
        for (var slot : EquipmentSlot.values()) {
            var stack = player.getItemBySlot(slot);
            if (slot.getType() == EquipmentSlot.Type.HAND ? stack.is(TinkerTags.Items.SHIELDS) : stack.is(TinkerTags.Items.WORN_ARMOR)) {
                result += ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(stack), ID);
            }
        }
        return result;
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        int rank = rank(player);
        if (rank <= 0) return;
        int colors = jarMask(player.getOffhandItem());
        for (var stack : player.getInventory().items) { // 36 storage slots, including the hotbar
            colors |= jarMask(stack);
            if (colors == 31) break;
        }
        for (int i = 0; i < EFFECTS.length; i++) {
            if ((colors & (1 << i)) != 0) {
                int amplifier = Math.min(rank, CAPS[i]) - 1;
                var effect = player.getEffect(EFFECTS[i]);
                if (effect == null || effect.getAmplifier() < amplifier || effect.getAmplifier() == amplifier && effect.endsWithin(12)) {
                    player.addEffect(new MobEffectInstance(EFFECTS[i], 22, amplifier, true, false, true));
                }
            }
        }
    }

    private static int jarMask(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return 0;
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !key.getNamespace().equals("iceandfire")) return 0;
        return switch (key.getPath()) {
            case "pixie_jar_0" -> 1;
            case "pixie_jar_1" -> 2;
            case "pixie_jar_2" -> 4;
            case "pixie_jar_3" -> 8;
            case "pixie_jar_4" -> 16;
            default -> 0;
        };
    }

    @SubscribeEvent public static void flee(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob pixie) || pixie.level().isClientSide || pixie.tickCount % 5 != 0
                || pixie.isNoAi() || !PixieTreasureModifier.isPixie(pixie)) return;
        Player avoid = null;
        double nearest = 100;
        for (Player player : pixie.level().players()) {
            double distance = pixie.distanceToSqr(player);
            if (distance >= nearest || rank(player) <= 0) continue;
            avoid = player;
            nearest = distance;
        }
        if (avoid == null) return;
        Vec3 away = pixie.position().subtract(avoid.position());
        if (away.lengthSqr() < .001) away = new Vec3(1, 0, 0);
        Vec3 destination = pixie.position().add(away.normalize().scale(5));
        pixie.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 1.15);
    }
}
