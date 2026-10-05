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
## Changed (2026-10-05, seventh pass - version unchanged)

* The perfect block window is now **0.5 seconds** instead of 1, in both directions: a hit within half a
  second of raising the guard, or pressing right click within half a second of being hit.
* A perfect block now **drops the guard immediately** (`stopUsingItem`), and you must **release right
  click before you can raise it again**. While the button is held the client keeps retrying the use
  (every 4 ticks, faster than the 8 tick lock), so holding it simply keeps the guard down; releasing it
  lets the lock expire after 0.4 seconds so the next press blocks again.
* While the lock is up you are not counted as blocking at all, so a spent guard cannot give you the
  40 percent reduction either.
## Changed (2026-10-05, eighth pass - version unchanged)

* The guard now shows the **shield** pose instead of the spear pose. The whip uses `UseAnim.BLOCK`
  again, and because vanilla treats any BLOCK animation as a shield (full damage negation, which would
  make the 40 percent rule dead code) the whip's handler now **cancels the vanilla shield settlement**
  through `ShieldBlockEvent` - so the shield neither negates the hit nor loses durability, and the
  damage goes on to be settled by the whip's own rules (60 percent on a normal block, nothing at all on
  a perfect block).
## Changed (2026-10-05, ninth pass - version unchanged)

* Blocking a hit now consumes durability again, using the shield formula (`1 + floor(damage)`), on both a
  perfect block and a normal one. Cancelling the vanilla shield settlement (which is what lets the guard
  use the shield pose without vanilla negating everything) also removed vanilla's durability cost, so the
  whip now pays it itself through Tinkers' own tool damage path - the tool damage animation and the
  broken state behave as usual.
* Swinging the whip already cost 1 durability per lash and is unchanged; the counter-attack from a
  perfect block does not charge a second time, since it is part of the same block.
## Fixed (2026-10-05, tenth pass - version unchanged)

* The broken-state model no longer points at the removed `_broken` part textures (the hand-drawn part art
  replaced them and the broken variants were deleted). It reuses the normal part textures instead, so a
  whip at zero durability shows the whip rather than missing textures. The durability bar and the tool
  state still tell you it is broken.
## Added (2026-10-05, eleventh pass - version unchanged)

### Whip marks: every lash makes the target weaker

Each time the whip connects with a creature it gains a stack of **Whip Weaken**: -10 percent movement
speed and -10 percent attack damage per stack, up to **8 stacks (-80 percent)**. The effect lasts 10
seconds and is refreshed by every new hit, so a target that keeps being lashed gets slower and hits
softer, and one you leave alone recovers.

It is a real status effect (`tinkersnewlife:whip_weaken`) built on attribute modifiers, so it applies to
AI mobs, players and summons alike and is visible both in the HUD and through `/attribute`. It is applied
by the lash, and by the whip counter-attack from a perfect block. Icons and names are in the usual places
(new 16x16 icon and `effect.tinkersnewlife.whip_weaken` in both languages).
## Changed (2026-10-05, twelfth pass - version unchanged)

* The whip has no mining speed at all now: `tconstruct:mining_speed` is 0 in the tool definition both as a
  base value and as a 0 multiplier, so the mining speed the large plate would otherwise contribute from
  its material is multiplied away as well. It cannot mine.
* Nothing had to be removed from the mining tags - the whip was never in `tconstruct:modifiable/harvest`
  (nor `small`, `aoe` or `interactable/left`). It only carries the weapon tags (durability, weapon,
  multipart, bonus_slots, interactable/right, melee/primary, melee/weapon).
## Changed (2026-10-05, thirteenth pass - version unchanged)

* The guard now has a cooldown, in the same style as the rapier's backstep: **2 seconds after a perfect
  block**, and **1 second when you release** the button any other way. It uses the vanilla item cooldown,
  so the sweep shows on the HUD and the client will not even start a new guard while it runs (the server
  checks it as well).
* Detail worth knowing: a perfect block ends the guard internally, which fires the same release callback -
  without care that would have overwritten the 2 second cooldown with the 1 second one. A short window
  check now keeps the longer cooldown intact.
## Added (2026-10-05, fourteenth pass - version unchanged)

* First person now raises the whip toward your view while you block, instead of only the third person
  shield pose. It hooks the hand render event, restores the vanilla hand translation first (that event
  fires before it, and skipping it is what makes held items fly off screen), then pulls the whip toward
  the centre of the screen, up and forward, with a slight tilt. It eases in over a quarter of a second and
  eases back out over the first quarter of the cooldown.
* Broken-state textures exist again: `handle_broken`, `plate_broken` and `bowstring_broken` are derived
  from your new part art (60 percent darker, two cracked pixels mid-span, a small chip off each end).
  Only pixels that already exist are touched, so the silhouette never gains stray pixels. The broken model
  points back at them.
## Added (2026-10-05, fifteenth pass - version unchanged)

### Supervisor, a whip-only modifier

The whip can now be given **Supervisor** (Dragon's Breath + Golden Apple + Nicholas' Blessing). Only the
whip can take it - the recipe is limited to a tag that contains nothing but the whip.

With it the lash deals no damage at all and does managing instead:

* Lashing anything that is yours (any mob whose owner is you - pets, mounts and the like - plus Goety
  servants, your ring-of-one-mind partner, and their pets as well) stacks **Strength and Speed** on it,
  one level per lash up to **level V**, lasting 20 seconds and refreshed by every lash.
* Lashing a **villager** has a 5 percent chance to restock it on the spot, for **half** the usual amount,
  at most **3 times per villager per day** (counted on the villager itself and independent of the vanilla
  two-restocks-a-day rule).
* No whip marks are applied while Supervisor is on - the point is to improve those targets, not weaken
  them.