package com.mofengbaizhi.tinkersnewlife.content.skill;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;

/**
 * TNL1173 the base of the skill system, modelled on the Goety apostle.
 *
 * <p>What the apostle does (decompiled, {@code Apostle} 2388 lines):
 * <ul>
 *   <li>every spell is its own {@code Goal} registered with
 *       {@code goalSelector.addGoal(3, new XxxSpellGoal(this))};</li>
 *   <li>every spell owns a cooldown FIELD ({@code coolDown}, {@code tornadoCoolDown},
 *       {@code infernoCoolDown}, {@code monolithCoolDown}, {@code damnedCoolDown}) decremented in
 *       {@code customServerAiStep};</li>
 *   <li>casting state lives on the entity as a synced spell id plus a tick counter
 *       ({@code SpellCastingCultist.SPELL} + {@code spellTicks});</li>
 *   <li>a phase transition is itself a goal ({@code SecondPhaseIndicator}) whose only job is to set a
 *       flag that every other goal checks -- that is how it stands still WITHOUT clearing the AI.</li>
 * </ul>
 *
 * <p>So a skill here is: <b>cooldown on the entity + isBusy gate + pose from the synced spell channel</b>.
 * Never {@code setNoAi}, never clearing velocity from a server tick event, never a marker cooldown on
 * players. See {@code docs/墨默技能开发准则.md}.
 */
public abstract class MomoSpellGoal extends net.minecraft.world.entity.ai.goal.Goal {

    protected final KtuluMomo momo;

    /** 1 blink, 2 astral resonance, 3 discordant chord */
    protected final int spellId;
    /** own cooldown, in ticks, counted from the moment the cast ends */
    private final int cooldownTicks;
    /** ticks from the start of the wind up to the end of the recovery */
    private final int totalTicks;

    protected MomoSpellGoal(KtuluMomo momo, int spellId, int cooldownTicks, int totalTicks) {
        this.momo = momo;
        this.spellId = spellId;
        this.cooldownTicks = cooldownTicks;
        this.totalTicks = totalTicks;
        // TNL1198 CRITICAL: do NOT claim MOVE/LOOK. The melee goal (priority 1) always runs while the
        // boss has a target and it owns both flags, so a spell that also claimed them could never
        // start -- the boss would look like it never casts. Goety's apostle does not take flags either;
        // it roots itself through the casting state. We root the same way: the goal zeroes the
        // movement and stops the navigation every tick, and the hurt guard cancels its attacks.
        // (no setFlags here on purpose)
    }

    /** how far the cast has progressed, 0..1, from the synced counter */
    protected float progress() {
        int left = this.momo.tnl$castTicks();
        if (this.totalTicks <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, 1.0F - (float) left / (float) this.totalTicks));
    }

    /** true when this momo may start this spell right now */
    protected boolean ready() {
        // TNL1196 GoalSelector runs on the CLIENT as well (Mob.aiStep), and every skill casts the level
        // to ServerLevel and spawns particles on it. Without this guard the whole thing would throw a
        // ClassCastException on every client tick. Vanilla's own casting goal checks the same thing.
        if (this.momo.level().isClientSide) {
            return false;
        }
        return !this.momo.tnl$isBusy()
                && this.cooldownLeft() <= 0
                && this.momo.isAlive()
                && this.momo.tnl$mayCastAgainSoon(this.spellId);   // TNL1178 rotation
    }

    /** ticks of cooldown still to go; each skill keeps its own field on the entity */
    protected abstract int cooldownLeft();

    /** called once when the wind up begins */
    protected abstract void onStart();

    /** called every tick of the cast; return true to end it */
    protected abstract boolean onTick();

    /** called once when the cast ends for any reason */
    protected abstract void onStop();

    /** subclasses decide their own trigger conditions */
    @Override
    public abstract boolean canUse();

    @Override
    public void start() {
        this.momo.tnl$setSpell(this.spellId, this.totalTicks);
        this.onStart();
    }

    @Override
    public boolean canContinueToUse() {
        return this.momo.isAlive();
    }

    @Override
    public void tick() {
        if (this.onTick()) {
            this.stop();
        }
    }

    @Override
    public void stop() {
        if (this.momo.tnl$spell() == this.spellId) {
            this.momo.tnl$setSpell(0, 0);
        }
        this.momo.tnl$markSpellCast(this.spellId);   // TNL1178 remember it for the rotation
        this.onStop();
    }

    /** the cooldown to apply when the cast ends */
    protected int cooldownTicks() {
        return this.cooldownTicks;
    }
}
