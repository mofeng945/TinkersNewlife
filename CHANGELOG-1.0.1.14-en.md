# Tinker's Newlife 1.0.1.14

This release focuses on **making optional mods truly optional**, **fixing the container-rate
statistics**, and **finishing the storage coverage for the Kaleidoscope (森罗) family** — plus
everything that was staged for 1.0.1.13, which was never published on its own (see the last
section).

## Highlights

### Create Is Finally a Real Optional Dependency
Wearing no Create used to crash the game on startup with
`java.lang.NoClassDefFoundError: com/simibubi/create/content/kinetics/base/KineticBlock`.
The energy converter used to hand back a Create-typed block from a class that is loaded on
**every** startup, which forced the JVM to load Create's classes even when Create was absent.
The Create-specific code now lives entirely behind isolated factories, and both converter
variants fall back gracefully (with a warning) if Create's API does not match — so the mod
loads fine with or without Create.

### Container Rate Statistics, Rebuilt ("Industrial Pioneer Certificate")
* **First scans are honest now.** A container seen for the first time in a chunk that has
  never been scanned is only used as a baseline — it no longer dumps a whole base's stock
  into the first hour as if it had just been produced.
* **Baselines stick.** A source that disappears for a while (unloaded chunk, missed sweep) is
  remembered, so when it comes back the real difference is reported instead of re-counting
  everything it holds.
* **Multi-block storage is counted once.** A 3×3×2 barrel used to have every one of its 18
  blocks report the same tank, so 12 buckets of wine showed up as 216. Storage is now
  attributed to the block entity that actually owns it.
* **AE2 disks are readable at all.** Disk contents were silently lost to a swallowed
  `NullPointerException` (a null save provider); disks are now read through three paths
  (drive API → internal inventory → Forge capability) with a no-op save provider.
* **The fluid page shows fluid sprites** (with proper tinting) instead of bucket icons.
* Extra diagnostics were added so a suspicious number can be traced back to the exact source.

### Kaleidoscope (森罗) Storage Is Now Tracked
Barrels, racks, cabinets, shakers, baskets, tables, trash cans, dragon-egg shells, record
walls… all report their contents now. That mod family registers almost no Forge capabilities,
so these blocks are read through a reflection-based provider (no compile-time dependency, and
it steps aside whenever a block does expose a real capability, so nothing is counted twice).

### Cognitive Mask: No More Nameplates
* The name's translucent background bar that appeared when a player was hidden behind a block
  is gone — it was drawn by Yes Steve Model's own renderer, which bypasses the Forge name-tag
  event; the mask now cancels the actual render call.
* The mask also wins against Enigmatic Legacy's Insignia, which force-*enables* your own
  nameplate: our handler now runs at the lowest event priority (Forge delivers to every
  listener and the last result wins).

### Dark Metal — Magic Resist Also Grants Effect Immunity
Wearing a Dark Metal armor part with **Magic Resist** now makes you immune to **Blindness**
and **Darkness**: new effects are refused outright, and any effect already on you is removed.

### Item NBT Cleanup, Done Vanilla's Way
* Stacks that carry an **empty** `{}` tag (left behind by mods that call `getOrCreateTag()`
  just to read) are now stripped back to the vanilla "no tag" state, which finally makes them
  stack with normal items again.
* The mod **no longer merges your inventory for you** — if you keep identical items spread
  apart on purpose, they stay that way.

### Twilight Forest Mazes + C2ME
`TFMaze`'s shared random source is now accessed through a synchronized wrapper, fixing the
"Accessing LegacyRandomSource from multiple threads" crash on C2ME worlds.

### Kill Credit for Cursed Techniques
Ownerless magic damage (techniques that damage without an attacker) now credits the caster, so
boss kills such as the Ender Dragon are counted correctly.

### Enigmatic Legacy's Ocean Stone No Longer Eats Night Vision
While the Ocean Stone / Mining Charm is worn, its per-tick night-vision removal is skipped
(configurable), so Vampirism night vision no longer flickers on and off.

