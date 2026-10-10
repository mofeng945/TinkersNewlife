# Tinker's Newlife 1.0.1.21 — Changelog

Cumulative since 1.0.1.20. Only `mod_version` was changed in `gradle.properties`.
Items marked "not yet tested in-game" were implemented to spec and pass the build / JSON
validation, but have not been verified by playtesting yet.

## 1. Cursed Techniques — True-Damage Pipeline Rework
- Removed the second, anonymous `hurt` call. The dummy showing three damage numbers was
  caused by an extra synthetic hit; it is now a single `hurt` plus a direct shortfall write.
  The client-sync suppression (`quiet`) was tightened to "skip the sync only when the
  target's health resets itself" (e.g. dummies), so the Apostle immunity window can be
  broken again.
- `EXTRA_HEALTH_CHANNEL = false`: disabled the extra-health channel (RevelationFix's
  accessor returned 666 on the Apostle and corrupted `startHp` / `dealt`, which showed up
  as "the Apostle takes no damage").
- `forgetEntity(UUID)` now clears `LAST_START_HP / DAMAGE_OWED / SHADOW_HEALTH / PENDING`.
  All probes were demoted to `LOGGER.debug` (27 info + 2 warn -> 30 debug).

## 2. Cursed Techniques — Recall / Seal Hardening
- `GourdJailEntity.forcePurge` now uses `TruePierce.rawSetHealth(living, 0.0F)`.
- `finishSeal` rollback restores `hpBeforePurge` and calls `cancelPurgeRecheck`.
- New 20-tick cross-tick recheck (`schedulePurgeRecheck` / `tickPurgeRecheck`, driven from
  `ServerLevelTickTailMixin` at TAIL).
- `CursedSpiritTechnique.recallReleased` now schedules a recheck and calls `forgetEntity`;
  `ApostlePatch.TRAP_COOLDOWN` entries are cleaned up on expiry;
  `ShikigamiHandler.recallAll` was renamed to `estimatedRecallRefund` (it was dead code).

## 3. FumoMo Doll — Dance
- Each block entity remembers its own `discStyle`; new `DISC_SOURCES` registry (by dimension
  and position) plus `discStyleNear()` (nearest source within 16 blocks). When two discs play
  at once, the doll now follows the nearest one.
- Dance sequence: one-second loop with no pause; `ND_TURN = 45`, `ND_LEAN = 15`
  (`ND_LEAN_SIGN = -1`), and a small upward bounce while turning, `ND_BOUNCE = 0.022`.
  Note: direction and bounce amount are not yet tested in-game.

## 4. Music Discs
- New disc "Little Wish" (`music_disc_tell_me`): audio at `sounds/music/tell_me_tell_me.ogg`,
  `ModSounds.MUSIC_TELL_ME(+_ID)`, `ModItems.MUSIC_DISC_TELL_ME` (`LENGTH_TICKS = 1324`),
  Chinese/English language keys, and the `music_discs` item tag.
- Texture: all 14 frames of the source GIF had their background removed (only pixels that
  were already transparent were cleared, plus a shared bounding box to avoid jitter),
  producing a 16x224 vertical strip of 14 frames with
  `music_disc_tell_me.png.mcmeta` (`frametime: 2`).
  Note: an early pass used corner-colour flood fill and also erased the fish's black
  headphones; that has been fixed.
- Language keys: name `Little Wish`, description `遥 - 塞壬唱片`.
- Obtainability: new loot modifier `little_wish_from_fishing`
  (`minecraft:gameplay/fishing`, `chance 0.01`) — a 1% extra fishing drop that does not
  replace vanilla fishing output. Registered in
  `data/forge/loot_modifiers/global_loot_modifiers.json`.
- JEI: two acquisition info pages — "friends' wine = 20% from End City chests" and
  "Little Wish = 1% from fishing" (with matching language keys).
  Note: both code-side and `sounds.json` attempts at changing disc volume had no effect
  (jukebox playback is streamed), so they were fully reverted; the audio file itself stays
  the single source of truth for volume.

## 5. Smelting and Materials
- Ancient cursed scroll -> curse residue: `amount` raised from `1` to `5` (mB)
  (`recipes/smeltery/melting/ancient_cursed_scroll.json`).

## 6. Ancient Cursed Scroll — Refresh Rules and Use
- Refresh rules: `AncientCursedScrollItem.roll()` now rolls part by part over the six
  `CursedSpeechRegistry.Part` slots, with at most one word per part; the hit chance decays
  by x0.62 each step, so more words means rarer. If nothing was rolled, a fallback word is
  granted for the first exclamation slot.
- Left-click chanting: left-clicking a target while holding the scroll triggers
  `onLeftClickEntity`, which runs `castFromScroll`:
  - a missing slot is filled with a random word from the two lowest rarity tiers of that
    part, and the word is learned first so the validity check passes;
  - curse power cost is multiplied by 5 via the `tnl_scroll_cast` flag;
  - the scroll is consumed only when the chant actually starts (`CurseChant.isChanting`).
- Air swings: new `PacketScrollCast` plus the client-side `ScrollAirSwingHandler`
  (`InputEvent.InteractionKeyMappingTriggered`) so swinging at air also casts.

## 7. Engineering and Tooling
- The temporary diagnostic probes written during this round have been removed.
- Cleanup: 38 temporary `tools\_tmp_*` files and directories were deleted (no existing
  artwork was touched).
- Added `tools/_add_jei_lang.py` (writes Chinese language keys through Python to avoid the
  `pwsh -Command` encoding pitfalls).

## Verification
- `check-json`: bad 0 (2070 files in common).
- `gradlew singleJar`: BUILD SUCCESSFUL.
- Artifact: `tinkersnewlife-1.0.1.21.jar` (15,632,859 bytes), deployed to both instances.
- Not yet tested in-game: the true-damage and seal changes, the new doll dance, the
  "Little Wish" obtainability and animated texture, the scroll refresh rules and left-click
  chanting, and the two JEI info pages.
