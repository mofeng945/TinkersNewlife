# Tinker's Newlife 1.0.3

## Fixed

### The whip now sweeps, instead of firing the rope at the crosshair

The first version drove the whip's root straight out towards the crosshair, which read as "shooting a
rope" rather than lashing with a whip. The left click now drives a **sweep**: the drive direction
turns through the crosshair from one side to the other, so the rope is flung through a wide arc and
the tip whips around at high speed - which is what makes it a whip.

* The sweep spans 136 degrees (68 degrees either side), starting 30% into the swing and covering 34%
  of it, with the stroke accelerated (`pow(t, 1.55)`) so the crack is snappy.
* One lash lasts 10 ticks (0.5 s), and consecutive lashes alternate left-to-right / right-to-left.
* The handle anchor follows the same formula the reference mod uses (feet + eye height - 0.58, 0.34 to
  the right hand side, 0.10 forward), so the arc looks like it comes out of your hand.
* Damage is unchanged in principle - sections that sweep through a target fast enough deal damage,
  scaled by section speed with 100% -> 50% -> 25% falloff per target in one lash - but tip speeds are
  now much higher, so the sweep does hit the damage cap. Turn `WhipLashEntity.SPEED_UNIT` up if it
  feels too strong.

## Notes

### Credits

The sweep behaviour is ported from **Better Whips** by my2167592261-cell, which is licensed under the
**MIT License** - the full licence text and a list of exactly what was taken (sweep angle, progress
window, attack duration, follow delay, hand-base formula, stroke acceleration curve, rope parameters
and the alternating swing direction) is in [`LICENSES/BetterWhips-MIT.txt`](LICENSES/BetterWhips-MIT.txt).

Everything is still this mod's own implementation - the rope solver, the contact sweep, the damage
and the renderer are unchanged from 1.0.2, only the drive path was replaced.
