# Tinker's Newlife 1.0.1.24-hotfix5

Fifth hotfix on 1.0.1.24 (previous: `1.0.1.24-hotfix4`). Fixes only, no new world content.

---

## 1. Villager trading

- **Discounted prices showed the original cost and trades could not be completed.**
  Cause: we rewrote the server-side `MerchantOffer.specialPriceDiff` every second, while the client
  kept the offer copies it received when the menu opened, so the displayed price disagreed with the
  price the server charged.
- Now: the offers are adjusted when the player right-clicks the villager (before vanilla's
  `startTrading` sends the offer packet), re-applied when the menu opens, and re-sent once per second
  while the discount/markup is active - so the displayed price always equals what is consumed.
- The 1.9x markup (alignment <= -45) and the reset back to normal prices are handled the same way.

## 2. Ktulu Momo

- **Head did not follow the look direction.** GeckoLib only exposes `netHeadYaw` / `headPitch` to the
  model and never rotates bones itself; the model now adds the clamped yaw/pitch offset to the head
  bone (Mob defaults: +/-75 deg yaw, +/-40 deg pitch).
- **All boss health checks now read the kernel authority** instead of `getHealth()`
  (boss bar, Requiem / R'lyeh Shadow thresholds, death guard comparisons, kernel internals), because
  other mods can wrap `getHealth` as `min(realHealth, maxHealth + delta)`. External display still uses
  the per-tick mirror sync plus delta neutralization.

## 3. Logs and performance

- **TConstruct "non-modifiable tool" warning flood fixed**: the Durandal migration hook used to build a
  ToolStack for every inventory item every second (~11 lines/s, 90k+ lines and 24 MB per session).
  It now checks `IModifiable` first; the same class of issue in the transcendent-dimension armor scan,
  the saber damage helpers and the bound-trait check was fixed too.
- **Title log noise reduced**: `[titles] active=... prefix=...` changed from INFO every 20 ticks to
  DEBUG only when the prefix actually changes.

## 4. Build / packaging

- **Fixed "fixes never reached the jar"**: `singleJar` zipped stale classes from
  `common/build/classes/java/main` first with duplicates excluded, shadowing fresh
  cursed/imagination classes. The stale classes are cleaned now and the merge order is
  modules-first/common-last; this release was verified class-by-class with `javap`.

---

## Notes
- Compile-verified plus static/decompiled cross-checks; **not individually play-tested**.
  Please check: villager 1/10 price display and payment, price reset when leaving the threshold,
  Momo head tracking, and Momo health bar / phase behaviour.
