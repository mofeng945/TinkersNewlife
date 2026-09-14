# Tinker's Newlife 1.0.1.12

**Minecraft 1.20.1 · Forge 47.4.22+ · Tinkers' Construct 3.11.2+** (soft-requires Goety)

A curse-power-storage and ritual release: two new containers — the **Curse-Sealing Bottle** and the **Curse Vault** — plus the **Curse Crafting Ritual**, a multiblock that spends curse power itself to craft special gear.

---

## Highlights

### Curse Storage: the Bottle and the Vault
- **Curse-Sealing Bottle** — a wearable curse container (curio "Trinket" slot): stores up to **5,000** curse power, shown on its durability bar, and **keeps its contents on death**. It is also a fluid container, so a smeltery drain can fill it with Curse Residue.
- **Curse Vault** — a placeable 100,000-point curse storehouse that **binds the placer as its user**. Indestructible (bedrock-level hardness, blast-proof, pistons cannot move it), and picked back up with an empty hand while keeping every stored point of curse power.
- **Curse Residue** — a new fluid: **1 mb = 10 curse power**. Ancient Cursed Scrolls can be melted in a smeltery for it, and both containers accept and pour it back.
- **Spending is now one cascade everywhere**: curse core → worn bottle → bound vaults, and only then Goety soul energy (1 curse = 3 souls). Every technique, domain, summon and construct checks *and* spends against that same total, so an empty core no longer blocks you while your bottle and vaults are full.
- **Crosshair readout** — aim at a Curse Vault and its stored curse/capacity appear right under the crosshair (above 10,000 shown as e.g. `1.2w`).

### Curse Crafting Ritual (new multiblock)
- A **5×5×3** structure around a **Gheloth Ore**: nine seared lanterns, seared brick walls, soul fire, and a modifier worktable ring. The Patchouli guide ships a **visualisable sample structure** you can ghost-plot in the world.
- Put materials on the eight lanterns, hand the core old-god item to the lantern above the ore, and the ritual **drains 10 curse power per tick** until the product is done. Recipes are plain JSON (`data/tinkersnewlife/recipes/curse_craft/`) and show up in **JEI** (catalyst: Gheloth Ore).
- Two recipes ship with it: **Curse-Sealing Bottle** (500 curse) and **Curse Vault** (3,000 curse).
- Materials are matched **exactly**: one extra item means no match — nothing is ever consumed on a mismatch, and interrupting the ritual floats every material back to its own lantern.

### Construct (擬造) Hardening
- **Constructed blocks keep their nature**: re-picking up a constructed Curse Vault now returns a *constructed* item with its original expiry (previously it turned into a real, permanent vault). When a constructed block expires it dissipates with **no drop**.
- **Constructed Tinkers' gear is real gear again**: Tinkers' recipes report a bare item, so a constructed tool/armor used to come out with no materials at all. The construct now copies the materials from the **same item you are wearing or carrying** (falling back to Tinkers' own random-material builder).
- **Fixed**: the constructed RevelationFix Divine Gold armor was built as a proxy item and therefore could not be equipped or rendered — such items are now constructed as real copies.
- No more "Constructed Air" shells: if a target cannot be resolved, construction refuses instead of handing you an empty item.

### Jade Integration
- Aiming at a **Curse Vault** now shows **who its owner is** (plus stored curse and residue) in Jade, using Forge's login-name cache — no extra data stored, works offline, and self-heals for old saves.

### Iron's Spells & Modular Staff
- The modular staff can **cast Iron's Spells again**: the soft-dependency was checking a mod id that never matched (`ironsspellbooks` vs `irons_spellbooks`), which silently disabled the staff's whole spell feature.

### Black Bird Manipulation: look-direction flight
- **W / S** now fly along your **full look direction** — look up to climb, **look down to descend** (previously the only vertical input was Space, so the bird could only go up). A / D still strafe horizontally, Space still gives an extra ascent, and Shift is still the dive-bomb.

### The Unnameable Effect Now Glitches Your Screen
- Being **Unnameable** no longer means just black fog and a swaying camera: the whole screen now suffers **signal interference** — horizontal tearing bands with a red/cyan chromatic split, static noise, a rolling interference bar sweeping downward, and irregular white/dark screen flashes.
- The pattern is deliberately **re-drawn only a few times per second** (instead of every frame), so it reads like a failing display rather than smooth snow; intensity scales with the effect's level.
- Config: `unnameable.glitch` (on/off) and `unnameable.glitch_intensity` (0–3, default 1). The black fog and camera sway are unaffected by these keys.

## Fixes
- **Ritual interaction** could do nothing at all because the lanterns were expected one block too high; the guide's structure page now matches the code exactly.
- Floating ritual materials used to drift up out of reach and become unretrievable; the core lantern also reported "0 materials" even with a full setup.
- Right-clicking an empty lantern no longer steals the material from a neighbouring one.
- The **Curse Vault** could not be picked up in survival (vanilla skips block interaction while sneaking with a full offhand) — pick-up now runs off the interaction event, works with an empty main hand regardless of the offhand, and left-click works too.
- Using a bottle on a vault could **duplicate** Curse Residue; pouring/drawing is now a plain fill/drain with the container written back.
- A stray trailing comma in the language files made **every mod text show up as a raw key** — fixed, and both language files are now validated strictly on every change.
- Servant/creative-tab polish: no more material-variant spam for slime skulls, and creative-tab tools fall back to Tinkers' own "first available material" logic instead of producing broken parts.
- A hard crash (`The stack count must be 1`) when a registry lookup returned air is gone — all registry lookups now go through a null-safe helper.
- Fixed a mid-ritual crash when the ritual structure was edited, and made all ritual state clean up safely on world unload.

## Guide & Documentation
- New Patchouli guide entries for the **Curse-Sealing Bottle**, **Curse Vault** and **Curse Residue**, plus **recipe pages** for both containers (core item, exact material list, curse cost and duration) linked to the ritual entry.
- The ritual entry includes a **multiblock page with the "Visualise" button**.
- **272 broken guide links** were fixed, and four items that were missing English names now have them.

## Under the hood
- Integration code is grouped per mod (`integration/<modid>/`) behind `ModList` checks — no `Class.forName` probing, no hard dependency on optional mods.
- Startup self-checks now **warn about misspelled mod ids** (both this release's `irons_spellbooks` and the construct risky-mod list's `goety_revelation` were silently dead for exactly that reason), and the blueprint compatibility report flags config entries that match no registered namespace.
- The Modrinth upload script now reads the version straight from `gradle.properties`, and the deploy script removes older mod jars so two versions can never load side by side.
