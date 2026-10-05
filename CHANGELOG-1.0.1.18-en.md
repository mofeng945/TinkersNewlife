# Tinker's Newlife 1.0.1.18

## Changed

### The stone shot shatters when it lands

Fired stones no longer stick in the ground waiting to be picked up. The moment a shot hits a block
it bursts into block-break particles - the particles of the stone that was actually loaded, so a
cobblestone shot gives cobblestone chips - and then the projectile disappears.

* 24 block-break particles are spawned at the exact impact point with a small spread, so it reads
  as a puff of chips rather than a single dot.
* The stone was consumed when the shot was fired either way, so nothing is lost compared to before:
  you just no longer have to walk over and pick the shot back up.
* Shots that hit a creature still behave like vanilla arrows (they deal damage, then fall); they
  shatter when that fall ends on the ground.

## Fixed

### Leftover item tags pointing at removed items

Five tag files and two tag entries still referenced items that no longer exist (dangling tag entries
are silently ignored by the game, but they are noise in the log and in tooltips):

* Removed five tag files that existed only for the removed weapon - the `seram/slashblade` tag,
  and the four `casts/multi_use|single_use/{blade,sheath}` tags.
* Removed the last `katana` entry from `minecraft:swords`, and the `blade` / `sheath` entries from
  `tconstruct:parts`.

## Notes

The slingshot's own ammo tag (`tinkersnewlife:slingshot_ammo`) was reviewed and is correct: it
covers `#forge:stone` and `#forge:cobblestone`, plus vanilla fallbacks. The slingshot needs no
further tags - Tinkers recognises modifiable items through its tool definitions, not through tags.
