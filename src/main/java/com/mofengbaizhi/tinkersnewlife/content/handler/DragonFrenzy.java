package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class DragonFrenzy {
    private static final String ENABLED = "TnlDragonFrenzy", RAGE = "TnlDragonRage", NEXT = "TnlDragonRageDecay";
    private static final UUID ATTACK = UUID.fromString("91031b17-9e36-446a-a22e-622c8512b6e4");
    private DragonFrenzy() {}

    public static void mark(LivingEntity dragon, boolean enabled) {
        var data = dragon.getPersistentData();
        data.remove(ENABLED);
        if (enabled) data.putBoolean(ENABLED, true);
        data.remove(RAGE);
        data.remove(NEXT);
        apply(dragon);
    }

    private static double bonus(LivingEntity dragon) {
        var data = dragon.getPersistentData();
        return data.getBoolean(ENABLED) ? Math.min(10, Math.max(0, data.getInt(RAGE))) * .05 : 0;
    }

    public static int meleeInterval(LivingEntity dragon, int baseTicks) {
        return Math.max(1, (int)Math.round(baseTicks / (1 + bonus(dragon))));
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    public static void hit(LivingDamageEvent event) {
        var dragon = event.getEntity();
        var attacker = event.getSource().getEntity();
        var data = dragon.getPersistentData();
        if (dragon.level().isClientSide || attacker == null || attacker == dragon || !(event.getAmount() > 0)
                || !data.getBoolean(ENABLED)) return;
        data.putInt(RAGE, Math.min(10, data.getInt(RAGE) + 1));
        data.putLong(NEXT, dragon.level().getGameTime() + 100);
        apply(dragon);
    }

    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event) {
        var dragon = event.getEntity();
        var data = dragon.getPersistentData();
        if (dragon.level().isClientSide || !data.getBoolean(ENABLED) || data.getInt(RAGE) <= 0) return;
        long now = dragon.level().getGameTime(), next = data.getLong(NEXT);
        if (now >= next) {
            long steps = (now - next) / 20 + 1;
            data.putInt(RAGE, (int)Math.max(0, data.getInt(RAGE) - steps));
            data.putLong(NEXT, now + 20);
        }
        apply(dragon);
    }

    private static void apply(LivingEntity dragon) {
        var attribute = dragon.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute == null) return;
        double bonus = bonus(dragon);
        var previous = attribute.getModifier(ATTACK);
        if (previous != null && previous.getAmount() == bonus) return;
        if (previous != null) attribute.removeModifier(ATTACK);
        if (bonus > 0) attribute.addTransientModifier(new AttributeModifier(ATTACK, "tinkersnewlife.dragon_frenzy", bonus,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
