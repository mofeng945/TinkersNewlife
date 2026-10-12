# Tinker's Newlife 1.0.1.22

> 2026-10-11 | Requires Forge 47.4.22+ | Minecraft 1.20.1

This release is headlined by the **portable device family** — every Tinkers workstation can now be
carried in your pocket, and the **Toolkit** stitches six of them together behind Tinkers' own tab bar.
It also adds the **cursed titles** system, reworks **curse affinity**, fixes a **JEI crash** on
Tinkers multi-module screens, and puts the project on the **MIT** license with a proper README.

Only `mod_version` (and `mod_license`) changed in `gradle.properties`.

---

## Added

### Portable Devices (carry your workshop)

* **Six portable devices**, each opening the real Tinkers interface anywhere:
  `portable_crafting_station`, `portable_tinkers_anvil`, `portable_part_builder`,
  `portable_modifier_worktable`, `portable_cast_chest`, `portable_part_chest`.
* **Contents live in the player's persistent data**, never in the item's NBT, so a portable chest
  cannot be used to duplicate its contents.
* The devices are **ghost block entities**: no block is ever placed in the world. The block itself
  creates the entity (`EntityBlock#newBlockEntity`), which keeps the real slot counts on both sides.
* **Portable Tinker Station was removed** as requested; the anvil covers that role.

### Portable Toolkit (six devices in one)

* **Shapeless recipe from the six portable devices**; opens the crafting-station interface.
* **Tinkers' own tab bar**, drawn by `TinkerTabsWidget` from `Menu.stationBlocks`: we fill that list
  ourselves, so all six devices appear as tabs with **their own icons**.
* **Clicking a tab opens that device**, and switching tabs keeps the tab bar visible.
* The tab bar only appears inside a **toolkit session**, so opening a single portable device directly
  does not show the other tabs.

### Cursed Titles

* **15 titles with rarities**, displayed as `[title][title]playerName`, keeping only the **three
  rarest** active ones.
* **Five unlock conditions** (binding rituals, curse crafting, puppet core, merchant kill, cursed ring)
  plus dynamic **sorcerer rank** derived from maximum curse power.
* **Title picker screen**: single vertical list, mouse-wheel paging, rarity colours, a picked counter,
  and a "restore automatic" button; reachable from the **HUD editor**.
* Titles update **in real time** (compared every 20 ticks; the cached display name is refreshed and
  re-synced when the prefix changes).

### Curse Affinity

* Affinity is **rolled once per item the first time it reaches a player** (marked in NBT), so
  creative-spawned curios get it too, and dropping and picking an item back up never re-rolls it.
* Written with `Slot=curio` so Curios renders a single "When worn as a curio" block; legacy
  slot-less vanilla entries are **stripped every tick** so the six bogus tooltip lines disappear.
* Shown as one `Curse Affinity +N` line, with the value kept in our own NBT key.

### Recipes and Art

* **Each portable tool = its prototype block surrounded by eight scorched bricks**;
  the prototype is matched **through a tag** (`tinkersnewlife:portable_prototype_*`), so adding a
  variant later only means editing the tag.
* **Toolkit = the six portable devices, shapeless.**
* Seven new **16x16 icons** for the portable devices (each model now points at its own texture).

---

## Fixed

* **JEI crash on Tinkers screens** (`width must be >= 0`): Mantle hands JEI a negative-width exclusion
  rect while a multi-module screen is mid-layout; negative rects are now clamped to zero.
* **"Connection lost" on world load**: the title-sync packet is server-to-client but was registered with
  the client-to-server helper; it now uses `registerClientPacket` with a delayed login sync.
* **Portable chests closed instantly / threw `ClassCastException`**: chest contents live in an
  `IItemHandler`, not a `Container`, so the inventory is routed through the capability.
* **Menus opening and closing at once**: `stillValid` is overridden, because the ghost sits far from the
  player and the server closes the screen on the next tick otherwise.
* **Slot-index crashes** on portable GUIs: unified ghost creation and an explicit side count in the
  menu buffer.
* **Silent Glove** no longer prints an extra ring-slot line in the middle of its tooltip (Curios already
  shows the slot modifier at the bottom).
* **Missing language keys**: `item_group.tinkersnewlife.curse_core.description` and
  `item_group.tinkersnewlife.long_short_blade.description` were showing as raw keys.

---

## Engineering

* Adopted the **MIT license** (`LICENSE`, plus `license="MIT"` in all four `mods.toml` files and
  `mod_license=MIT`).
* Added a proper **`README.md`** (environment, build, deploy, module layout, conventions); the Forge MDK
  boilerplate `README.txt` was removed.
* **Workspace cleanup**: temporary decompile dumps and `bin/` removed (about 77 MB).
* `check-json`: **2092 files checked, bad 0**.

## Verification

* `gradlew singleJar`: **BUILD SUCCESSFUL**.
* Artifact: **`tinkersnewlife-1.0.1.22.jar`** (~15.2 MB), deployed to both instances, MD5 identical.
* Not yet playtested: the toolkit tab switching on a fresh world, the portable device recipes, and the
  new device icons.
