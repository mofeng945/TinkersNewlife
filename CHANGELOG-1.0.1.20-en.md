# Tinker's Newlife 1.0.1.20

> 2026-10-09 | Requires Forge 47.4.22+ | Minecraft 1.20.1

This is the **"Long-Short Blade"** release. It completes the new weapon type introduced in the previous
version: **per-context × per-form models**, **material tinting**, a **dash-based thrust** and **thrust
afterimages**. It also fixes a **duplication exploit** with the offhand "partner blade", fixes the
slingshot's missing upper limb, and adds an **automatic Tinkers part-texture generation** step at startup.

---

## Added

### Weapon: Long-Short Blade (dual wield + two forms)

* **One tool, one blade in each hand**: while the main hand holds it, a "partner blade" is placed in the
  offhand with the **opposite form** (long / short).
* **Vanilla `F` (swap offhand) is the form switch** — the form travels with the item stack, so no extra key.
* **Long form**
  * Alternating swings (main hand and offhand take turns; the offhand swing uses its own animation path).
  * Melee hits build **fever** (1 per hit, capped at 100).
  * **Thrust**: dash forward for 160% damage, invulnerable during the dash.
  * **Kill aura**: continuous damage within a **2 block** radius; the player model **raises both arms and
    spins rapidly** while active.
* **Short form**
  * **Hold right-click to charge**, release to **throw** (a projectile is thrown; the weapon itself **never
    leaves the player**). On impact it detonates a **3 block non-destructive explosion** that counts as the
    player's kill (loot / XP work normally).
  * **Execution**: targets below **20%** health take **500%** damage, with a **left-arm swing**.
* **Every skill costs durability**: thrust 2 / aura 10 / execution 3 / throw 2 (through Tinkers' own
  durability API, so Reinforced and similar modifiers are respected).

### Per-context × per-form models (four texture sets)

* Inventory **not-held**, inventory **long**, inventory/held **short**, held **long** — four independent looks.
* Implemented with custom `ItemOverrides` plus `applyTransform`: **context** (inventory / in hand) and
  **form** (long / short) are resolved in two separate stages.
* **Material tinting**: all four context models go through Tinkers' material resolution, so the textures
  change colour with the material.

### Thrust afterimages

* While dashing, the server broadcasts **position and rotation every 2 ticks** to every player that can see
  the entity.
* The client caches the afterimages; each lives **0.35 s**, fades by its remaining lifetime, and is drawn
  **again with the same model**.
* **Your own afterimage is hidden in first person** (visible in third person).

### Automatic part-texture generation at startup (configurable)

* New config option **`auto_part_textures.enable_auto_part_textures`**, **on by default**.
* On reaching the **main menu** it runs the Tinkers part-texture generator once in
  **"missing only"** mode.
* If **nothing was generated** (0 entries) it does **nothing at all** (the resource pack is left untouched).
* When something was generated, the generated resource pack is **force-enabled and moved to the top**.
* ⚠ Tinkers' generator is **not fault tolerant per entry**, so one failure aborts the whole run. We wrap it
  in exception handling, so it **never crashes the game**, and the reason is written to the log.

### New material variant textures

* Added the **`neptunium` / `promethium` / `malicious_star_spirit`** (and others) part textures for tools and
  armour — over 1700 files.

---

## Fixed

### The slingshot's upper limb was not drawn

When the slingshot's parts were changed to one tool handle + one bow limb + one bowstring, the item
model was only told about three of the bow's four geometry pieces. A geometry piece that is not
listed in a Tinkers tool model's `parts` list **is not rendered at all**, so the upper limb was
simply missing from the item.

* The model now lists all four pieces: `limb_bottom` and `limb_top` both map to the bow limb part
  (there is only one limb part, so both limbs share its material and keep their own textures),
  `grip` maps to the tool handle and `bowstring` maps to the bowstring.
* On top of that, the broken model still carried the old indices: its upper limb pointed at the tool
  handle and it had no `grip` entry at all, so a broken slingshot was drawn without its handle. Both
  models now use the same mapping.

### Offhand "partner blade" duplication

Taking the offhand blade used to trigger an immediate refill, allowing infinite copies. The rule is now
**"leaving the offhand destroys it, and an empty offhand refills it"** — the refill is always the single
blade, so the total count never increases.

### Partner blade vanishing from the main hand after swapping hands

The main hand is actually the *selected hotbar slot*, which is inside the inventory scan, and it was being
cleared as a stray. Both hands are now excluded (slot index plus instance identity).

### Thrust afterimages never appeared

The broadcast used "players tracking this entity", but a player does **not** track itself in vanilla, so
single-player received nothing. It now uses "tracking this entity **and itself**".

### Thrust is no longer a teleport

It now **applies forward velocity** and lets the engine move the player (collisions, steps and fluids are
handled by vanilla).

---

## Notes

* ⚠ **Not** included in this version: per-entry skipping when the Tinkers generator fails (Tinkers only has a
  single outer `try`, so its loop cannot be fixed from outside; it would require calling it per material
  instead). Left for a follow-up.
* Harmless pre-existing log noise: `Missing textures in model …` and
  `[模型] 没找到 tinkersnewlife:item/spear`.
