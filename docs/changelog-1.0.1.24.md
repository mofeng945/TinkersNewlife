# Tinker's Newlife 1.0.1.24 Changelog

> **New dependency:** The Momo boss model/animations are now powered by **GeckoLib** —
> `geckolib-forge-1.20.1-4.8.4` is required (Goety already depends on it, so most packs have it).

---

## Fixes

### 1. Startup crash with Iron's Spellbooks (irons_lib) — the reason for this release
- Both mods hooked the same "does charging slow you down" logic in `LocalPlayer`, and the losing
  injection aborted the game during startup (`@Redirect conflict ... InjectionError`).
- Reworked so we no longer compete for that injection point: no more conflict,
  and **spear charging still never slows you down** (works even with irons_lib installed).

### 2. Momo (boss)
- **Fake death** (lose health on one hit, refill on the next, only the Infinity sword could kill it) — fixed:
  while the health core still reports it alive, every death mark is undone automatically.
- **Killed by the Infinity sword while the clones protect it** — fixed: the boss cannot die while clones live.
- **Clones couldn't lose health** — fixed: clones no longer inherit the boss damage cap / invulnerability / rebound.
- **Clones no longer gain L2Hostility levels or traits**, and already-applied attribute bonuses are stripped.
- **Breath's Requiem healing** (only healed after being hit, stalled around 80%) — fixed: heals toward full
  every second and always tops up before the domain ends.
- **Skills failing during hit-stun** — changed: a skill now starts right after the stagger ends (no missed cast);
  only a spell that is *currently being cast* gets interrupted by a hit.
- **Ghost boss bar** (leftover bar after changing dimension / walking away) — fixed (server cleanup + client fallback).
- **Requiem hexagram invisible** — fixed: the ground hexagram renders correctly now (thicker, brighter).
- **Comet Kick pose sticking / model parts flying apart** — fixed.

---

## New content (Momo boss — work in progress)

- **R'lyeh's Shadow**: summons 3 clones (half the boss's max health, no damage cap, no protection,
  tagged as minions). **The boss is invulnerable until all clones are killed.**
  One clone prefers to wander (melee/ranged AI still in development).
- **Comet Kick**: ~2× speed straight dash, then a diagonal kick on impact (20% of attack damage)
  with heavy knockback; hitting a wall adds blunt damage based on impact speed. Dash leaves afterimages.
- **GeckoLib migration**: model and animations are now loaded from resource files —
  walk, blink (with afterimages), hurt, resonance, discordant chord (raise + swing),
  Requiem (enter + loop + exit), kick.

---

## Notes / known issues

- The Momo boss is **not finished** (melee/ranged AI and some skills are still being tuned).
  Please test it in a test world rather than relying on it in a main save.
- With irons_lib installed, a `already redirected by irons_lib` WARN in the log is normal and harmless.
- A log line `[长矛] 蓄力减速已跳过（常量改写通道生效）` means the spear no-slowdown logic is active.
