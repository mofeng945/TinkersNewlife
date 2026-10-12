# Tinker's Newlife 1.0.6

## Fixed

### The lash now actually throws the whip - the real drive algorithm is ported

The lash was previously my own model: a distance-constrained rope (Verlet/PBD) with only the root point
driven. In that model the hand moves about a block, so an 8.6 block rope simply hangs where it is - the
last build only looked like a hanging whip, which is exactly what you saw.

The reference mod does something completely different, and it is now ported line for line from its
`physics/TrainerStylePrecisionGuide`:

* The rope is a **fan of directions around the player's eye**, not a chain of distance constraints. Each
  point stores a direction plus its distance from the eye (`rootRadius + accumulated segment length`),
  so the shape is inherently inextensible.
* The root direction is the crosshair swept through **68 degrees either way**.
* Each point's direction follows the direction of the point before it with an exponential blend whose
  time constant is `0.24 s / (points - 1)` - a **delayed wave that travels from the hand to the tip**.
  That wave is what a whip actually is.
* Each point is accelerated towards `eye + direction * radius`: 640 for position, 430 radially, 28 for
  velocity matching, plus a 5200 gaussian "crosshair gate" that pulls each link through the crosshair
  line as the wave passes it, all scaled by a tip gain of 1.18 and capped at 4400.

Because the radius is measured from the eye, the tip reaches up to 1.15 + 8.64 ≈ 9.8 blocks, and when
the direction wave sweeps 136 degrees the tip is flung through a huge arc on its own.

Outside a lash the whip still uses this mod's own rope (gravity, constraints, ground collision), so it
rests as a fully extended whip hanging from your hand.

## Notes

Ported from **Better Whips** (MIT, my2167592261-cell) - see `LICENSES/BetterWhips-MIT.txt`. The contact
sweep, the damage model and the renderer are unchanged and remain this mod's own.