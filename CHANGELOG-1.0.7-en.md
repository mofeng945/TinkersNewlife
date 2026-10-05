# Tinker's Newlife 1.0.7

## Fixed

### The whip is a Verlet rope pinned to a swinging hand - the missing piece was the hand anchor

Your description of the last build was exactly right: a straight rope appeared, then a slow left/right
wave crept from the root to the tail and the rope was pulled back before the wave finished. That is what
my previous build did - it drove the rope through an eye-centred direction fan and never moved the hand.

Reading the reference's per-substep loop made the real mechanism obvious:

* `predict()` is an ordinary **Verlet rope** with `GRAVITY = -21.5` and
  `TICK_VELOCITY_RETENTION = 0.989`, but `points[0]` is **pinned hard to the anchor** - not sprung to it.
* `precisionHandAnchor()` is the part I had never ported: the hand itself swings. Windup raises it
  0.68 blocks and pushes it 0.16 to the side; the stroke then accelerates (`pow(t, 1.55)`) to 0.86
  forward, 0.08 below and 0.10 across; the recovery settles back to 0.20 forward.
* `TrainerStylePrecisionGuide` is only an **extra** force, applied from 30% progress onward - it is a
  refinement, not the mechanism, which is why my port of it alone could never throw the whip.

So the whip is a Verlet rope plus a hand that actually swings 1.1 blocks through a diagonal arc in four
ticks, with the root pinned to it. That is what drags the rope out and snaps the tip.

The root also stays pinned through the whole lash (until 130% progress) instead of snapping back to the
idle pose, so the wave is no longer cut off mid-flight.

## Notes

Ported from **Better Whips** (MIT, my2167592261-cell) - the rope integration, the hand anchor and the
guide; see `LICENSES/BetterWhips-MIT.txt`. Contact sweep, damage and renderer remain this mod's own.