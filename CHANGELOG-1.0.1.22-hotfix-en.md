# Tinker's Newlife 1.0.1.22-hotfix

> 2026-10-11 | Requires Forge 47.4.22+ | Minecraft 1.20.1

A quick hotfix for 1.0.1.22. Only `mod_version` changed in `gradle.properties`.

## Fixed

* **Items no longer merge in the inventory** (the most visible one: freshly fished fish would not stack).
  The per-tick affinity scan called `getOrCreateTag()` *before* checking whether the item was a curio,
  so **every** item in the inventory was given an empty `{}` tag. Vanilla compares the raw tag
  (`{} != null`), so tagged and untagged copies of the same item could never stack. The scan now only
  reads the tag first and writes nothing unless the item really is a curio and really rolls affinity.
* **Existing items heal themselves.** The old marker left by earlier versions is stripped from any item
  that has no real affinity value, so items already stuck in your inventory will merge again within a
  second of being held.
* **The recipe book can fill recipes again** (it requires matching stacks, so it was hit by the same
  tag noise).
* **Fishing no longer yields the Ancient Cursed Scroll.** The scroll injector only checked the table
  *path*, but Celestial Artifacts adds `celestial_artifacts:chests/fishing_treasure` to
  `minecraft:gameplay/fishing`, so that fishing-only table was being given the scroll. Fishing tables
  are now skipped entirely.
* **Faster startup.** The long-short-blade model wrapper used to scan and copy the entire baked-model
  map (hundreds of thousands of entries in a large pack); it now tries a few explicit keys first and
  only falls back to the scan if none match. The nameplate diagnostic log was also moved off the
  render hot path (it was writing 1000+ lines per session).

## Notes

* Only `mod_version` was touched; `mod_license` stays `MIT`.
* Not verified in-game by the assistant: the stacking fix and the fishing fix are code-reviewed and
  build-verified, and were reported fixed by playtesting feedback.
