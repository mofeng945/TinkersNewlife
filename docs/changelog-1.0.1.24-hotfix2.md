# Tinker's Newlife 1.0.1.24-hotfix2

Second hotfix on top of 1.0.1.24 (previous one: `1.0.1.24-hotfix`). Mostly bug fixes and feel changes, no new world content.

---

## Compatibility / crashes

### 1. Startup crash with Iron's Spellbooks (irons_lib) — and spear charging still ignores slowdown
- Both mods hooked the same "does charging slow you down" logic in `LocalPlayer`; the losing injection aborted startup.
- Reworked so we no longer compete for that injection point, and **spear charging still never slows you down even with irons_lib installed**.

### 2. Dragonsteel (fire / frost / lightning) now strictly follows Ice and Fire
- Materials, recipes and fluids were already `mod_loaded`-gated; this build also gates the **trait effects**
  so nothing half-applied remains when Ice and Fire is absent.

### 3. "Seven Curses Bound" (Malicious Star Spirit material) ejected gear even when the time condition was met
- Root cause: our own self-counted suffering ratio (and a broken stat-read gate) disagreed with Enigmatic Legacy's own accounting.
- Now we **prefer Enigmatic Legacy's authoritative check** (`isTheWorthyOne` / suffering fraction) and only fall back to the old logic when its API is unavailable. A `[七咒]` diagnostic line is logged if gear is still ejected.

---

## Feel / gameplay

### 4. Merchant Momo
- **Always retaliates when hit** (creative or survival, regardless of her current target);
- **Aggro decays**: she gives up 30 seconds after the last hit (hitting her again refreshes it);
- **Immune to her employer's damage** (melee and arrows): no damage, no grudge, no retaliation;
- **Each purchase gives +5 favor** (was +1);
- **Daily refresh now re-rolls the whole offer list** (previously easy to mistake for a simple restock);
- **Hire renewal is explicit**: the hire screen shows "currently hired (N days left)", the button reads "renew N days", and renewing has a clear message + sound.

### 5. "Heart" (conscience) toggle moved to a game rule
- Removed from the config file; use `/gamerule doConscienceHeart true|false` (default on), saved with the world.

### 6. Modular staff crosshair
- While holding the modular staff and a Dynamic-Crosshair-style mod is installed, a vanilla crosshair is always drawn at screen center.

### 7. Dragonsteel Frost hits now freeze like ice dragon breath
- Uses Ice and Fire's own frozen state (ice statue, cannot move or attack); the old random frost-ice blocks were removed.

---

## Tooltips / cooldowns

### 8. Restored two accidentally removed tooltips
- **Dragon Blood Tank**: shows "Dragon blood X/Y mB" (Shift expands the three blood types);
- **Dragon Staff**: restored the stored-dragon tooltip (mode, per-slot dragons, slot count, attack bonus, Shift hint).

### 9. Dragon Staff cooldown uses the vanilla item cooldown
- No more "skill cooling down, N ticks left" spam; the hotbar slot shows the vanilla cooldown sweep instead.

### 10. Log spam cleanup (also in the previous hotfix)
- The construct screen no longer spams `Tool stack constructed using non-modifiable tool` hundreds of times per second.

---

## Growth / spell levels

### 11. "Spiritual Ether" now gains growth from ranged kills
- Ranged kills are correctly credited, and bows/crossbows can finally grow draw speed / velocity / accuracy.

### 12. "Origin Magic" fixes
- **Curios (gloves, flying sword, etc.) now count** (previously only main hand / offhand / armor were scanned);
- **Trait level is no longer clamped to 3**: an 8-level knight shield now grants **+16 spell levels** instead of +6.

---

## Notes
- Everything above was built successfully and statically verified but **not verified in-game**. Please try:
  irons_lib starts fine and spear charging is fast; merchant retaliation/decay/friendly-fire/daily offers/renewal;
  ranged kills grow the bow; gloves/flying sword grant spell levels; frost hits freeze like ice dragon breath.
- Version `1.0.1.24-hotfix2` — replace the old jar, do not install both.
