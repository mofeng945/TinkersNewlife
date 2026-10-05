# Tinker's Newlife 1.0.1.16-hotfix5

## Changed

### The sword qi arc now really is a circle centred on the player

The arc is a flat fan-shaped texture, so the fix is not to bend the mesh - it is to put the **centre
of its circle on the player** and let the arc wrap around them. That is what this build does:

* The ring centre is the caster's position.
* The radius is the distance from the caster to the blade light, so the arc sweeps at exactly that
  distance around the player. It is clamped on both ends: a minimum so a point-blank hit cannot put
  the centre on top of the arc, and a maximum so long-flying Dreadsteel sword qi does not turn into a
  ring dozens of blocks wide (far away the centre just sits between the player and the qi).
* The arc's plane contains the caster, so the centre is on the player's side: concave towards the
  player, convex away from them.

### The thin-line problem is solved by tilting, as you suggested

A plane that contains the player and the arc is edge-on when the player looks straight at it, which
is why this used to degenerate into a line. The arc's span is therefore tilted about 20 degrees off
the "straight at the target" direction, which makes the plane visible with a factor of
`sin(20°) = 0.34` - clearly readable, not a line, and not glaring either. The tilt is a single
constant, so a smaller value aims the arc more exactly at the target (and looks thinner), a larger
value looks fuller (and sits further from the target).

## Notes

Two consequences worth knowing:

* With the tilt, the arc's plane no longer passes exactly through the target - it sweeps roughly
  `0.34 * radius` blocks beside it (about 1 block at 3 blocks range). The band itself is wide, so it
  still reads as "cutting through" the target.
* The arcs are now concentric around the player instead of billboarded to whoever looks at them, so
  the stages differ by size, mirroring, radius and a small in-plane swing rather than by big preset
  angles.
