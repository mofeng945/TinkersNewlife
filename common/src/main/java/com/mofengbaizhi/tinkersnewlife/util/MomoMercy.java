package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Normal hits are adjusted at LivingDamageEvent; true-pierce's health-delta budget must be adjusted before hurt. */
public final class MomoMercy {
    private MomoMercy() {}
    private record Hit(LivingEntity target, DamageSource source, boolean scaled) {}
    private static final ThreadLocal<Hit> CURRENT = new ThreadLocal<>();

    public static boolean boss(Entity actor) { return actor instanceof KtuluMomo momo && momo.tnl$isBossForm(); }
    public static float multiplier(ServerPlayer player) {
        return MomoMercyFormula.multiplier(ConscienceHandler.getAlignment(player), MomoFavor.get(player));
    }
    /** Preserve the original damage source (notably anonymous magic and elemental immunities). */
    public static boolean hurt(Entity caster, LivingEntity target, DamageSource source, float amount, boolean scaled) {
        if (!(target instanceof ServerPlayer) || !boss(caster)) return target.hurt(source, amount);
        Hit previous = CURRENT.get();
        CURRENT.set(new Hit(target, source, scaled));
        try { return target.hurt(source, amount); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    /** Returns true when this hit replaces the ordinary Heart incoming-damage multiplier. */
    public static boolean handle(net.minecraftforge.event.entity.living.LivingDamageEvent event, ServerPlayer victim) {
        Hit hit = CURRENT.get();
        boolean scoped = hit != null && hit.target == victim && hit.source == event.getSource();
        if (!scoped && !boss(event.getSource().getEntity())) return false;
        if (!scoped || !hit.scaled) event.setAmount(event.getAmount() * multiplier(victim));
        return true;
    }
}