### New: Wizard Armor Set
A full 4-piece Tinkers-style armor set with five material slots per piece, material-driven
colours, custom models and textures, and guide-book entries.

### New: Momo the Merchant
A complete rework: portrait dialogue, separate trade and hire screens, a per-player favor
statistic (affects prices and dialogue), hire durations, daily purchase limits, and a guide
book she hands out.

### New: The Heart of Conscience
A karma system with 38 tracked deeds, alignment thresholds and effects, a color-shifting HUD
heart, tooltips, and a guide-book entry.

### Cursed Techniques & Domains
Domains now have sound and visual identity, bind effects have four distinct modes, shikigami
and captured bosses got a long list of behavioral fixes, and the Curse Vault, curse bottle and
several curse crafting recipes were reworked with new art.

### New Curios
**Life Lamp Ring**, **Ring of One Mind** (paired rings sharing cursed energy), and the
**Industrial Pioneer Certificate** (binds to a dimension and grants attributes based on that
dimension's net production).

### Flying Sword
The sword is now rendered in the world render stage (so it is visible even with Yes Steve
Model's player renderer), is visible in first person and under your feet, and its trail has a
shader-pack-safe fallback.

### War Scythe: Its Extra Reach Works Now
The war scythe was always meant to reach farther, but it looked up an attribute name that does not
exist in Forge 1.20.1 (`forge:reach_distance`), so the bonus silently never applied. It now uses
**`forge:entity_reach`**: while held in the main hand you can attack — and interact with — creatures
**2 blocks farther**. Block reach (breaking/placing) is deliberately left untouched.

### Quantum Vault, Up to Tier 6
A searchable, paged count-based storage UI with six tiers.

### Iron's Spellbooks Integration
Several new Tinkers materials (Holy Spirit, Formless Ice, Blazing Gold, Orichalcum, Mithril,
Mana Gold), their fluids, casting/casting-adjacent traits, and smeltery/casting recipes.

### Also In This Release
* White Space portals: reliable linking, safer removal, and dismantling with the Inverted
  Spear of Heaven (including while sneaking).
* Player data (alignment, favor, counters) survives death again.
* Achievement/advancement and KubeJS/JSON fixes, plus a large cleanup of temporary
  diagnostics (four mixins and two utility classes removed).

## Carried Over From 1.0.1.13 (Never Published On Its Own)
* Unnameable no longer blinds you — it corrupts your screen
* Unnameable now tears the screen apart
* Unnameable whispers to you
* Domains now have a voice
* The Prison Realm can now be crafted
* Durandal's Sword: 256×256 art downscaled to a 64×64 sprite
* The Curse Bottle is now a proper jar
* The Curse Vault got a new look — an iron cage with living energy
* Two more curse crafting recipes: Playful Cloud & Inverted Spear of Heaven
* Boundary Fragments are ten times rarer

## Configuration
New or changed config options in this release:

* `item_tag_normalize` — strip empty `{}` item tags (default `true`). Stack merging is never
  performed.
* `enigmatic_curio_keeps_night_vision` — Enigmatic Legacy's Ocean Stone no longer removes
  night vision (default `true`).
* `twilight_maze_thread_safe_random` — make Twilight Forest maze RNG thread-safe (default
  `true`).
* `cognitive_mask.hide_from_radar` / `cognitive_mask.allow_retaliation` — as before.
* `converter_stress_impact` — Create stress cost of the energy converter.

## Notes
* Requires Minecraft 1.20.1, Forge 47+, Tinkers' Construct 3.11.2.166+ and Curios.
* Create, Mekanism, AE2, Iron's Spellbooks, Enigmatic Legacy, Vampirism, Ice and Fire,
  Patchouli, Kaleidoscope (森罗) mods, Twilight Forest and Xaero's maps are all optional —
  install them and the related features light up; leave them out and the mod loads normally.
