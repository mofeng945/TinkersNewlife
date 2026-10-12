# Tinker's Newlife 1.0.1.14-hotfix

A hotfix on top of **1.0.1.14**, plus one new weapon. Everything from 1.0.1.14 is unchanged
except what is listed below.

## New

### The Tang Hengdao — a new modular saber (sponsor weapon)
A Chinese-style saber, forged at the Tinker Station like any other Tinkers tool.

* **Five parts**, all standard Tinkers parts: Small Blade + Broad Blade + Large Plate +
  Tough Binding + Tough Handle (no custom part recipes needed).
* **Base stats** 80 durability / 3 attack damage / 2.8 attack speed / 0 mining speed,
  with multipliers 1.1 / 0.8 / 1.2 / 0.1.
* **Built-in trait "Soldier's Saber"** (level-less):
  * While held in the main hand you can attack — and interact with — creatures **1 block farther**
    (entity reach only; block reach is untouched).
  * Within 4 blocks, every hit **chains extra stages** depending on how close you are:
    **0** stages at 4 blocks, **1** at 3, **2** at 2, **3** at 1, and **4** at point blank.
    Each stage lands as its **own separate hit** for 50% of the tool's attack panel — so the closer you
    stand, the more damage instances you get, each with its own damage number.
  * Every stage also swings its own **grey arc slash** — a real crescent-shaped slash effect
    (SlashBlade-style), not the vanilla sweep particle. Each stage's slash has its own angle,
    size and offset so they read as a combo instead of a single blur.
* Full art included, and **broken textures are generated** for it as well.

## Fixes

### Level-less traits no longer show a level
Traits that are meant to be "you have it or you don't" were displaying a level
(for example `Soldier's Saber I`). Tinkers accumulates material traits per part, so a trait
sitting on two armour parts could even read `II`. Fixed for:

* Soldier's Saber (Tang Hengdao)
* Void Touch, Ender Power (Void Metal)
* Aristocratic Dining, Earplugs, Hardened Skin

### Tool models: in-hand poses fixed
Five tool models referenced a Tinkers parent model that **does not exist**
(`tconstruct:item/base/default`), so they fell back to hand-written transforms. They now use
real parents, matching Tinkers' own tools:

* War Scythe, Tang Hengdao → the Cleaver's base (`item/base/tall`)
* Curse Core, Silent Glove, Yo-Yo → Forge's `item/default-tool`

### Keybind hints now follow your keybind settings
The flying-sword tooltip told you to "press R" while the actual default bind is **Z** — and it kept
saying R after you rebound it. Every hint that names a key now reads your **current** binding, so it
updates the moment you change it in the controls screen. Default-key suffixes were also removed from
key names (the controls screen already shows the bound key, so the hard-coded one only ever went stale).

### Shift tooltips removed
The extra tooltip lines that some traits added while holding **Shift** are gone (over 30 traits).
Trait names and descriptions are still shown by Tinkers' own tooltip.

## Configuration

* **`conscience.enabled`** — new, at the **top** of `config/mofengbaizhi/tinkersnewlife-common.toml`,
  default `true`. This is the good/evil ("Heart") system toggle. With it turned **off**:
  * you never get a **Heart** equipped on login (an existing one is removed), and
  * your alignment is always treated as **0**, so no good/evil rules, thresholds or attribute
    changes apply.
  Your stored alignment is **not** deleted, so turning the option back on restores your progress.

## HUD

* The HUD layout screen (**F6** by default) now has a **Heart HUD toggle** — turn the heart icon
  above your hotbar on or off freely. The setting is saved next to the HUD position and width.

## Content

* The **war scythe** got redrawn part textures.
* Texture variants and broken textures were regenerated for the **war scythe** and the
  **Tang Hengdao** (383 and 381 material variants respectively, each with matching broken art),
  so every material now has its own look and its own damaged look.

## Notes

* Requires Minecraft 1.20.1, Forge 47+, Tinkers' Construct 3.11.2.166+ and Curios.
* Create, Mekanism, AE2, Iron's Spellbooks, Enigmatic Legacy, Vampirism, Ice and Fire,
  Patchouli, Kaleidoscope (森罗) mods, Twilight Forest and Xaero's maps are all optional —
  install them and the related features light up; leave them out and the mod loads normally.
