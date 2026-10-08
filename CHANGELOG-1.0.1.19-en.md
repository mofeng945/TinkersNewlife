# Tinker's Newlife 1.0.1.19

> 2026-10-08 | Requires Forge 47.4.22+ | Minecraft 1.20.1

This is the **"Malicious Star Spirit" release**: a full crossover set for Goety x Enigmatic Legacy
(a new ingot, four new fluids plus an alloy chain, a new Tinkers material, three new traits and a
complete waiver of the Seven Curses), plus a **fluid search box** for the melter / alloyer that
supports Chinese input and pinyin. It also fixes a server crash caused by self-recursive dragonsteel
explosions, a batch of whip feel issues, and several problems reported from testing.

---

## Added

### New material: Malicious Star Spirit (requires both Goety and Enigmatic Legacy)

* **Sinister Glow Ingot**: a 16x80, 5-frame animation (4 ticks per frame, exactly 1 second per loop).
* **Four new fluids**: Liquid Ectoplasm (300), Liquid Evil (1200), Watcher Ectoplasm (1800),
  Flowing Stardust (2000), plus **Molten Sinister Glow** (2000) and **Molten Etherium** (1950).
  Textures reuse the vanilla Tinkers molten base with a palette derived by luminance percentile.
* **Alloy chain**: ectoplasm + evil essence + astral dust + void echo + watcher ectoplasm +
  **molten etherium** produce **molten sinister glow** (1850 mb in, 360 mb out, 2000 degrees).
* **Obtaining it**: two Goety **forge ritual** recipes (master forge when Goety Revelation is present,
  plain forge otherwise; identical materials and cost), each yielding **4 ingots**.
* **Full melting / casting / re-casting** set, plus re-casting for the three source materials.
* **Tinkers material "Malicious Star Spirit"** (tier 4, Enigmatic Legacy required): all 12 part stats
  sit **5%-10% below Divine Gold**, and it works for **head, handle, binding, all four armor plates,
  maille, shield plating, bow limb and bow grip**.
* **Colours in both places**: `mantle/colors.json` (name text colour) and
  `assets/.../tinkering/materials/malicious_star_spirit.json` (part/tool tinting, seven-step palette),
  with the palette keeping **both the purple and the light blue**.

### Three new traits

* **Seven Curses Bound** (general, **no level**): usable only while wearing the Cursed Ring and having
  spent **at least 99%** of your time in the world under the curses. Otherwise items carrying the trait
  in hand / armor / offhand / curios are moved back into the inventory (or dropped) every 20 ticks.
  Playtime is read from **Enigmatic Legacy's own vanilla statistic** first (so it can be tweaked with
  `/scoreboard` while debugging), with a self-counted fallback.
* **Spiritual Ether** (tools, including ranged): kills randomly grow **one of seven stats**
  (durability, attack damage, attack speed, mining speed, accuracy, velocity, draw speed),
  0.1%-1.5% per level, capped at **1000%**; each level adds **+0.5 block reach**, and hits have a
  1% per level chance to apply **Frost** and other debuffs.
  Melee tools only grow stats they actually have (the check uses Tinkers' `tool_actions` module, so
  only bows and crossbows grow the ranged stats).
* **Transcendent Dimension** (armor): kills grow a random stat (durability / armor / toughness) per
  piece and **always** add all-type damage reduction (0.05% per level per kill, capped at 80%).
  At **4 total levels** all Seven Curses are **waived** (tooltip lines struck through and replaced).

### Complete waiver of the Seven Curses (at 4+ total Transcendent Dimension)

Doubled damage taken, neutral mobs attacking you, -30% armor effectiveness, -50% damage to monsters,
eternal burning, soul shattering on death and incurable insomnia are **all waived**.
The soul-shattering curse no longer spawns crystals; items follow the normal death drop logic.

### Melter / Alloyer: fluid search box

* **JEI syntax** (space = AND, `|` = OR, `-` = exclude, `@mod`, `#tag`), plus matching on Chinese names,
  English ids and display names, plus **pinyin** when a pinyin search mod is present.
* Typing Chinese directly through the IME works; matches are re-packed compactly and stay highlighted.

### Other additions

* Momo at 30+ favour unlocks a trade row: 1 Gheloth Remains for 1 Mofeng Baizhi fufu.
* **fumo stonecutting**: skins can be converted into each other (10 recipes).
* **Two new fumo skins**: **78lin fufu** and **Luochen fufu**.
* **New docs**: `docs/穿透与穿甲全流程.md` (pierce / armor-pierce flow) and
  `docs/善恶系统·条目表.md` (conscience system tables, plus a plain-text version).
* Conscience value caps now grow over time: cap 0 at first, +2 per in-game day, up to 50.

---

### JEI variant folding

* **Multiple NBT variants of the same item are folded into one JEI slot** (the slot shows `1/N`;
  hover it and scroll to cycle through the variants).
* Covers **all mods**: one interpreter is registered for every item in the game, but items
  **without NBT are never folded** (it returns JEI's "no subtype"), so ordinary items are unaffected.
* Granularity: **Tinkers tools and parts fold by material combination** (`tic_materials`), so damage,
  installed modifiers and custom names do not create extra entries; other mods fold by whole NBT.

## Fixed

* **Server crash from self-recursive dragonsteel explosions**: the fire effect dealt manual `hurt`
  damage with an attacker-owned explosion source, which re-triggered its own `LivingAttackEvent`,
  causing chained explosions and a `StackOverflowError`. A recursion gate was added; effect strength
  is unchanged.
* **Whip feel**: overshoot swinging behind you after a lash, the tip sinking into the ground, the swing
  direction being fixed to one side, a new lash spawning instead of reusing the live one, and lashes
  not benefiting from bowstring ranged modifiers (the whip was not in `tconstruct:modifiable/ranged`).
* **Whip mark**: the next lash deals **+10% damage per stack** (up to 8 stacks, x1.8) without consuming them.
* **Removed the hard Patchouli dependency**: `GUIDE_BOOK` is now optional both at declaration and at
  runtime, falling back to a plain item instead of failing the whole mod load.
* **Ice and Fire community edition compatibility**: the `mods.toml` version range was widened to `[1.0.0,)`.
* fumo placed from a stonecutter no longer reverts to the default skin when broken and replaced.
* Water poured during a merged Twin Ring domain can now be cleared after the merge ends.
* **Ancient curse scrolls**: chest drop rate cut to one third, and they only appear in chest loot
  (no longer from fishing).
* The music disc now has a **20%** chance in End City loot.
* The Shift stat-growth tooltip now **appends to Tinkers' existing stat line** instead of adding a
  second line, and melee tools no longer show "draw speed".
* Several own mistakes were fixed and documented: `perStat` **overrides** rather than merges `default`,
  material traits **stack per part** (which is what showed a level), reading vanilla stats threw an NPE
  until the ids were registered in `CUSTOM_STAT`, and material render files live under `assets`, not `data`.

---

## Notes

* Not everything in this release has been verified in game: the material and its parts, the four fluids
  and the rituals, the eight curse waivers, the three traits and their tooltips, the fluid search box
  and the two new skins all deserve a pass in game.
* Implementation details for whips, piercing and the conscience system are in the dedicated documents
  under `docs/`.
