# Tinker's Newlife 1.0.4

## Fixed

### The whip rests coiled and is flung open by the swing

Two things were making the whip read as "a rope fired at the crosshair", and both are now fixed:

* **The resting state was already a straight rope.** On spawn the 25 points were laid out along the
  crosshair, i.e. an 8.6-block rope extended in front of you before anything happened - so the very
  first frame already looked like a rope that had been shot out. The rope now starts (and returns to)
  a tight **coil in the hand**: 8 turns of a ~0.19 block helix, pulled in with the same coil gains the
  reference mod uses.
* **The hand barely moved.** The root was only ever driven 1.15 blocks forward, so even a 136 degree
  sweep rotated the rope around a point that hardly moved. The hand anchor now traces the arm's real
  arc instead: it rises during the windup, then snaps down and across during the stroke (accelerated
  with `pow(t, 1.55)`, exactly like the reference) and recovers afterwards. The coil is released at
  the start of the stroke and re-coiled during the recovery, so what you see is: whip coiled in hand
  -> flung open across your front -> settling back into a coil.

The lash also lives a bit longer (18 ticks for a swing, 26 for a slam) so the recovery and the
re-coiling are actually visible.

## Notes

Still ported from **Better Whips** (MIT, my2167592261-cell) - the sweep window, the stroke
acceleration curve, the arm pose angles (pitch 1.52 -> -0.58), the hand-base formula and the coil
constants; see `LICENSES/BetterWhips-MIT.txt`. The rope solver, contacts, damage and renderer remain
this mod's own.
