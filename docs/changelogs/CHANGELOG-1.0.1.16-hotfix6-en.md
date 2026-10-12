# Tinker's Newlife 1.0.1.16-hotfix6

## Fixed

### The blade lights no longer lie on one line

With the arc centred on the player, every stage was using the same plane - the previous build had
reduced the per-stage angle to a small in-plane swing, so a four-hit combo drew four arcs on top of
each other and read as a single line.

The per-stage angle is now the rotation of the **whole arc plane** about the player-to-target axis, so
each strike sweeps through a different plane and a combo fans out around the target.

* The Tang Hengdao's four stages use the spread angles again (-62 / +28 / -26 / +66 degrees, plus a
  small random jitter), and the Dreadsteel sword qi rotates each arc it leaves behind by a random
  angle along its flight.
* This cannot make any arc look thin: the visibility factor of a tilted arc - `sin(TILT_DEG)`, i.e.
  the `0.34` from the previous build - does not depend on the rotation angle at all, so every stage
  stays equally readable no matter where its plane is turned.
* Mirroring, band width and the golden-angle spawn offsets are unchanged, so the stages are still
  distinguishable even where the planes cross.

## Notes

Nothing else was changed. If the arcs should also sit at slightly different distances (instead of all
at exactly the same radius), that is one more per-stage factor on the radius - just ask.
