# Tinkers' New Life 1.0.1.23

## New: Miner's Vision modifier
- A new harvest-tool modifier that **highlights every `forge:ores` block in range with its own texture, visible through terrain** (client-side only, so only you see it).
- Range grows **5 blocks per level**, up to level 5 (25 blocks).
- The first level consumes **1 ability slot**; later levels only consume materials.
- Each level costs **20 Nether Quartz + 20 Diamonds + 20 Gold Ingots + 20 Emeralds**.
- Can only be applied to harvest tools.

## Fixed: portable devices could open but never craft
- The ghost station block entity was created without a level, and Tinkers' crafting result bails out immediately when `level` is null, which is why the GUI worked and nothing could ever be crafted.
- The client never had the station registered in its virtual table, so the crafting recipe could never reach it.
- Recipes are now delivered to the client by our own packet, calling Tinkers' own `updateRecipe`, bypassing the position-based sync that can never find a pocket-position station.
- Station contents are now **saved on every slot change** instead of only when the screen closes, so a crash with the GUI open no longer risks losing items.

## Changed: disarm rework
- Disarming now drops the items immediately instead of storing them: mobs drop both hands to the ground, players get the items moved into their inventory (dropping to the ground only when the inventory is full).
- While disarmed, the victim **cannot pick up any item**, and anything that ends up in either hand is moved to the inventory again.
- Items dropped by disarm **cannot be destroyed by lightning, explosions, fire, lava or cacti** (protection ends as soon as they are picked up).

## New: Dovahkiin trait
- A new **level-less** trait: your dragon breath attacks deal **true damage** to dragons of other elements, and half of it to dragons of the same element.
- Attached to the dragonsteel weapons and to dreadsteel.

## Dragonsteel updates
- Dragonsteel armor now resists Ice and Fire dragon breath: each set is **immune to its own element** and takes **50% less** from the other two; **dreadsteel armor is immune to all three**.
- Dragonsteel weapons now mix in a matching **dragon breath damage** on hit (5 × trait level), using Ice and Fire's custom damage types.
- Dreadsteel sword qi counts as an elementless dragon breath only for the Dovahkiin true-damage segment; everything else stays magic damage and cannot be resisted.

## Other fixes
- Fixed the NBT tag count flickering between 9 and 10 on items: the affinity normalizer only looked at the legacy key while affinity now lives in `AttributeModifiers`, which made it strip the "already rolled" marker every second.
- Fixed freshly obtained items (including fished fish) never stacking, by no longer writing an empty tag onto every item.
- Repository housekeeping: all changelogs moved into `docs/changelogs`, stale branches removed.
