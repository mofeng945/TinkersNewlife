# Tinker's Newlife 1.0.1.16-hotfix3

## Fixed

### The sword qi arc was bent the wrong way

The arc of both the Tang Hengdao's blade light and the Dreadsteel sword qi is a flat mesh: the tips
sit at `sqrt(R^2 + (rad*sin76)^2)` from the player, which is **farther** than the middle of the arc
(for R = 3 and rad = 1.25 that is 3.24 against 3). The arc was therefore convex towards the player -
the exact opposite of what it should be.

The arc is now bent into a shallow spherical cap around the player: the two tips come `rad*(1-cos t)`
blocks **closer** to the player than the middle, so the same distance becomes 2.38 against 3 and the
arc is now concave towards the player and convex away from it.

* The blade light entity now carries the caster's position (synced, so every client can draw it), and
  both the Tang Hengdao's per-stage blade lights and the Dreadsteel sword qi's arcs pass it in.
* When no caster is known the arc falls back to bending towards whoever is looking at it, which is
  exactly right when the caster is the one looking.
* A safety clamp keeps the arc from engulfing the camera in point-blank hits.

## Notes

Nothing else was changed. Two constants control the look: the cap depth (1.0 is the exact spherical
cap; lower makes it flatter) and the point-blank depth clamp (higher keeps the arc further away when
you are right on top of the target).
