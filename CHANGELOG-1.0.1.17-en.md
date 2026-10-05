# Tinker's Newlife 1.0.1.17

## Changed

### The whip is now a faithful port of the reference implementation

The whole lash pipeline was read out of the reference mod (Better Whips, MIT, my2167592261-cell) before
any code was written this time, and the whip was rewritten against it. Previous attempts failed because
they drove an invented model instead of the real one.

**What the whip actually is:** a 57 point rope whose root is pinned to a hand that really swings.

* **Rope**: 57 points / 56 segments, segment lengths taken from the reference's authored model
  (0.19 -> 0.44 blocks, total ~8.49 blocks), per-segment collider radii 0.059 -> 0.018 (tip knob 0.0388).
* **Mass taper**: `0.065 + 0.935 * (1 - taper)^2` - the tip is 1/15 of the root, which is why a whip tip
  cracks.
* **Per-particle speed caps**: `82 + 108 * taper^1.65` blocks per second (root 82, tip 190).
* **Constraints**: XPBD length (compliance 3e-8) plus bend/anti-fold (1.6e-7), maximum stretch 1.003,
  a stiff handle zone (first 20 segments at 3.4x, with continuity checkpoints) and self collision (0.055).
* **Adaptive sub steps**: 6 / 8 / 12 / 18 / 24 chosen from the maximum particle speed.

**Left click** runs the reference's arm timeline exactly: `windup = clamp(min(3, period-2),1,3)` and
`stroke = clamp(period-windup-1,1,4)` (period is the weapon's attack cooldown), so the progress goes
0 -> 0.30 during the windup and 0.30 -> 1.0 during the stroke, with `pow(t, 1.55)` acceleration on the
stroke. The arm anchor is `precisionHandAnchor`: it raises 0.68 blocks, then drives 0.86 forward,
0.08 below and 0.10 across. **When the drive ends the root returns to the hand and the rope flies free**
for another 25 ticks - that flying section is what throws the whip, and the damage window
(`windup+1 .. windup+10`) covers it.

**Right click** charges for up to 60 ticks with the reference's spin (centrifugal plus tangential forces,
hand orbit radius 0.18, tip visual scale up to 10x), then releases into a 14 tick pendulum slam with the
reference's release forces. When the tip touches a block or an entity, a shockwave is released.

**Damage** follows the reference exactly: `floor(sectionSpeed / 10) * 0.2`, halved for every target
already hit by that lash (`base / 2^prior`, zero after 30), with the whip crack sound.

## Notes

* Ported from Better Whips (MIT) - see `LICENSES/BetterWhips-MIT.txt`. The rope, the anchors, the three
  drives, the constraint family and the damage formula are ports; the entity, the item, the renderer and
  the contact loop are this mod's own.
* Known simplifications: block collision is per-point depenetration (the reference uses swept capsules
  per segment), the idle coil flourish is not implemented, and the arm is not posed by a renderer mixin.
## Tweaked (2026-10-05, second pass - version unchanged)

* **Left click no longer deals a direct melee hit at all.** Hitting something with the whip is purely the
  simulated lash now: `onLeftClickEntity` cancels the vanilla melee damage, and the damage only comes
  from rope sections sweeping through a target fast enough. Attacking an entity still starts a lash (the
  swing packet does not always arrive for entity hits), and a per-tick gate makes sure one swing can only
  ever start one lash.
