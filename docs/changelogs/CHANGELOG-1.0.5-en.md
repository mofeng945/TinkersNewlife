# Tinker's Newlife 1.0.5

## Fixed

### The whip is fully extended, so the lash actually throws it

The previous build coiled the whip into a helix **as the start of every lash** and only released it a
third of the way in - so the 3 to 4 tick stroke had to unroll 8.6 blocks of rope and simply could not,
which is why the lash never looked like it threw the whip.

The resting pose is now a **fully extended whip**: it hangs from your hand and trails behind, exactly
like a whip you are holding, and there is no coil pull at all during a lash. That reference mod's coil
constants are an idle flourish (they only trigger after 100 ticks - five seconds - of standing still),
not a wind-up, which is what I had misread.

On top of that the hand now swings a wider arc (radius 0.95 -> 1.25 blocks) and the root spring is
stiffer (220 -> 320), so the motion actually carries all the way to the tip instead of leaving part of
the rope behind.

## Notes

The pose is settled by the simulation itself in the first couple of ticks (gravity plus ground
collision), so the whip drops into a natural drape rather than a neat coil. The coil helper methods
are kept in the code but marked unused, for a future "reel the whip back in while idling" flourish.

Still ported from Better Whips (MIT, my2167592261-cell) - see `LICENSES/BetterWhips-MIT.txt`.