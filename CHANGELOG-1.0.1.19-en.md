# Tinker's Newlife 1.0.1.19

## Fixed

### A critical tag bug: one dangling entry was breaking whole Tinkers tags

The previous release removed a weapon from this mod but left references to its items behind in this
mod's tag files. In 1.20.1 a tag fails to load **as a whole** if any of its entries points at an item
that does not exist, and that failure then cascades along tag dependencies. In practice:

* `tconstruct:modifiable/durability` - and with it `aoe`, `bonus_slots`, `harvest`, `melee/sword`,
  `melee/primary`, `interactable/right`, `multipart` and `weapon` - failed to load, so tools from this
  mod (the Tang Hengdao, the rapier, the spear, the slingshot, the yoyo and the rest) silently lost
  their durability / weapon / melee tags.
* Those failures cascaded up into `tconstruct:modifiable` itself and dragged unrelated tags with them
  (`twilightforest:banned_uncraftables`, `parry:excluded_shields`).
* The same problem hit `tconstruct:casts/gold`, `casts/sand` and `casts/red_sand` because of the
  removed blade/sheath cast items.

Every dangling reference has been removed: the leftover `katana` / `blade` / `sheath` entries, the
removed blade and sheath cast items, and the references to the deleted `casts/.../{blade,sheath}` and
`seram/slashblade` tags. The affected files were rewritten as regular JSON.

Verified: all 184 tag files parse, and a cross-check of every `tinkersnewlife:` reference in the data
folder against the ids this mod actually registers comes back empty.

## Notes

Nothing else was changed. Tags are loaded when a world is loaded, so restart the game after updating
for them to be rebuilt.