* **The lash damage now scales with the tool's attack damage.** The reference formula
  `floor(speed / 10) * 0.2` is multiplied by `panel / 3.5` (3.5 being this whip's base attack damage), so
  a stronger whip cracks harder and a weaker one less. At the base panel nothing changes.
* **The lash speed now scales with the tool's attack speed.** The windup and stroke lengths are
  `round(3 * scale)` and `round(4 * scale)` where `scale = attackPeriodTicks / 13` (13 ticks being this
  whip's base attack speed of 1.6), clamped to 0.5x - 2.5x. At the base attack speed that is exactly the
  reference's 3 + 4 tick lash; faster whips snap in about 4 ticks, slower ones wind up for up to 14.
* The slam shockwave damage uses the same panel scaling.
## Fixed (2026-10-05, third pass - version unchanged)

### The whip reaches full length again

The previous pass tried to express "swing speed follows attack speed" by stretching the drive itself:
`round(3 * scale)` windup and `round(4 * scale)` stroke ticks with `scale = attackPeriodTicks / 13`.
That was the wrong axis. The drive is what throws the rope, and a slower drive means a slower hand over
the same 1.1 block arc - so a whip whose attack speed is below the base (attack speed is decided by the
materials you build it from) ended up winding up for 5 to 10 ticks and no longer flung the rope out.

The reference's own formula is back, and it already gives you attack-speed scaling without hurting reach:
`windup = clamp(min(3, period - 2), 1, 3)` and `stroke = clamp(period - windup - 1, 1, 4)`. A fast whip
gets a shorter windup and stroke (snappier), while the stroke is **capped at 4 ticks** so the hand always
stays fast and the rope always flies. A slow whip keeps the same fast lash and simply has a longer
cooldown - fewer lashes per second.

The attack-damage scaling of the damage stays as it is.
## Changed (2026-10-05, fifth pass - version unchanged)

### The drive is fixed at 3 + 4 ticks, the attack speed only sets the cooldown, and the reach is longer

The drive is the 7 ticks in which the hand is scripted through its arc; it is the force that throws the
rope. Making it longer slows the hand down over the same arc (less reach and less damage) and making it
too short means the wave has not reached the tip before the hand stops. The reference never hit either
case because its whip has a fixed attack speed, so its 3 + 4 tick drive was always the same. This whip's
attack speed varies with the materials, so the drive is now simply fixed at the reference's 3 + 4 ticks
and the attack speed only decides how often you may lash:

* `startLash` keeps a per-player next-allowed tick of `now + attackPeriodTicks` and resets the attack
  strength ticker, so the whip cooldown and the vanilla attack indicator agree.
* Reach increased: rope rest lengths are scaled by 1.20 (total ~10.2 blocks instead of 8.49), the hand
  arc is bigger (forward 0.86 -> 1.20, vertical 0.68 -> 0.85, lateral 0.16 -> 0.22), the guide pulls
  harder (640 -> 820, tip gain 1.18 -> 1.25), the rope flies free for 32 ticks instead of 25, and the
  left-click damage window is 14 ticks instead of 10.

Also included here (built earlier but not yet shipped): the lash timeline now runs off an internal age
counter that only advances once the owner is resolved, so the client no longer swallows the opening
ticks of a lash.
## Changed (2026-10-05, sixth pass - version unchanged)

### Right click now retracts the whip and raises a guard

Right click no longer charges a slam. It retracts the whip body if a lash is still out - the rope is
pulled back into your hand over about eight ticks and then the lash entity retires - and it starts
blocking for as long as you hold the button.

Blocking works on two windows:

* **Perfect block** - a hit landing within one second after you raise the guard (or within one second
  *before* you raised it, which is refunded on the spot) cancels the damage completely, snaps the whip
  out, and throws the entire damage back at the attacker.
* **Normal block** - any other hit you block is only reduced by 40 percent.

Implementation note: the guard deliberately does **not** use `UseAnim.BLOCK`. Vanilla's
`LivingEntity#isBlocking()` only looks at the use animation, so any BLOCK item is treated as a shield
and negates damage entirely - which would make the 40 percent rule dead code. The whip uses
`UseAnim.SPEAR` (a raised-weapon guard pose) and the whip's own `WhipBlockHandler` owns the damage maths.

The old charge/slam code is kept in the entity and physics (it is simply no longer triggered by right
click) so it can be wired to another input later if you want it back.