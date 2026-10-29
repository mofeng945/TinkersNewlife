# Tinker's Newlife 1.0.1.24-hotfix3

Third hotfix on 1.0.1.24 (previous: `1.0.1.24-hotfix2`). Fixes only, no new world content.

---

## 1. Durandal: "Unbreakable" replaced by our own "Indestructible"

### Durandal tools could not be modified
- Cause: Tinkers' native `unbreakable` is designed to be earned, and carries a prerequisite check
  (Reinforced V + Netherite). We granted it as an **innate material trait**, so a freshly built tool
  had the trait without the prerequisites and failed validation every time — **no upgrades or
  modifications were possible**.
- Durandal now uses our own trait "**Indestructible**": the tool **never loses durability and never breaks**,
  with **no prerequisites**, so it can be upgraded normally.
- **Existing tools are migrated automatically** (inventory / armor / offhand are checked and rebuilt).
  Players' legitimately earned vanilla Unbreakable is left alone.

---

## 2. Staff and spells

### Staff could not cast spells stored in worn spell containers
- Cause: Iron's Spellbooks requires a spell to be "learned"; spells **inscribed** with the Arcane Anvil /
  Super-Tier Magic / Magic Conduction were selectable in the wheel but rejected as unlearned at cast time.
- Now: **a spell inscribed in any spell container you hold or wear (including curios) counts as learned**,
  so the staff (and Iron's own implements) can cast them.

### Super-Tier Magic no longer ignored on curios
- It only scanned main hand / offhand / armor and **missed all Curios slots** (gloves, flying sword…).
- Curios are scanned now; a rate-limited diagnostic log was added for troubleshooting.

### Super-Tier Magic no longer overwrites other level boosts
- It used to push the final level to "max level × 2", **wiping other mods' additions**.
- It now computes a delta from the **base level** and adds it, so other sources are preserved.
- Added a last-priority cast-time floor guard: if something lowers the level afterwards,
  the Super-Tier promise is restored — **only raised, never lowered** for higher values from others.

---

## 3. Miner's Vision recipe

### The level-1 recipe shadowed the upgrade recipe
- Both recipes use the **same item types** (only amounts differ), and Tinkers picks the first recipe that
  matches materials regardless of the tool's current level — so the level-1 recipe always won and
  upgrading to levels 2–5 was impossible.
- Added a modifier recipe type with a **current-level range**: the level-1 recipe only matches at level 0,
  the upgrade recipe only at levels 1–4.
- Amounts unchanged: 20 of each for level 1, 30 of each for levels 2–5 (×1.5).

---

## 4. Development environment (no gameplay impact)

### `gradlew runData` fixed
- Fixed datagen in dev: GeckoLib runtime dependency, mixin refmap properties for the `data` run, and loading
  the multi-module `shared` output (excluding the sibling modules' entry classes).
- Note: this mod currently has no datagen providers, so runData neither generates nor validates recipe JSON.

---

## Notes
- Everything above is **build-verified and statically checked, not verified in-game**. Please test:
  1. old Durandal sword: trait becomes "Indestructible" and the tool **can be upgraded**; durability never drops;
  2. staff casting spells inscribed on armor/curios;
  3. Super-Tier Magic working when only gloves/flying sword are worn;
  4. Miner's Vision upgrading from level 1 with 30 of each item;
  5. Super-Tier Magic coexisting with other spell-level mods without overwriting them.
- Version `1.0.1.24-hotfix3` — replace the old jar, do not install both.
