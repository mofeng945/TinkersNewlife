package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Entity-owned, monotonic encounter state. All unconfirmed tuning is named here. */
public final class MomoCombatState {
    public static final int DECISION_INTERVAL = 60;
    public static final int BOW_INTERVAL = 40;
    public static final int DODGE_COOLDOWN = 30;
    public static final float PROJECTILE_DODGE_CHANCE = 0.35F;
    public static final float FINAL_DODGE_CHANCE = 0.5F;
    public static final float RARE_BOW_CHANCE = 0.1F;
    public static final int STAGGER_TICKS = 30;
    public static final double ORBIT_RADIUS = 8;
    private int phase = 1;
    private int dodgeAfter;
    private boolean entryDomainPending;
    private boolean flightEntryPending;
    private boolean flightEntryRunning;
    public boolean flightEntryPending() { return flightEntryPending; }
    public boolean flightEntryRunning() { return flightEntryRunning; }
    public void beginFlightEntry() { flightEntryRunning = true; }
    public void finishFlightEntry() { flightEntryRunning = false; flightEntryPending = false; }

    public int phase() { return phase; }
    public boolean entryDomainPending() { return entryDomainPending; }
    public void consumeEntryDomain() { entryDomainPending = false; }
    public float arrowFraction() { return phase == 1 ? 0.04F : phase == 2 ? 0.08F : 0.15F; }
    public double groundMetersPerSecond() { return phase == 1 ? 1.8 : phase == 2 ? 4.5 : 5.6; }
    public double flightMetersPerSecond() { return 3.6; }
    public boolean canDodge(int tick) { return tick >= dodgeAfter; }
    public void didDodge(int tick) { dodgeAfter = tick + DODGE_COOLDOWN; }

    public void tick(KtuluMomo momo) {
        float ratio = momo.tnl$authority() / Math.max(1F, momo.getMaxHealth());
        int desired = ratio <= 0.5F ? 3 : ratio <= 0.75F ? 2 : 1;
        if (desired > phase) {
            // A large hit cannot skip the mandatory first domain entry.
            if (phase == 1) entryDomainPending = true;
            if (desired == 3) flightEntryPending = true;
            phase = desired;
        }
        var attack = momo.getAttribute(Attributes.ATTACK_DAMAGE);
        double damage = phase == 1 ? 10 : phase == 2 ? 15 : 35;
        if (attack != null && attack.getBaseValue() != damage) attack.setBaseValue(damage);
        // Vanilla walking calibration is approximate; exact m/s needs in-game measurement.
        var speed = momo.getAttribute(Attributes.MOVEMENT_SPEED);
        double attribute = groundMetersPerSecond() / 20.0;
        if (speed != null && speed.getBaseValue() != attribute) speed.setBaseValue(attribute);
    }

    public void save(CompoundTag tag) {
        tag.putInt("TNLCombatPhase", phase);
        tag.putBoolean("TNLCombatEntryDomain", entryDomainPending);
        tag.putBoolean("TNLCombatFlightEntry", flightEntryPending);
    }
    public void load(CompoundTag tag) {
        phase = Math.max(1, Math.min(3, tag.getInt("TNLCombatPhase")));
        entryDomainPending = tag.getBoolean("TNLCombatEntryDomain");
        flightEntryPending = tag.getBoolean("TNLCombatFlightEntry");
        flightEntryRunning = false;
    }
}
