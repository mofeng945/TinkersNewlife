# Tinker's Newlife 1.0.1.24-hotfix4

Fourth hotfix on 1.0.1.24 (previous: `1.0.1.24-hotfix3`). Fixes only, no new world content.

---

## 1. Iron's Spellbooks trait overlap

- **Super-Tier Magic x Origin Magic** used to take the max spell level (only one applied) - they now **add up**.
- **Arcane Conduction / Blazing / Cold-Blooded** used to count only the single best piece - they now **stack per worn piece**, curios included.
- Multiple traits that inscribe spells into the **same item container** used to skip if a container already existed - they now **append** (dedupe + capacity check).
- Curios slots are now scanned for: Arcane Conduction, Blazing, Cold-Blooded, All Spells Unity, Arcane Domination, Inscription.
- **Arcane Domination (Magic Gold)**: previously a flat +100% regardless of piece count; now **+100% per worn piece** (3 pieces = +300%). Lang/guide text updated.

## 2. True damage and the boss health kernel

- **True damage could not change health at all on 1.20.1**: there is no `health` field (health lives in synced data), the old code wrote into `f_20920_` (actually the attack animation field), and a leaked "suppress sync" flag also skipped `setHealth`. Now we write `DATA_HEALTH_ID` directly, fall back to `setHealth` correctly, and reset the flag in `try/finally`.
- **Training dummy**: no more double damage / red "true damage" popup / dummy being killed. We bypass `setHealth` overrides by writing synced data, mirror the dummy's cached `lastHealth`, and skip the reverse write for self-accounting targets.
- **Removed two legacy targeted paths**: the Apollyon extra-health-channel reflection cluster and the Goety apostle low-health execute fallback. The true-damage path is now a single generic pipeline.
- **Boss health kernel hardening (no mod-specific code)**: the kernel reads synced health instead of trusting `getHealth()`; any external `setHealth` drop goes through the same per-tick 6% cap plus 1.5 s invulnerability window; NaN/infinity/negative values are sanitized; the death branch no longer uses `kill()` (which our own gate rejected, so a 0-health boss could never die).
- **Ktulu Momo health display / never dying (verified in game)**: some mod wraps `getHealth` as `min(real, maxHealth + delta)`, dragging displayed health negative. We now detect and zero that value every tick and force the mirrored health back to the kernel authority at the end of the server tick.

## 3. Ice and Fire link

- **Dragon blood bottle vs Tinkers seared tank / drain did nothing**: another addon ships duplicate fluid-transfer recipes for the same items but references **empty** fluid tags, so it matched first and resolved to 0 mB while still reporting success. We now populate `forge:molten_{fire,ice,lightning}_dragon_blood` with our still blood fluids, making those recipes work; our own transfer recipes remain as a fallback.

## 4. Art

- Ktulu Momo model: slim (3px) arms with explicit per-face UVs, and updated `kick` animation keyframes (author-provided).

---

## Notes
- Everything is **compile-verified plus static / decompiled cross-checks**; only the Momo health-delta fix has been **confirmed in game**, the rest is **not individually play-tested**.
