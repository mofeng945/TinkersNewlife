# Tinker's Newlife 1.0.1.16-hotfix4

## Changed

### The sword qi arc is a flat arc again - its circle centre is now aimed at the player

The previous build bent the arc mesh into a shallow spherical cap to make it "open" towards the
player. That is not what was wanted: the arc is a flat fan-shaped texture and it should stay flat, and
what has to face the player is the **centre of its circle** - the arc tip (its convex side) away from
the player, its centre (the concave side) towards the player.

So the bending is gone (the vertices are coplanar again, exactly as the texture expects) and the only
thing that changed is the arc's in-plane rotation:

* The arc now takes the direction from the blade light to its caster, projects it into the arc's own
  plane, and solves the rotation angle so that the arc's tip points exactly **away** from the caster -
  which puts the circle's centre on the caster's side.
* If that direction is degenerate (the caster sitting right on the arc's normal, so the plane has no
  usable "away from the caster" direction), it falls back to the angle the blade light was spawned
  with instead of spinning off.
* Because the orientation is now decided by the caster's position, the per-stage preset angles
  (-62/28/-26/66 degrees) and the Dreadsteel sword qi's random 360 degree spin are gone; both now
  request only a small +/-4 degree jitter so a combo does not look like one single cut. The stages are
  still distinguished by their mirroring, their size and their offsets.

## Notes

Nothing else was changed. The tang Hengdao and the Dreadsteel sword qi share this arc, so both are
fixed at once.
