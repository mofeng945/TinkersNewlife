# Tinker's Newlife 1.0.2

## New

### The Whip - a Tinkers whip with a physically simulated lash

A new tool built at the Tinker Station from **a tough handle, a large plate and a bowstring** - all
three are standard Tinkers parts, so nothing new has to be patterned or cast.

Its lash is not a hitbox: it is a **simulated rope** (25 points, spring driven at the handle, distance
constraints carrying the motion out to the tip, ground collision, 8 sub steps a tick). Only sections
that sweep through a target fast enough deal damage, and the damage scales with how fast that section
is moving - so a proper crack hurts far more than a lazy swing.

* **Left click** lashes out along your crosshair.
* **Hold right click for 2 seconds** and release to slam the ground, releasing a shockwave that hits
  everything around you and knocks it away.
* Within one lash each target is hit once; follow-up targets take 100% -> 50% -> 25% -> and so on.
* A direct melee hit still works at point-blank range, so the whip never feels like it misses.
* The handle material names the tool, and the plate/handle materials feed durability and attack.

## Notes

The lash is drawn as a tapered ribbon that always faces the camera, and it is simulated on the server
(the damage) and on the client (the visuals) from the same inputs, so no per-tick point syncing is
needed. Textures are generated placeholders - overwrite them freely.