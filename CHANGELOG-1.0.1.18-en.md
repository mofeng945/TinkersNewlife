# Tinker's Newlife 1.0.1.18

> 2026-10-06 | Requires Forge 47.4.22+ | Minecraft 1.20.1

This is the big **Fumo doll** release: dolls became a skin system (drop in an image and a new doll
appears), they dance to music, and there is a new disc you can play yourself. It also fixes a batch of
in-game issues, including the witch-wand losing its focus when switched with Goety's own radial wheel,
and Soul Eater putting Soul Hunger on you.

---

## Added

### Fumo doll skins

* **Extracted a base class**: "what a doll looks like" now has exactly one definition, shared by the
  block, the inventory/held item and the worn doll (helmet slot and curios).
* **Automatic skin scanning**: `assets/tinkersnewlife/textures/fumo/` is walked **at startup** - drop in
  `<name>.png` (a 64x64 player-skin-style image) plus a translation key `item.tinkersnewlife.fumo_<name>`
  and the doll registers itself on the next launch. No code change needed.
* **Ten dolls ship in this release**: **Mofeng Baizhi fufu** (default), **Yuejin fufu**, **Tiaodengjian fufu**,
  **Summy fufu**, **Yanying fufu**, **Bian Cangqiong fufu**, **mogumogumo fufu**, **Xiyu fufu**,
  **`_yueye_ fufu`** and **Yunli fufu**.
* **Their own creative tab**: every doll now lives in a dedicated **Tinkers' New Life · fumo** page; the
  main tab no longer mixes dolls in with everything else.
* **Names follow the skin**: both the item name and the name **Jade** shows use that skin's own
  translation key, instead of one shared name.
* **8-way placement**: a doll can be placed on the four cardinals and the four diagonals (it used to be
  cardinals only). Dolls already standing in an existing world keep exactly the look and facing they had.

### Dolls dance to music

* **Friends' Wine integration**: when that mod's doll starts playing music, every one of our dolls within
  **16 blocks** spins and does a jelly squash - on the **same rhythm** as that doll (the same 0.91667-second
  cycle, one full turn and two squashes per cycle, the same compression and width ratios) - and stops
  within about a second of the music stopping. This is a **soft dependency**: without that mod everything
  starts up normally with no errors.
* **New music disc "朋友的酒🍺DJ版"** (track by **kkr**): drop it into a **vanilla jukebox** and every doll
  within 16 blocks dances too - this one needs **no** other mod installed.

### Other integration work

* **Severing recipes for every linked mod's mobs** (454 of them), then narrowed by gear so boss equipment
  is not farmable (415 left). Trophies, music discs, saddles and materials such as leather, scales, bones
  and ectoplasm are deliberately kept.
* **Dual-title apostles**: double health; damage is multiplied by another 1.5 on top of the configured
  multiplier (1.5 by default, so 2.25 in total); their loot table is rolled **twice**.

---

## Fixed

* **Witch wand: switching focuses with Goety's radial wheel made the focus vanish.** Our own "focus pouch"
  kept mirroring its stored focus back over the staff's slot. That whole self-made layer (custom screen,
  J/R keybinds, two packets, the mirror chain, the periodic correction and the auto-combo) has been
  **deleted** - the focus now lives only in Goety's native staff slot and is managed by Goety's own wheel
  and focus bag.
* **Soul Eater** now **imitates Goety's own Soul Eater enchantment**: only melee kills (or kills by your own
  projectile) count, for souls x(level+1). The old implementation also caused Soul Hunger on yourself and
  doubled only sometimes; both are gone (souls now go through Goety's `SEHelper.increaseSouls`), and a
  one-time repair fixes soul state left broken by the old code.
* **A placed doll showed the wrong name**: the block entity now reports its own skin to **Jade**.
* **Breaking a doll dropped nothing**: the block had no loot table and no drop override; it now drops the
  doll **with its own skin**, and creative pick-block returns the same.
* **Held/GUI dolls had no outer hair layer**: the item path used vanilla's hat thickness (0.25px, invisible
  at the doll's scale) - it now uses the same thickened model as the block and the worn doll (0.6px).
* **The disc's grey description line showed twice**: vanilla `RecordItem` already adds `item.<id>.desc`,
  and this mod's generic tooltip handler added it again; only vanilla's line remains.
* Cleaning up dangling tag entries that pointed at removed items.

---

## Changed

* Doll break particles are now **grey wool**, for every skin.
* Dolls stay still when no music is playing, and stop immediately when the music stops, the disc is taken
  out or the jukebox is broken.
* The self-made focus-pouch screen and its keybinds are gone - use **Goety's own radial wheel** and focus bag
  for witch mode.

---

## Notes

* The Friends' Wine integration is not required to play; it simply does nothing when that mod is absent.
* Disc audio `doll_music.ogg` is provided by the pack author and ships with the mod (track by **kkr**).
* To add a skin: put `<name>.png` into `assets/tinkersnewlife/textures/fumo/` and add
  `item.tinkersnewlife.fumo_<name>` to both `zh_cn.json` and `en_us.json`, then restart.
  **Export as PNG** - JPG has no alpha channel and shows a solid background.

---

## Added later the same day

* **Fumo dolls can be cut into each other** on a stonecutter: any doll in, any other of the ten out.
  (When you add a new skin later, remember to add its own stonecutting recipe as well - skins are scanned at runtime.)
* The disc **"朋友的酒🍺DJ版"** can now be found in **End City treasure chests** (**20%** chance).
* **Ancient Cursed Scrolls** only appear in **chest-type** loot tables and can **never be fished up**
  (if you ever caught one on an older build, that was the bug this build removes).* **A new Fumo row in Momo's trade screen**: once your favour with Momo reaches **30**, the trade screen gains a
  final row - **1 Gheloth Remains** for **1 Mofeng Baizhi fufu**. Below 30 favour the row is **not shown at all**,
  and normal trades never shift to the wrong offer. The existing "first time you reach 30 favour, one fufu is
  gifted" behaviour is **kept**.

* **Ancient Cursed Scrolls are now three times rarer in chests** (about 40% before, about **13%** now).
* **A search box for the smeltery / foundry fluid list**: no more hunting one bar at a time. It matches
  **Chinese names, registry names, `@mod`, `#tag`** and the JEI syntax (**space = AND, `|` = OR, `-` = exclude**);
  **matching fluid bars get an amber outline** and the rest are hidden, and **clicking a bar still picks that
  exact fluid** (no index shift). When **Just Enough Characters** is installed it also matches **pinyin**
  (full pinyin and initials); without that mod there is **no pinyin search**.