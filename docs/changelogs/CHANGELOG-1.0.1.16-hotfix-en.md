# Tinker's Newlife 1.0.1.16-hotfix

A hotfix on top of **1.0.1.16**. Everything from 1.0.1.16 is unchanged except what is listed below.

## Fixes

### The slingshot can now be assembled at the Tinker Station / Tinker's Anvil

The slingshot's station data was incomplete, so the tool could not be put together in the
crafting interface even though the item itself existed. This is now shipped in full:

* **Station layout** (`tinkering/station_layouts/slingshot.json`): three input slots —
  two `tconstruct:bow_limb` (bow limbs) and one `tconstruct:bowstring` (bowstring) —
  laid out exactly like Tinkers' own longbow, with its own `sortIndex` that does not
  collide with any other tool of this mod.
* **Tool definition** (`tinkering/tool_definitions/slingshot.json`): part list
  `bow_limb / bow_limb / bowstring`, primary part 0, random tier-1 default materials,
  base 120 durability / 0 attack damage / 1.0 attack speed, 1.25x durability multiplier,
  1 ability slot + 3 upgrade slots.
* **Text**: the group title, the per-slot labels (bow limb / bow limb / bowstring) and the
  group description now exist in both `zh_cn` and `en_us`, so the interface shows real names
  instead of raw translation keys.

Verification done on this build: the game log reports `Loaded 38 station slot layouts` and
`Loaded 70 tool definitions` with **no error line for `tinkersnewlife:slingshot`**.
The `Missing textures ... _tconstruct_unknown` warnings in the same log are normal Tinkers
behaviour for a tool whose display stack carries no materials yet (several other tools of this
mod, including the yoyo, produce the same line) and do not affect the crafting interface.

## Notes

Nothing else was changed. This hotfix contains no new items, no balance changes and no
recipe changes beyond making the slingshot reachable in the station interface.
