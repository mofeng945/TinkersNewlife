# Tinkers' Newlife 1.0.1.16

## New content

### New ranged tool: Slingshot
- Crafted at the Tinker Station from **two bow limbs + one bowstring**.
- **Hold right-click to draw** (the slingshot trembles while drawn, more the longer you hold) and release to fire.
- **Ammo: any stone or cobblestone in your inventory** (tag `tinkersnewlife:slingshot_ammo`, which includes `#forge:stone`, `#forge:cobblestone` and the vanilla fallbacks). Fired stones really fly and can be picked up again.
- **Airborne slam**: hitting an airborne target searches for the nearest land directly below it.
  - Land found: the target is forced down and takes a burst of **kinetic damage** the moment it hits ground or water (this shot's impact + the distance fallen, capped at 60).
  - Void below: the target is only tugged downwards for a moment and then released back to normal behaviour, with no damage - bosses such as the Ender Dragon are never dumped into the void.
  - **Works on creative players too**: the kinetic damage uses this mod's own damage type `tinkersnewlife:kinetic`, which carries `#minecraft:bypasses_invulnerability`, so creative invulnerability cannot stop it; creative flight is force-disabled so the target can actually be dragged down.

### Aquaculture 2 integration - Neptunium
- New material **Neptunium** (tier 3 / diamond level) with the **Power of the Sea King** trait.
  - Tools: in water or rain - no mining speed penalty, +50% attack speed, +60% damage.
  - Armor: water breathing, clearer underwater vision, +20% movement speed and -30% damage taken in water or rain.
  - Fishing rods: Luck of the Sea II + Lure I.
  - Ranged weapons: projectiles ignore water drag.
- Full smeltery support: melting for ingots/nuggets/blocks/tools/armor (durability-aware) and casting back into ingots/nuggets/blocks, plus a repair kit for anvil repairs. The molten fluid is registered only when Aquaculture 2 is present.

### Lava Fishing integration - Promethium
- New material **Promethium** (tier 4 / netherite level) with the **Heat Lover** trait, matching the original mod's armor effects 1:1: 25% fire damage reduction per piece, clear vision under lava, slow healing and speed while hot, and walking on lava.
  - Tools: +60% damage and faster mining while on fire or in lava.
  - Ranged: projectiles set their target on fire.
- Molten Promethium fluid (own textures, bucket and creative tab entry), melting recipes for ingots/nuggets/blocks/armor/slingshot/ammo and casting back into ingots/nuggets/blocks. Registered only when Lava Fishing is present.

### Sand casts everywhere
- All 27 casting recipes now ship **single-use sand cast** variants alongside the reusable gold casts (previously only gold casts existed), matching Tinkers' Construct itself.

### Patchouli guide
- New material entries: **Neptunium**, **Promethium**.
- New tool entries: **Slingshot**, **Spear**, **Rapier**.

## Fixes
- The apostle patch config now lives under `config/mofengbaizhi/tinkersnewlife-apostle.toml` like every other config file of this mod.
- Slingshot model and part textures wired up correctly (missing/incorrect model data previously made the item render as a missing model).